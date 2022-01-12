// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAIConfig;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.HTTP;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.GetServeEventsResult;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

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

    @Test
    void testThirdPartyConflation() {
        MapWithAIInfo.THIRD_PARTY_CONFLATE.put(true);
        final MapWithAIInfo info = new MapWithAIInfo("testThirdPartyConflation",
                this.wireMockServer.baseUrl() + "/testThirdPartyConflation");
        // ADDRESS has a default /conflate endpoint from a mocked copy of conflation
        // servers.
        info.setCategory(MapWithAICategory.ADDRESS);
        final Bounds downloadBounds = new Bounds(-10, -10, 10, 10);
        final BoundingBoxMapWithAIDownloader boundingBoxMapWithAIDownloader = new BoundingBoxMapWithAIDownloader(
                downloadBounds, info, false);
        this.wireMockServer.stubFor(WireMock.get("/testThirdPartyConflation").willReturn(WireMock.aResponse().withBody(
                "<osm version=\"0.6\"><node id=\"1\" lat=\"0\" lon=\"0\" version=\"1\"/><node id=\"2\" lat=\"1\" lon=\"1\" version=\"1\"/></osm>")));
        final StubMapping conflationStub = this.wireMockServer
                .stubFor(WireMock.post("/conflate").willReturn(WireMock.aResponse()
                        .withBody("<osm version=\"0.6\"><node id=\"1\" lat=\"0\" lon=\"0\" version=\"1\"/></osm>")));
        final DataSet ds = assertDoesNotThrow(
                () -> boundingBoxMapWithAIDownloader.parseOsm(NullProgressMonitor.INSTANCE));
        assertEquals(1, ds.allPrimitives().size());
        assertEquals(1L, ds.allPrimitives().iterator().next().getPrimitiveId().getUniqueId());

        final GetServeEventsResult serveEvents = this.wireMockServer
                .getServeEvents(ServeEventQuery.forStubMapping(conflationStub));
        assertEquals(1, serveEvents.getRequests().size());
        final LoggedRequest request = serveEvents.getRequests().get(0).getRequest();
        assertEquals(1, request.getParts().size(),
                request.getParts().stream().map(Request.Part::getName).collect(Collectors.joining(",")));
        assertNotNull(request.getPart("external"));
    }
}
