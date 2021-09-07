// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnable;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Pair;

/**
 * @author Taylor Smock
 */
@org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Command
class MergeDuplicateWaysTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new JOSMTestRules().projection();

    /**
     * Test method for {@link MergeDuplicateWays#removeCommonTags(DataSet)}.
     */
    @Test
    void testRemoveCommonTags() {
        final DataSet ds1 = new DataSet(TestUtils.newNode("orig_id=2222 highway=secondary"));
        GetDataRunnable.removeCommonTags(ds1);
        assertEquals(1, ds1.allPrimitives().stream().mapToInt(prim -> prim.getKeys().size()).sum(),
                "orig_id should be removed");
        GetDataRunnable.removeCommonTags(ds1);
        assertEquals(1, ds1.allPrimitives().stream().mapToInt(prim -> prim.getKeys().size()).sum(),
                "No other tags should be removed");
    }

    /**
     * Test method for {@link MergeDuplicateWays#filterDataSet(DataSet)}.
     */
    @Test
    void testFilterDataSet() {
        final DataSet ds1 = new DataSet();
        final Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 1)), new Node(new LatLon(1, 2)));
        final Way way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)),
                new Node(new LatLon(2, 2)));
        way1.getNodes().forEach(ds1::addPrimitive);
        way2.getNodes().forEach(ds1::addPrimitive);
        ds1.addPrimitive(way1);
        ds1.addPrimitive(way2);

        new MergeDuplicateWays(ds1).executeCommand();
        assertFalse(way1.isDeleted(), "way1 should not yet be deleted");
        assertFalse(way2.isDeleted(), "way2 should not yet be deleted");
        way1.getNodes().forEach(node -> assertFalse(way2.containsNode(node)));
        way2.getNodes().forEach(node -> assertFalse(way1.containsNode(node)));

        final Node tNode = new Node(new LatLon(1, 1));
        ds1.addPrimitive(tNode);
        way1.addNode(1, tNode);

        new MergeDuplicateWays(ds1).executeCommand();
        assertNotSame(way1.isDeleted(), way2.isDeleted(), "Either way1 or way2 should be delted, but not both");
        final Way tWay = way1.isDeleted() ? way2 : way1;
        assertEquals(4, tWay.getNodesCount(), "The undeleted way should have the missing node(s) from the other way");
    }

    /**
     * Test method for {@link MergeDuplicateWays#mergeWays(Way, Way, Set)}.
     */
    @Test
    void testMergeWays() {
        final int undoRedoTries = 10;

        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)));
        final Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> set = new LinkedHashSet<>();
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(0, way2.firstNode())));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        way2.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way2);
        ds.addPrimitive(way1);

        // Test with one node in common
        assertNull(MergeDuplicateWays.mergeWays(way1, way2, set),
                "We cannot merge the ways, since there is insufficient overlap");
        assertFalse(way2.isDeleted(), "way2 should not be deleted");
        assertFalse(way1.isDeleted(), "way1 should not be deleted");
        assertEquals(2, way1.getNodesCount(), "way1 nodes should not have been modified");

        // Test with two nodes in common
        final Node tNode = new Node(new LatLon(0, 0));
        ds.addPrimitive(tNode);
        way2.addNode(0, tNode);
        set.clear(); // we can't use the last pair added
        set.add(new Pair<>(new Pair<>(0, way1.firstNode()), new Pair<>(0, way2.firstNode())));
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(1, way2.getNode(1))));
        Command command = MergeDuplicateWays.mergeWays(way1, way2, set);
        for (int i = 0; i < undoRedoTries; i++) {
            checkCommand(command, way1, way2, 3);
        }
        command.executeCommand();

        // Test with a reversed way
        way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)));
        way2.addNode(0, new Node(new LatLon(0, 0)));
        ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        way2.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way2);
        ds.addPrimitive(way1);
        final List<Node> way2Nodes = way2.getNodes();
        Collections.reverse(way2Nodes);
        way2.setNodes(way2Nodes);
        set.clear();
        set.add(new Pair<>(new Pair<>(0, way1.firstNode()), new Pair<>(2, way2.lastNode())));
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(1, way2.getNode(1))));

        command = MergeDuplicateWays.mergeWays(way1, way2, set);
        for (int i = 0; i < undoRedoTries; i++) {
            checkCommand(command, way1, way2, 3);
        }

        // Test that nodes on both sides get added
        way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)));
        way2.addNode(0, new Node(new LatLon(0, 0)));
        way2.addNode(0, new Node(new LatLon(-1, -1)));
        ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        way2.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way2);
        ds.addPrimitive(way1);
        set.clear();
        set.add(new Pair<>(new Pair<>(0, way1.firstNode()), new Pair<>(2, way2.getNode(2))));
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(3, way2.getNode(3))));
        final List<Node> currentWay2Nodes = way2.getNodes();
        command = MergeDuplicateWays.mergeWays(way1, way2, set);

        for (int i = 0; i < undoRedoTries; i++) {
            checkCommand(command, way1, way2, 4);
            command.executeCommand();
            assertEquals(currentWay2Nodes.get(0), way1.firstNode(),
                    "The first node of the way1 should be the first node of way2");
            assertEquals(currentWay2Nodes.get(1), way1.getNode(1),
                    "The second node of way1 should be the second node of way2");
            command.undoCommand();
        }

        assertThrows(NullPointerException.class, () -> MergeDuplicateWays.mergeWays(null, null, null),
                "Throw NPE if any argument is null");
        assertNull(MergeDuplicateWays.mergeWays(new Way(), new Way(), Collections.emptySet()),
                "We should return null if there is no overlap");
        assertNull(MergeDuplicateWays.mergeWays(new Way(), new Way(), set),
                "We should return null if there is no overlap");
    }

    private static void checkCommand(Command command, Way way1, Way way2, int expectedNodes) {
        int way1InitialNodes = way1.getNodesCount();
        command.executeCommand();
        assertTrue(way2.isDeleted(), "way2 should be deleted");
        assertFalse(way1.isDeleted(), "way1 should not be deleted");
        assertEquals(expectedNodes, way1.getNodesCount(), "way1 should have an additional node");

        command.undoCommand();
        assertFalse(way2.isDeleted(), "way2 should not be deleted");
        assertFalse(way1.isDeleted(), "way1 should not be deleted");
        assertEquals(way1InitialNodes, way1.getNodesCount(), "way1 should not have an additional node");
    }

    /**
     * Test method for {@link MergeDuplicateWays#checkDirection(Set)}.
     */
    @Test
    void testCheckDirection() {
        final LinkedHashSet<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> set = new LinkedHashSet<>();
        final Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair1 = new Pair<>(
                new Pair<>(0, new Node(new LatLon(0, 0))), new Pair<>(0, new Node(new LatLon(0, 0))));
        final Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair2 = new Pair<>(
                new Pair<>(1, new Node(new LatLon(1, 0))), new Pair<>(1, new Node(new LatLon(1, 0))));
        set.add(pair1);
        set.add(pair2);

        assertTrue(MergeDuplicateWays.checkDirection(set), "The direction is the same");
        pair1.a.a = pair1.a.a - 1;
        assertTrue(MergeDuplicateWays.checkDirection(set), "The direction is the same");
        pair1.a.a = pair1.a.a + 3;
        assertFalse(MergeDuplicateWays.checkDirection(set), "The direction is not the same");
        pair1.a.a = pair1.a.a - 2;

        assertTrue(MergeDuplicateWays.checkDirection(set), "The direction is the same");
        pair1.b.a = pair1.b.a - 1;
        assertTrue(MergeDuplicateWays.checkDirection(set), "The direction is the same");
        pair1.b.a = pair1.b.a + 3;
        assertFalse(MergeDuplicateWays.checkDirection(set), "The direction is not the same");
        pair1.b.a = pair1.b.a - 2;

        assertThrows(NullPointerException.class, () -> MergeDuplicateWays.checkDirection(null),
                "Throw an NPE if the argument is null");

        assertFalse(MergeDuplicateWays.checkDirection(Collections.emptySet()),
                "If there are no pairs of nodes, the direction is not the same");
        set.remove(pair1);
        assertFalse(MergeDuplicateWays.checkDirection(set),
                "If there is only one set of pairs, then the direction is not the same");
    }

    /**
     * Test method for {@link MergeDuplicateWays#sorted(List)}.
     */
    @Test
    void testSorted() {
        List<Integer> integerList = Arrays.asList(1, 2, 3, 4, 6, 7, 8, 9, 5);
        assertFalse(MergeDuplicateWays.sorted(integerList), "The list is not yet sorted");
        integerList = integerList.stream().sorted().collect(Collectors.toList());
        assertTrue(MergeDuplicateWays.sorted(integerList), "The list is sorted");
        integerList.remove(3);
        assertFalse(MergeDuplicateWays.sorted(integerList), "The list is not sorted");

        integerList = Arrays.asList(1);
        assertTrue(MergeDuplicateWays.sorted(integerList), "The list is sorted");
    }

    /**
     * Test method for {@link MergeDuplicateWays#getDuplicateNodes(Way, Way)}.
     */
    @Test
    void testGetDuplicateNodes() {
        final Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        final Way way2 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));

        Map<Pair<Integer, Node>, Map<Integer, Node>> duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size(), "There should be two duplicate pairs");
        assertEquals(2, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.addNode(new Node(new LatLon(0, 0)));

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size(), "There should be two duplicate pairs");
        assertEquals(3, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count(),
                "There are three nodes that should be in the list");

        way2.addNode(way2.firstNode());

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size(), "There should be two duplicate pairs");
        assertEquals(4, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count(),
                "There should be four nodes in the duplicate values list");

        way2.setNodes(way2.getNodes().stream().limit(2).collect(Collectors.toList()));
        way2.addNode(new Node(new LatLon(2, 2)));

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size(), "There should be two duplicate pairs");
        assertEquals(2, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count(),
                "There should only be two duplicate nodes");
    }

    /**
     * Test method for {@link MergeDuplicateWays#getDescriptionText}
     */
    @Test
    void testGetDescriptionText() {
        final Command command = new MergeDuplicateWays(new DataSet());
        assertNotNull(command.getDescriptionText(), "The description should not be null");
        assertFalse(command.getDescriptionText().isEmpty(), "The description should not be empty");
    }

    /**
     * Test method for {@link MergeDuplicateWays#nodeInCompressed}
     */
    @Test
    void testNodeInCompressed() {
        final Node testNode = new Node();
        assertThrows(NullPointerException.class, () -> MergeDuplicateWays.nodeInCompressed(testNode, null),
                "Throw an NPE if the compressed collection is null");
        assertSame(testNode, MergeDuplicateWays.nodeInCompressed(testNode, Collections.emptySet()),
                "If a node is not in the set, it should be returned");
        final Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> testSet = new HashSet<>();
        testSet.add(new Pair<>(new Pair<>(1, new Node()), new Pair<>(2, new Node())));

        assertSame(testNode, MergeDuplicateWays.nodeInCompressed(testNode, testSet),
                "If a node is not in the set, it shoudl be returned");
        Pair<Pair<Integer, Node>, Pair<Integer, Node>> matchPair = new Pair<>(new Pair<>(2, testNode),
                new Pair<>(1, new Node()));
        testSet.add(matchPair);
        assertSame(matchPair.b.b, MergeDuplicateWays.nodeInCompressed(testNode, testSet),
                "If a node has a pairing, then the paired node should be returned");

        testSet.remove(matchPair);
        matchPair = new Pair<>(new Pair<>(2, new Node()), new Pair<>(1, testNode));
        testSet.add(matchPair);
        assertSame(matchPair.a.b, MergeDuplicateWays.nodeInCompressed(testNode, testSet),
                "If a node has a pairing, then the paired node should be returned");
    }

    /**
     * Non-regression test for <a
     * href=https://gitlab.com/gokaart/JOSM_MapWithAI/-/issues/81>#81</a>.
     */
    @Test
    void testDeletedNode() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(LatLon.ZERO), new Node(LatLon.NORTH_POLE));
        Way way2 = TestUtils.newWay("highway=residential", new Node(LatLon.ZERO), new Node(LatLon.NORTH_POLE));
        DataSet ds = new DataSet();
        List<Way> ways = Arrays.asList(way1, way2);
        for (Way way : ways) {
            way.getNodes().forEach(ds::addPrimitive);
            ds.addPrimitive(way);
        }
        way2.firstNode().setDeleted(true);
        assertDoesNotThrow(() -> new MergeDuplicateWays(ds, ways).executeCommand());
    }

    /**
     * Non-regression test for <a
     * href=https://gitlab.com/gokaart/JOSM_MapWithAI/-/issues/81>#81</a>.
     */
    @Test
    void testDeletedWay() {
        Way way1 = TestUtils.newWay("highway=residential", new Node(LatLon.ZERO), new Node(LatLon.NORTH_POLE));
        Way way2 = TestUtils.newWay("highway=residential", new Node(LatLon.ZERO), new Node(LatLon.NORTH_POLE));
        DataSet ds = new DataSet();
        List<Way> ways = Arrays.asList(way1, way2);
        for (Way way : ways) {
            way.getNodes().forEach(ds::addPrimitive);
            ds.addPrimitive(way);
        }
        way2.setDeleted(true);
        assertDoesNotThrow(() -> new MergeDuplicateWays(ds, ways).executeCommand());
    }
}
