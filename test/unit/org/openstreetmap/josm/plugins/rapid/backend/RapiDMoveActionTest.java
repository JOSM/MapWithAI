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
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDMoveActionTest {
    RapiDMoveAction moveAction;

    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Before
    public void setup() {
        moveAction = new RapiDMoveAction();
    }

    @Test
    public void testMoveAction() {
        DataSet osmData = new DataSet();
        DataSet rapidData = new DataSet();
        Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        Way way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-0.1, -0.1)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(node -> rapidData.addPrimitive(node));
        way2.getNodes().forEach(node -> osmData.addPrimitive(node));
        osmData.addPrimitive(way2);
        rapidData.addPrimitive(way1);

        OsmDataLayer osmLayer = new OsmDataLayer(osmData, "osm", null);
        RapiDLayer rapidLayer = new RapiDLayer(RapiDDataUtils.getData(RapiDDataUtilsTest.getTestBBox()), "rapid", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        MainApplication.getLayerManager().addLayer(rapidLayer);
        MainApplication.getLayerManager().setActiveLayer(rapidLayer);

        rapidData.addSelected(way1);

        moveAction.actionPerformed(null);

        Assert.assertEquals(osmLayer, MainApplication.getLayerManager().getActiveLayer());

        UndoRedoHandler.getInstance().undo();
    }
}
