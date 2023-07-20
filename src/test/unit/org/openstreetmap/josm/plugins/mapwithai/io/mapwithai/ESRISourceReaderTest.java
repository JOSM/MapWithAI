// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.tools.JosmRuntimeException;

import com.github.tomakehurst.wiremock.WireMockServer;

@Projection
@Wiremock
class ESRISourceReaderTest {

    @BasicWiremock
    public WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        ESRISourceReader.SOURCE_CACHE.clear();
    }

    @AfterEach
    void tearDown() {
        this.setUp();
    }

    /**
     * Test that ESRI servers are properly added
     *
     * @throws IOException If there is an issue with reading the network
     *                     file/wiremocked file
     */
    @Test
    void testAddEsriLayer() throws IOException {
        // TODO wiremock
        MapWithAIInfo info = new MapWithAIInfo("TEST", "test_url", "bdf6c800b3ae453b9db239e03d7c1727");
        info.setSourceType(MapWithAIType.ESRI);
        String tUrl = wireMockServer.url("/sharing/rest");
        for (String url : Arrays.asList(tUrl, tUrl + "/")) {
            info.setUrl(url);
            final ESRISourceReader reader = new ESRISourceReader(info);
            List<MapWithAIInfo> layers = reader.parse().stream().map(future -> {
                try {
                    return future.get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new JosmRuntimeException(e);
                }
            }).collect(Collectors.toList());
            Future<?> workerQueue = MainApplication.worker.submit(() -> {
                /* Sync threads */});
            Awaitility.await().atMost(Durations.FIVE_SECONDS).until(workerQueue::isDone);
            assertFalse(layers.isEmpty(), "There should be a MapWithAI layer");
            assertTrue(layers.stream().noneMatch(i -> info.getUrl().equals(i.getUrl())),
                    "The ESRI server should be expanded to feature servers");
            assertTrue(layers.stream().allMatch(i -> MapWithAIType.ESRI_FEATURE_SERVER.equals(i.getSourceType())),
                    "There should only be ESRI feature servers");
            assertEquals(24, layers.size());
        }
    }
}
