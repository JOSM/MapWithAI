// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.actions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.ImageIcon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.ImageProvider;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

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
    static JOSMTestRules rule = new MapWithAITestRules().projection();

    @Test
    void testAddMapWithAILayerActionTest() {
        MapWithAIInfo info = MapWithAILayerInfo.getInstance().getLayers().stream()
                .filter(i -> i.getName().equalsIgnoreCase("MapWithAI")).findAny().orElse(null);
        assertNotNull(info);
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

    @Test
    void testRemoteIcon() throws IOException, ExecutionException, InterruptedException {
        final ImageIcon blankImage = ImageProvider.createBlankIcon(ImageProvider.ImageSizes.LARGEICON);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // BufferedImage is what the current implementation uses. Otherwise, we will
        // have to copy it into a BufferedImage.
        assertTrue(blankImage.getImage() instanceof BufferedImage);
        final BufferedImage bi = (BufferedImage) blankImage.getImage();
        ImageIO.write(bi, "png", byteArrayOutputStream);
        byte[] originalImage = byteArrayOutputStream.toByteArray();
        final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        try {
            wireMockServer.start();
            wireMockServer.addStubMapping(wireMockServer
                    .stubFor(WireMock.get("/icon").willReturn(WireMock.aResponse().withBody(originalImage))));
            final MapWithAIInfo info = MapWithAILayerInfo.getInstance().getLayers().stream()
                    .filter(i -> i.getName().equalsIgnoreCase("MapWithAI")).findAny().orElse(null);
            assertNotNull(info);
            final MapWithAIInfo remoteInfo = new MapWithAIInfo(info);
            remoteInfo.setIcon(wireMockServer.baseUrl() + "/icon");
            final AddMapWithAILayerAction action = new AddMapWithAILayerAction(remoteInfo);
            GuiHelper.runInEDTAndWait(() -> {
                /* Sync EDT */});
            MainApplication.worker.submit(() -> {
                /* Sync worker thread */}).get();
            final Object image = action.getValue(Action.LARGE_ICON_KEY);
            assertTrue(image instanceof ImageIcon);
            final ImageIcon attachedIcon = (ImageIcon) image;
            assertTrue(attachedIcon.getImage() instanceof BufferedImage);
            byteArrayOutputStream.reset();
            ImageIO.write((BufferedImage) attachedIcon.getImage(), "png", byteArrayOutputStream);
            final byte[] downloadedImage = byteArrayOutputStream.toByteArray();
            assertArrayEquals(originalImage, downloadedImage);
        } finally {
            wireMockServer.stop();
        }
    }

}
