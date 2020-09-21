// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.awaitility.Durations;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer.ContinuousDownloadAction;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Territories;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAILayerTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new MapWithAITestRules().sources().wiremock().preferences().main().projection()
            .fakeAPI().territories();

    MapWithAILayer layer;

    @Before
    public void setUp() {
        layer = new MapWithAILayer(new DataSet(), "test", null);
        Territories.initialize(); // Required to avoid an NPE (see JOSM-19132)
    }

    @Test
    public void testGetSource() {
        assertNull(layer.getChangesetSourceTag(), "The source tag should be null");
        DataSet to = new DataSet();
        DataSet from = new DataSet();
        Way way = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        way.getNodes().stream().forEach(from::addPrimitive);
        from.addPrimitive(way);
        way.put(GetDataRunnable.MAPWITHAI_SOURCE_TAG_KEY, MapWithAIPlugin.NAME);
        MapWithAIAddCommand command = new MapWithAIAddCommand(from, to, Collections.singleton(way));
        UndoRedoHandler.getInstance().add(command);
        assertNotNull(layer.getChangesetSourceTag(), "The source tag should not be null");
        assertFalse(layer.getChangesetSourceTag().trim().isEmpty(), "The source tag should not be an empty string");
        assertEquals(MapWithAIPlugin.NAME, layer.getChangesetSourceTag(),
                "The source tag should be the plugin name (by default)");
    }

    @Test
    public void testGetInfoComponent() {
        final Object tObject = layer.getInfoComponent();
        assertTrue(tObject instanceof JPanel, "The info component should be a JPanel instead of a string");

        JPanel jPanel = (JPanel) tObject;
        final List<Component> startComponents = Arrays.asList(jPanel.getComponents());
        for (final Component comp : startComponents) {
            final JLabel label = (JLabel) comp;
            assertFalse(label.getText().contains("URL"), "The layer doesn't have a custom URL");
            assertFalse(label.getText().contains("Maximum Additions"), "The layer doesn't have its own max additions");
            assertFalse(label.getText().contains("Switch Layers"),
                    "The layer doesn't have its own switchlayer boolean");
        }

        layer.setMapWithAIUrl(new MapWithAIInfo("bad_url", "bad_url"));
        layer.setMaximumAddition(0);
        layer.setSwitchLayers(false);

        jPanel = (JPanel) layer.getInfoComponent();
        final List<Component> currentComponents = Arrays.asList(jPanel.getComponents());

        for (final Component comp : currentComponents) {
            final JLabel label = (JLabel) comp;
            if (label.getText().contains("URL")) {
                assertEquals(tr("URL: {0}", "bad_url"), label.getText(), "The layer should have the bad_url set");
            } else if (label.getText().contains("Maximum Additions")) {
                assertEquals(tr("Maximum Additions: {0}", 0), label.getText(),
                        "The layer should have max additions set");
            } else if (label.getText().contains("Switch Layers")) {
                assertEquals(tr("Switch Layers: {0}", false), label.getText(),
                        "The layer should have switchlayers set");
            }
        }
    }

    @Test
    public void testGetLayer() {
        Layer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        assertNull(mapWithAILayer, "There should be no MapWithAI layer yet");

        mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        await().atMost(Durations.ONE_SECOND).until(() -> MapWithAIDataUtils.getLayer(false) != null);
        assertEquals(MapWithAILayer.class, mapWithAILayer.getClass(),
                "The MapWithAI layer should be of the MapWithAILayer.class");

        for (Boolean create : Arrays.asList(Boolean.FALSE, Boolean.TRUE)) {
            Layer tMapWithAI = MapWithAIDataUtils.getLayer(create);
            assertSame(mapWithAILayer, tMapWithAI, "getLayer should always return the same layer");
        }
    }

    @Test
    public void testSelection() throws InvocationTargetException, InterruptedException {
        MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        DataSet ds = mapWithAILayer.getDataSet();
        GetDataRunnable getData = new GetDataRunnable(
                Arrays.asList(new Bounds(34.4524384, -5.7400005, 34.5513153, -5.6686014)), ds, null);
        getData.setMaximumDimensions(5_000);
        getData.fork().join();
        assertTrue(ds.getSelected().isEmpty());
        SwingUtilities.invokeAndWait(() -> ds.setSelected(ds.allNonDeletedCompletePrimitives()));
        assertEquals(1, ds.getSelected().size());
        OsmPrimitive prim = ds.getSelected().iterator().next();
        assertTrue(prim instanceof Way);
        SwingUtilities.invokeAndWait(() -> ds.setSelected(((Way) prim).getNodes()));
        assertEquals(((Way) prim).getNodes().size(), ds.getSelected().size());
        assertTrue(((Way) prim).getNodes().parallelStream().allMatch(ds::isSelected));
    }

    @Test
    public void testGetData() {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(true);
        final OsmDataLayer osm = new OsmDataLayer(new DataSet(), "test", null);
        MainApplication.getLayerManager().addLayer(osm);
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer, osm);

        assertTrue(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty(), "There should be no data source yet");

        osm.getDataSet().addDataSource(new DataSource(new Bounds(0, 0, 0.001, 0.001), "random test"));

        osm.lock();
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        assertTrue(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty(),
                "There should be no data due to the lock");
        osm.unlock();

        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        await().atMost(Durations.TEN_SECONDS).until(() -> !mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty());
        assertFalse(mapWithAILayer.getDataSet().getDataSourceBounds().isEmpty(), "There should be a data source");
        assertEquals(1, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count(),
                "There should only be one data source");

        osm.getDataSet().addDataSource(new DataSource(new Bounds(-0.001, -0.001, 0, 0), "random test"));
        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        await().atMost(Durations.TEN_SECONDS).until(
                () -> mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count() == 2);
        assertEquals(2, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count(),
                "There should be two data sources");

        MapWithAIDataUtils.getMapWithAIData(mapWithAILayer);
        assertEquals(2, mapWithAILayer.getDataSet().getDataSourceBounds().parallelStream().distinct().count(),
                "There should be two data sources");
    }

    @Test
    public void testGetMenuEntries() {
        Layer layer = MapWithAIDataUtils.getLayer(true);
        await().atMost(Durations.ONE_SECOND).until(() -> MapWithAIDataUtils.getLayer(false) != null);
        Action[] actions = layer.getMenuEntries();
        assertTrue(actions.length > 0);
        assertEquals(ContinuousDownloadAction.class, layer.getMenuEntries()[actions.length - 3].getClass());
    }

    @Test
    public void testLayerSwitch() {
        MapPaintUtils.addMapWithAIPaintStyles();
        Layer osm = new OsmDataLayer(new DataSet(), "TEST", null);
        MainApplication.getLayerManager().addLayer(osm);
        MainApplication.getLayerManager().addLayer(layer);
        MainApplication.getLayerManager().setActiveLayer(layer);
        StyleSource pref = MapPaintUtils.getMapWithAIPaintStyle();
        layer.activeOrEditLayerChanged(null);
        assertTrue(pref.active);
        MainApplication.getLayerManager().setActiveLayer(osm);
        layer.activeOrEditLayerChanged(null);
        assertTrue(pref.active);
        Config.getPref().putBoolean(MapWithAIPlugin.NAME + ".boolean:toggle_with_layer", true);

        layer.activeOrEditLayerChanged(null);
        assertFalse(pref.active);
    }
}
