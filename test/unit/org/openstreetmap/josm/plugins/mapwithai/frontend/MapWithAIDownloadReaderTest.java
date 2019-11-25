// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.frontend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.stream.Collectors;

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
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;

public class MapWithAIDownloadReaderTest {
    @Rule
    public JOSMTestRules rules = new JOSMTestRules().projection();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAIPreferenceHelper.setMapWithAIURLs(MapWithAIPreferenceHelper.getMapWithAIURLs().stream().map(map -> {
            map.put("url", getDefaultMapWithAIAPIForTest(
                    map.getOrDefault("url", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)));
            return map;
        }).collect(Collectors.toList()));
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

    private String getDefaultMapWithAIAPIForTest(String url) {
        return getDefaultMapWithAIAPIForTest(url, "https://www.facebook.com");
    }

    private String getDefaultMapWithAIAPIForTest(String url, String wireMockReplace) {
        return url.replace(wireMockReplace, wireMock.baseUrl());
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
        DownloadSettings settings = new DownloadSettings(
                new Bounds(39.0239848, -108.4522247, 39.1066201, -108.3368683), false, false);
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
