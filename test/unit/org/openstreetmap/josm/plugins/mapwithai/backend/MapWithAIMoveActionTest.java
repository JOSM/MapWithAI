// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DuplicateCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIMoveActionTest {
    MapWithAIMoveAction moveAction;
    DataSet mapWithAIData;
    OsmDataLayer osmLayer;
    Way way1;
    Way way2;

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Before
    public void setUp() {
        moveAction = new MapWithAIMoveAction();
        final DataSet osmData = new DataSet();
        mapWithAIData = new DataSet();
        way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-0.1, -0.1)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(node -> mapWithAIData.addPrimitive(node));
        way2.getNodes().forEach(node -> osmData.addPrimitive(node));
        osmData.addPrimitive(way2);
        mapWithAIData.addPrimitive(way1);

        osmLayer = new OsmDataLayer(osmData, "osm", null);
        final MapWithAILayer mapWithAILayer = new MapWithAILayer(mapWithAIData, "MapWithAI", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        MainApplication.getLayerManager().addLayer(mapWithAILayer);
        MainApplication.getLayerManager().setActiveLayer(mapWithAILayer);
    }

    @Test
    public void testMoveAction() {
        mapWithAIData.addSelected(way1);
        moveAction.actionPerformed(null);
        assertEquals(osmLayer, MainApplication.getLayerManager().getActiveLayer(),
                "Current layer should be the OMS layer");
        assertNotNull(osmLayer.getDataSet().getPrimitiveById(way1), "way1 should have been added to the OSM layer");
        UndoRedoHandler.getInstance().undo();
        assertNull(osmLayer.getDataSet().getPrimitiveById(way1), "way1 should have been removed from the OSM layer");
    }

    @Test
    public void testConflationDupeKeyRemoval() {
        mapWithAIData.unlock();
        way1.lastNode().put(DuplicateCommand.KEY, "n" + Long.toString(way2.lastNode().getUniqueId()));
        mapWithAIData.lock();
        mapWithAIData.addSelected(way1);
        final DataSet ds = osmLayer.getDataSet();

        moveAction.actionPerformed(null);
        assertTrue(((Way) ds.getPrimitiveById(way1)).lastNode().equals(((Way) ds.getPrimitiveById(way2)).lastNode()),
                "The duplicate node should have been replaced");
        assertFalse(((Way) ds.getPrimitiveById(way2)).lastNode().hasKey(DuplicateCommand.KEY),
                "The dupe key should no longer exist");
        assertFalse(((Way) ds.getPrimitiveById(way1)).lastNode().hasKey(DuplicateCommand.KEY),
                "The dupe key should no longer exist");

        UndoRedoHandler.getInstance().undo();
        assertFalse(way2.lastNode().hasKey(DuplicateCommand.KEY), "The dupe key should no longer exist");
        assertTrue(way1.lastNode().hasKey(DuplicateCommand.KEY), "The dupe key should no longer exist");
    }

    @Test
    public void testConflationConnKeyRemoval() {
        mapWithAIData.unlock();
        way1.lastNode().put(ConnectedCommand.KEY, "w" + Long.toString(way2.getUniqueId()) + ",n"
                + Long.toString(way2.lastNode().getUniqueId()) + ",n" + Long.toString(way2.firstNode().getUniqueId()));
        mapWithAIData.lock();
        mapWithAIData.addSelected(way1);

        moveAction.actionPerformed(null);
        assertFalse(way2.lastNode().hasKey(ConnectedCommand.KEY), "The conn key should have been removed");
        assertFalse(way2.firstNode().hasKey(ConnectedCommand.KEY), "The conn key should have been removed");
        assertFalse(way2.getNode(1).hasKey(ConnectedCommand.KEY), "The conn key should have been removed");
        assertTrue(way1.isDeleted(), "way1 should be deleted when added");

        UndoRedoHandler.getInstance().undo();
        assertFalse(way2.lastNode().hasKey(ConnectedCommand.KEY), "The conn key shouldn't exist");
        assertTrue(way1.lastNode().hasKey(ConnectedCommand.KEY), "The conn key should exist");
        assertFalse(way1.lastNode().isDeleted(), "way1 should no longer be deleted");
    }
}
