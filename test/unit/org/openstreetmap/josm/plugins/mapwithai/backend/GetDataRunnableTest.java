// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Geometry;

public class GetDataRunnableTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules().projection();

    @Test
    public void testAddMissingElement() {
        Way way1 = TestUtils.newWay("", new Node(new LatLon(-5.7117803, 34.5011898)),
                new Node(new LatLon(-5.7111915, 34.5013994)), new Node(new LatLon(-5.7104175, 34.5016354)));
        Way way2 = new Way();
        way2.setNodes(way1.getNodes());
        way2.addNode(1, new Node(new LatLon(-5.7115826, 34.5012438)));
        Map<WaySegment, List<WaySegment>> map = GetDataRunnable.checkWayDuplications(way1, way2);
        GetDataRunnable.addMissingElement(map.entrySet().iterator().next());

        assertEquals(4, way1.getNodesCount());
        assertEquals(4, way2.getNodesCount());
        assertSame(way2.getNode(1), way2.getNode(1));
        way1.removeNode(way1.getNode(1));

        List<Node> nodes = way2.getNodes();
        Collections.reverse(nodes);
        way2.setNodes(nodes);

        map = GetDataRunnable.checkWayDuplications(way1, way2);
        GetDataRunnable.addMissingElement(map.entrySet().iterator().next());

        assumeTrue(Math.abs(Geometry.getDistance(new Node(new LatLon(0, 0)), new Node(new LatLon(0, 1)))
                * ProjectionRegistry.getProjection().getMetersPerUnit() - 111_319.5) < 0.5);
        assertEquals(4, way1.getNodesCount());
        assertEquals(4, way2.getNodesCount());
        assertTrue(way1.getNodes().containsAll(way2.getNodes()));
    }

    @Test
    public void testCleanupArtifacts() {
        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        Way way2 = TestUtils.newWay("", way1.firstNode(), new Node(new LatLon(-1, -1)));
        DataSet ds = new DataSet();
        way1.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way1);
        way2.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
        ds.addPrimitive(way2);

        GetDataRunnable.cleanupArtifacts(way1);

        assertEquals(2, ds.getWays().parallelStream().filter(way -> !way.isDeleted()).count());

        Node tNode = new Node(way1.lastNode(), true);
        ds.addPrimitive(tNode);
        way2.addNode(tNode);

        GetDataRunnable.cleanupArtifacts(way1);
        assertEquals(1, ds.getWays().parallelStream().filter(way -> !way.isDeleted()).count());
    }

}
