// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Command;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Command
class AddNodeToWayCommandTest {
    private Node toAdd;
    private Way way;
    private AddNodeToWayCommand command;
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new JOSMTestRules().projection();

    @BeforeEach
    void setupArea() {
        toAdd = new Node(new LatLon(0, 0));
        way = TestUtils.newWay("", new Node(new LatLon(0.1, 0.1)), new Node(new LatLon(-0.1, -0.1)));
        new DataSet(toAdd, way.firstNode(), way.lastNode(), way);
        command = new AddNodeToWayCommand(toAdd, way, way.firstNode(), way.lastNode());
    }

    @Test
    void testAddNodeToWay() {
        command.executeCommand();
        assertEquals(3, way.getNodesCount(), "A node should have been added to the way");

        command.undoCommand();
        assertEquals(2, way.getNodesCount(), "The way should no longer be modified");

        command = new AddNodeToWayCommand(toAdd, way, way.lastNode(), way.firstNode());

        command.executeCommand();
        assertEquals(3, way.getNodesCount(), "A node should have been added to the way");

        command.undoCommand();
        assertEquals(2, way.getNodesCount(), "The way should no longer be modified");
    }

    @Test
    void testDescription() {
        assertNotNull(command.getDescriptionText(), "The command should have a description");
    }

    @Test
    void testModifiedAddedDeleted() {
        final List<OsmPrimitive> added = new ArrayList<>();
        final List<OsmPrimitive> modified = new ArrayList<>();
        final List<OsmPrimitive> deleted = new ArrayList<>();
        command.fillModifiedData(modified, deleted, added);
        assertTrue(deleted.isEmpty(), "Nothing should have been deleted");
        assertTrue(added.isEmpty(), "Nothing should have been added");
        assertEquals(2, modified.size(), "The way should have been modified, node is included in count");
    }

    @Test
    void testMultiAddConnections() {
        command.executeCommand();
        Node tNode = new Node(new LatLon(0.01, 0.01));
        way.getDataSet().addPrimitive(tNode);
        command = new AddNodeToWayCommand(tNode, way, way.firstNode(), way.lastNode());
        command.executeCommand();
        assertEquals(new LatLon(0.1, 0.1), way.firstNode().getCoor());
        assertEquals(new LatLon(0.01, 0.01), way.getNode(1).getCoor());
        assertEquals(new LatLon(0, 0), way.getNode(2).getCoor());
        assertEquals(new LatLon(-0.1, -0.1), way.lastNode().getCoor());
        command.undoCommand();
        tNode.setCoor(new LatLon(-0.01, -0.01));
        command = new AddNodeToWayCommand(tNode, way, way.firstNode(), way.lastNode());
        command.executeCommand();
        assertEquals(new LatLon(0.1, 0.1), way.firstNode().getCoor());
        assertEquals(new LatLon(0, 0), way.getNode(1).getCoor());
        assertEquals(new LatLon(-0.01, -0.01), way.getNode(2).getCoor());
        assertEquals(new LatLon(-0.1, -0.1), way.lastNode().getCoor());
    }
}