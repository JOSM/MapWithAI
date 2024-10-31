// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAIConfig;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.HTTP;
import org.openstreetmap.josm.testutils.annotations.OsmApi;

import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.AbsentPattern;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * Test class for {@link BoundingBoxMapWithAIDownloader}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@HTTP
@MapWithAIConfig
@OsmApi(OsmApi.APIType.FAKE)
@Wiremock
class BoundingBoxMapWithAIDownloaderTest {
    private static final String TEST_DATA = "<osm version=\"0.6\"><node id=\"1\" lat=\"0\" lon=\"0\" version=\"1\"/><node id=\"2\" lat=\"1\" lon=\"1\" version=\"1\"/></osm>";

    @Test
    void testThirdPartyConflation(WireMockRuntimeInfo wireMockRuntimeInfo) {
        MapWithAIInfo.THIRD_PARTY_CONFLATE.put(true);
        final MapWithAIInfo info = new MapWithAIInfo("testThirdPartyConflation",
                wireMockRuntimeInfo.getHttpBaseUrl() + "/testThirdPartyConflation");
        // ADDRESS has a default /conflate endpoint from a mocked copy of conflation
        // servers.
        info.setCategory(MapWithAICategory.ADDRESS);
        final Bounds downloadBounds = new Bounds(-10, -10, 10, 10);
        final BoundingBoxMapWithAIDownloader boundingBoxMapWithAIDownloader = new BoundingBoxMapWithAIDownloader(
                downloadBounds, info, false);
        wireMockRuntimeInfo.getWireMock().register(
                WireMock.get("/testThirdPartyConflation").willReturn(WireMock.aResponse().withBody(TEST_DATA)));

        final StubMapping conflationStub = wireMockRuntimeInfo.getWireMock()
                .register(WireMock.post("/conflate").willReturn(WireMock.aResponse()
                        .withBody("<osm version=\"0.6\"><node id=\"1\" lat=\"0\" lon=\"0\" version=\"1\"/></osm>")));
        final DataSet ds = assertDoesNotThrow(
                () -> boundingBoxMapWithAIDownloader.parseOsm(NullProgressMonitor.INSTANCE));
        assertEquals(1, ds.allPrimitives().size());
        assertEquals(1L, ds.allPrimitives().iterator().next().getPrimitiveId().getUniqueId());

        final List<ServeEvent> serveEvents = wireMockRuntimeInfo.getWireMock()
                .getServeEvents(ServeEventQuery.forStubMapping(conflationStub));
        assertEquals(1, serveEvents.size());
        final LoggedRequest request = serveEvents.get(0).getRequest();
        assertEquals(1, request.getParts().size(),
                request.getParts().stream().map(Request.Part::getName).collect(Collectors.joining(",")));
        assertNotNull(request.getPart("external"));
    }

    /**
     * Non-regression test for #22624: Improperly added resultOffset to URLs sent to
     * MapWithAI servers
     */
    @Test
    void testNonRegression22624(WireMockRuntimeInfo wireMockRuntimeInfo) {
        MapWithAIInfo.THIRD_PARTY_CONFLATE.put(true);
        MapWithAIInfo info = new MapWithAIInfo("testNonRegression22624",
                wireMockRuntimeInfo.getHttpBaseUrl() + "/no-conflation?bbox={bbox}",
                MapWithAIType.ESRI_FEATURE_SERVER.getTypeString(), null, "testNonRegression22624");
        info.setConflationUrl(wireMockRuntimeInfo.getHttpBaseUrl() + "/conflation?bbox={bbox}");
        info.setConflation(true);
        final Bounds downloadBounds = new Bounds(-10, -10, 10, 10);
        final BoundingBoxMapWithAIDownloader boundingBoxMapWithAIDownloader = new BoundingBoxMapWithAIDownloader(
                downloadBounds, info, false);

        StubMapping noConflation = wireMockRuntimeInfo.getWireMock()
                .register(WireMock.get("/no-conflation").willReturn(WireMock.badRequest()));
        StubMapping resultOffset = wireMockRuntimeInfo.getWireMock()
                .register(WireMock.get(WireMock.urlPathEqualTo("/conflation"))
                        .withQueryParam("bbox", new AnythingPattern())
                        .withQueryParam("resultOffset", new EqualToPattern("0")).willReturn(WireMock.badRequest()));
        StubMapping noResultOffset = wireMockRuntimeInfo.getWireMock()
                .register(WireMock.get(WireMock.urlPathEqualTo("/conflation"))
                        .withQueryParam("bbox", new AnythingPattern())
                        .withQueryParam("resultOffset", AbsentPattern.ABSENT)
                        .willReturn(WireMock.aResponse().withBody(TEST_DATA)));

        assertDoesNotThrow(() -> boundingBoxMapWithAIDownloader.parseOsm(NullProgressMonitor.INSTANCE));
        wireMockRuntimeInfo.getWireMock().verifyThat(0,
                RequestPatternBuilder.forCustomMatcher(noConflation.getRequest()));
        wireMockRuntimeInfo.getWireMock().verifyThat(0,
                RequestPatternBuilder.forCustomMatcher(resultOffset.getRequest()));
        wireMockRuntimeInfo.getWireMock().verifyThat(1,
                RequestPatternBuilder.forCustomMatcher(noResultOffset.getRequest()));
    }
}
