// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDActionTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Test
    public void testGetLayer() {
        Layer rapid = RapiDAction.getLayer(false);
        Assert.assertNull(rapid);

        rapid = RapiDAction.getLayer(true);
        Assert.assertEquals(RapiDLayer.class, rapid.getClass());

        Layer tRapid = RapiDAction.getLayer(false);
        Assert.assertSame(rapid, tRapid);

        tRapid = RapiDAction.getLayer(true);
        Assert.assertSame(rapid, tRapid);
    }

    @Test
    public void testGetData() {
        final RapiDLayer rapid = RapiDAction.getLayer(true);
        final OsmDataLayer osm = new OsmDataLayer(new DataSet(), "test", null);
        MainApplication.getLayerManager().addLayer(osm);
        RapiDAction.getRapiDData(rapid, osm);

        Assert.assertTrue(rapid.getDataSet().getDataSourceBounds().isEmpty());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(0, 0, 0.001, 0.001), "random test"));

        osm.lock();
        RapiDAction.getRapiDData(rapid);
        Assert.assertTrue(rapid.getDataSet().getDataSourceBounds().isEmpty());
        osm.unlock();

        RapiDAction.getRapiDData(rapid);
        Assert.assertFalse(rapid.getDataSet().getDataSourceBounds().isEmpty());
        Assert.assertEquals(1, rapid.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(-0.001, -0.001, 0, 0), "random test"));
        RapiDAction.getRapiDData(rapid);
        Assert.assertEquals(2, rapid.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        RapiDAction.getRapiDData(rapid);
        Assert.assertEquals(2, rapid.getDataSet().getDataSourceBounds().parallelStream().distinct().count());
    }
}
