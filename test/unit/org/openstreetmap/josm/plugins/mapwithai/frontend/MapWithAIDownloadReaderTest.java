// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.frontend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.download.DownloadSettings;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAILayerInfoTest;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;

public class MapWithAIDownloadReaderTest {
    @Rule
    public JOSMTestRules rules = new JOSMTestRules().projection().territories();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAILayerInfoTest.setupMapWithAILayerInfo(wireMock);
    }

    @After
    public void tearDown() {
        wireMock.stop();
        MapWithAILayerInfoTest.resetMapWithAILayerInfo();
    }

    @Test
    public void testGetLabel() {
        assertEquals(tr("Download from {0} API", MapWithAIPlugin.NAME), new MapWithAIDownloadReader().getLabel());
    }

    @Test
    public void testIsExpert() {
        assertFalse(new MapWithAIDownloadReader().onlyExpert());
    }

    @Test
    public void testDoDownload() {
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        // TODO revert commit that adds these lines as soon as MapWithAI fixes timeout
        // issue see
        // https://mapwith.ai/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox=-108.4522247,39.0239848,-108.3368683,39.1066201&result_type=road_building_vector_xml
        DownloadSettings settings = new DownloadSettings(new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314),
                false, false);
        MapWithAIDownloadReader.MapWithAIDownloadData data = new MapWithAIDownloadReader.MapWithAIDownloadData(
                MapWithAIPreferenceHelper.getMapWithAIUrl(), errors -> {
                });
        reader.doDownload(data, settings);
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> MapWithAIDataUtils.getLayer(false) != null);
        assertNotNull(MapWithAIDataUtils.getLayer(false));
        Awaitility.await().atMost(Durations.TEN_SECONDS)
                .until(() -> !MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
        assertFalse(MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
        assertTrue(settings.getDownloadBounds().get().toBBox().bboxIsFunctionallyEqual(
                MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().get(0).toBBox(), 0.0001));
    }
}
