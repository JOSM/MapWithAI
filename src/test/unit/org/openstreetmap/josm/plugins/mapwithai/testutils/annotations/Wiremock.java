// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
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
import org.openstreetmap.josm.testutils.annotations.Territories;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.ReflectionUtils;

/**
 * Test annotation to ensure that wiremock is used
 *
 * @author Taylor Smock
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD })
@BasicPreferences
@HTTP
@BasicWiremock(value = "src/test/resources/wiremock")
@ExtendWith(Wiremock.TestMapWithAIUrls.class)
public @interface Wiremock {
    /**
     * Set to {@code false} to turn off wiremock (use only in integration tests)
     *
     * @return {@code true} indicates that Wiremock must be used
     */
    boolean value() default true;

    /**
     * This is the base wiremock extension class
     */
    class WiremockExtension {
        /**
         * Get the default wiremock server
         *
         * @param context The context to search
         * @return The wiremock server
         */
        public static WireMockRuntimeInfo getWiremock(ExtensionContext context) {
            return context.getStore(ExtensionContext.Namespace.create(BasicWiremock.WireMockExtension.class))
                    .get(BasicWiremock.WireMockExtension.class, BasicWiremock.WireMockExtension.class).getRuntimeInfo();
        }
    }

    /**
     * Extension for {@link MapWithAILayerInfo}
     */
    class MapWithAILayerInfoExtension extends WiremockExtension implements BeforeAllCallback, AfterAllCallback {

        @Override
        public void afterAll(ExtensionContext context) {
            resetMapWithAILayerInfo(context);
        }

        @Override
        public void beforeAll(ExtensionContext context) {
            MapWithAILayerInfo.setImageryLayersSites(null);
            AtomicBoolean finished = new AtomicBoolean();
            MapWithAILayerInfo.getInstance().clear();
            MapWithAILayerInfo.getInstance().load(false, () -> finished.set(true));
            Awaitility.await().atMost(Durations.TEN_SECONDS).until(finished::get);
        }

        private static void resetMapWithAILayerInfo(ExtensionContext context) {
            synchronized (MapWithAILayerInfo.class) {
                final var info = MapWithAILayerInfo.getInstance();
                info.clear();
                if (AnnotationSupport.isAnnotated(context.getTestClass(), Territories.class)) {
                    // This should probably only be run if territories is initialized
                    info.getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry).forEach(info::add);
                }
                info.save();
            }
        }

    }

    class TestMapWithAIUrls extends WiremockExtension
            implements IMapWithAIUrls, BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {
        ExtensionContext context;
        private static boolean conflationServerInitialized;

        /**
         * Replace URL servers with wiremock
         *
         * @param baseUrl The wiremock to point to
         * @param url            The URL to fix
         * @return A url that points at the wiremock server
         */
        public static String replaceUrl(String baseUrl, String url) {
            try {
                URL temp = new URL(url);
                return baseUrl + temp.getFile();
            } catch (MalformedURLException error) {
                Logging.error(error);
            }
            return null;
        }

        @Override
        public String getConflationServerJson() {
            conflationServerInitialized = true;
            return replaceUrl(getWiremock(this.context).getHttpBaseUrl(),
                    MapWithAIUrls.getInstance().getConflationServerJson());
        }

        @Override
        public String getMapWithAISourcesJson() {
            return replaceUrl(getWiremock(this.context).getHttpBaseUrl(),
                    MapWithAIUrls.getInstance().getMapWithAISourcesJson());
        }

        @Override
        public String getMapWithAIPaintStyle() {
            return replaceUrl(getWiremock(this.context).getHttpBaseUrl(),
                    MapWithAIUrls.getInstance().getMapWithAIPaintStyle());
        }

        @Override
        public void beforeAll(ExtensionContext context) {
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
            assertDoesNotThrow(() -> AnnotationUtils.resetStaticClass(DataAvailability.class));
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            final Optional<Wiremock> annotation = AnnotationUtils.findFirstParentAnnotation(context, Wiremock.class);
            this.context = context;
            if (annotation.isPresent()) {
                this.beforeAll(context);
            }
            final WireMock wireMockServer = getWiremock(context).getWireMock();

            if (wireMockServer.allStubMappings().getMappings().stream()
                    .filter(mapping -> mapping.getRequest().getUrl() != null).noneMatch(mapping -> mapping.getRequest()
                            .getUrl().equals("/MapWithAI/json/conflation_servers.json"))) {
                wireMockServer.register(WireMock.get("/MapWithAI/json/conflation_servers.json")
                        .willReturn(WireMock.aResponse().withBody("{}")).atPriority(-5));
            }
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) {
            this.context = extensionContext;
        }

        @Override
        public void afterAll(ExtensionContext context) {
            this.context = context;
            // @Wiremock stops the WireMockServer prior to this method being called
            final WireMockServer wireMockServer = assertDoesNotThrow(() -> {
                final Field serverField = WireMockRuntimeInfo.class.getDeclaredField("wireMockServer");
                ReflectionUtils.setObjectsAccessible(serverField);
                return (WireMockServer) serverField.get(getWiremock(context));
            });
            try {
                wireMockServer.start();
                MapPaintUtils.removeMapWithAIPaintStyles();
                wireMockServer.stop();
            } finally {
                MapWithAIConfig.setUrlsProvider(new InvalidMapWithAIUrls());
            }
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
