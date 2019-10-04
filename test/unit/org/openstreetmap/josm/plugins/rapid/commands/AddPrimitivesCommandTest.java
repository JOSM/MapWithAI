// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

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

public class AddPrimitivesCommandTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testAddPrimitives() {
        final DataSet dataSet = new DataSet();
        final Way way1 = TestUtils.newWay("highway=secondary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        Assert.assertNull(way1.getDataSet());

        final Collection<OsmPrimitive> added = AddPrimitivesCommand.addPrimitives(dataSet, Collections.singleton(way1));
        Assert.assertEquals(3, added.size());
        Assert.assertSame(dataSet, way1.getDataSet());
    }

    @Test
    public void testUndoRedo() {
        final DataSet dataSet = new DataSet();
        final List<OsmPrimitive> added = new ArrayList<>();
        final List<OsmPrimitive> modified = new ArrayList<>();
        final List<OsmPrimitive> deleted = new ArrayList<>();
        final Way way1 = TestUtils.newWay("highway=secondary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, -0.1)));
        AddPrimitivesCommand command = new AddPrimitivesCommand(dataSet, Collections.singleton(way1), null);
        final Collection<OsmPrimitive> selection = dataSet.getAllSelected();
        Assert.assertNull(way1.getDataSet());

        command.executeCommand();
        command.fillModifiedData(modified, deleted, added);
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));
        Assert.assertTrue(deleted.isEmpty());
        Assert.assertTrue(modified.isEmpty());
        Assert.assertEquals(3, added.size());

        command.undoCommand();
        Assert.assertNull(way1.getDataSet());
        Assert.assertEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        added.clear();
        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));
        command.fillModifiedData(modified, deleted, added);
        Assert.assertTrue(deleted.isEmpty());
        Assert.assertTrue(modified.isEmpty());
        Assert.assertEquals(3, added.size());

        command.undoCommand();

        command = new AddPrimitivesCommand(dataSet, Collections.singleton(way1), Collections.singleton(way1));

        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertNotEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        command.undoCommand();
        Assert.assertNull(way1.getDataSet());
        Assert.assertEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        dataSet.addPrimitive(way1.firstNode());
        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertNotEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        command.undoCommand();
        Assert.assertNull(way1.getDataSet());
        Assert.assertEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        dataSet.addPrimitive(way1.lastNode());
        dataSet.addPrimitive(way1);
        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertNotEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        command.undoCommand();
        Assert.assertSame(dataSet, way1.getDataSet());

        dataSet.removePrimitive(way1);
        dataSet.removePrimitive(way1.lastNode());
        new DataSet().addPrimitive(way1.lastNode());
        command.executeCommand();
        Assert.assertNull(way1.getDataSet());
        Assert.assertEquals(new TreeSet<>(selection), new TreeSet<>(dataSet.getAllSelected()));

        command.undoCommand();
        Assert.assertNull(way1.getDataSet());
    }

    @Test
    public void testDescription() {
        Assert.assertNotNull(new AddPrimitivesCommand(new DataSet(), null, null).getDescriptionText());
    }
}
