// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
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
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import mockit.integration.TestRunnerDecorator;

public class MapWithAITestRules extends JOSMTestRules {

    private boolean sources;
    private boolean wiremock;
    private WireMockServer wireMock;
    private boolean workerExceptions = true;
    private UncaughtExceptionHandler currentExceptionHandler;
    private String currentReleaseUrl;
    private Collection<String> sourceSites;

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

        if (wiremock) {
            wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock")
                    .extensions(new WireMockUrlTransformer()).dynamicPort());
            wireMock.start();
            resetMapWithAILayerInfo();
            setupMapWithAILayerInfo(wireMock);
            MapWithAIDataUtils.setPaintStyleUrl(MapWithAIDataUtils.getPaintStyleUrl()
                    .replace(Config.getUrls().getJOSMWebsite(), wireMock.baseUrl()));
            currentReleaseUrl = DataAvailability.getReleaseUrl();
            DataAvailability
                    .setReleaseUrl(wireMock.baseUrl() + "/gokaart/JOSM_MapWithAI/-/raw/pages/public/json/sources.json");
            Config.getPref().put("osm-server.url", wireMock.baseUrl());
            sourceSites = MapWithAILayerInfo.getImageryLayersSites();
            MapWithAILayerInfo.setImageryLayersSites(sourceSites.stream().map(t -> {
                try {
                    URL temp = new URL(t);
                    return wireMock.baseUrl() + temp.getFile();
                } catch (MalformedURLException error) {
                    Logging.error(error);
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList()));
            MapWithAILayerInfo.getInstance().getLayers()
                    .forEach(l -> l.setUrl(l.getUrl().replaceAll("https?:\\/\\/.*?\\/", wireMock.baseUrl() + "/")));
            try {
                OsmApi.getOsmApi().initialize(NullProgressMonitor.INSTANCE);
            } catch (OsmTransferCanceledException | OsmApiInitializationException e) {
                Logging.error(e);
            }
        }
        if (sources) {
            AtomicBoolean finished = new AtomicBoolean();
            MapWithAILayerInfo.getInstance().load(false, () -> finished.set(true));
            Awaitility.await().atMost(Durations.TEN_SECONDS).until(finished::get);
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
            MapWithAILayerInfo.setImageryLayersSites(sourceSites);
        }
        if (workerExceptions) {
            Thread.setDefaultUncaughtExceptionHandler(currentExceptionHandler);
        }
        TestRunnerDecorator.cleanUpAllMocks();
    }

    private static void setupMapWithAILayerInfo(WireMockServer wireMockServer) {
        synchronized (MapWithAITestRules.class) {
            resetMapWithAILayerInfo();
            MapWithAILayerInfo.getInstance().getLayers().stream().forEach(
                    i -> i.setUrl(GetDataRunnableTest.getDefaultMapWithAIAPIForTest(wireMockServer, i.getUrl())));
            MapWithAILayerInfo.getInstance().save();
        }
    }

    private static void resetMapWithAILayerInfo() {
        synchronized (MapWithAILayerInfo.class) {
            MapWithAILayerInfo.getInstance().clear();
            MapWithAILayerInfo.getInstance().getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry)
                    .forEach(MapWithAILayerInfo.getInstance()::add);
            MapWithAILayerInfo.getInstance().save();
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

    /**
     * Replace URL's with the wiremock URL
     *
     * @author Taylor Smock
     */
    private class WireMockUrlTransformer extends ResponseTransformer {

        @Override
        public String getName() {
            return "Convert urls in responses to wiremock url";
        }

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            if (wireMock != null) {
                String origBody = response.getBodyAsString();
                String newBody = origBody.replaceAll("https?:\\/\\/.*?\\/", wireMock.baseUrl() + "/");
                return Response.Builder.like(response).but().body(newBody).build();
            }
            return response;
        }

    }
}
