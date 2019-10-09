// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import org.junit.Assert;
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
import org.openstreetmap.josm.plugins.rapid.commands.CreateConnectionsCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class RapiDMoveActionTest {
    RapiDMoveAction moveAction;
    DataSet rapidData;
    OsmDataLayer osmLayer;
    Way way1;
    Way way2;

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Before
    public void setUp() {
        moveAction = new RapiDMoveAction();
        final DataSet osmData = new DataSet();
        rapidData = new DataSet();
        way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-0.1, -0.1)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(node -> rapidData.addPrimitive(node));
        way2.getNodes().forEach(node -> osmData.addPrimitive(node));
        osmData.addPrimitive(way2);
        rapidData.addPrimitive(way1);

        osmLayer = new OsmDataLayer(osmData, "osm", null);
        final RapiDLayer rapidLayer = new RapiDLayer(rapidData, "rapid",
                null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        MainApplication.getLayerManager().addLayer(rapidLayer);
        MainApplication.getLayerManager().setActiveLayer(rapidLayer);
    }

    @Test
    public void testMoveAction() {
        rapidData.addSelected(way1);
        moveAction.actionPerformed(null);
        Assert.assertEquals(osmLayer, MainApplication.getLayerManager().getActiveLayer());
        Assert.assertNotNull(osmLayer.getDataSet().getPrimitiveById(way1));
        UndoRedoHandler.getInstance().undo();
        Assert.assertNull(osmLayer.getDataSet().getPrimitiveById(way1));
    }

    @Test
    public void testConflationDupeKeyRemoval() {
        rapidData.unlock();
        way1.lastNode().put(CreateConnectionsCommand.DUPE_KEY, "n" + Long.toString(way2.lastNode().getUniqueId()));
        rapidData.lock();
        rapidData.addSelected(way1);
        final DataSet ds = osmLayer.getDataSet();

        moveAction.actionPerformed(null);
        Assert.assertFalse(((Way) ds.getPrimitiveById(way2)).lastNode().hasKey(CreateConnectionsCommand.DUPE_KEY));
        Assert.assertFalse(((Way) ds.getPrimitiveById(way1)).lastNode().hasKey(CreateConnectionsCommand.DUPE_KEY));

        UndoRedoHandler.getInstance().undo();
        Assert.assertFalse(way2.lastNode().hasKey(CreateConnectionsCommand.DUPE_KEY));
        Assert.assertTrue(way1.lastNode().hasKey(CreateConnectionsCommand.DUPE_KEY));
    }

    @Test
    public void testConflationConnKeyRemoval() {
        rapidData.unlock();
        way1.lastNode().put(CreateConnectionsCommand.CONN_KEY, "w" + Long.toString(way2.getUniqueId()) + ",n"
                + Long.toString(way2.lastNode().getUniqueId()) + ",n" + Long.toString(way2.firstNode().getUniqueId()));
        rapidData.lock();
        rapidData.addSelected(way1);

        moveAction.actionPerformed(null);
        Assert.assertFalse(way2.lastNode().hasKey(CreateConnectionsCommand.CONN_KEY));
        Assert.assertFalse(way2.firstNode().hasKey(CreateConnectionsCommand.CONN_KEY));
        Assert.assertFalse(way2.getNode(1).hasKey(CreateConnectionsCommand.CONN_KEY));
        Assert.assertTrue(way1.lastNode().isDeleted());

        UndoRedoHandler.getInstance().undo();
        Assert.assertFalse(way2.lastNode().hasKey(CreateConnectionsCommand.CONN_KEY));
        Assert.assertTrue(way1.lastNode().hasKey(CreateConnectionsCommand.CONN_KEY));
        Assert.assertFalse(way1.lastNode().isDeleted());
    }
}
