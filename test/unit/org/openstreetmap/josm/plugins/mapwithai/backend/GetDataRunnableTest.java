// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class GetDataRunnableTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules().projection();

    @Test
    @Ignore("Failing on GitLab CI")
    public void testAddMissingElement() {
        Way way1 = TestUtils.newWay("", new Node(new LatLon(-5.7117803, 34.5011898)),
                new Node(new LatLon(-5.7111915, 34.5013994)), new Node(new LatLon(-5.7104175, 34.5016354)));
        Way way2 = new Way(way1);
        way2.addNode(1, new Node(new LatLon(-5.7115826, 34.5012438)));
        Map<WaySegment, List<WaySegment>> map = GetDataRunnable.checkWayDuplications(way1, way2);
        GetDataRunnable.addMissingElement(map.entrySet().iterator().next());

        assertEquals(4, way1.getNodesCount());
        assertEquals(4, way2.getNodesCount());

        way1.removeNode(way1.getNode(1));

        List<Node> nodes = way2.getNodes();
        Collections.reverse(nodes);
        way2.setNodes(nodes);

        map = GetDataRunnable.checkWayDuplications(way1, way2);
        GetDataRunnable.addMissingElement(map.entrySet().iterator().next());

        assertEquals(4, way1.getNodesCount());
        assertEquals(4, way2.getNodesCount());
        assertTrue(way1.getNodes().containsAll(way2.getNodes()));
    }

}
