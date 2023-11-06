// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAIPluginMock;
import org.openstreetmap.josm.spi.preferences.Config;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

/**
 * Tests for {@link BlacklistUtils}
 *
 * @author Taylor Smock
 *
 */
@WireMockTest
class BlacklistUtilsTest {
    private static String blacklistUrl;

    @BeforeAll
    static void setup(WireMockRuntimeInfo wireMockRuntimeInfo) {
        TestUtils.assumeWorkingJMockit();
        blacklistUrl = wireMockRuntimeInfo.getHttpBaseUrl() + "/MapWithAI/json/blacklisted_versions.json";
        BlacklistUtils.setBlacklistUrl(blacklistUrl);
        new MapWithAIPluginMock();
    }

    @BeforeEach
    @AfterEach
    void cleanup(WireMockRuntimeInfo wireMockRuntimeInfo) {
        final var file = new File(Config.getDirs().getCacheDirectory(false), "mirror_http___localhost_"
                + wireMockRuntimeInfo.getHttpPort() + "_MapWithAI_json_blacklisted_versions.json");
        // Ensure that we aren't reading a cached response from a different test
        if (file.exists()) {
            assertTrue(file.delete());
        }
    }

    @AfterAll
    static void tearDown() {
        BlacklistUtils.setBlacklistUrl(BlacklistUtils.DEFAULT_BLACKLIST_URL);
        blacklistUrl = null;
    }

    @Test
    void testArrayBad(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock().register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("[\"" + MapWithAIPlugin.getVersionInfo() + "\"]")));
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testArrayGood(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock().register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("[null, 0, false]")));
        assertFalse(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testObjectBad(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock()
                .register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(aResponse()
                        .withStatus(200).withBody("{ \"" + MapWithAIPlugin.getVersionInfo() + "\": \"reason here\"}")));
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testObjectGood(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock().register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("{ \"version\": \"reason here\"}")));
        assertFalse(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testNullJson(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock().register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("null")));
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testBrokenJson(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock()
                .register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(aResponse()
                        .withStatus(200).withBody("{ \"" + MapWithAIPlugin.getVersionInfo() + "\": \"reason here\"")));
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testNoResponse(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock()
                .register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(noContent()));
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testNotFound(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock()
                .register(get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(notFound()));
        assertTrue(BlacklistUtils.isBlacklisted());
    }
}
