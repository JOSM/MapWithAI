// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.IMapWithAIUrls;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Set the MapWithAI config for the test
 *
 * @author Taylor Smock
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(MapWithAIConfig.MapWithAIConfigExtension.class)
@Wiremock
public @interface MapWithAIConfig {
    class MapWithAIConfigExtension extends Wiremock.WiremockExtension implements BeforeEachCallback, AfterEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig
                    .setUrlsProvider(new WireMockMapWithAIUrls(getWiremock(context)));
        }

        @Override
        public void afterEach(ExtensionContext context) {
            org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig
                    .setUrlsProvider(new UnsupportedMapWithAIUrls(
                            org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig.getUrls()));
        }

        private static final class WireMockMapWithAIUrls implements IMapWithAIUrls {
            private final WireMockServer wireMockServer;

            public WireMockMapWithAIUrls(final WireMockServer wireMockServer) {
                this.wireMockServer = wireMockServer;
            }

            @Override
            public String getConflationServerJson() {
                return this.wireMockServer.baseUrl() + "/MapWithAI/json/conflation_servers.json";
            }

            @Override
            public String getMapWithAISourcesJson() {
                return this.wireMockServer.baseUrl() + "/MapWithAI/json/sources.json";
            }

            @Override
            public String getMapWithAIPaintStyle() {
                return this.wireMockServer.baseUrl() + "/josmfile?page=Styles/MapWithAI&zip=1";
            }
        }

        private static final class UnsupportedMapWithAIUrls implements IMapWithAIUrls {
            private IMapWithAIUrls oldUrls;

            public UnsupportedMapWithAIUrls(IMapWithAIUrls oldUrls) {
                this.oldUrls = oldUrls;
            }

            @Override
            public String getConflationServerJson() {
                throw new UnsupportedOperationException("Use @MapWithAIConfig");
            }

            @Override
            public String getMapWithAISourcesJson() {
                throw new UnsupportedOperationException("Use @MapWithAIConfig");
            }

            @Override
            public String getMapWithAIPaintStyle() {
                if (this.oldUrls != null && Stream.of(Thread.currentThread().getStackTrace())
                        .map(StackTraceElement::getMethodName).anyMatch("afterAll"::equals)) {
                    final IMapWithAIUrls urls = this.oldUrls;
                    this.oldUrls = null;
                    return urls.getMapWithAIPaintStyle();
                }
                throw new UnsupportedOperationException("Use @MapWithAIConfig");
            }
        }
    }
}
