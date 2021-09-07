// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.plugins.mapwithai.backend.DataAvailability;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.HTTP;

/**
 * Test annotation to ensure that wiremock is used
 *
 * @author Taylor Smock
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD })
@BasicPreferences
@HTTP
@ExtendWith(Wiremock.DataAvailabilityExtension.class)
@ExtendWith(Wiremock.MapPaintUtilsExtension.class)
@ExtendWith(Wiremock.MapWithAIConflationCategoryExtension.class)
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
                    && !response.getHeaders().getContentTypeHeader().mimeTypePart().contains("application/zip")) {
                String origBody = response.getBodyAsString();
                String newBody = origBody.replaceAll("https?:\\/\\/.*?\\/",
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
        static WireMockServer getWiremock(ExtensionContext context) {
            ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(BasicWiremock.class);
            return context.getStore(namespace).get(WireMockServer.class, WireMockServer.class);
        }
    }

    /**
     * Extension for {@link MapPaintUtils}
     */
    class MapPaintUtilsExtension extends WiremockExtension {
        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            try {
                super.afterAll(context);
            } finally {
                MapPaintUtils.removeMapWithAIPaintStyles();
                MapPaintUtils.setPaintStyleUrl("https://invalid.url/josmfile?page=Styles/MapWithAI&zip=1");
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            super.beforeAll(context);
            MapPaintUtils.setPaintStyleUrl(replaceUrl(getWiremock(context), MapPaintUtils.getPaintStyleUrl()));
        }
    }

    /**
     * Extension for {@link MapWithAILayerInfo}
     */
    class MapWithAILayerInfoExtension extends WiremockExtension {
        private static Collection<String> getSourceSites(ExtensionContext context) {
            ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(MapWithAILayerInfoExtension.class);
            return (Collection<String>) context.getStore(namespace).get(MapWithAILayerInfo.class, Collection.class);
        }

        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            try {
                super.afterAll(context);
            } finally {
                MapWithAILayerInfo.setImageryLayersSites(
                        Collections.singleton("https://invalid.url/JOSM_MapWithAI/json/sources.json"));
                resetMapWithAILayerInfo();
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            super.beforeAll(context);
            MapWithAILayerInfo.setImageryLayersSites(
                    MapWithAILayerInfo.getImageryLayersSites().stream().map(t -> replaceUrl(getWiremock(context), t))
                            .filter(Objects::nonNull).collect(Collectors.toList()));
            AtomicBoolean finished = new AtomicBoolean();
            MapWithAILayerInfo.getInstance().clear();
            MapWithAILayerInfo.getInstance().load(false, () -> finished.set(true));
            Awaitility.await().atMost(Durations.TEN_SECONDS).until(finished::get);
        }

        private void resetMapWithAILayerInfo() {
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

    /**
     * Extension for {@link DataAvailability}
     */
    class DataAvailabilityExtension extends WiremockExtension {
        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            try {
                super.afterAll(context);
            } finally {
                DataAvailability.setReleaseUrl("https://invalid.url/JOSM_MapWithAI/json/sources.json");
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            super.beforeAll(context);
            DataAvailability.setReleaseUrl(replaceUrl(getWiremock(context), DataAvailability.getReleaseUrl()));
        }
    }

    /**
     * Extension for {@link MapWithAIConflationCategory}
     */
    class MapWithAIConflationCategoryExtension extends WiremockExtension {
        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            try {
                super.afterAll(context);
            } finally {
                MapWithAIConflationCategory.resetConflationJsonLocation();
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            super.beforeAll(context);
            MapWithAIConflationCategory.setConflationJsonLocation(
                    replaceUrl(getWiremock(context), MapWithAIConflationCategory.getConflationJsonLocation()));
        }
    }

}
