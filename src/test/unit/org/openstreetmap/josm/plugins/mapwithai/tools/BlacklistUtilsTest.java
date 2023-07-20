// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAIPluginMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Tests for {@link BlacklistUtils}
 *
 * @author Taylor Smock
 *
 */
class BlacklistUtilsTest {
    private static WireMockServer wireMock;

    @BeforeAll
    static void setup() {
        TestUtils.assumeWorkingJMockit();
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        BlacklistUtils.setBlacklistUrl(wireMock.baseUrl() + "/MapWithAI/json/blacklisted_versions.json");
        new MapWithAIPluginMock();
    }

    @BeforeEach
    void clear() {
        wireMock.resetMappings();
    }

    @AfterAll
    static void tearDown() {
        BlacklistUtils.setBlacklistUrl(BlacklistUtils.DEFAULT_BLACKLIST_URL);
        assertTrue(wireMock.findAllUnmatchedRequests().isEmpty());
        wireMock.stop();
    }

    @Test
    void testArrayBad() {
        wireMock.addStubMapping(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("[\"" + MapWithAIPlugin.getVersionInfo() + "\"]"))
                .build());
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testArrayGood() {
        wireMock.addStubMapping(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("[null, 0, false]")).build());
        assertFalse(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testObjectBad() {
        wireMock.addStubMapping(get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(aResponse()
                .withStatus(200).withBody("{ \"" + MapWithAIPlugin.getVersionInfo() + "\": \"reason here\"}")).build());
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testObjectGood() {
        wireMock.addStubMapping(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("{ \"version\": \"reason here\"}")).build());
        assertFalse(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testNullJson() {
        wireMock.addStubMapping(get(urlMatching("/MapWithAI/json/blacklisted_versions.json"))
                .willReturn(aResponse().withStatus(200).withBody("null")).build());
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testBrokenJson() {
        wireMock.addStubMapping(get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(
                aResponse().withStatus(200).withBody("{ \"" + MapWithAIPlugin.getVersionInfo() + "\": \"reason here\""))
                .build());
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testNoResponse() {
        wireMock.addStubMapping(
                get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(noContent()).build());
        assertTrue(BlacklistUtils.isBlacklisted());
    }

    @Test
    void testNotFound() {
        wireMock.addStubMapping(
                get(urlMatching("/MapWithAI/json/blacklisted_versions.json")).willReturn(notFound()).build());
        assertTrue(BlacklistUtils.isBlacklisted());
    }
}
