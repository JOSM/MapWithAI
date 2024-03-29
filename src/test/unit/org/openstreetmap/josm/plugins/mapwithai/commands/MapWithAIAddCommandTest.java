// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MissingConnectionTagsMocker;
import org.openstreetmap.josm.plugins.mapwithai.testutils.PleaseWaitDialogMocker;
import org.openstreetmap.josm.plugins.mapwithai.testutils.SwingUtilitiesMocker;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.BleedTest;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Command;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.AssertionsInEDT;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;
import org.openstreetmap.josm.tools.Logging;

import mockit.Mock;

/**
 * Test class for {@link MapWithAIAddCommand}
 *
 * @author Taylor Smock
 */
@AssertionsInEDT
@BasicPreferences
@Command
@Main
@MapWithAISources
@Projection
@Wiremock
class MapWithAIAddCommandTest {
    private final static String HIGHWAY_RESIDENTIAL = "highway=residential";

    @BeforeEach
    void setUp() {
        // Required to avoid an NPE with AutoZoomHandler
        new WindowMocker() {
            @Mock
            public void pack() {
                // Do nothing
            }
        };
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "Temp", null));
    }

    @Test
    void testMoveCollection() {
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
        command = new MapWithAIAddCommand(ds1, ds2, Collections.singletonList(way3));
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
    void testCreateConnections() {
        new PleaseWaitDialogMocker();
        final DataSet ds1 = new DataSet();
        final Node way1FirstNode = new Node(new LatLon(0, 0));
        final Node way1LastNode = new Node(new LatLon(0, 0.15));
        final Way way1 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, way1FirstNode, way1LastNode);
        final Node way2FirstNode = new Node(new LatLon(0, 0.05));
        final Way way2 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, way2FirstNode, new Node(new LatLon(0.05, 0.2)));
        // SimplePrimitiveId doesn't understand negative ids
        way1.setOsmId(1, 1);
        way1FirstNode.setOsmId(1, 1);
        way1LastNode.setOsmId(2, 1);

        way2FirstNode.put("conn",
                "w".concat(Long.toString(way1.getUniqueId())).concat(",n")
                        .concat(Long.toString(way1FirstNode.getUniqueId())).concat(",n")
                        .concat(Long.toString(way1LastNode.getUniqueId())));
        way1.getNodes().forEach(ds1::addPrimitive);
        way2.getNodes().forEach(ds1::addPrimitive);
        ds1.addPrimitive(way2);
        ds1.addPrimitive(way1);
        MapWithAIAddCommand.createConnections(ds1, Collections.singletonList(way2FirstNode.save())).executeCommand();
        assertEquals(3, way1.getNodesCount(), "The way should now have three nodes");
        assertFalse(way1.isFirstLastNode(way2.firstNode()), "The ways should be connected");

        final Node way3FirstNode = new Node(new LatLon(0, 0));
        final Way way3 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, way3FirstNode, new Node(new LatLon(-0.1, -0.1)));
        way3FirstNode.put("dupe", "n".concat(Long.toString(way1FirstNode.getUniqueId())));
        way3.getNodes().forEach(ds1::addPrimitive);
        ds1.addPrimitive(way3);
        MapWithAIAddCommand.createConnections(ds1, Collections.singletonList(way3FirstNode.save())).executeCommand();
        assertNotEquals(way3FirstNode, way3.firstNode(), "The original first node should no longer be the first node");
        assertEquals(way1.firstNode(), way3.firstNode(), "way1 and way3 should have the same first nodes");
        assertTrue(way3FirstNode.isDeleted(), "way3 should be deleted");
    }

    @BleedTest
    void testCreateConnectionsUndo() {
        new MissingConnectionTagsMocker();

        final DataSet osmData = new DataSet();
        final DataSet mapWithAIData = new DataSet();
        final Node way1LastNode = new Node(new LatLon(0.1, 0.1));
        final Way way1 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0)), way1LastNode);
        final Node way2LastNode = new Node(new LatLon(0.1, 0.1));
        final Way way2 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(-0.1, -0.1)), way2LastNode);
        way1.getNodes().forEach(mapWithAIData::addPrimitive);
        way2.getNodes().forEach(osmData::addPrimitive);
        osmData.addPrimitive(way2);
        mapWithAIData.addPrimitive(way1);
        mapWithAIData.setSelected(way1);
        way2LastNode.setOsmId(1, 1);
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

        final Tag dupe = new Tag("dupe", "n".concat(Long.toString(way2LastNode.getUniqueId())));
        way1LastNode.put(dupe);
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
    void testMultipleUndoRedoWithMove() {
        final DataSet to = new DataSet();
        final DataSet from = new DataSet();
        final Way way1 = TestUtils.newWay("highway=tertiary", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(from::addPrimitive);
        from.addPrimitive(way1);
        from.addPrimitive(new Node(new LatLon(-0.1, 0.1)));

        final Node way1FirstNode = way1.firstNode();

        UndoRedoHandler.getInstance().add(new MapWithAIAddCommand(from, to, Collections.singleton(way1)));

        final Node tNode = (Node) to.getPrimitiveById(way1FirstNode);
        assertNotNull(tNode);

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
     * JOSM <a href="https://josm.openstreetmap.de/ticket/18351">#18351</a>
     *
     */
    @Test
    void testRegression18351() {
        System.getProperty("java.awt.headless", "true");
        TestUtils.assumeWorkingJMockit();
        new WindowMocker();
        new PleaseWaitDialogMocker();
        SwingUtilitiesMocker swingMocker = new SwingUtilitiesMocker();

        swingMocker.futureTasks.clear();
        final Node wayFirstNode = new Node(new LatLon(39.0339521, -108.4874581));
        Way way = TestUtils.newWay("highway=residential mapwithai:source=MapWithAI source=digitalglobe", wayFirstNode,
                new Node(new LatLon(39.0292629, -108.4875117)));
        wayFirstNode.put("dupe", "n176220609");
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
        Awaitility.await().pollDelay(Durations.ONE_HUNDRED_MILLISECONDS).until(() -> true);
        List<Future<?>> futures = new ArrayList<>(swingMocker.futureTasks);
        Assertions.assertDoesNotThrow(() -> {
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    Logging.error(e);
                    Logging.logWithStackTrace(Logging.LEVEL_ERROR, e);
                    throw e;
                }
            }
        });
    }

    @Test
    void testMultiConnectionsSimultaneous() {
        SharpAngles test = new SharpAngles();
        final Node way1FirstNode = new Node(new LatLon(3.4186753, 102.0559126));
        final Node way1LastNode = new Node(new LatLon(3.4185682, 102.0555264));
        Way way1 = TestUtils.newWay("highway=residential", way1FirstNode, way1LastNode);
        final Node way2FirstNode = new Node(new LatLon(3.4186271, 102.0559502));
        Way way2 = TestUtils.newWay("highway=residential", way2FirstNode, new Node(new LatLon(3.4188681, 102.0562935)));
        final Node originalFirstNode = new Node(new LatLon(3.4185368, 102.0560268));
        final Node originalLastNode = new Node(new LatLon(3.4187717, 102.0558451));
        Way original = TestUtils.newWay("highway=tertiary", originalFirstNode, originalLastNode);
        original.setOsmId(1, 1);
        originalFirstNode.setOsmId(1, 1);
        originalLastNode.setOsmId(2, 1);
        String connectedValue = "w" + original.getUniqueId() + ",n" + originalFirstNode.getUniqueId() + ",n"
                + originalLastNode.getUniqueId();
        way1FirstNode.put(ConnectedCommand.KEY, connectedValue);
        way2FirstNode.put(ConnectedCommand.KEY, connectedValue);

        DataSet ds = new DataSet();
        DataSet osmData = new DataSet();
        OsmDataLayer layer = new OsmDataLayer(osmData, "Test Layer", null);
        for (Way way : Arrays.asList(way1, way2)) {
            way.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
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
