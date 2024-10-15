// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.HTTP;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

/**
 * Test class for {@link DataConflationSender}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@HTTP
@Wiremock
@MapWithAISources
class DataConflationSenderTest {
    static class MapWithAIConflationCategoryMock extends MockUp<MapWithAIConflationCategory> {
        static String url;

        @Mock
        public static String conflationUrlFor(final Invocation invocation, final MapWithAICategory category) {
            if (url == null) {
                return invocation.proceed(category);
            } else {
                return url;
            }
        }
    }

    @BeforeEach
    void beforeEach() {
        MapWithAIConflationCategoryMock.url = null;
    }

    @Test
    void testEmptyUrl() {
        MapWithAIConflationCategoryMock.url = "";
        new MapWithAIConflationCategoryMock();

        final DataSet external = new DataSet(new Node(LatLon.NORTH_POLE));
        final DataSet openstreetmap = new DataSet(new Node(LatLon.ZERO));
        final DataConflationSender dataConflationSender = new DataConflationSender(MapWithAICategory.OTHER,
                openstreetmap, external);
        dataConflationSender.run();
        assertNull(assertDoesNotThrow((ThrowingSupplier<DataSet>) dataConflationSender::get));
    }

    @Test
    void testWorkingUrl(WireMockRuntimeInfo wireMockRuntimeInfo) {
        MapWithAIConflationCategoryMock.url = wireMockRuntimeInfo.getHttpBaseUrl() + "/conflate";
        final StubMapping stubMapping = wireMockRuntimeInfo.getWireMock()
                .register(WireMock.post("/conflate").willReturn(WireMock.aResponse().withBody(
                        "<?xml version='1.0' encoding='UTF-8'?><osm version='0.6' generator='DataConflationSenderTest#testWorkingUrl'><node id='1' version='1' visible='true' lat='89.0' lon='0.1' /></osm>")));
        new MapWithAIConflationCategoryMock();

        final DataSet external = new DataSet(new Node(LatLon.NORTH_POLE));
        final DataSet openstreetmap = new DataSet(new Node(LatLon.ZERO));
        final DataConflationSender dataConflationSender = new DataConflationSender(MapWithAICategory.OTHER,
                openstreetmap, external);
        dataConflationSender.run();
        final DataSet conflated = assertDoesNotThrow((ThrowingSupplier<DataSet>) dataConflationSender::get);
        assertNotNull(conflated);
        assertEquals(1, conflated.allPrimitives().size());
        assertEquals(1, conflated.getNodes().size());
        final Node conflatedNode = conflated.getNodes().iterator().next();
        assertEquals(new LatLon(89, 0.1), conflatedNode.getCoor());
        wireMockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.like(stubMapping.getRequest()));
    }

    @Test
    void testWorkingUrlTimeout(WireMockRuntimeInfo wireMockRuntimeInfo) {
        MapWithAIConflationCategoryMock.url = wireMockRuntimeInfo.getHttpBaseUrl() + "/conflate";
        final StubMapping stubMapping = wireMockRuntimeInfo.getWireMock().register(WireMock.post("/conflate")
                .willReturn(WireMock.aResponse().withBody(
                        "<?xml version='1.0' encoding='UTF-8'?><osm version='0.6' generator='DataConflationSenderTest#testWorkingUrl'><node id='1' version='1' visible='true' lat='89.0' lon='0.1' /></osm>")
                        .withFixedDelay(500)));
        new MapWithAIConflationCategoryMock();

        final DataSet external = new DataSet(new Node(LatLon.NORTH_POLE));
        final DataSet openstreetmap = new DataSet(new Node(LatLon.ZERO));
        final DataConflationSender dataConflationSender = new DataConflationSender(MapWithAICategory.OTHER,
                openstreetmap, external);
        MainApplication.worker.execute(dataConflationSender);
        final DataSet conflated = assertDoesNotThrow(() -> dataConflationSender.get(1, TimeUnit.MILLISECONDS));
        assertNotNull(conflated);
        assertEquals(1, conflated.allPrimitives().size());
        assertEquals(1, conflated.getNodes().size());
        final Node conflatedNode = conflated.getNodes().iterator().next();
        assertEquals(new LatLon(89, 0.1), conflatedNode.getCoor());
        wireMockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.like(stubMapping.getRequest()));
    }

    static Stream<Arguments> testNonWorkingUrl() {
        return Stream.of(Arguments.of(WireMock.noContent()), Arguments.of(WireMock.notFound()),
                Arguments.of(WireMock.forbidden()), Arguments.of(WireMock.serverError()),
                Arguments.of(WireMock.unauthorized()));
    }

    @ParameterizedTest
    @MethodSource
    void testNonWorkingUrl(final ResponseDefinitionBuilder response, final WireMockRuntimeInfo wireMockRuntimeInfo) {
        MapWithAIConflationCategoryMock.url = wireMockRuntimeInfo.getHttpBaseUrl() + "/conflate";
        final StubMapping stubMapping = wireMockRuntimeInfo.getWireMock().register(WireMock.post("/conflate").willReturn(response));
        new MapWithAIConflationCategoryMock();

        final DataSet external = new DataSet(new Node(LatLon.NORTH_POLE));
        final DataSet openstreetmap = new DataSet(new Node(LatLon.ZERO));
        final DataConflationSender dataConflationSender = new DataConflationSender(MapWithAICategory.OTHER,
                openstreetmap, external);
        dataConflationSender.run();
        final DataSet conflated = assertDoesNotThrow((ThrowingSupplier<DataSet>) dataConflationSender::get);
        assertNull(conflated);
        wireMockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.like(stubMapping.getRequest()));
    }
}
