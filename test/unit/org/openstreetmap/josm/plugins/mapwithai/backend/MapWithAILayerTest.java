// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAILayerTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public WireMockRule wireMockRule = new WireMockRule(options().usingFilesUnderDirectory("test/resources/wiremock"));

    MapWithAILayer layer;

    @Before
    public void setUp() {
        String URL = MapWithAIDataUtils.getMapWithAIUrl().replace("https://www.facebook.com/maps",
                wireMockRule.baseUrl());
        MapWithAIDataUtils.setMapWithAIUrl(URL, true);
        layer = new MapWithAILayer(new DataSet(), "test", null);
    }

    @Test
    public void testGetSource() {
        Assert.assertNotNull(layer.getChangesetSourceTag());
        Assert.assertFalse(layer.getChangesetSourceTag().trim().isEmpty());
    }

    @Test
    public void testGetInfoComponent() {
        final Object tObject = layer.getInfoComponent();
        Assert.assertTrue(tObject instanceof JPanel);

        JPanel jPanel = (JPanel) tObject;
        final List<Component> startComponents = Arrays.asList(jPanel.getComponents());
        for (final Component comp : startComponents) {
            final JLabel label = (JLabel) comp;
            Assert.assertFalse(label.getText().contains("URL"));
            Assert.assertFalse(label.getText().contains("Maximum Additions"));
            Assert.assertFalse(label.getText().contains("Switch Layers"));
        }

        layer.setMapWithAIUrl("bad_url");
        layer.setMaximumAddition(0);
        layer.setSwitchLayers(false);

        jPanel = (JPanel) layer.getInfoComponent();
        final List<Component> currentComponents = Arrays.asList(jPanel.getComponents());

        for (final Component comp : currentComponents) {
            final JLabel label = (JLabel) comp;
            if (label.getText().contains("URL")) {
                Assert.assertEquals(tr("URL: {0}", "bad_url"), label.getText());
            } else if (label.getText().contains("Maximum Additions")) {
                Assert.assertEquals(tr("Maximum Additions: {0}", 0), label.getText());
            } else if (label.getText().contains("Switch Layers")) {
                Assert.assertEquals(tr("Switch Layers: {0}", false), label.getText());
            }
        }
    }

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
