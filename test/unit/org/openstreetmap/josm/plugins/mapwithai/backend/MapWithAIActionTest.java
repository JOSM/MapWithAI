// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIActionTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Test
    public void testGetLayer() {
        Layer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        Assert.assertNull(mapWithAILayer);

        mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        Assert.assertEquals(MapWithAILayer.class, mapWithAILayer.getClass());

        Layer tMapWithAI = MapWithAIDataUtils.getLayer(false);
        Assert.assertSame(mapWithAILayer, tMapWithAI);

        tMapWithAI = MapWithAIDataUtils.getLayer(true);
        Assert.assertSame(mapWithAILayer, tMapWithAI);
    }

    @Test
    public void testGetData() {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        final OsmDataLayer osm = new OsmDataLayer(new DataSet(), "test", null);
        MainApplication.getLayerManager().addLayer(osm);
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer, osm);

        Assert.assertTrue(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(0, 0, 0.001, 0.001), "random test"));

        osm.lock();
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        Assert.assertTrue(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());
        osm.unlock();

        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        Assert.assertFalse(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());
        Assert.assertEquals(1, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        osm.getDataSet().addDataSource(new DataSource(new Bounds(-0.001, -0.001, 0, 0), "random test"));
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        Assert.assertEquals(2, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count());

        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        Assert.assertEquals(2, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count());
    }
}
