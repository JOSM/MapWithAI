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
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MovePrimitiveDataSetCommandTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

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
        Assert.assertEquals(0, to.allNonDeletedPrimitives().size());
        Assert.assertEquals(4, from.allNonDeletedPrimitives().size());

        move.executeCommand();
        move.fillModifiedData(modified, deleted, added);
        Assert.assertEquals(3, deleted.size());
        Assert.assertEquals(0, added.size()); // the JOSM Add command doesn't add to this list
        Assert.assertEquals(0, modified.size());
        Assert.assertEquals(1, from.allNonDeletedPrimitives().size());
        Assert.assertEquals(3, to.allNonDeletedPrimitives().size());
        Assert.assertNotNull(to.getPrimitiveById(way1));
        Assert.assertTrue(way1.isDeleted());

        move.undoCommand();
        Assert.assertEquals(0, to.allNonDeletedPrimitives().size());
        Assert.assertEquals(4, from.allNonDeletedPrimitives().size());
        Assert.assertNotNull(from.getPrimitiveById(way1));
        Assert.assertFalse(way1.isDeleted());
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
        Assert.assertEquals(0, added.size()); // the JOSM Add command doesn't add to this list
        Assert.assertEquals(0, modified.size());
        Assert.assertEquals(1, from.allNonDeletedPrimitives().size());
        Assert.assertEquals(3, to.allNonDeletedPrimitives().size());
        Assert.assertNotNull(to.getPrimitiveById(way1));

        move.undoCommand();
        Assert.assertEquals(0, to.allNonDeletedPrimitives().size());
        Assert.assertEquals(4, from.allNonDeletedPrimitives().size());
        Assert.assertEquals(from, way1.getDataSet());

        for (final DataSet ds : Arrays.asList(from, to)) {
            ds.lock();
            move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));
            move.executeCommand();
            Assert.assertEquals(0, to.allNonDeletedPrimitives().size());
            Assert.assertEquals(4, from.allNonDeletedPrimitives().size());
            Assert.assertNotNull(from.getPrimitiveById(way1));
            move.undoCommand();
            Assert.assertFalse(from.getPrimitiveById(way1).isDeleted());
            ds.unlock();
        }

        move = new MovePrimitiveDataSetCommand(to, null, Collections.singleton(way1));
        move.executeCommand();
        Assert.assertEquals(0, to.allNonDeletedPrimitives().size());
        Assert.assertEquals(4, from.allNonDeletedPrimitives().size());
        Assert.assertNotNull(from.getPrimitiveById(way1));
        move.undoCommand();
        Assert.assertFalse(from.getPrimitiveById(way1).isDeleted());

        move = new MovePrimitiveDataSetCommand(to, to, Collections.singleton(way1));
        move.executeCommand();
        Assert.assertEquals(0, to.allNonDeletedPrimitives().size());
        Assert.assertEquals(4, from.allNonDeletedPrimitives().size());
        Assert.assertNotNull(from.getPrimitiveById(way1));
        move.undoCommand();
        Assert.assertFalse(from.getPrimitiveById(way1).isDeleted());
    }

    @Test
    public void testMultipleUndoRedoWithMove() {
        UndoRedoHandler.getInstance().clean(); // Needed due to command line testing keeping instance from somewhere.
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().stream().forEach(node -> from.addPrimitive(node));
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        UndoRedoHandler.getInstance().add(new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1)));

        final Node tNode = (Node) to.getPrimitiveById(way1.firstNode());

        UndoRedoHandler.getInstance().add(new MoveCommand(tNode, LatLon.ZERO));

        Assert.assertTrue(UndoRedoHandler.getInstance().getRedoCommands().isEmpty());
        Assert.assertEquals(2, UndoRedoHandler.getInstance().getUndoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        Assert.assertTrue(UndoRedoHandler.getInstance().getUndoCommands().isEmpty());
        Assert.assertEquals(2, UndoRedoHandler.getInstance().getRedoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());
    }

    @Test
    public void testDescription() {
        Assert.assertNotNull(new MovePrimitiveDataSetCommand(new DataSet(), new DataSet(), Collections.emptyList())
                .getDescriptionText());
    }
}
