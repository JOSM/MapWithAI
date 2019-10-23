// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;


import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.commands.MergeDuplicateWays;
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
     * Test method for {@link GetDataRunnable#removeCommonTags(DataSet)}.
     */
    @Test
    public void testRemoveCommonTags() {
        DataSet ds1 = new DataSet(TestUtils.newNode("orig_id=2222 highway=secondary"));
        GetDataRunnable.removeCommonTags(ds1);
        Assert.assertEquals(1, ds1.allPrimitives().stream().mapToInt(prim -> prim.getKeys().size()).sum());
        GetDataRunnable.removeCommonTags(ds1);
        Assert.assertEquals(1, ds1.allPrimitives().stream().mapToInt(prim -> prim.getKeys().size()).sum());
    }

    /**
     * Test method for {@link GetDataRunnable#filterDataSet(DataSet)}.
     */
    @Test
    public void testFilterDataSet() {
        DataSet ds1 = new DataSet();
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 1)), new Node(new LatLon(1, 2)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)),
                new Node(new LatLon(2, 2)));
        way1.getNodes().forEach(ds1::addPrimitive);
        way2.getNodes().forEach(ds1::addPrimitive);
        ds1.addPrimitive(way1);
        ds1.addPrimitive(way2);

        new MergeDuplicateWays(ds1).executeCommand();
        Assert.assertFalse(way1.isDeleted());
        Assert.assertFalse(way2.isDeleted());
        way1.getNodes().forEach(node -> Assert.assertFalse(way2.containsNode(node)));
        way2.getNodes().forEach(node -> Assert.assertFalse(way1.containsNode(node)));

        Node tNode = new Node(new LatLon(1, 1));
        ds1.addPrimitive(tNode);
        way1.addNode(1, tNode);

        new MergeDuplicateWays(ds1).executeCommand();
        Assert.assertNotSame(way1.isDeleted(), way2.isDeleted());
        Way tWay = way1.isDeleted() ? way2 : way1;
        Assert.assertEquals(4, tWay.getNodesCount());
    }

    /**
     * Test method for {@link GetDataRunnable#mergeWays(Way, Way, Set)}.
     */
    @Test
    public void testMergeWays() {
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)));
        Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> set = new LinkedHashSet<>();
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(0, way2.firstNode())));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        way2.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way2);
        ds.addPrimitive(way1);

        // Test with one node in common
        Assert.assertNull(MergeDuplicateWays.mergeWays(way1, way2, set));
        Assert.assertFalse(way2.isDeleted());
        Assert.assertFalse(way1.isDeleted());
        Assert.assertEquals(2, way1.getNodesCount());

        Node tNode = new Node(new LatLon(0, 0));
        ds.addPrimitive(tNode);
        way2.addNode(0, tNode);
        set.clear(); // we can't use the last pair added
        set.add(new Pair<>(new Pair<>(0, way1.firstNode()), new Pair<>(0, way2.firstNode())));
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(1, way2.getNode(1))));
        MergeDuplicateWays.mergeWays(way1, way2, set).executeCommand();

        Assert.assertTrue(way2.isDeleted());
        Assert.assertFalse(way1.isDeleted());
        Assert.assertEquals(3, way1.getNodesCount());

        way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        way2 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(1, 2)));
        way2.addNode(0, new Node(new LatLon(0, 0)));
        ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        way2.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way2);
        ds.addPrimitive(way1);
        List<Node> way2Nodes = way2.getNodes();
        Collections.reverse(way2Nodes);
        way2.setNodes(way2Nodes);
        set.clear();
        set.add(new Pair<>(new Pair<>(0, way1.firstNode()), new Pair<>(2, way2.lastNode())));
        set.add(new Pair<>(new Pair<>(1, way1.lastNode()), new Pair<>(1, way2.getNode(1))));
        MergeDuplicateWays.mergeWays(way1, way2, set).executeCommand();

        Assert.assertTrue(way2.isDeleted());
        Assert.assertFalse(way1.isDeleted());
        Assert.assertEquals(3, way1.getNodesCount());

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
        List<Node> currentWay2Nodes = way2.getNodes();
        MergeDuplicateWays.mergeWays(way1, way2, set).executeCommand();

        Assert.assertTrue(way2.isDeleted());
        Assert.assertFalse(way1.isDeleted());
        Assert.assertEquals(4, way1.getNodesCount());
        Assert.assertEquals(currentWay2Nodes.get(0), way1.firstNode());
        Assert.assertEquals(currentWay2Nodes.get(1), way1.getNode(1));
    }

    /**
     * Test method for {@link GetDataRunnable#checkDirection(Set)}.
     */
    @Test
    public void testCheckDirection() {
        LinkedHashSet<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> set = new LinkedHashSet<>();
        Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair1 = new Pair<>(new Pair<>(0, new Node(new LatLon(0, 0))),
                new Pair<>(0, new Node(new LatLon(0, 0))));
        Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair2 = new Pair<>(new Pair<>(1, new Node(new LatLon(1, 0))),
                new Pair<>(1, new Node(new LatLon(1, 0))));
        set.add(pair1);
        set.add(pair2);

        Assert.assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.a.a = pair1.a.a - 1;
        Assert.assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.a.a = pair1.a.a + 3;
        Assert.assertFalse(MergeDuplicateWays.checkDirection(set));
        pair1.a.a = pair1.a.a - 2;

        Assert.assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.b.a = pair1.b.a - 1;
        Assert.assertTrue(MergeDuplicateWays.checkDirection(set));
        pair1.b.a = pair1.b.a + 3;
        Assert.assertFalse(MergeDuplicateWays.checkDirection(set));
        pair1.b.a = pair1.b.a - 2;
    }

    /**
     * Test method for {@link GetDataRunnable#sorted(List)}.
     */
    @Test
    public void testSorted() {
        List<Integer> integerList = Arrays.asList(1, 2, 3, 4, 6, 7, 8, 9, 5);
        Assert.assertFalse(MergeDuplicateWays.sorted(integerList));
        integerList = integerList.stream().sorted().collect(Collectors.toList());
        Assert.assertTrue(MergeDuplicateWays.sorted(integerList));
        integerList.remove(3);
        Assert.assertFalse(MergeDuplicateWays.sorted(integerList));

        integerList = Arrays.asList(1);
        Assert.assertTrue(MergeDuplicateWays.sorted(integerList));
    }

    /**
     * Test method for {@link GetDataRunnable#getDuplicateNodes(Way, Way)}.
     */
    @Test
    public void testGetDuplicateNodes() {
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));

        Map<Pair<Integer, Node>, Map<Integer, Node>> duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        Assert.assertEquals(2, duplicateNodes.size());
        Assert.assertEquals(2, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.addNode(new Node(new LatLon(0, 0)));

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        Assert.assertEquals(2, duplicateNodes.size());
        Assert.assertEquals(3, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.addNode(way2.firstNode());

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        Assert.assertEquals(2, duplicateNodes.size());
        Assert.assertEquals(4, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());

        way2.setNodes(way2.getNodes().stream().limit(2).collect(Collectors.toList()));
        way2.addNode(new Node(new LatLon(2, 2)));

        duplicateNodes = MergeDuplicateWays.getDuplicateNodes(way1, way2);
        Assert.assertEquals(2, duplicateNodes.size());
        Assert.assertEquals(2, duplicateNodes.values().stream().flatMap(col -> col.keySet().stream()).count());
    }

}
