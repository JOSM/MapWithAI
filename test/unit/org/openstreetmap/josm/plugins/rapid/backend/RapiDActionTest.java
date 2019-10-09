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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class RapiDActionTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Test
    public void testGetLayer() {
        Layer rapid = RapiDDataUtils.getLayer(false);
        Assert.assertNull(rapid);

        rapid = RapiDDataUtils.getLayer(true);
        Assert.assertEquals(RapiDLayer.class, rapid.getClass());

        Layer tRapid = RapiDDataUtils.getLayer(false);
        Assert.assertSame(rapid, tRapid);

        tRapid = RapiDDataUtils.getLayer(true);
        Assert.assertSame(rapid, tRapid);
    }

    @Test
    public void testGetData() {
        final RapiDLayer rapid = RapiDDataUtils.getLayer(true);
        final OsmDataLayer osm = new OsmDataLayer(new DataSet(), "test", null);
        MainApplication.getLayerManager().addLayer(osm);
        RapiDDataUtils.getRapiDData(rapid, osm);

        Assert.assertTrue(rapid.getDataSet().getDataSourceBounds().isEmpty());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(0, 0, 0.001, 0.001), "random test"));

        osm.lock();
        RapiDDataUtils.getRapiDData(rapid);
        Assert.assertTrue(rapid.getDataSet().getDataSourceBounds().isEmpty());
        osm.unlock();

        RapiDDataUtils.getRapiDData(rapid);
        Assert.assertFalse(rapid.getDataSet().getDataSourceBounds().isEmpty());
        Assert.assertEquals(1, rapid.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(-0.001, -0.001, 0, 0), "random test"));
        RapiDDataUtils.getRapiDData(rapid);
        Assert.assertEquals(2, rapid.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        RapiDDataUtils.getRapiDData(rapid);
        Assert.assertEquals(2, rapid.getDataSet().getDataSourceBounds().parallelStream().distinct().count());
    }
}
