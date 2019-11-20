// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnable;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Pair;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 */
public class MergeDuplicateWaysTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    /**
     * Test method for {@link MergeDuplicateWays#removeCommonTags(DataSet)}.
     */
    @Test
    public void testRemoveCommonTags() {
        final DataSet ds1 = new DataSet(TestUtils.newNode("orig_id=2222 highway=secondary"));
        GetDataRunnable.removeCommonTags(ds1);
        assertEquals(1, ds1.allPrimitives().stream().mapToInt(prim -> prim.getKeys().size()).sum());
        GetDataRunnable.removeCommonTags(ds1);
        assertEquals(1, ds1.allPrimitives().stream().mapToInt(prim -> prim.getKeys().size()).sum());
    }

    /**
     * Test method for {@link MergeDuplicateWays#filterDataSet(DataSet)}.
     */
    @Test
    public void testFilterDataSet() {
        final DataSet ds1 = new DataSet();
        final Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 1)), new Node(new LatLon(1, 2)));
        final Way way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)),
                new Node(new LatLon(2, 2)));
        way1.getNodes().forEach(ds1::addPrimitive);
        way2.getNodes().forEach(ds1::addPrimitive);
        ds1.addPrimitive(way1);
        ds1.addPrimitive(way2);

        new MergeDuplicateWays(ds1).executeCommand();
        assertFalse(way1.isDeleted());
        assertFalse(way2.isDeleted());
        way1.getNodes().forEach(node -> assertFalse(way2.containsNode(node)));
        way2.getNodes().forEach(node -> assertFalse(way1.containsNode(node)));

        final Node tNode = new Node(new LatLon(1, 1));
        ds1.addPrimitive(tNode);
        way1.addNode(1, tNode);

        new MergeDuplicateWays(ds1).executeCommand();
        assertNotSame(way1.isDeleted(), way2.isDeleted());
        final Way tWay = way1.isDeleted() ? way2 : way1;
        assertEquals(4, tWay.getNodesCount());
    }

    /**
     * Test method for {@link MergeDuplicateWays#mergeWays(Way, Way, Set)}.
     */
    @Test
    public void testMergeWays() {
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
        assertNull(MergeDuplicateWays.mergeWays(way1, way2, set));
        assertFalse(way2.isDeleted());
        assertFalse(way1.isDeleted());
        assertEquals(2, way1.getNodesCount());

        // Test with two nodes in common
        final Node tNode = new Node(new LatLon(0, 0));
        ds.addPrimitive(tNode);
        way2.addNode(0, tNode);
        set.clear(); // we can't use the last pair added
        set.add(new Pair<>(new Pair<>(0, way1.firstNode()), new Pair<>(0, way2.firstNode())));
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(1, way2.getNode(1))));
        Command command = MergeDuplicateWays.mergeWays(way1, way2, set);
        for (int i = 0; i < undoRedoTries; i++) {
            command.executeCommand();
            assertTrue(way2.isDeleted());
            assertFalse(way1.isDeleted());
            assertEquals(3, way1.getNodesCount());
            command.undoCommand();
            assertFalse(way2.isDeleted());
            assertFalse(way1.isDeleted());
            assertEquals(2, way1.getNodesCount());
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
            command.executeCommand();
            assertTrue(way2.isDeleted());
            assertFalse(way1.isDeleted());
            assertEquals(3, way1.getNodesCount());

            command.undoCommand();
            assertFalse(way2.isDeleted());
            assertFalse(way1.isDeleted());
            assertEquals(2, way1.getNodesCount());
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
            command.executeCommand();
            assertTrue(way2.isDeleted());
            assertFalse(way1.isDeleted());
            assertEquals(4, way1.getNodesCount());
            assertEquals(currentWay2Nodes.get(0), way1.firstNode());
            assertEquals(currentWay2Nodes.get(1), way1.getNode(1));
            command.undoCommand();
            assertFalse(way2.isDeleted());
            assertFalse(way1.isDeleted());
            assertEquals(2, way1.getNodesCount());
        }

        assertThrows(NullPointerException.class, () -> MergeDuplicateWays.mergeWays(null, null, null));
        assertNull(MergeDuplicateWays.mergeWays(new Way(), new Way(), Collections.emptySet()));
        assertNull(MergeDuplicateWays.mergeWays(new Way(), new Way(), set));
    }

    /**
     * Test method for {@link MergeDuplicateWays#checkDirection(Set)}.
     */
    @Test
    public void testCheckDirection() {
        final LinkedHashSet<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> set = new LinkedHashSet<>();
        final Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair1 = new Pair<>(
                new Pair<>(0, new Node(new LatLon(0, 0))), new Pair<>(0, new Node(new LatLon(0, 0))));
        final Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair2 = new Pair<>(
                new Pair<>(1, new Node(new LatLon(1, 0))), new Pair<>(1, new Node(new LatLon(1, 0))));
        set.add(pair1);
        set.add(pair2);

        assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.a.a = pair1.a.a - 1;
        assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.a.a = pair1.a.a + 3;
        assertFalse(MergeDuplicateWays.checkDirection(set));
        pair1.a.a = pair1.a.a - 2;

        assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.b.a = pair1.b.a - 1;
        assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.b.a = pair1.b.a + 3;
        assertFalse(MergeDuplicateWays.checkDirection(set));
        pair1.b.a = pair1.b.a - 2;

        assertThrows(NullPointerException.class, () -> MergeDuplicateWays.checkDirection(null));

        assertFalse(MergeDuplicateWays.checkDirection(Collections.emptySet()));
        set.remove(pair1);
        assertFalse(MergeDuplicateWays.checkDirection(set));
    }

    /**
     * Test method for {@link MergeDuplicateWays#sorted(List)}.
     */
    @Test
    public void testSorted() {
        List<Integer> integerList = Arrays.asList(1, 2, 3, 4, 6, 7, 8, 9, 5);
        assertFalse(MergeDuplicateWays.sorted(integerList));
        integerList = integerList.stream().sorted().collect(Collectors.toList());
        assertTrue(MergeDuplicateWays.sorted(integerList));
        integerList.remove(3);
        assertFalse(MergeDuplicateWays.sorted(integerList));

        integerList = Arrays.asList(1);
        assertTrue(MergeDuplicateWays.sorted(integerList));
    }

    /**
     * Test method for {@link MergeDuplicateWays#getDuplicateNodes(Way, Way)}.
     */
    @Test
    public void testGetDuplicateNodes() {
        final Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        final Way way2 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));

        Map<Pair<Integer, Node>, Map<Integer, Node>> duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size());
        assertEquals(2, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.addNode(new Node(new LatLon(0, 0)));

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size());
        assertEquals(3, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.addNode(way2.firstNode());

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size());
        assertEquals(4, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.setNodes(way2.getNodes().stream().limit(2).collect(Collectors.toList()));
        way2.addNode(new Node(new LatLon(2, 2)));

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        assertEquals(2, duplicateNodes.size());
        assertEquals(2, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());
    }

    /**
     * Test method for {@link MergeDuplicateWays#getDescriptionText}
     */
    @Test
    public void testGetDescriptionText() {
        final Command command = new MergeDuplicateWays(new DataSet());
        assertNotNull(command.getDescriptionText());
        assertFalse(command.getDescriptionText().isEmpty());
    }

    /**
     * Test method for {@link MergeDuplicateWays#nodeInCompressed}
     */
    @Test
    public void testNodeInCompressed() {
        final Node testNode = new Node();
        assertThrows(NullPointerException.class, () -> MergeDuplicateWays.nodeInCompressed(testNode, null));
        assertSame(testNode, MergeDuplicateWays.nodeInCompressed(testNode, Collections.emptySet()));
        final Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> testSet = new HashSet<>();
        testSet.add(new Pair<>(new Pair<>(1, new Node()), new Pair<>(2, new Node())));

        assertSame(testNode, MergeDuplicateWays.nodeInCompressed(testNode, testSet));
        Pair<Pair<Integer, Node>, Pair<Integer, Node>> matchPair = new Pair<>(new Pair<>(2, testNode),
                new Pair<>(1, new Node()));
        testSet.add(matchPair);
        assertSame(matchPair.b.b, MergeDuplicateWays.nodeInCompressed(testNode, testSet));

        testSet.remove(matchPair);
        matchPair = new Pair<>(new Pair<>(2, new Node()), new Pair<>(1, testNode));
        testSet.add(matchPair);
        assertSame(matchPair.a.b, MergeDuplicateWays.nodeInCompressed(testNode, testSet));
    }
}
