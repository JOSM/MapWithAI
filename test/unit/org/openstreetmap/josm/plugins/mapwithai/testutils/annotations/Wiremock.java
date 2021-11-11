// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.plugins.mapwithai.backend.DataAvailability;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.IMapWithAIUrls;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIUrls;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.testutils.annotations.AnnotationUtils;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.HTTP;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

/**
 * Test annotation to ensure that wiremock is used
 *
 * @author Taylor Smock
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD })
@BasicPreferences
@HTTP
@ExtendWith(Wiremock.TestMapWithAIUrls.class)
@BasicWiremock(value = "test/resources/wiremock", responseTransformers = Wiremock.WireMockUrlTransformer.class)
public @interface Wiremock {
    /**
     * Set to {@code false} to turn off wiremock (use only in integration tests)
     *
     * @return {@code true} indicates that Wiremock must be used
     */
    boolean value() default true;

    /**
     * Replace URL's with the wiremock URL
     *
     * @author Taylor Smock
     */
    class WireMockUrlTransformer extends ResponseTransformer {
        private final ExtensionContext context;

        public WireMockUrlTransformer(ExtensionContext context) {
            this.context = context;
        }

        @Override
        public String getName() {
            return "Convert urls in responses to wiremock url";
        }

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            if (!request.getUrl().endsWith("/capabilities")
                    && response.getHeaders().getContentTypeHeader().mimeTypePart() != null
                    && !response.getHeaders().getContentTypeHeader().mimeTypePart().contains("application/zip")) {
                String origBody = response.getBodyAsString();
                String newBody = origBody.replaceAll("https?://.*?/",
                        WiremockExtension.getWiremock(context).baseUrl() + "/");
                return Response.Builder.like(response).but().body(newBody).build();
            }
            return response;
        }
    }

    /**
     * This is the base wiremock extension class
     */
    class WiremockExtension extends BasicWiremock.WireMockExtension {
        /**
         * Get the default wiremock server
         *
         * @param context The context to search
         * @return The wiremock server
         */
        public static WireMockServer getWiremock(ExtensionContext context) {
            ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(BasicWiremock.class);
            return context.getStore(namespace).get(WireMockServer.class, WireMockServer.class);
        }
    }

    /**
     * Extension for {@link MapWithAILayerInfo}
     */
    class MapWithAILayerInfoExtension extends WiremockExtension {
        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            try {
                super.afterAll(context);
            } finally {
                resetMapWithAILayerInfo();
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            super.beforeAll(context);
            MapWithAILayerInfo.setImageryLayersSites(null);
            AtomicBoolean finished = new AtomicBoolean();
            MapWithAILayerInfo.getInstance().clear();
            MapWithAILayerInfo.getInstance().load(false, () -> finished.set(true));
            Awaitility.await().atMost(Durations.TEN_SECONDS).until(finished::get);
        }

        private static void resetMapWithAILayerInfo() {
            // This should probably only be run if territories is initialized
            // TODO update if @Territories becomes an annotation
            synchronized (MapWithAILayerInfo.class) {
                MapWithAILayerInfo.getInstance().clear();
                MapWithAILayerInfo.getInstance().getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry)
                        .forEach(MapWithAILayerInfo.getInstance()::add);
                MapWithAILayerInfo.getInstance().save();
            }
        }

    }

    class TestMapWithAIUrls extends WiremockExtension implements IMapWithAIUrls {
        ExtensionContext context;
        private static boolean conflationServerInitialized;

        @Override
        public String getConflationServerJson() {
            conflationServerInitialized = true;
            return replaceUrl(getWiremock(this.context), MapWithAIUrls.getInstance().getConflationServerJson());
        }

        @Override
        public String getMapWithAISourcesJson() {
            return replaceUrl(getWiremock(this.context), MapWithAIUrls.getInstance().getMapWithAISourcesJson());
        }

        @Override
        public String getMapWithAIPaintStyle() {
            return replaceUrl(getWiremock(this.context), MapWithAIUrls.getInstance().getMapWithAIPaintStyle());
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            super.beforeAll(context);
            final Optional<Wiremock> annotation = AnnotationUtils.findFirstParentAnnotation(context, Wiremock.class);
            this.context = context;
            if (Boolean.FALSE.equals(annotation.map(Wiremock::value).orElse(Boolean.TRUE))) {
                MapWithAIConfig.setUrlsProvider(MapWithAIUrls.getInstance());
            } else {
                MapWithAIConfig.setUrlsProvider(this);
            }
            if (conflationServerInitialized) {
                MapWithAIConflationCategory.initialize();
            }
            AnnotationUtils.resetStaticClass(DataAvailability.class);
        }

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            final Optional<Wiremock> annotation = AnnotationUtils.findFirstParentAnnotation(context, Wiremock.class);
            this.context = context;
            if (annotation.isPresent()) {
                this.beforeAll(context);
            }
            final WireMockServer wireMockServer = getWiremock(context);

            super.beforeEach(context);

            if (wireMockServer.getStubMappings().stream().filter(mapping -> mapping.getRequest().getUrl() != null)
                    .noneMatch(mapping -> mapping.getRequest().getUrl()
                            .equals("/JOSM_MapWithAI/json/conflation_servers.json"))) {
                wireMockServer.stubFor(WireMock.get("/JOSM_MapWithAI/json/conflation_servers.json")
                        .willReturn(WireMock.aResponse().withBody("{}")).atPriority(-5));
            }
        }

        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            // @Wiremock stops the WireMockServer prior to this method being called
            getWiremock(context).start();
            MapPaintUtils.removeMapWithAIPaintStyles();
            try {
                // This stops the WireMockServer again.
                super.afterAll(context);
            } finally {
                MapWithAIConfig.setUrlsProvider(new InvalidMapWithAIUrls());
            }
        }

        public WireMockServer getWireMockServer() {
            return getWiremock(context);
        }
    }

    class InvalidMapWithAIUrls implements IMapWithAIUrls {
        @Override
        public String getConflationServerJson() {
            throw new UnsupportedOperationException("Please use the @Wiremock annotation");
        }

        @Override
        public String getMapWithAISourcesJson() {
            return this.getConflationServerJson();
        }

        @Override
        public String getMapWithAIPaintStyle() {
            return this.getConflationServerJson();
        }
    }
}
