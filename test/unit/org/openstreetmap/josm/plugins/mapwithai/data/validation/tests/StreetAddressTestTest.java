// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Pair;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class StreetAddressTestTest {
    private final static String ADDR_STREET = "addr:street";
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    @Test
    public void testVisitWay() throws NoSuchMethodException, SecurityException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        StreetAddressTest test = new StreetAddressTest();
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);
        Node node1 = new Node(new LatLon(1, 1.00001));
        node1.put(ADDR_STREET, "Test");
        ds.addPrimitive(node1);
        test.visit(way1);
        assertTrue(test.getErrors().isEmpty());
        way1.put("highway", "residential");

        test.visit(way1);
        assertFalse(test.getErrors().isEmpty());

        way1.put("name", "Test1");
        test.clear();
        test.visit(way1);
        assertFalse(test.getErrors().isEmpty());

        way1.put("name", "Test");
        test.clear();
        test.visit(way1);
        assertTrue(test.getErrors().isEmpty());

        node1.remove(ADDR_STREET);
        test.clear();
        test.visit(way1);
        assertTrue(test.getErrors().isEmpty());

        way1.put("name", "Test1");
        test.clear();
        Node firstNode = way1.firstNode();
        Method setIncomplete = AbstractPrimitive.class.getDeclaredMethod("setIncomplete", boolean.class);
        setIncomplete.setAccessible(true);
        setIncomplete.invoke(firstNode, true);
        assertTrue(way1.firstNode().isIncomplete());
        test.visit(way1);
        assertTrue(test.getErrors().isEmpty());
    }

    @Test
    public void testGetLikelyNames() {
        Map<String, Integer> likelyNames = new HashMap<>();
        assertTrue(StreetAddressTest.getLikelyNames(likelyNames).isEmpty());
        likelyNames.put("Test Name 1", 0);
        assertEquals("Test Name 1", StreetAddressTest.getLikelyNames(likelyNames).get(0));
        likelyNames.put("Test Name 2", 1);
        assertEquals("Test Name 2", StreetAddressTest.getLikelyNames(likelyNames).get(0));
        assertEquals(1, StreetAddressTest.getLikelyNames(likelyNames).size());
        likelyNames.put("Test Name 3", 1);
        likelyNames.put(null, 50000);
        likelyNames.put(" ", 20000);
        assertEquals(2, StreetAddressTest.getLikelyNames(likelyNames).size());
        assertTrue(
                StreetAddressTest.getLikelyNames(likelyNames).containsAll(Arrays.asList("Test Name 2", "Test Name 3")));
    }

    @Test
    public void testGetAddressPOI() {
        Node poi1 = new Node(new LatLon(0, 0));
        assertTrue(StreetAddressTest.getAddressPOI(Collections.singleton("Test Street"), Collections.singleton(poi1))
                .isEmpty());
        poi1.put(ADDR_STREET, "Test Street");
        assertEquals(poi1, StreetAddressTest
                .getAddressPOI(Collections.singleton("Test Street"), Collections.singleton(poi1)).get(0));
        assertEquals(poi1, StreetAddressTest.getAddressPOI(Collections.singleton("Test Street"),
                Arrays.asList(new Node(new LatLon(0, 0)), poi1, new Node(new LatLon(1, 1)))).get(0));
    }

    @Test
    public void testGetAddressOccurance() {
        Collection<IPrimitive> holder = new HashSet<>();
        assertTrue(StreetAddressTest.getAddressOccurance(holder).isEmpty());
        Node tNode = new Node(new LatLon(0, 0));
        holder.add(tNode);
        assertTrue(StreetAddressTest.getAddressOccurance(holder).isEmpty());
        tNode.put(ADDR_STREET, "Test Road 1");
        assertEquals(1, StreetAddressTest.getAddressOccurance(holder).get("Test Road 1"));
        for (int i = 0; i < 10; i++) {
            Node tNode2 = new Node(tNode);
            tNode2.clearOsmMetadata();
            holder.add(tNode2);
        }
        assertEquals(11, StreetAddressTest.getAddressOccurance(holder).get("Test Road 1"));

        tNode = new Node(tNode);
        tNode.clearOsmMetadata();
        tNode.remove(ADDR_STREET);
        holder.add(tNode);
        assertEquals(11, StreetAddressTest.getAddressOccurance(holder).get("Test Road 1"));
        assertEquals(1, StreetAddressTest.getAddressOccurance(holder).size());

        tNode.put(ADDR_STREET, "Test Road 2");
        assertEquals(11, StreetAddressTest.getAddressOccurance(holder).get("Test Road 1"));
        assertEquals(1, StreetAddressTest.getAddressOccurance(holder).get("Test Road 2"));
        assertEquals(2, StreetAddressTest.getAddressOccurance(holder).size());
    }

    @Test
    public void testGetNearbyAddresses() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);

        assertTrue(StreetAddressTest.getNearbyAddresses(way1).isEmpty());

        Node node1 = new Node(new LatLon(1, 2));
        node1.put(ADDR_STREET, "Test1");
        ds.addPrimitive(node1);

        assertTrue(StreetAddressTest.getNearbyAddresses(way1).isEmpty());

        Node node2 = new Node(new LatLon(1, 1.0001));
        node2.put(ADDR_STREET, "Test2");
        ds.addPrimitive(node2);

        assertEquals(1, StreetAddressTest.getNearbyAddresses(way1).size());
        assertSame(node2, StreetAddressTest.getNearbyAddresses(way1).get(0));

        Node node3 = new Node(new LatLon(1, 0.9999));
        ds.addPrimitive(node3);

        assertSame(node2, StreetAddressTest.getNearbyAddresses(way1).get(0));
        assertEquals(1, StreetAddressTest.getNearbyAddresses(way1).size());

        node3.put(ADDR_STREET, "Test3");
        assertTrue(StreetAddressTest.getNearbyAddresses(way1).containsAll(Arrays.asList(node2, node3)));
        assertEquals(2, StreetAddressTest.getNearbyAddresses(way1).size());
    }

    @Test
    public void testIsNearestRoad() {
        Node node1 = new Node(new LatLon(0, 0));
        DataSet ds = new DataSet(node1);
        double boxCorners = 0.0009;
        Way way1 = TestUtils.newWay("", new Node(new LatLon(boxCorners, boxCorners)),
                new Node(new LatLon(boxCorners, -boxCorners)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(-boxCorners, boxCorners)),
                new Node(new LatLon(-boxCorners, -boxCorners)));
        for (Way way : Arrays.asList(way1, way2)) {
            way.getNodes().forEach(ds::addPrimitive);
            ds.addPrimitive(way);
        }

        assertFalse(StreetAddressTest.isNearestRoad(way1, node1));
        assertFalse(StreetAddressTest.isNearestRoad(way2, node1));

        way1.put("highway", "residential");
        way2.put("highway", "motorway");

        assertTrue(StreetAddressTest.isNearestRoad(way1, node1));
        assertTrue(StreetAddressTest.isNearestRoad(way2, node1));

        node1.setCoor(new LatLon(boxCorners * 0.9, boxCorners * 0.9));
        assertTrue(StreetAddressTest.isNearestRoad(way1, node1));
        assertFalse(StreetAddressTest.isNearestRoad(way2, node1));

        node1.setCoor(new LatLon(-boxCorners * 0.9, -boxCorners * 0.9));
        assertTrue(StreetAddressTest.isNearestRoad(way2, node1));
        assertFalse(StreetAddressTest.isNearestRoad(way1, node1));

        node1.setCoor(new LatLon(0.00005, 0.00005));
        assertFalse(StreetAddressTest.isNearestRoad(way2, node1));
        assertTrue(StreetAddressTest.isNearestRoad(way1, node1));
    }

    @Test
    public void testDistanceToWay() {
        Node node1 = new Node(new LatLon(0, 0));
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        Pair<Way, Double> distance = StreetAddressTest.distanceToWay(way1, node1);
        assertSame(way1, distance.a);
        assertEquals(0, distance.b, 0.0);
        node1.setCoor(new LatLon(0.001, 0.001));

        distance = StreetAddressTest.distanceToWay(way1, node1);
        assertSame(way1, distance.a);
        assertEquals(Geometry.getDistance(way1, node1), distance.b, 0.0);
    }

    @Test
    public void testIsHighway() {
        Node node = new Node(new LatLon(0, 0));
        assertFalse(StreetAddressTest.isHighway(node));
        node.put(ADDR_STREET, "Test Road 1");
        assertFalse(StreetAddressTest.isHighway(node));

        Way way = TestUtils.newWay("", node, new Node(new LatLon(1, 1)));
        assertFalse(StreetAddressTest.isHighway(way));
        way.put("highway", "residential");
        assertTrue(StreetAddressTest.isHighway(way));
    }

    @Test
    public void testHasStreetAddressTags() {
        Node node = new Node(new LatLon(0, 0));
        assertFalse(StreetAddressTest.hasStreetAddressTags(node));
        node.put(ADDR_STREET, "Test Road 1");
        assertTrue(StreetAddressTest.hasStreetAddressTags(node));
    }

    @Test
    public void testExpandBBox() {
        BBox bbox = new BBox();
        bbox.add(0, 0);
        assertSame(bbox, StreetAddressTest.expandBBox(bbox, 0.01));
        assertTrue(BBox.bboxesAreFunctionallyEqual(bbox, new BBox(-0.01, -0.01, 0.01, 0.01), 0.0));
    }

}
