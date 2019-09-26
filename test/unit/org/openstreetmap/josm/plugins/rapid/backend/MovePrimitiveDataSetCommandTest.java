// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class MovePrimitiveDataSetCommandTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testMovePrimitives() {
        DataSet to = new DataSet();
        DataSet from = new DataSet();
        Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().stream().forEach(node -> from.addPrimitive(node));
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        MovePrimitiveDataSetCommand move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());

        move.executeCommand();
        Assert.assertEquals(1, from.allPrimitives().size());
        Assert.assertEquals(3, to.allPrimitives().size());
        Assert.assertEquals(to, way1.getDataSet());

        move.undoCommand();
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());

        way1.firstNode().put("highway", "stop");

        move.executeCommand();
        Assert.assertEquals(1, from.allPrimitives().size());
        Assert.assertEquals(3, to.allPrimitives().size());
        Assert.assertEquals(to, way1.getDataSet());

        move.undoCommand();
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());
    }
}
