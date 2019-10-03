// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;;

public class AddNodeToWayCommandTest {
    private Node toAdd;
    private Way way;
    private AddNodeToWayCommand command;
    @Rule
    public JOSMTestRules test = new JOSMTestRules();

    @Before
    public void setupArea() {
        toAdd = new Node(new LatLon(0.1, 0.1));
        way = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(-0.1, -0.1)));
        new DataSet(toAdd, way.firstNode(), way.lastNode(), way);
        command = new AddNodeToWayCommand(toAdd, way, way.firstNode(), way.lastNode());
    }

    @Test
    public void testAddNodeToWay() {
        command.executeCommand();
        Assert.assertEquals(3, way.getNodesCount());

        command.undoCommand();
        Assert.assertEquals(2, way.getNodesCount());

        command = new AddNodeToWayCommand(toAdd, way, way.lastNode(), way.firstNode());

        command.executeCommand();
        Assert.assertEquals(3, way.getNodesCount());

        command.undoCommand();
        Assert.assertEquals(2, way.getNodesCount());
    }

    @Test
    public void testDescription() {
        Assert.assertNotNull(command.getDescriptionText());
    }

    @Test
    public void testModifiedAddedDeleted() {
        final List<OsmPrimitive> added = new ArrayList<>();
        final List<OsmPrimitive> modified = new ArrayList<>();
        final List<OsmPrimitive> deleted = new ArrayList<>();
        command.fillModifiedData(modified, deleted, added);
        Assert.assertTrue(deleted.isEmpty());
        Assert.assertTrue(added.isEmpty());
        Assert.assertEquals(2, modified.size());
    }
}
