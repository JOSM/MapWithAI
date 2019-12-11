// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIAddComandTest {
    private final static String HIGHWAY_RESIDENTIAL = "highway=residential";

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        Config.getPref().put("osm-server.url", wireMock.baseUrl());
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

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
        Assert.assertNotNull(ds2.getPrimitiveById(way1));
        Assert.assertNotNull(ds2.getPrimitiveById(way1.firstNode()));
        Assert.assertNotNull(ds2.getPrimitiveById(way1.lastNode()));
        Assert.assertTrue(way1.isDeleted());
        Assert.assertNotNull(ds2.getPrimitiveById(way2));
        Assert.assertNotNull(ds2.getPrimitiveById(way2.firstNode()));
        Assert.assertNotNull(ds2.getPrimitiveById(way2.lastNode()));
        Assert.assertTrue(way2.isDeleted());

        Assert.assertNull(ds2.getPrimitiveById(way3));
        command = new MapWithAIAddCommand(ds1, ds2, Arrays.asList(way3));
        command.executeCommand();
        Assert.assertNotNull(ds2.getPrimitiveById(way3));
        Assert.assertNotNull(ds2.getPrimitiveById(way3.firstNode()));
        Assert.assertNotNull(ds2.getPrimitiveById(way3.lastNode()));
        Assert.assertTrue(way3.isDeleted());

        command.undoCommand();
        Assert.assertNull(ds2.getPrimitiveById(way3));
        Assert.assertNull(ds2.getPrimitiveById(way3.firstNode()));
        Assert.assertNull(ds2.getPrimitiveById(way3.lastNode()));
        Assert.assertFalse(ds1.getPrimitiveById(way3).isDeleted());
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
        MapWithAIAddCommand.createConnections(ds1, Collections.singletonList(way2.firstNode())).executeCommand();
        Assert.assertEquals(3, way1.getNodesCount());
        Assert.assertFalse(way1.isFirstLastNode(way2.firstNode()));

        final Way way3 = TestUtils.newWay(HIGHWAY_RESIDENTIAL, new Node(new LatLon(0, 0)),
                new Node(new LatLon(-0.1, -0.1)));
        way3.firstNode().put("dupe", "n".concat(Long.toString(way1.firstNode().getUniqueId())));
        way3.getNodes().forEach(node -> ds1.addPrimitive(node));
        ds1.addPrimitive(way3);
        final Node way3Node1 = way3.firstNode();
        MapWithAIAddCommand.createConnections(ds1, Collections.singletonList(way3.firstNode())).executeCommand();
        Assert.assertNotEquals(way3Node1, way3.firstNode());
        Assert.assertEquals(way1.firstNode(), way3.firstNode());
        Assert.assertTrue(way3Node1.isDeleted());
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

        Assert.assertEquals(3, osmData.allNonDeletedPrimitives().size());
        Assert.assertEquals(3, mapWithAIData.allNonDeletedPrimitives().size());

        MapWithAIAddCommand command = new MapWithAIAddCommand(mapWithAIData, osmData, mapWithAIData.getSelected());
        command.executeCommand();
        Assert.assertEquals(6, osmData.allNonDeletedPrimitives().size());
        Assert.assertTrue(mapWithAIData.allNonDeletedPrimitives().isEmpty());

        command.undoCommand();
        Assert.assertEquals(3, osmData.allNonDeletedPrimitives().size());
        Assert.assertEquals(3, mapWithAIData.allNonDeletedPrimitives().size());

        final Tag dupe = new Tag("dupe", "n".concat(Long.toString(way2.lastNode().getUniqueId())));
        way1.lastNode().put(dupe);
        command = new MapWithAIAddCommand(mapWithAIData, osmData, mapWithAIData.getSelected());
        command.executeCommand();
        Assert.assertEquals(5, osmData.allNonDeletedPrimitives().size());
        Assert.assertTrue(mapWithAIData.allNonDeletedPrimitives().isEmpty());

        command.undoCommand();
        Assert.assertEquals(3, osmData.allNonDeletedPrimitives().size());
        Assert.assertEquals(3, mapWithAIData.allNonDeletedPrimitives().size());
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

        UndoRedoHandler.getInstance().add(new MapWithAIAddCommand(from, to, Collections.singleton(way1)));

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

    /**
     * https://josm.openstreetmap.de/ticket/18351
     */
    @Test
    public void testRegression18351() {
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
        assertNotNull(osmData.getPrimitiveById(176232378, OsmPrimitiveType.NODE));
        assertNotNull(osmData.getPrimitiveById(176220609, OsmPrimitiveType.NODE));
        assertNotNull(osmData.getPrimitiveById(way));
    }
}
