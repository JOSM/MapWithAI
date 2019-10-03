// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class MovePrimitiveDataSetCommandTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testMovePrimitives() {
        final Collection<OsmPrimitive> added = new ArrayList<>();
        final Collection<OsmPrimitive> modified = new ArrayList<>();
        final Collection<OsmPrimitive> deleted = new ArrayList<>();
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().stream().forEach(node -> from.addPrimitive(node));
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        final MovePrimitiveDataSetCommand move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());

        move.executeCommand();
        move.fillModifiedData(modified, deleted, added);
        Assert.assertEquals(3, deleted.size());
        Assert.assertEquals(3, added.size());
        Assert.assertEquals(0, modified.size());
        Assert.assertEquals(1, from.allPrimitives().size());
        Assert.assertEquals(3, to.allPrimitives().size());
        Assert.assertEquals(to, way1.getDataSet());

        move.undoCommand();
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());
    }

    @Test
    public void testMovePrimitivesAdditionalData() {
        final Collection<OsmPrimitive> added = new ArrayList<>();
        final Collection<OsmPrimitive> modified = new ArrayList<>();
        final Collection<OsmPrimitive> deleted = new ArrayList<>();
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().stream().forEach(node -> from.addPrimitive(node));
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        MovePrimitiveDataSetCommand move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));

        way1.firstNode().put("highway", "stop");

        modified.clear();
        added.clear();
        deleted.clear();
        move.executeCommand();
        move.fillModifiedData(modified, deleted, added);
        Assert.assertEquals(3, deleted.size());
        Assert.assertEquals(3, added.size());
        Assert.assertEquals(0, modified.size());
        Assert.assertEquals(1, from.allPrimitives().size());
        Assert.assertEquals(3, to.allPrimitives().size());
        Assert.assertEquals(to, way1.getDataSet());

        move.undoCommand();
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());

        for (final DataSet ds : Arrays.asList(from, to)) {
            ds.lock();
            move.executeCommand();
            Assert.assertEquals(0, to.allPrimitives().size());
            Assert.assertEquals(4, from.allPrimitives().size());
            Assert.assertEquals(from, way1.getDataSet());
            move.undoCommand();
            ds.unlock();
        }

        move = new MovePrimitiveDataSetCommand(to, null, Collections.singleton(way1));
        move.executeCommand();
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());
        move.undoCommand();

        move = new MovePrimitiveDataSetCommand(to, to, Collections.singleton(way1));
        move.executeCommand();
        Assert.assertEquals(0, to.allPrimitives().size());
        Assert.assertEquals(4, from.allPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());
        move.undoCommand();
    }

    @Test
    public void testDescription() {
        Assert.assertNotNull(new MovePrimitiveDataSetCommand(new DataSet(), new DataSet(), Collections.emptyList())
                .getDescriptionText());
    }
}
