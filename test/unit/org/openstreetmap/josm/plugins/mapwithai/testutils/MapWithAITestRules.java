// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
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
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import mockit.integration.TestRunnerDecorator;

public class MapWithAITestRules extends JOSMTestRules {

    private boolean sources;
    private boolean wiremock;
    private WireMockServer wireMock;
    private boolean workerExceptions = true;
    private UncaughtExceptionHandler currentExceptionHandler;
    private String currentReleaseUrl;

    public MapWithAITestRules() {
        super();
        super.assertionsInEDT();
    }

    public MapWithAITestRules sources() {
        this.sources = true;
        return this;
    }

    public MapWithAITestRules wiremock() {
        this.wiremock = true;
        super.territories();
        return this;
    }

    public MapWithAITestRules noWorkerExceptions() {
        this.workerExceptions = false;
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
        TestRunnerDecorator.cleanUpAllMocks();
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
            currentReleaseUrl = DataAvailability.getReleaseUrl();
            DataAvailability.setReleaseUrl(wireMock.baseUrl() + "/JOSM_MapWithAI/json/sources.json");
            Config.getPref().put("osm-server.url", wireMock.baseUrl());
            try {
                OsmApi.getOsmApi().initialize(NullProgressMonitor.INSTANCE);
            } catch (OsmTransferCanceledException | OsmApiInitializationException e) {
                Logging.error(e);
            }
        }
        if (workerExceptions) {
            currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                Logging.error(t.getClass().getSimpleName());
                Logging.error(e);
            });
        }
    }

    @Override
    protected void after() throws ReflectiveOperationException {
        super.after();
        if (wiremock) {
            wireMock.stop();
            List<LoggedRequest> requests = wireMock.findUnmatchedRequests().getRequests();
            requests.forEach(r -> Logging.error(r.getAbsoluteUrl()));
            assertTrue(requests.isEmpty());
            resetMapWithAILayerInfo();
            DataAvailability.setReleaseUrl(currentReleaseUrl);
            Config.getPref().put("osm-server.url", null);
        }
        if (workerExceptions) {
            Thread.setDefaultUncaughtExceptionHandler(currentExceptionHandler);
        }
        TestRunnerDecorator.cleanUpAllMocks();
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
        synchronized (MapWithAILayerInfo.class) {
            MapWithAILayerInfo.instance.clear();
            MapWithAILayerInfo.instance.getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry)
                    .forEach(MapWithAILayerInfo.instance::add);
            MapWithAILayerInfo.instance.save();
        }
    }

    /**
     * Get the wiremock instance
     *
     * @return The WireMock
     */
    public WireMockServer getWireMock() {
        return this.wireMock;
    }
}
