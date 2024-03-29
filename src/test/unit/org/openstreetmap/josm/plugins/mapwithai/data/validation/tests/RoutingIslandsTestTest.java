// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.tools.Access;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * Test class for {@link RoutingIslandsTest}
 *
 * @author Taylor Smock
 * @since xxx
 */
@BasicPreferences
@NoExceptions
@MapWithAISources
@Projection
@Timeout(30)
class RoutingIslandsTestTest {
    /**
     * Test method for {@link RoutingIslandsTest#RoutingIslandsTest()} and the
     * testing apparatus
     */
    @Test
    void testRoutingIslandsTest() {
        RoutingIslandsTest.setErrorLevel(RoutingIslandsTest.ROUTING_ISLAND, Severity.WARNING);
        RoutingIslandsTest test = new RoutingIslandsTest();
        test.startTest(null);
        test.endTest();
        assertTrue(test.getErrors().isEmpty());

        DataSet ds = new DataSet();

        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        Way way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-1, 0)), way1.firstNode());
        addToDataSet(ds, way1);
        addToDataSet(ds, way2);

        ds.addDataSource(new DataSource(new Bounds(0, 0, 1, 1), "openstreetmap.org"));

        test.clear();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertTrue(test.getErrors().isEmpty());

        ds.addDataSource(new DataSource(new Bounds(-5, -5, 5, 5), "openstreetmap.org"));
        test.clear();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertFalse(test.getErrors().isEmpty());
        assertEquals(2, test.getErrors().get(0).getPrimitives().size());

        ds.clear();
        way1 = TestUtils.newWay("highway=motorway oneway=yes", new Node(new LatLon(39.1156655, -108.5465434)),
                new Node(new LatLon(39.1157251, -108.5496874)), new Node(new LatLon(39.11592, -108.5566841)));
        way2 = TestUtils.newWay("highway=motorway oneway=yes", new Node(new LatLon(39.1157244, -108.55674)),
                new Node(new LatLon(39.1155548, -108.5496901)), new Node(new LatLon(39.1154827, -108.5462431)));
        addToDataSet(ds, way1);
        addToDataSet(ds, way2);
        ds.addDataSource(
                new DataSource(new Bounds(new LatLon(39.1136949, -108.558445), new LatLon(39.117242, -108.5489166)),
                        "openstreetmap.org"));
        test.clear();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertFalse(test.getErrors().isEmpty());
        Way way3 = TestUtils.newWay("highway=motorway oneway=no", way1.getNode(1), way2.getNode(1));
        addToDataSet(ds, way3);
        test.clear();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertFalse(test.getErrors().isEmpty());

        Node tNode = new Node(new LatLon(39.1158845, -108.5599312));
        addToDataSet(ds, tNode);
        way1.addNode(tNode);
        tNode = new Node(new LatLon(39.115723, -108.5599239));
        addToDataSet(ds, tNode);
        way2.addNode(0, tNode);
        test.clear();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertTrue(test.getErrors().isEmpty());
    }

    /**
     * Test roundabouts
     */
    @Test
    void testRoundabouts() {
        RoutingIslandsTest test = new RoutingIslandsTest();
        Way roundabout = TestUtils.newWay("highway=residential junction=roundabout oneway=yes",
                new Node(new LatLon(39.119582, -108.5262686)), new Node(new LatLon(39.1196494, -108.5260935)),
                new Node(new LatLon(39.1197572, -108.5260784)), new Node(new LatLon(39.1197929, -108.526391)),
                new Node(new LatLon(39.1196595, -108.5264047)));
        roundabout.addNode(roundabout.firstNode()); // close it up
        DataSet ds = new DataSet();
        addToDataSet(ds, roundabout);
        ds.addDataSource(
                new DataSource(new Bounds(new LatLon(39.1182025, -108.527574), new LatLon(39.1210588, -108.5251112)),
                        "openstreetmap.org"));
        Way incomingFlare = TestUtils.newWay("highway=residential oneway=yes",
                new Node(new LatLon(39.1196377, -108.5257567)), roundabout.getNode(3));
        addToDataSet(ds, incomingFlare);
        Way outgoingFlare = TestUtils.newWay("highway=residential oneway=yes", roundabout.getNode(2),
                incomingFlare.firstNode());
        addToDataSet(ds, outgoingFlare);

        Way outgoingRoad = TestUtils.newWay("highway=residential", incomingFlare.firstNode(),
                new Node(new LatLon(39.1175184, -108.5219623)));
        addToDataSet(ds, outgoingRoad);

        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertTrue(test.getErrors().isEmpty());
    }

    /**
     * Test method for {@link RoutingIslandsTest#checkForUnconnectedWays}.
     */
    @BasicPreferences
    @Test
    void testCheckForUnconnectedWaysIncoming() {
        RoutingIslandsTest.checkForUnconnectedWays(Collections.emptySet(), Collections.emptySet(), null);
        Way way1 = TestUtils.newWay("highway=residential oneway=yes", new Node(new LatLon(0, 0)),
                new Node(new LatLon(1, 1)));
        Set<Way> incomingSet = new HashSet<>();
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);
        incomingSet.add(way1);
        RoutingIslandsTest.checkForUnconnectedWays(incomingSet, Collections.emptySet(), null);
        assertEquals(1, incomingSet.size());
        assertSame(way1, incomingSet.iterator().next());

        Way way2 = TestUtils.newWay("highway=residential", way1.firstNode(), new Node(new LatLon(-1, -2)));
        way2.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
        ds.addPrimitive(way2);

        RoutingIslandsTest.checkForUnconnectedWays(incomingSet, Collections.emptySet(), null);
        assertEquals(2, incomingSet.size());
        assertTrue(Arrays.asList(way1, way2).containsAll(incomingSet));

        Way way3 = TestUtils.newWay("highway=residential", way2.lastNode(), new Node(new LatLon(-2, -1)));
        way3.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
        ds.addPrimitive(way3);

        incomingSet.clear();
        incomingSet.add(way1);
        RoutingIslandsTest.checkForUnconnectedWays(incomingSet, Collections.emptySet(), null);
        assertEquals(3, incomingSet.size());
        assertTrue(Arrays.asList(way1, way2, way3).containsAll(incomingSet));

        Config.getPref().putInt("validator.routingislands.maxrecursion", 1);
        incomingSet.clear();
        incomingSet.add(way1);
        RoutingIslandsTest.checkForUnconnectedWays(incomingSet, Collections.emptySet(), null);
        assertEquals(2, incomingSet.size());
        assertTrue(Arrays.asList(way1, way2).containsAll(incomingSet));
    }

    /**
     * Test method for {@link RoutingIslandsTest#checkForUnconnectedWays}.
     */
    @Test
    void testCheckForUnconnectedWaysOutgoing() {
        RoutingIslandsTest.checkForUnconnectedWays(Collections.emptySet(), Collections.emptySet(), null);
        Way way1 = TestUtils.newWay("highway=residential oneway=yes", new Node(new LatLon(0, 0)),
                new Node(new LatLon(1, 1)));
        Set<Way> outgoingSet = new HashSet<>();
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);
        outgoingSet.add(way1);
        RoutingIslandsTest.checkForUnconnectedWays(Collections.emptySet(), outgoingSet, null);
        assertEquals(1, outgoingSet.size());
        assertSame(way1, outgoingSet.iterator().next());

        Way way2 = TestUtils.newWay("highway=residential", way1.firstNode(), new Node(new LatLon(-1, -2)));
        way2.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
        ds.addPrimitive(way2);

        RoutingIslandsTest.checkForUnconnectedWays(Collections.emptySet(), outgoingSet, null);
        assertEquals(2, outgoingSet.size());
        assertTrue(Arrays.asList(way1, way2).containsAll(outgoingSet));

        Way way3 = TestUtils.newWay("highway=residential", way2.lastNode(), new Node(new LatLon(-2, -1)));
        way3.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
        ds.addPrimitive(way3);

        outgoingSet.clear();
        outgoingSet.add(way1);
        RoutingIslandsTest.checkForUnconnectedWays(Collections.emptySet(), outgoingSet, null);
        assertEquals(3, outgoingSet.size());
        assertTrue(Arrays.asList(way1, way2, way3).containsAll(outgoingSet));

        Config.getPref().putInt("validator.routingislands.maxrecursion", 1);
        outgoingSet.clear();
        outgoingSet.add(way1);
        RoutingIslandsTest.checkForUnconnectedWays(Collections.emptySet(), outgoingSet, null);
        assertEquals(2, outgoingSet.size());
        assertTrue(Arrays.asList(way1, way2).containsAll(outgoingSet));
    }

    /**
     * Non-regression test for #21551
     */
    @Test
    void testNonRegression21551() {
        ValidatorPrefHelper.PREF_OTHER.put(Boolean.TRUE);
        final DataSet dataSet = new DataSet();
        dataSet.addDataSource(new DataSource(new Bounds(-18, -180, 18, 180), "World bounds testNonRegression21551"));
        final Way way1 = new Way();
        way1.setNodes(DoubleStream.iterate(0, d -> d + 1).limit(20).mapToObj(d -> new LatLon(d, d)).map(Node::new)
                .collect(Collectors.toList()));
        final Way way2 = new Way();
        way2.setNodes(DoubleStream.iterate(-1, d -> d - 1).limit(19).mapToObj(d -> new LatLon(d, d)).map(Node::new)
                .collect(Collectors.toList()));
        way2.addNode(way1.firstNode());
        addToDataSet(dataSet, way1);
        addToDataSet(dataSet, way2);
        way1.put("highway", "residential");
        way2.put("highway", "residential");
        way2.put(Access.AccessTags.MOTOR_VEHICLE.getKey() + ":forward", Access.AccessTags.NO.getKey());
        way2.put(Access.AccessTags.MOTOR_VEHICLE.getKey() + ":backward", Access.AccessTags.NO.getKey());
        final RoutingIslandsTest test = new RoutingIslandsTest();
        test.startTest(NullProgressMonitor.INSTANCE);
        test.visit(way1);
        test.visit(way2);
        assertDoesNotThrow(test::endTest);
    }

    /**
     * Test method for {@link RoutingIslandsTest#outsideConnections(Node)}.
     */
    @Test
    void testOutsideConnections() {
        Node node = new Node(new LatLon(0, 0));
        DataSet ds = new DataSet(node);
        ds.addDataSource(new DataSource(new Bounds(-0.1, -0.1, -0.01, -0.01), "Test bounds"));
        node.setOsmId(1, 1);
        assertTrue(RoutingIslandsTest.outsideConnections(node));
        ds.addDataSource(new DataSource(new Bounds(-0.1, -0.1, 0.1, 0.1), "Test bounds"));
        assertFalse(RoutingIslandsTest.outsideConnections(node));
        node.put("amenity", "parking_entrance");
        assertTrue(RoutingIslandsTest.outsideConnections(node));
    }

    /**
     * Test method for {@link RoutingIslandsTest#isOneway(Way, String)}.
     */
    @Test
    void testIsOneway() {
        Way way = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        assertEquals(Integer.valueOf(0), RoutingIslandsTest.isOneway(way, null));
        assertEquals(Integer.valueOf(0), RoutingIslandsTest.isOneway(way, " "));
        way.put("oneway", "yes");
        assertEquals(Integer.valueOf(1), RoutingIslandsTest.isOneway(way, null));
        assertEquals(Integer.valueOf(1), RoutingIslandsTest.isOneway(way, " "));
        way.put("oneway", "-1");
        assertEquals(Integer.valueOf(-1), RoutingIslandsTest.isOneway(way, null));
        assertEquals(Integer.valueOf(-1), RoutingIslandsTest.isOneway(way, " "));

        way.put("vehicle:forward", "yes");
        assertEquals(Integer.valueOf(0), RoutingIslandsTest.isOneway(way, "vehicle"));
        way.put("vehicle:backward", "no");
        assertEquals(Integer.valueOf(1), RoutingIslandsTest.isOneway(way, "vehicle"));
        way.put("vehicle:forward", "no");
        assertNull(RoutingIslandsTest.isOneway(way, "vehicle"));
        way.put("vehicle:backward", "yes");
        assertEquals(Integer.valueOf(-1), RoutingIslandsTest.isOneway(way, "vehicle"));

        way.put("oneway", "yes");
        way.remove("vehicle:backward");
        way.remove("vehicle:forward");
        assertEquals(Integer.valueOf(1), RoutingIslandsTest.isOneway(way, "vehicle"));
        way.remove("oneway");
        assertEquals(Integer.valueOf(0), RoutingIslandsTest.isOneway(way, "vehicle"));

        way.put("oneway", "-1");
        assertEquals(Integer.valueOf(-1), RoutingIslandsTest.isOneway(way, "vehicle"));
    }

    /**
     * Test method for {@link RoutingIslandsTest#firstNode(Way, String)}.
     */
    @Test
    void testFirstNode() {
        Way way = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        assertEquals(way.firstNode(), RoutingIslandsTest.firstNode(way, null));
        way.put("oneway", "yes");
        assertEquals(way.firstNode(), RoutingIslandsTest.firstNode(way, null));
        way.put("oneway", "-1");
        assertEquals(way.lastNode(), RoutingIslandsTest.firstNode(way, null));

        way.put("vehicle:forward", "yes");
        assertEquals(way.firstNode(), RoutingIslandsTest.firstNode(way, "vehicle"));
        way.put("vehicle:backward", "no");
        assertEquals(way.firstNode(), RoutingIslandsTest.firstNode(way, "vehicle"));
        way.put("vehicle:forward", "no");
        assertEquals(way.firstNode(), RoutingIslandsTest.firstNode(way, "vehicle"));
        way.put("vehicle:backward", "yes");
        assertEquals(way.lastNode(), RoutingIslandsTest.firstNode(way, "vehicle"));
    }

    /**
     * Test method for {@link RoutingIslandsTest#lastNode(Way, String)}.
     */
    @Test
    void testLastNode() {
        Way way = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        assertEquals(way.lastNode(), RoutingIslandsTest.lastNode(way, null));
        way.put("oneway", "yes");
        assertEquals(way.lastNode(), RoutingIslandsTest.lastNode(way, null));
        way.put("oneway", "-1");
        assertEquals(way.firstNode(), RoutingIslandsTest.lastNode(way, null));

        way.put("vehicle:forward", "yes");
        assertEquals(way.lastNode(), RoutingIslandsTest.lastNode(way, "vehicle"));
        way.put("vehicle:backward", "no");
        assertEquals(way.lastNode(), RoutingIslandsTest.lastNode(way, "vehicle"));
        way.put("vehicle:forward", "no");
        assertEquals(way.lastNode(), RoutingIslandsTest.lastNode(way, "vehicle"));
        way.put("vehicle:backward", "yes");
        assertEquals(way.firstNode(), RoutingIslandsTest.lastNode(way, "vehicle"));
    }

    /**
     * Test with a way that by default does not give access to motor vehicles
     */
    @Test
    void testNoAccessWay() {
        Way i70w = TestUtils.newWay("highway=motorway hgv=designated", new Node(new LatLon(39.1058104, -108.5258586)),
                new Node(new LatLon(39.1052235, -108.5293733)));
        Way i70e = TestUtils.newWay("highway=motorway hgv=designated", new Node(new LatLon(39.1049905, -108.5293074)),
                new Node(new LatLon(39.1055829, -108.5257975)));
        Way testPath = TestUtils.newWay("highway=footway", i70w.lastNode(true), i70e.firstNode(true));
        DataSet ds = new DataSet();
        ds.addDataSource(new DataSource(new Bounds(-90, -180, 90, 180), ""));
        Arrays.asList(i70w, i70e, testPath).forEach(way -> addToDataSet(ds, way));

        RoutingIslandsTest test = new RoutingIslandsTest();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertFalse(test.getErrors().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "highway=services", "highway=rest_area", "highway=platform", "waterway=services",
            "waterway=rest_area", "waterway=dam" })
    void testExemptions(final String tag) {
        final Way testWay = TestUtils.newWay(tag, new Node(new LatLon(39.1058104, -108.5258586)),
                new Node(new LatLon(39.1052235, -108.5293733)));
        final DataSet ds = new DataSet();
        ds.addDataSource(new DataSource(new Bounds(-90, -180, 90, 180), ""));
        testWay.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(testWay);

        RoutingIslandsTest test = new RoutingIslandsTest();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertTrue(test.getErrors().isEmpty());
    }

    private static void addToDataSet(DataSet ds, OsmPrimitive primitive) {
        if (primitive instanceof Way) {
            ((Way) primitive).getNodes().stream().distinct().filter(node -> node.getDataSet() == null)
                    .forEach(ds::addPrimitive);
        }
        if (primitive.getDataSet() == null) {
            ds.addPrimitive(primitive);
        }
        long id = Math.max(ds.allPrimitives().stream().mapToLong(AbstractPrimitive::getId).max().orElse(0L), 0L);
        for (OsmPrimitive osm : ds.allPrimitives().stream().filter(prim -> prim.getUniqueId() < 0)
                .collect(Collectors.toList())) {
            id++;
            osm.setOsmId(id, 1);
        }
    }
}
