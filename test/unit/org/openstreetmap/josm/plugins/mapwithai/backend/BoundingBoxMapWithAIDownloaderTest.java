// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAIConfig;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.HTTP;
import org.openstreetmap.josm.tools.Logging;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Test class for {@link BoundingBoxMapWithAIDownloader}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@HTTP
@Wiremock
@MapWithAIConfig
class BoundingBoxMapWithAIDownloaderTest {
    @RegisterExtension
    JOSMTestRules josmTestRules = new JOSMTestRules().fakeAPI();

    @BasicWiremock
    public WireMockServer wireMockServer;

    @ParameterizedTest
    @ValueSource(strings = { "text/json", "application/json", "application/geo+json" })
    void testEsriExceededTransferLimit(String responseType) {
        final Bounds downloadBounds = new Bounds(10, 10, 20, 20);
        final MapWithAIInfo info = new MapWithAIInfo("testEsriExceededTransferLimit",
                wireMockServer.baseUrl() + "/esriExceededLimit");
        final BoundingBoxMapWithAIDownloader boundingBoxMapWithAIDownloader = new BoundingBoxMapWithAIDownloader(
                downloadBounds, info, false);
        final JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        objectBuilder.add("type", "FeatureCollection");
        objectBuilder.add("properties", Json.createObjectBuilder().add("exceededTransferLimit", true));
        objectBuilder.add("features", Json.createArrayBuilder());
        wireMockServer.stubFor(WireMock
                .get(boundingBoxMapWithAIDownloader
                        .getRequestForBbox(downloadBounds.getMinLon(), downloadBounds.getMinLat(),
                                downloadBounds.getMinLon(), downloadBounds.getMinLat())
                        .replace(wireMockServer.baseUrl(), ""))
                .willReturn(WireMock.aResponse().withHeader("Content-Type", responseType)
                        .withBody(objectBuilder.build().toString())));

        Logging.clearLastErrorAndWarnings();
        final DataSet ds = assertDoesNotThrow(
                () -> boundingBoxMapWithAIDownloader.parseOsm(NullProgressMonitor.INSTANCE));
        List<String> errors = Logging.getLastErrorAndWarnings();
        assertEquals(1, errors.size(),
                "We weren't handling transfer limit issues. Are we now?\n" + String.join("\n", errors));
        assertTrue(errors.get(0).contains("Could not fully download"));
        assertTrue(ds.isEmpty());
    }
}
