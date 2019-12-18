// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;

public class DownloadMapWithAITaskTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules().preferences().fakeAPI().projection();
    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAIPreferenceHelper.setMapWithAIURLs(MapWithAIPreferenceHelper.getMapWithAIURLs().stream().map(map -> {
            map.put("url", GetDataRunnableTest.getDefaultMapWithAIAPIForTest(wireMock,
                    map.getOrDefault("url", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)));
            return map;
        }).collect(Collectors.toList()));
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

    @Test
    public void testDownloadOsmServerReaderDownloadParamsBoundsProgressMonitor()
            throws InterruptedException, ExecutionException {
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        Future<?> future = task.download(
                new BoundingBoxMapWithAIDownloader(MapWithAIDataUtilsTest.getTestBounds(),
                        MapWithAIPreferenceHelper.getMapWithAIUrl().get(0).get("url"), false),
                new DownloadParams(), MapWithAIDataUtilsTest.getTestBounds(), NullProgressMonitor.INSTANCE);
        future.get();
        assertNotNull(task.getDownloadedData(), "Data should be downloaded");
    }

    @Test
    public void testGetConfirmationMessage() throws MalformedURLException {
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        assertAll(
                () -> assertTrue(task.getConfirmationMessage(new URL("https://fake.api")).contains("fake.api"),
                        "We should get a confirmation message"),
                () -> assertNull(task.getConfirmationMessage(null), "The message should be null if the URL is null"));
    }

}
