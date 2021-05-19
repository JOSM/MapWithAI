// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.runners.model.InitializationError;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.io.OsmApiInitializationException;
import org.openstreetmap.josm.io.OsmTransferCanceledException;
import org.openstreetmap.josm.plugins.mapwithai.backend.DataAvailability;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
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
    private static WireMockServer wireMock;
    private static final List<Object> wireMockUsers = Collections.synchronizedList(new ArrayList<>());
    private boolean workerExceptions = true;
    private UncaughtExceptionHandler currentExceptionHandler;
    private String currentReleaseUrl;
    private Collection<String> sourceSites;
    private Runnable mapwithaiLayerInfoMocker;
    private boolean territories;

    public MapWithAITestRules() {
        super();
    }

    /**
     * Use if a fully initialized {@link MapWithAILayerInfo} is required
     *
     * @return this, for easy chaining
     */
    public MapWithAITestRules sources() {
        return sources(null);
    }

    /**
     * Use if you don't need all of {@link MapWithAILayerInfo}
     *
     * @param mapwithaiLayerInfoMocker A mocker so that MapWithAILayerInfo isn't
     *                                 fully instantiated
     * @return this, for easy chaining
     */
    public MapWithAITestRules sources(Runnable mapwithaiLayerInfoMocker) {
        this.sources = true;
        this.mapwithaiLayerInfoMocker = mapwithaiLayerInfoMocker;
        return this;
    }

    @Override
    public MapWithAITestRules territories() {
        this.territories = true;
        super.territories();
        return this;
    }

    public MapWithAITestRules wiremock() {
        this.wiremock = true;
        territories();
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

        if (mapwithaiLayerInfoMocker != null) {
            mapwithaiLayerInfoMocker.run();
        }
        synchronized (wireMockUsers) {
            if (wiremock && wireMock == null) {
                wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock")
                        .extensions(new WireMockUrlTransformer()).dynamicPort());
                wireMock.start();
            }
            // Sometimes this is called twice, the second time resetting the config but not resetting the urls.
            if (wiremock && wireMock != null) {
                MapPaintUtils.setPaintStyleUrl(replaceUrl(wireMock, MapPaintUtils.getPaintStyleUrl()));
                // Avoid cases where tests could write the wiremock url to some fields.
                if (currentReleaseUrl == null) {
                    currentReleaseUrl = DataAvailability.getReleaseUrl();
                }
                DataAvailability.setReleaseUrl(replaceUrl(wireMock, DataAvailability.getReleaseUrl()));
                Config.getPref().put("osm-server.url", wireMock.baseUrl());
                // Avoid cases where tests could write the wiremock url to some fields.
                if (sourceSites == null) {
                    sourceSites = MapWithAILayerInfo.getImageryLayersSites();
                }
                MapWithAILayerInfo.setImageryLayersSites(sourceSites.stream().map(t -> replaceUrl(wireMock, t))
                        .filter(Objects::nonNull).collect(Collectors.toList()));
                MapWithAIConflationCategory.setConflationJsonLocation(
                        replaceUrl(wireMock, MapWithAIConflationCategory.getConflationJsonLocation()));
                try {
                    OsmApi.getOsmApi().initialize(NullProgressMonitor.INSTANCE);
                } catch (OsmTransferCanceledException | OsmApiInitializationException e) {
                    Logging.error(e);
                }
                wireMockUsers.add(this);
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
                public MapWithAILayerInfo getInstance(Invocation<MapWithAILayerInfo> inv) throws Throwable {
                    if (!sources) {
                        return null;
                    }
                    return inv.proceed();
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

    @Override
    protected void after() throws ReflectiveOperationException {
        super.after();
        synchronized (wireMockUsers) {
            wireMockUsers.remove(this);
            if (wiremock && wireMockUsers.isEmpty()) {
                MapPaintUtils.removeMapWithAIPaintStyles();
                // Run in EDT to avoid stopping wiremock server before wiremock requests finish.
                GuiHelper.runInEDTAndWait(wireMock::stop);
                List<LoggedRequest> requests = wireMock.findUnmatchedRequests().getRequests();
                wireMock = null;
                requests.forEach(r -> Logging.error(r.getAbsoluteUrl()));
                assertTrue(requests.isEmpty());
                Config.getPref().put("osm-server.url", null);
                // Avoid cases where tests could write the wiremock url to some fields.
                if (currentReleaseUrl != null) {
                    DataAvailability.setReleaseUrl(currentReleaseUrl);
                    currentReleaseUrl = null;
                }
                if (sourceSites != null) {
                    MapWithAILayerInfo.setImageryLayersSites(sourceSites);
                    sourceSites = null;
                }
                MapWithAIConflationCategory.resetConflationJsonLocation();
                resetMapWithAILayerInfo();
            }
        }
        if (workerExceptions) {
            Thread.setDefaultUncaughtExceptionHandler(currentExceptionHandler);
        }
        TestRunnerDecorator.cleanUpAllMocks();
    }

    private void resetMapWithAILayerInfo() {
        if (territories) {
            synchronized (MapWithAILayerInfo.class) {
                MapWithAILayerInfo.getInstance().clear();
                MapWithAILayerInfo.getInstance().getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry)
                        .forEach(MapWithAILayerInfo.getInstance()::add);
                MapWithAILayerInfo.getInstance().save();
            }
        }
    }

    /**
     * Replace URL servers with wiremock
     *
     * @param wireMockServer The wiremock to point to
     * @param url            The URL to fix
     * @return A url that points at the wiremock server
     */
    private static String replaceUrl(WireMockServer wireMockServer, String url) {
        try {
            URL temp = new URL(url);
            return wireMockServer.baseUrl() + temp.getFile();
        } catch (MalformedURLException error) {
            Logging.error(error);
        }
        return null;
    }

    /**
     * Get the wiremock instance
     *
     * @return The WireMock
     */
    public WireMockServer getWireMock() {
        return wireMock;
    }

    /**
     * Replace URL's with the wiremock URL
     *
     * @author Taylor Smock
     */
    private static class WireMockUrlTransformer extends ResponseTransformer {

        @Override
        public String getName() {
            return "Convert urls in responses to wiremock url";
        }

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            if (wireMock != null && !request.getUrl().endsWith("/capabilities")
                    && !response.getHeaders().getContentTypeHeader().mimeTypePart().contains("application/zip")) {
                String origBody = response.getBodyAsString();
                String newBody = origBody.replaceAll("https?:\\/\\/.*?\\/", wireMock.baseUrl() + "/");
                return Response.Builder.like(response).but().body(newBody).build();
            }
            return response;
        }

    }
}
