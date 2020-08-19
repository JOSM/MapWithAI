// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
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
    public void testVisitWay() throws ReflectiveOperationException {
        StreetAddressTest test = new StreetAddressTest();
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);
        Node node1 = new Node(new LatLon(1, 1.00001));
        node1.put(ADDR_STREET, "Test");
        ds.addPrimitive(node1);
        test.visit(way1);
        test.endTest();
        assertTrue(test.getErrors().isEmpty());
        way1.put("highway", "residential");

        test.visit(node1);
        test.endTest();
        assertFalse(test.getErrors().isEmpty());

        way1.put("name", "Test1");
        test.clear();
        test.visit(node1);
        test.endTest();
        assertFalse(test.getErrors().isEmpty());

        way1.put("name", "Test");
        test.clear();
        test.visit(node1);
        test.endTest();
        assertTrue(test.getErrors().isEmpty());

        node1.remove(ADDR_STREET);
        test.clear();
        test.visit(node1);
        test.endTest();
        assertTrue(test.getErrors().isEmpty());

        way1.put("name", "Test1");
        test.clear();
        Node firstNode = way1.firstNode();
        Method setIncomplete = AbstractPrimitive.class.getDeclaredMethod("setIncomplete", boolean.class);
        setIncomplete.setAccessible(true);
        setIncomplete.invoke(firstNode, true);
        assertTrue(way1.firstNode().isIncomplete());
        test.visit(node1);
        test.endTest();
        assertTrue(test.getErrors().isEmpty());
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
