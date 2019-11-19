// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIActionTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().main().projection().timeout(100000);

    private MapWithAIAction action;

    @Before
    public void setUp() {
        action = new MapWithAIAction();
    }

    @Test
    public void testEnabled() {
        assertFalse(action.isEnabled());
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "temporary", null));
        assertTrue(action.isEnabled());
    }

    @Test
    public void testDownload() {
        assertTrue(MainApplication.getLayerManager().getLayers().isEmpty());
        action.actionPerformed(null);
        assertTrue(MainApplication.getLayerManager().getLayers().isEmpty());

        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "temporary", null));
        action.actionPerformed(null);
        Awaitility.await().timeout(8, TimeUnit.SECONDS)
        .until(() -> 1 == MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).size());
        assertEquals(1, MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).size());

        assertSame(MapWithAIDataUtils.getLayer(false), MainApplication.getLayerManager().getActiveLayer());
        action.actionPerformed(null);
        assertNotSame(MapWithAIDataUtils.getLayer(false), MainApplication.getLayerManager().getActiveLayer());
        MainApplication.getLayerManager().removeLayer(MainApplication.getLayerManager().getActiveLayer());
        action.actionPerformed(null);
        assertSame(MapWithAIDataUtils.getLayer(false), MainApplication.getLayerManager().getActiveLayer());
    }

    @Test
    public void testToggleLayer() {
        MapWithAIAction.toggleLayer(null);
        Layer layer = new OsmDataLayer(new DataSet(), "Test layer", null);
        assertNull(MainApplication.getLayerManager().getActiveLayer());
        MainApplication.getLayerManager().addLayer(layer);
        assertSame(layer, MainApplication.getLayerManager().getActiveLayer());
        MapWithAIAction.toggleLayer(layer);
        assertSame(layer, MainApplication.getLayerManager().getActiveLayer());
        MapWithAIDataUtils.getLayer(true);
        // Adding the MapWithAI layer switches to it
        assertSame(MapWithAIDataUtils.getLayer(false), MainApplication.getLayerManager().getActiveLayer());
        MapWithAIAction.toggleLayer(layer);
        assertSame(layer, MainApplication.getLayerManager().getActiveLayer());
        MapWithAIAction.toggleLayer(layer);
        assertSame(MapWithAIDataUtils.getLayer(false), MainApplication.getLayerManager().getActiveLayer());
        MapWithAIAction.toggleLayer(null);
        assertSame(MapWithAIDataUtils.getLayer(false), MainApplication.getLayerManager().getActiveLayer());
    }

    @Test
    public void testCreateNotification() {
        Notification notification = MapWithAIAction.createMessageDialog();
        assertNull(notification);

        MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
        notification = MapWithAIAction.createMessageDialog();
        assertNotNull(notification);
        DataSource source = new DataSource(new Bounds(38.8876078, -77.012173, 38.892087, -77.0059234), "test area");
        layer.getDataSet().addDataSource(source);
        notification = MapWithAIAction.createMessageDialog();
        assertNotNull(notification);
        assertEquals(Notification.TIME_DEFAULT, notification.getDuration());
    }
}
