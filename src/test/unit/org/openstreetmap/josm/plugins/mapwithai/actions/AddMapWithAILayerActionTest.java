// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.actions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.ImageIcon;

import org.junit.jupiter.api.BeforeEach;
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
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.ThreadSync;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import jakarta.json.Json;

/**
 * Test class for {@link AddMapWithAILayerAction}
 *
 * @author Taylor Smock
 */
@NoExceptions
@BasicPreferences
@MapWithAISources
@Projection
class AddMapWithAILayerActionTest {

    private static class ThreadSyncMWAI extends ThreadSync.ThreadSyncExtension {
        public ThreadSyncMWAI() {
            this.registerForkJoinPool(MapWithAIDataUtils.getForkJoinPool());
        }
    }

    @RegisterExtension
    static ThreadSyncMWAI threadSync = new ThreadSyncMWAI();

    @BasicWiremock
    WireMockServer wireMockServer;

    private static MapWithAIInfo info;
    private static MapWithAIInfo backupInfo;

    @BeforeEach
    void setup() {
        final Map<String, StringValuePattern> parameterMap = new HashMap<>();
        final AnythingPattern anythingPattern = new AnythingPattern();
        parameterMap.put("geometryType", anythingPattern);
        parameterMap.put("geometry", anythingPattern);
        parameterMap.put("inSR", new EqualToPattern("4326"));
        parameterMap.put("f", new EqualToPattern("geojson"));
        parameterMap.put("outfields", new EqualToPattern("*"));
        parameterMap.put("result_type", new EqualToPattern("road_building_vector_xml"));
        parameterMap.put("resultOffset", anythingPattern);
        wireMockServer.stubFor(
                WireMock.get(new UrlPathPattern(new EqualToPattern("/query"), false)).withQueryParams(parameterMap)
                        .willReturn(WireMock.aResponse()
                                .withBody(Json.createObjectBuilder().add("type", "FeatureCollection")
                                        .add("features", Json.createArrayBuilder().build()).build().toString()))
                        .atPriority(Integer.MIN_VALUE));
        info = MapWithAILayerInfo.getInstance().getLayers().stream()
                .filter(i -> i.getName().equalsIgnoreCase("MapWithAI")).findAny().orElse(null);
        assertNotNull(info);
        info = new MapWithAIInfo(info);
    }

    @Test
    void testAddMapWithAILayerActionTest() {
        AddMapWithAILayerAction action = new AddMapWithAILayerAction(info);
        assertDoesNotThrow(() -> action.actionPerformed(null));
        OsmDataLayer osmLayer = new OsmDataLayer(new DataSet(), "TEST DATA", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        osmLayer.getDataSet()
                .addDataSource(new DataSource(new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314), "TEST"));

        assertNull(MapWithAIDataUtils.getLayer(false));
        action.updateEnabledState();
        action.actionPerformed(null);
        threadSync.threadSync();
        assertNotNull(MapWithAIDataUtils.getLayer(false));

        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));

        MapWithAILayerInfo.getInstance().remove(info);

        MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
        assertTrue(layer.getDataSet().isEmpty());

        action.updateEnabledState();
        action.actionPerformed(null);
        threadSync.threadSync();
        assertFalse(layer.getDataSet().isEmpty());

        MainApplication.getLayerManager().removeLayer(MapWithAIDataUtils.getLayer(false));
        MapWithAILayer mapwithaiLayer = new MapWithAILayer(new DataSet(), "TEST", null);
        MainApplication.getLayerManager().addLayer(mapwithaiLayer);

        mapwithaiLayer.getDataSet()
                .addDataSource(new DataSource(new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314), ""));
        action.actionPerformed(null);
        threadSync.threadSync();
        assertFalse(mapwithaiLayer.getDataSet().isEmpty());
    }

    @Test
    void testRemoteIcon() throws IOException {
        final ImageIcon blankImage = ImageProvider.createBlankIcon(ImageProvider.ImageSizes.LARGEICON);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // BufferedImage is what the current implementation uses. Otherwise, we will
        // have to copy it into a BufferedImage.
        final BufferedImage bi = assertInstanceOf(BufferedImage.class, blankImage.getImage());
        ImageIO.write(bi, "png", byteArrayOutputStream);
        byte[] originalImage = byteArrayOutputStream.toByteArray();
        wireMockServer.addStubMapping(
                wireMockServer.stubFor(WireMock.get("/icon").willReturn(WireMock.aResponse().withBody(originalImage))));
        final MapWithAIInfo remoteInfo = new MapWithAIInfo(info);
        remoteInfo.setIcon(wireMockServer.baseUrl() + "/icon");
        final AddMapWithAILayerAction action = new AddMapWithAILayerAction(remoteInfo);
        threadSync.threadSync();
        final Object image = action.getValue(Action.LARGE_ICON_KEY);
        final ImageIcon attachedIcon = assertInstanceOf(ImageIcon.class, image);
        final BufferedImage bufferedImage = assertInstanceOf(BufferedImage.class, attachedIcon.getImage());
        byteArrayOutputStream.reset();
        ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
        final byte[] downloadedImage = byteArrayOutputStream.toByteArray();
        assertArrayEquals(originalImage, downloadedImage);
    }

    @Test
    void testNonRegression22683() {
        final OsmDataLayer layer = new OsmDataLayer(new DataSet(), "testNonRegression22683", null);
        layer.getDataSet().addDataSource(new DataSource(new Bounds(0, 0, 0.001, 0.001), "Area 1"));
        layer.getDataSet().addDataSource(new DataSource(new Bounds(-0.001, -0.001, 0, 0), "Area 2"));
        MainApplication.getLayerManager().addLayer(layer);
        final List<LogRecord> logs = new ArrayList<>();
        Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
                // Do nothing
            }

            @Override
            public void close() {
                // Do nothing
            }
        };
        Logging.getLogger().addHandler(testHandler);
        try {
            info.setUrl(wireMockServer.baseUrl());
            info.setSourceType(MapWithAIType.ESRI_FEATURE_SERVER);
            final AddMapWithAILayerAction action = new AddMapWithAILayerAction(info);
            Logging.clearLastErrorAndWarnings();
            assertDoesNotThrow(() -> action.actionPerformed(null));
            threadSync.threadSync();
            final List<LogRecord> ides = logs.stream()
                    .filter(record -> record.getThrown() instanceof IllegalArgumentException).toList();
            assertTrue(ides.isEmpty(), ides.stream().map(LogRecord::getMessage).collect(Collectors.joining("\n")));
        } finally {
            Logging.getLogger().removeHandler(testHandler);
        }
    }
}
