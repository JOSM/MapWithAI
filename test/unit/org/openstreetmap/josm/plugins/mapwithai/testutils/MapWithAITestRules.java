// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.logging.Level;

import org.junit.runners.model.InitializationError;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.io.OsmApiInitializationException;
import org.openstreetmap.josm.io.OsmTransferCanceledException;
import org.openstreetmap.josm.plugins.mapwithai.backend.DataAvailability;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnableTest;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Logging;

import com.github.tomakehurst.wiremock.WireMockServer;

public class MapWithAITestRules extends JOSMTestRules {

    private boolean sources;
    private boolean wiremock;
    private WireMockServer wireMock;

    public MapWithAITestRules sources() {
        this.sources = true;
        return this;
    }

    public MapWithAITestRules wiremock() {
        this.wiremock = true;
        super.territories();
        return this;
    }

    /**
     * Set up before running a test
     *
     * @throws InitializationError          If an error occurred while creating the
     *                                      required environment.
     * @throws ReflectiveOperationException if a reflective access error occurs
     */
    @Override
    protected void before() throws InitializationError, ReflectiveOperationException {
        super.before();
        Logging.getLogger().setFilter(record -> record.getLevel().intValue() >= Level.WARNING.intValue()
                || record.getSourceClassName().startsWith("org.openstreetmap.josm.plugins.mapwithai"));
        if (sources) {
            MapWithAILayerInfo.instance.load(false);
        }
        if (wiremock) {
            wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));
            wireMock.start();
            resetMapWithAILayerInfo();
            setupMapWithAILayerInfo(wireMock);
            MapWithAIDataUtils.setPaintStyleUrl(MapWithAIDataUtils.getPaintStyleUrl()
                    .replace(Config.getUrls().getJOSMWebsite(), wireMock.baseUrl()));
            DataAvailability.setReleaseUrl(wireMock.baseUrl() + "/JOSM_MapWithAI/json/sources.json");
            Config.getPref().put("osm-server.url", wireMock.baseUrl());
            try {
                OsmApi.getOsmApi().initialize(NullProgressMonitor.INSTANCE);
            } catch (OsmTransferCanceledException | OsmApiInitializationException e) {
                Logging.error(e);
            }
        }
    }

    @Override
    protected void after() {
        if (wiremock) {
            wireMock.stop();
            resetMapWithAILayerInfo();
            DataAvailability.setReleaseUrl(DataAvailability.DEFAULT_SERVER_URL);
            Config.getPref().put("osm-server.url", null);
        }
    }

    private static void setupMapWithAILayerInfo(WireMockServer wireMockServer) {
        synchronized (MapWithAITestRules.class) {
            resetMapWithAILayerInfo();
            MapWithAILayerInfo.instance.getLayers().stream().forEach(
                    i -> i.setUrl(GetDataRunnableTest.getDefaultMapWithAIAPIForTest(wireMockServer, i.getUrl())));
            MapWithAILayerInfo.instance.save();
        }
    }

    private static void resetMapWithAILayerInfo() {
        synchronized (MapWithAITestRules.class) {
            MapWithAILayerInfo.instance.clear();
            MapWithAILayerInfo.instance.getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry)
                    .forEach(MapWithAILayerInfo.instance::add);
            MapWithAILayerInfo.instance.save();
        }
    }
}
