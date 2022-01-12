// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.actions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

/**
 * Test class for {@link AddMapWithAILayerAction}
 *
 * @author Taylor Smock
 */
@NoExceptions
@BasicPreferences
@MapWithAISources
class AddMapWithAILayerActionTest {
    @RegisterExtension
    JOSMTestRules rule = new MapWithAITestRules().projection();

    @Test
    void testAddMapWithAILayerActionTest() {
        MapWithAIInfo info = MapWithAILayerInfo.getInstance().getLayers().stream()
                .filter(i -> i.getName().equalsIgnoreCase("MapWithAI")).findAny().orElse(null);
        AddMapWithAILayerAction action = new AddMapWithAILayerAction(info);
        assertDoesNotThrow(() -> action.actionPerformed(null));
        OsmDataLayer osmLayer = new OsmDataLayer(new DataSet(), "TEST DATA", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        osmLayer.getDataSet()
                .addDataSource(new DataSource(new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314), "TEST"));

        assertNull(MapWithAIDataUtils.getLayer(false));
        action.updateEnabledState();
        action.actionPerformed(null);
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> MapWithAIDataUtils.getLayer(false) != null);
        assertNotNull(MapWithAIDataUtils.getLayer(false));

        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));

        MapWithAILayerInfo.getInstance().remove(info);

        MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
        assertTrue(layer.getDataSet().isEmpty());

        action.updateEnabledState();
        action.actionPerformed(null);
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> !layer.getDataSet().isEmpty());
        assertFalse(layer.getDataSet().isEmpty());

        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        MapWithAILayer mapwithaiLayer = new MapWithAILayer(new DataSet(), "TEST", null);
        MainApplication.getLayerManager().addLayer(mapwithaiLayer);

        mapwithaiLayer.getDataSet()
                .addDataSource(new DataSource(new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314), ""));
        action.actionPerformed(null);
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> !mapwithaiLayer.getDataSet().isEmpty());
        assertFalse(mapwithaiLayer.getDataSet().isEmpty());

    }

}
