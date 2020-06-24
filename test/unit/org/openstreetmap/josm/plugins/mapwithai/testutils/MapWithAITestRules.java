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
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
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

import mockit.Mock;
import mockit.MockUp;
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
            MapWithAIDataUtils.setPaintStyleUrl(replaceUrl(wireMock, MapWithAIDataUtils.getPaintStyleUrl()));
            currentReleaseUrl = DataAvailability.getReleaseUrl();
            DataAvailability.setReleaseUrl(replaceUrl(wireMock, DataAvailability.getReleaseUrl()));
            Config.getPref().put("osm-server.url", wireMock.baseUrl());
            sourceSites = MapWithAILayerInfo.getImageryLayersSites();
            MapWithAILayerInfo.setImageryLayersSites(sourceSites.stream().map(t -> replaceUrl(wireMock, t))
                    .filter(Objects::nonNull).collect(Collectors.toList()));
            MapWithAIConflationCategory.setConflationJsonLocation(
                    replaceUrl(wireMock, MapWithAIConflationCategory.getConflationJsonLocation()));
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
        } else {
            // This only exists to ensure that if MapWithAILayerInfo is called, things
            // happen...
            new MockUp<MapWithAILayerInfo>() {
                @Mock
                public MapWithAILayerInfo getInstance() {
                    return null;
                }
            };
        }
        if (workerExceptions) {
            currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                Logging.error(t.getClass().getSimpleName());
                Logging.error(e);
            });
        }
    }

    /**
     * Replace URL servers with wiremock
     *
     * @param wireMock The wiremock to point to
     * @param url      The URL to fix
     * @return A url that points at the wiremock server
     */
    private static String replaceUrl(WireMockServer wireMock, String url) {
        try {
            URL temp = new URL(url);
            return wireMock.baseUrl() + temp.getFile();
        } catch (MalformedURLException error) {
            Logging.error(error);
        }
        return null;
    }

    @Override
    protected void after() throws ReflectiveOperationException {
        super.after();
        if (wiremock) {
            wireMock.stop();
            List<LoggedRequest> requests = wireMock.findUnmatchedRequests().getRequests();
            requests.forEach(r -> Logging.error(r.getAbsoluteUrl()));
            assertTrue(requests.isEmpty());
            DataAvailability.setReleaseUrl(currentReleaseUrl);
            Config.getPref().put("osm-server.url", null);
            MapWithAILayerInfo.setImageryLayersSites(sourceSites);
            MapWithAIConflationCategory.resetConflationJsonLocation();
            resetMapWithAILayerInfo();
        }
        if (workerExceptions) {
            Thread.setDefaultUncaughtExceptionHandler(currentExceptionHandler);
        }
        TestRunnerDecorator.cleanUpAllMocks();
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
