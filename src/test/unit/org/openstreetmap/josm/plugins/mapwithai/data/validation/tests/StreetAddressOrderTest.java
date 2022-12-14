// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Geometry;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class StreetAddressOrderTest {
    private static final String ADDR_STREET = "addr:street";
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new JOSMTestRules().projection();

    @Test
    void testVisitWay() {
        Way way = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        DataSet ds = new DataSet();
        way.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way);
        Node tNode = new Node(new LatLon(0, 0.00001));
        ds.addPrimitive(tNode);
        tNode = new Node(tNode, true);
        tNode.put(ADDR_STREET, "Test Road");
        ds.addPrimitive(tNode);

        tNode = new Node(tNode, true);
        tNode.setCoor(new LatLon(0.00001, 0.00001));
        tNode.put("addr:housenumber", "1");
        ds.addPrimitive(tNode);

        StreetAddressOrder test = new StreetAddressOrder();
        test.visit(way);
        assertTrue(test.getErrors().isEmpty());
        way.put("highway", "residential");

        test.visit(way);
        assertTrue(test.getErrors().isEmpty());

        way.put("name", "Test Road");
        test.visit(way);
        assertTrue(test.getErrors().isEmpty());

        tNode = new Node(tNode, true);
        tNode.setCoor(new LatLon(0.00002, 0.00002));
        tNode.put("addr:housenumber", "2");
        ds.addPrimitive(tNode);
        test.visit(way);
        assertTrue(test.getErrors().isEmpty());

        tNode = new Node(tNode, true);
        tNode.setCoor(new LatLon(0.000015, 0.000015));
        tNode.put("addr:housenumber", "20");
        ds.addPrimitive(tNode);
        test.visit(way);
        assertEquals(1, test.getErrors().size());

        test.clear();
        way.setDeleted(true);
        test.visit(way);
        assertTrue(test.getErrors().isEmpty());
    }

    @Test
    void testCreateError() {
        StreetAddressOrder test = new StreetAddressOrder();
        test.createError(new Node(new LatLon(0, 0)).save(), Collections.emptyList());
        assertTrue(test.getErrors().isEmpty());

        test.createError(new Node(new LatLon(0, 0)), Collections.emptyList());
        assertEquals(1, test.getErrors().size());

        test.clear();
        test.createError(new Node(new LatLon(0, 0)), Collections.singleton(new Node(new LatLon(1, 1))));
        assertEquals(1, test.getErrors().get(0).getHighlighted().size());

        test.clear();
        test.createError(new Node(new LatLon(0, 0)), Collections.singleton(new Node(new LatLon(1, 1)).save()));
        assertTrue(test.getErrors().get(0).getHighlighted().isEmpty());
    }

    @Test
    void testConvertAddrHouseNumberToDouble() {
        assertEquals(24.5, StreetAddressOrder.convertAddrHouseNumberToDouble("24 1/2"));
        assertEquals(24.5, StreetAddressOrder.convertAddrHouseNumberToDouble("24.5"));
        assertEquals(24, StreetAddressOrder.convertAddrHouseNumberToDouble("24"));

        assertEquals(25.5, StreetAddressOrder.convertAddrHouseNumberToDouble("24 3/2"));
        assertTrue(Double.isNaN(StreetAddressOrder.convertAddrHouseNumberToDouble("Not a number")));
    }

    @Test
    void testCheckOrdering() {
        List<IPrimitive> primitives = new ArrayList<>();
        primitives.add(new Node(new LatLon(0, 0)));
        primitives.add(new Node(new LatLon(1, 1)));
        primitives.add(new Node(new LatLon(2, 2)));
        assertTrue(StreetAddressOrder.checkOrdering(primitives).isEmpty());
        primitives.add(primitives.remove(1));
        assertFalse(StreetAddressOrder.checkOrdering(primitives).isEmpty());

        assertTrue(StreetAddressOrder.checkOrdering(Collections.emptyList()).isEmpty());
    }

    @Test
    void testGetAddressesInDirection() {
        Way way1 = TestUtils.newWay("", new Node(new LatLon(-1, -1)), new Node(new LatLon(1, 1)));

        List<OsmPrimitive> addresses = new ArrayList<>();
        assertTrue(StreetAddressOrder.getAddressesInDirection(true, addresses, way1).isEmpty());
        assertTrue(StreetAddressOrder.getAddressesInDirection(false, addresses, way1).isEmpty());
        addresses.add(new Node(new LatLon(1, 0)));
        assertSame(addresses.get(0), StreetAddressOrder.getAddressesInDirection(true, addresses, way1).get(0));
        assertTrue(StreetAddressOrder.getAddressesInDirection(false, addresses, way1).isEmpty());
        ((Node) addresses.get(0)).setCoor(new LatLon(0, 1));
        assertSame(addresses.get(0), StreetAddressOrder.getAddressesInDirection(false, addresses, way1).get(0));
        assertTrue(StreetAddressOrder.getAddressesInDirection(true, addresses, way1).isEmpty());

        assertTrue(StreetAddressOrder.getAddressesInDirection(true, addresses, way1.save()).isEmpty());
        assertTrue(StreetAddressOrder.getAddressesInDirection(false, addresses, way1.save()).isEmpty());

        List<PrimitiveData> primitiveData = addresses.stream().map(OsmPrimitive::save).collect(Collectors.toList());
        assertTrue(StreetAddressOrder.getAddressesInDirection(true, primitiveData, way1).isEmpty());
        assertTrue(StreetAddressOrder.getAddressesInDirection(false, primitiveData, way1).isEmpty());
    }

    @Test
    void testGetCentroid() {
        Node node1 = new Node(new LatLon(0, 0));
        assertSame(node1, StreetAddressOrder.getCentroid(node1));
        assertNull(StreetAddressOrder.getCentroid(node1.save()));

        Way way1 = TestUtils.newWay("", node1, new Node(new LatLon(1, 1)), new Node(new LatLon(0, 1)), node1);
        EastNorth way1Centroid = Geometry.getCentroid(way1.getNodes());
        assertEquals(way1Centroid, StreetAddressOrder.getCentroid(way1).getEastNorth());

        Relation relation1 = TestUtils.newRelation("", new RelationMember("", way1));
        assertNull(StreetAddressOrder.getCentroid(relation1));
        relation1.put("type", "multipolygon");
        assertNull(StreetAddressOrder.getCentroid(relation1));
        relation1.removeMember(0);
        relation1.addMember(new RelationMember("outer", way1));
        assertEquals(way1Centroid, StreetAddressOrder.getCentroid(relation1).getEastNorth());
    }

    @Test
    void testGetNeighbors() {
        List<IPrimitive> primitives = new ArrayList<>();
        primitives.add(new Node(new LatLon(0, 0)));
        assertTrue(StreetAddressOrder.getNeighbors(primitives.get(0), primitives).isEmpty());

        primitives.add(new Node(new LatLon(1, 1)));
        assertSame(primitives.get(1), StreetAddressOrder.getNeighbors(primitives.get(0), primitives).get(0));
        assertEquals(1, StreetAddressOrder.getNeighbors(primitives.get(1), primitives).size());

        assertSame(primitives.get(0), StreetAddressOrder.getNeighbors(primitives.get(1), primitives).get(0));
        assertEquals(1, StreetAddressOrder.getNeighbors(primitives.get(1), primitives).size());

        primitives.add(new Node(new LatLon(2, 2)));
        assertSame(primitives.get(1), StreetAddressOrder.getNeighbors(primitives.get(0), primitives).get(0));
        assertEquals(1, StreetAddressOrder.getNeighbors(primitives.get(0), primitives).size());

        assertSame(primitives.get(0), StreetAddressOrder.getNeighbors(primitives.get(1), primitives).get(0));
        assertSame(primitives.get(2), StreetAddressOrder.getNeighbors(primitives.get(1), primitives).get(1));
        assertEquals(2, StreetAddressOrder.getNeighbors(primitives.get(1), primitives).size());

        assertSame(primitives.get(1), StreetAddressOrder.getNeighbors(primitives.get(2), primitives).get(0));
        assertEquals(1, StreetAddressOrder.getNeighbors(primitives.get(2), primitives).size());
    }

    @Test
    void testGetNearbyAddresses() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);

        assertTrue(StreetAddressOrder.getNearbyAddresses(way1).isEmpty());

        Node node1 = new Node(new LatLon(1, 2));
        node1.put(ADDR_STREET, "Test1");
        ds.addPrimitive(node1);

        assertTrue(StreetAddressOrder.getNearbyAddresses(way1).isEmpty());

        Node node2 = new Node(new LatLon(1, 1.0001));
        node2.put(ADDR_STREET, "Test2");
        ds.addPrimitive(node2);

        assertEquals(1, StreetAddressOrder.getNearbyAddresses(way1).size());
        assertSame(node2, StreetAddressOrder.getNearbyAddresses(way1).get(0));

        Node node3 = new Node(new LatLon(1, 0.9999));
        ds.addPrimitive(node3);

        assertSame(node2, StreetAddressOrder.getNearbyAddresses(way1).get(0));
        assertEquals(1, StreetAddressOrder.getNearbyAddresses(way1).size());

        node3.put(ADDR_STREET, "Test3");
        assertTrue(StreetAddressOrder.getNearbyAddresses(way1).containsAll(Arrays.asList(node2, node3)));
        assertEquals(2, StreetAddressOrder.getNearbyAddresses(way1).size());
    }

    @Test
    void testIsNearestRoad() {
        Node node1 = new Node(new LatLon(0, 0));
        DataSet ds = new DataSet(node1);
        double boxCorners = 0.0009;
        Way way1 = TestUtils.newWay("", new Node(new LatLon(boxCorners, boxCorners)),
                new Node(new LatLon(boxCorners, -boxCorners)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(-boxCorners, boxCorners)),
                new Node(new LatLon(-boxCorners, -boxCorners)));
        ds.addPrimitiveRecursive(way1);
        ds.addPrimitiveRecursive(way2);

        List<Way> competingWays = ds
                .searchWays(StreetAddressTest.expandBBox(new BBox(node1.getBBox()), StreetAddressTest.BBOX_EXPANSION));
        // We shouldn't find anything here (no highway tags)
        assertFalse(StreetAddressOrder.isNearestRoad(way1, competingWays, node1));
        assertFalse(StreetAddressOrder.isNearestRoad(way2, competingWays, node1));

        way1.put("highway", "residential");
        way2.put("highway", "motorway");

        // Both ways are equidistant, so both are the "nearest" road
        assertTrue(StreetAddressOrder.isNearestRoad(way1, competingWays, node1));
        assertTrue(StreetAddressOrder.isNearestRoad(way2, competingWays, node1));

        node1.setCoor(new LatLon(boxCorners * 0.9, boxCorners * 0.9));
        competingWays = ds
                .searchWays(StreetAddressTest.expandBBox(new BBox(node1.getBBox()), StreetAddressTest.BBOX_EXPANSION));
        // Nearest road should be way1
        assertTrue(StreetAddressOrder.isNearestRoad(way1, competingWays, node1));
        assertFalse(StreetAddressOrder.isNearestRoad(way2, competingWays, node1));

        node1.setCoor(new LatLon(-boxCorners * 0.9, -boxCorners * 0.9));
        competingWays = ds
                .searchWays(StreetAddressTest.expandBBox(new BBox(node1.getBBox()), StreetAddressTest.BBOX_EXPANSION));
        assertTrue(StreetAddressOrder.isNearestRoad(way2, competingWays, node1));
        assertFalse(StreetAddressOrder.isNearestRoad(way1, competingWays, node1));

        node1.setCoor(new LatLon(0.00005, 0.00005));
        competingWays = ds
                .searchWays(StreetAddressTest.expandBBox(new BBox(node1.getBBox()), StreetAddressTest.BBOX_EXPANSION));
        assertFalse(StreetAddressOrder.isNearestRoad(way2, competingWays, node1));
        assertTrue(StreetAddressOrder.isNearestRoad(way1, competingWays, node1));
    }
}
