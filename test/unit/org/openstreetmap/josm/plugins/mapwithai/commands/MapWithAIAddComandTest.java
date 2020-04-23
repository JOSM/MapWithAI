// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.SaveActionBase;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.tests.SharpAngles;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.SwingUtilitiesMocker;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Logging;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIAddComandTest {
    private final static String HIGHWAY_RESIDENTIAL = "highway=residential";

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new MapWithAITestRules().wiremock().projection().assertionsInEDT();

    @Test
    public void testMoveCollection() {
        final DataSet ds1 = new DataSet();
        final DataSet ds2 = new DataSet();
        final Way way1 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0)),
                new Node(new LatLon(0, 0.1)));
        final Way way2 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(-0.1, -0.2)), way1.firstNode());
        final Way way3 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(65, 65)),
                new Node(new LatLon(66, 66)));
        for (final Way way : Arrays.asList(way1, way2, way3)) {
            for (final Node node : way.getNodes()) {
                if (!ds1.containsNode(node)) {
                    ds1.addPrimitive(node);
                }
            }
            if (!ds1.containsWay(way)) {
                ds1.addPrimitive(way);
            }
        }
        MapWithAIAddCommand command = new MapWithAIAddCommand(ds1, ds2, Arrays.asList(way1, way2));
        command.executeCommand();
        for (Way way : Arrays.asList(way1, way2)) {
            assertNotNull(ds2.getPrimitiveById(way), "DataSet should still contain object");
            assertTrue(way.isDeleted(), "The way should be deleted");
        }

        assertNull(ds2.getPrimitiveById(way3), "DataSet should not yet have way3");
        command = new MapWithAIAddCommand(ds1, ds2, Arrays.asList(way3));
        command.executeCommand();
        assertNotNull(ds2.getPrimitiveById(way3), "DataSet should still contain object");
        assertTrue(way3.isDeleted(), "The way should be deleted");

        command.undoCommand();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> ds2.getPrimitiveById(way3) == null);
        assertNull(ds2.getPrimitiveById(way3), "DataSet should no longer contain object");
        assertFalse(ds1.getPrimitiveById(way3).isDeleted(),
                "The way should no longer be deleted in its original DataSet");
    }

    @Test
    public void testCreateConnections() {
        final DataSet ds1 = new DataSet();
        final Way way1 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0)),
                new Node(new LatLon(0, 0.15)));
        final Way way2 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0.05)),
                new Node(new LatLon(0.05, 0.2)));
        way2.firstNode().put("conn",
                "w".concat(Long.toString(way1.getUniqueId())).concat(",n")
                        .concat(Long.toString(way1.firstNode().getUniqueId())).concat(",n")
                        .concat(Long.toString(way1.lastNode().getUniqueId())));
        way1.getNodes().forEach(node -> ds1.addPrimitive(node));
        way2.getNodes().forEach(node -> ds1.addPrimitive(node));
        ds1.addPrimitive(way2);
        ds1.addPrimitive(way1);
        MapWithAIAddCommand.createConnections(ds1, Collections.singletonList(way2.firstNode().save())).executeCommand();
        assertEquals(3, way1.getNodesCount(), "The way should now have three nodes");
        assertFalse(way1.isFirstLastNode(way2.firstNode()), "The ways should be connected");

        final Way way3 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0)),
                new Node(new LatLon(-0.1, -0.1)));
        way3.firstNode().put("dupe", "n".concat(Long.toString(way1.firstNode().getUniqueId())));
        way3.getNodes().forEach(node -> ds1.addPrimitive(node));
        ds1.addPrimitive(way3);
        final Node way3Node1 = way3.firstNode();
        MapWithAIAddCommand.createConnections(ds1, Collections.singletonList(way3.firstNode().save())).executeCommand();
        assertNotEquals(way3Node1, way3.firstNode(), "The original first node should no longer be the first node");
        assertEquals(way1.firstNode(), way3.firstNode(), "way1 and way3 should have the same first nodes");
        assertTrue(way3Node1.isDeleted(), "way3 should be deleted");
    }

    @Test
    public void testCreateConnectionsUndo() {
        final DataSet osmData = new DataSet();
        final DataSet mapWithAIData = new DataSet();
        final Way way1 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        final Way way2 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(-0.1, -0.1)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(node -> mapWithAIData.addPrimitive(node));
        way2.getNodes().forEach(node -> osmData.addPrimitive(node));
        osmData.addPrimitive(way2);
        mapWithAIData.addPrimitive(way1);
        mapWithAIData.setSelected(way1);

        MapWithAIAddCommand command = new MapWithAIAddCommand(mapWithAIData, osmData, mapWithAIData.getSelected());
        command.executeCommand();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> mapWithAIData.allNonDeletedPrimitives().isEmpty());
        assertEquals(6, osmData.allNonDeletedPrimitives().size(), "All primitives should now be in osmData");
        assertTrue(mapWithAIData.allNonDeletedPrimitives().isEmpty(),
                "There should be no remaining non-deleted primitives");

        command.undoCommand();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> osmData.allNonDeletedPrimitives().size() == 3);
        assertEquals(3, osmData.allNonDeletedPrimitives().size(), "The DataSet should be in its original state");
        assertEquals(3, mapWithAIData.allNonDeletedPrimitives().size(), "The DataSet should be in its original state");

        final Tag dupe = new Tag("dupe", "n".concat(Long.toString(way2.lastNode().getUniqueId())));
        way1.lastNode().put(dupe);
        command = new MapWithAIAddCommand(mapWithAIData, osmData, mapWithAIData.getSelected());
        command.executeCommand();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> osmData.allNonDeletedPrimitives().size() == 5);
        assertEquals(5, osmData.allNonDeletedPrimitives().size(), "All primitives should now be in osmData");
        assertTrue(mapWithAIData.allNonDeletedPrimitives().isEmpty(),
                "There should be no remaining non-deleted primitives");

        command.undoCommand();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> osmData.allNonDeletedPrimitives().size() == 3);
        assertEquals(3, osmData.allNonDeletedPrimitives().size(), "The DataSet should be in its original state");
        assertEquals(3, mapWithAIData.allNonDeletedPrimitives().size(), "The DataSet should be in its original state");
    }

    @Test
    public void testMultipleUndoRedoWithMove() {
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().stream().forEach(node -> from.addPrimitive(node));
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        final Node way1FirstNode = way1.firstNode();

        UndoRedoHandler.getInstance().add(new MapWithAIAddCommand(from, to, Collections.singleton(way1)));

        final Node tNode = (Node) to.getPrimitiveById(way1FirstNode);

        UndoRedoHandler.getInstance().add(new MoveCommand(tNode, LatLon.ZERO));

        assertTrue(UndoRedoHandler.getInstance().getRedoCommands().isEmpty(), "There shouldn't be any redo commands");
        assertEquals(2, UndoRedoHandler.getInstance().getUndoCommands().size(), "There should be two undo commands");

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        assertTrue(UndoRedoHandler.getInstance().getUndoCommands().isEmpty(), "There should be no undo commands");
        assertEquals(2, UndoRedoHandler.getInstance().getRedoCommands().size(), "There should be two redo commands");
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());

        UndoRedoHandler.getInstance().undo(UndoRedoHandler.getInstance().getUndoCommands().size());
        UndoRedoHandler.getInstance().redo(UndoRedoHandler.getInstance().getRedoCommands().size());
    }

    /**
     * https://josm.openstreetmap.de/ticket/18351
     *
     * @throws InterruptedException
     * @throws InvocationTargetException
     */
    @Test
    public void testRegression18351() throws InvocationTargetException, InterruptedException {
        System.getProperty("java.awt.headless", "true");
        List<FutureTask<?>> futures = new SwingUtilitiesMocker().futureTasks;
        Way way = TestUtils.newWay("highway=residential mapwithai:source=MapWithAI source=digitalglobe",
                new Node(new LatLon(39.0339521, -108.4874581)), new Node(new LatLon(39.0292629, -108.4875117)));
        way.firstNode().put("dupe", "n176220609");
        way.getNode(way.getNodesCount() - 1).put("dupe", "n176232378");
        DataSet mapWithAIData = new DataSet();
        DataSet osmData = new DataSet();
        way.getNodes().forEach(mapWithAIData::addPrimitive);
        mapWithAIData.addPrimitive(way);
        mapWithAIData.addSelected(way);
        MapWithAIAddCommand command = new MapWithAIAddCommand(mapWithAIData, osmData, mapWithAIData.getSelected());
        command.executeCommand();
        Awaitility.await().atMost(Durations.FIVE_SECONDS)
                .until(() -> osmData.getPrimitiveById(176232378, OsmPrimitiveType.NODE) != null);
        Awaitility.await().atMost(Durations.FIVE_SECONDS)
                .until(() -> osmData.getPrimitiveById(176220609, OsmPrimitiveType.NODE) != null);
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> osmData.getPrimitiveById(way) != null);

        assertNotNull(osmData.getPrimitiveById(176232378, OsmPrimitiveType.NODE));
        assertNotNull(osmData.getPrimitiveById(176220609, OsmPrimitiveType.NODE));
        assertNotNull(osmData.getPrimitiveById(way));
        Awaitility.await().pollDelay(Durations.ONE_HUNDRED_MILLISECONDS);
        Assertions.assertDoesNotThrow(() -> {
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    Logging.error(e);
                    throw e;
                }
            }
        });
    }

    @Test
    public void testMultiConnectionsSimultaneous() {
        SharpAngles test = new SharpAngles();
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(3.4186753, 102.0559126)),
                new Node(new LatLon(3.4185682, 102.0555264)));
        Way way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(3.4186271, 102.0559502)),
                new Node(new LatLon(3.4188681, 102.0562935)));
        Way original = TestUtils.newWay("highway=tertiary", new Node(new LatLon(3.4185368, 102.0560268)),
                new Node(new LatLon(3.4187717, 102.0558451)));
        String connectedValue = "w" + Long.toString(original.getUniqueId()) + ",n"
                + Long.toString(original.firstNode().getUniqueId()) + ",n"
                + Long.toString(original.lastNode().getUniqueId());
        way1.firstNode().put(ConnectedCommand.KEY, connectedValue);
        way2.firstNode().put(ConnectedCommand.KEY, connectedValue);

        DataSet ds = new DataSet();
        DataSet osmData = new DataSet();
        OsmDataLayer layer = new OsmDataLayer(osmData, "Test Layer", null);
        for (Way way : Arrays.asList(way1, way2)) {
            way.getNodes().parallelStream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
            if (way.getDataSet() == null) {
                ds.addPrimitive(way);
            }
        }
        original.getNodes().forEach(osmData::addPrimitive);
        osmData.addPrimitive(original);

        MapWithAIAddCommand connectionsCommand = new MapWithAIAddCommand(ds, osmData, ds.allPrimitives());
        connectionsCommand.executeCommand();
        test.startTest(NullProgressMonitor.INSTANCE);
        test.visit(ds.allPrimitives());
        test.endTest();
        assertTrue(test.getErrors().isEmpty());

        SaveActionBase.doSave(layer, new File("post_command1.osm"), false);
        connectionsCommand.undoCommand();

        connectionsCommand = new MapWithAIAddCommand(ds, osmData, ds.allPrimitives());
        connectionsCommand.executeCommand();
        test.startTest(NullProgressMonitor.INSTANCE);
        test.visit(osmData.allPrimitives());
        test.endTest();
        assertTrue(test.getErrors().isEmpty());
        SaveActionBase.doSave(layer, new File("post_command2.osm"), false);
    }

}
