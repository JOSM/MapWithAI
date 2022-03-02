// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Command;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Command
class MovePrimitiveDataSetCommandTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new JOSMTestRules().projection();

    @Test
    void testMovePrimitives() {
        final Collection<OsmPrimitive> added = new ArrayList<>();
        final Collection<OsmPrimitive> modified = new ArrayList<>();
        final Collection<OsmPrimitive> deleted = new ArrayList<>();
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(from::addPrimitive);
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        final MovePrimitiveDataSetCommand move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));
        assertAll(() -> assertEquals(0, to.allNonDeletedPrimitives().size()),
                () -> assertEquals(4, from.allNonDeletedPrimitives().size()));

        move.executeCommand();
        move.fillModifiedData(modified, deleted, added);
        final Collection<? extends OsmPrimitive> participatingPrimitives = move.getParticipatingPrimitives();
        assertAll(() -> assertEquals(0, deleted.size()), () -> assertEquals(0, added.size()), // the JOSM Add command
                                                                                              // doesn't add to this
                                                                                              // list
                () -> assertEquals(0, modified.size()), () -> assertEquals(1, from.allNonDeletedPrimitives().size()),
                () -> assertEquals(3, to.allNonDeletedPrimitives().size()),
                () -> assertNotNull(to.getPrimitiveById(way1)), () -> assertTrue(way1.isDeleted()),
                // This should be 6 (3 from originating dataset
                () -> assertEquals(3, participatingPrimitives.size()),
                () -> assertTrue(participatingPrimitives.contains(way1)),
                () -> assertTrue(participatingPrimitives.containsAll(way1.getNodes())));

        move.undoCommand();
        assertAll(() -> assertEquals(0, to.allNonDeletedPrimitives().size()),
                () -> assertEquals(4, from.allNonDeletedPrimitives().size()),
                () -> assertNotNull(from.getPrimitiveById(way1)), () -> assertFalse(way1.isDeleted()));
    }

    @Test
    void testMovePrimitivesAdditionalData() {
        final Collection<OsmPrimitive> added = new ArrayList<>();
        final Collection<OsmPrimitive> modified = new ArrayList<>();
        final Collection<OsmPrimitive> deleted = new ArrayList<>();
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(from::addPrimitive);
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        MovePrimitiveDataSetCommand move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));

        way1.firstNode().put("highway", "stop");

        move.executeCommand();
        move.fillModifiedData(modified, deleted, added);
        final List<OsmPrimitive> participatingPrimitives = new ArrayList<>(move.getParticipatingPrimitives());
        assertAll(() -> assertEquals(0, deleted.size()), () -> assertEquals(0, added.size()), // the JOSM Add command
                                                                                              // doesn't add to this
                                                                                              // list
                () -> assertEquals(0, modified.size()), () -> assertEquals(1, from.allNonDeletedPrimitives().size()),
                () -> assertEquals(3, to.allNonDeletedPrimitives().size()),
                () -> assertNotNull(to.getPrimitiveById(way1)),
                // This should be 6, but due to the way hashCode is implemented in
                // OsmPrimitive, it is 3 (the code hashes the id only).
                // Fortunately, Sets use the hashcode to check contains
                () -> assertEquals(3, participatingPrimitives.size()));

        move.undoCommand();
        assertAll(() -> assertEquals(0, to.allNonDeletedPrimitives().size()),
                () -> assertEquals(4, from.allNonDeletedPrimitives().size()),
                () -> assertEquals(from, way1.getDataSet()));

        for (final DataSet ds : Arrays.asList(from, to)) {
            ds.lock();
            move = new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1));
            move.executeCommand();
            participatingPrimitives.clear();
            participatingPrimitives.addAll(move.getParticipatingPrimitives());
            assertAll(() -> assertEquals(0, to.allNonDeletedPrimitives().size()),
                    () -> assertEquals(4, from.allNonDeletedPrimitives().size()),
                    () -> assertNotNull(from.getPrimitiveById(way1)),
                    // Nothing should change...
                    () -> assertTrue(participatingPrimitives.isEmpty()));
            move.undoCommand();
            assertFalse(from.getPrimitiveById(way1).isDeleted());
            ds.unlock();
        }

        move = new MovePrimitiveDataSetCommand(to, null, Collections.singleton(way1));
        move.executeCommand();
        participatingPrimitives.clear();
        participatingPrimitives.addAll(move.getParticipatingPrimitives());
        assertAll(() -> assertEquals(0, to.allNonDeletedPrimitives().size()),
                () -> assertEquals(4, from.allNonDeletedPrimitives().size()),
                () -> assertNotNull(from.getPrimitiveById(way1)), () -> assertTrue(participatingPrimitives.isEmpty()));
        move.undoCommand();
        assertFalse(from.getPrimitiveById(way1).isDeleted());

        move = new MovePrimitiveDataSetCommand(to, to, Collections.singleton(way1));
        move.executeCommand();
        participatingPrimitives.clear();
        participatingPrimitives.addAll(move.getParticipatingPrimitives());
        assertAll(() -> assertEquals(0, to.allNonDeletedPrimitives().size()),
                () -> assertEquals(4, from.allNonDeletedPrimitives().size()),
                () -> assertNotNull(from.getPrimitiveById(way1)), () -> assertTrue(participatingPrimitives.isEmpty()));
        move.undoCommand();
        assertFalse(from.getPrimitiveById(way1).isDeleted());
    }

    @Test
    void testMultipleUndoRedoWithMove() {
        UndoRedoHandler.getInstance().clean(); // Needed due to command line testing keeping instance from somewhere.
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().stream().forEach(node -> from.addPrimitive(node));
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        final Node way1Node1 = way1.firstNode();

        UndoRedoHandler.getInstance().add(new MovePrimitiveDataSetCommand(to, from, Collections.singleton(way1)));

        final Node tNode = (Node) to.getPrimitiveById(way1Node1);

        UndoRedoHandler.getInstance().add(new MoveCommand(tNode, LatLon.ZERO));

        assertTrue(UndoRedoHandler.getInstance().getRedoCommands().isEmpty());
        assertEquals(2, UndoRedoHandler.getInstance().getUndoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        assertTrue(UndoRedoHandler.getInstance().getUndoCommands().isEmpty());
        assertEquals(2, UndoRedoHandler.getInstance().getRedoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());
    }

    @Test
    void testDescription() {
        Node tNode = new Node(new LatLon(0, 0));
        DataSet from = new DataSet(tNode);
        assertNotNull(new MovePrimitiveDataSetCommand(new DataSet(), from, Collections.singleton(tNode))
                .getDescriptionText());
    }
}
