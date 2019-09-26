// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDAddCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDAddComandTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testMoveCollection() {
        DataSet ds1 = new DataSet();
        DataSet ds2 = new DataSet();
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        Way way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-0.1, -0.2)), way1.firstNode());
        Way way3 = TestUtils.newWay("highway=residential", new Node(new LatLon(65, 65)), new Node(new LatLon(66, 66)));
        for (Way way : Arrays.asList(way1, way2, way3)) {
            for (Node node : way.getNodes()) {
                if (!ds1.containsNode(node)) {
                    ds1.addPrimitive(node);
                }
            }
            if (!ds1.containsWay(way)) {
                ds1.addPrimitive(way);
            }
        }
        ds1.lock();
        RapiDAddCommand command = new RapiDAddCommand(ds1, ds2, Arrays.asList(way1, way2));
        command.executeCommand();
        Assert.assertTrue(ds2.containsWay(way1));
        Assert.assertTrue(ds2.containsNode(way1.firstNode()));
        Assert.assertTrue(ds2.containsNode(way1.lastNode()));
        Assert.assertFalse(ds1.containsWay(way1));
        Assert.assertTrue(ds2.containsWay(way2));
        Assert.assertTrue(ds2.containsNode(way2.firstNode()));
        Assert.assertTrue(ds2.containsNode(way2.lastNode()));
        Assert.assertFalse(ds1.containsWay(way2));

        Assert.assertFalse(ds2.containsWay(way3));
        command = new RapiDAddCommand(ds1, ds2, Arrays.asList(way3));
        command.executeCommand();
        Assert.assertTrue(ds2.containsWay(way3));
        Assert.assertTrue(ds2.containsNode(way3.firstNode()));
        Assert.assertTrue(ds2.containsNode(way3.lastNode()));
        Assert.assertFalse(ds1.containsWay(way3));
    }

    @Test
    public void testCreateConnections() {
        DataSet ds1 = new DataSet();
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.15)));
        Way way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0.05)),
                new Node(new LatLon(0.05, 0.2)));
        way2.firstNode().put("conn",
                "w".concat(Long.toString(way1.getUniqueId())).concat(",n")
                .concat(Long.toString(way1.firstNode().getUniqueId())).concat(",n")
                        .concat(Long.toString(way1.lastNode().getUniqueId())));
        way1.getNodes().forEach(node -> ds1.addPrimitive(node));
        way2.getNodes().forEach(node -> ds1.addPrimitive(node));
        ds1.addPrimitive(way2);
        ds1.addPrimitive(way1);
        RapiDAddCommand.createConnections(ds1, Collections.singletonList(way2.firstNode()));
        Assert.assertEquals(3, way1.getNodesCount());
        Assert.assertFalse(way1.isFirstLastNode(way2.firstNode()));

        Way way3 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(-0.1, -0.1)));
        way3.firstNode().put("dupe", "n".concat(Long.toString(way1.firstNode().getUniqueId())));
        way3.getNodes().forEach(node -> ds1.addPrimitive(node));
        ds1.addPrimitive(way3);
        Node way3Node1 = way3.firstNode();
        RapiDAddCommand.createConnections(ds1, Collections.singletonList(way3.firstNode()));
        Assert.assertNotEquals(way3Node1, way3.firstNode());
        Assert.assertEquals(way1.firstNode(), way3.firstNode());
        Assert.assertFalse(ds1.containsNode(way3Node1));
    }
}
