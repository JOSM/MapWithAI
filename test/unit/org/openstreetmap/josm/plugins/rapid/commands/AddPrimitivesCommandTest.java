// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

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
import org.openstreetmap.josm.plugins.rapid.commands.AddPrimitivesCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class AddPrimitivesCommandTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testAddPrimitives() {
        DataSet dataSet = new DataSet();
        Way way1 = TestUtils.newWay("highway=secondary", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        Assert.assertNull(way1.getDataSet());

        Collection<OsmPrimitive> added = AddPrimitivesCommand.addPrimitives(dataSet, Collections.singleton(way1));
        Assert.assertEquals(3, added.size());
        Assert.assertSame(dataSet, way1.getDataSet());
    }

    @SuppressWarnings("UndefinedEquals")
    @Test
    public void testUndoRedo() {
        DataSet dataSet = new DataSet();
        Way way1 = TestUtils.newWay("highway=secondary", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, -0.1)));
        AddPrimitivesCommand command = new AddPrimitivesCommand(dataSet, Collections.singleton(way1), null);
        Collection<OsmPrimitive> selection = dataSet.getAllSelected();
        Assert.assertNull(way1.getDataSet());

        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertEquals(selection, dataSet.getAllSelected());

        command.undoCommand();
        Assert.assertNull(way1.getDataSet());
        Assert.assertEquals(selection, dataSet.getAllSelected());

        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertEquals(selection, dataSet.getAllSelected());

        command.undoCommand();

        command = new AddPrimitivesCommand(dataSet, Collections.singleton(way1), Collections.singleton(way1));

        command.executeCommand();
        Assert.assertSame(dataSet, way1.getDataSet());
        Assert.assertNotEquals(selection, dataSet.getAllSelected());

        command.undoCommand();
        Assert.assertNull(way1.getDataSet());
        Assert.assertEquals(selection, dataSet.getAllSelected());
    }
}
