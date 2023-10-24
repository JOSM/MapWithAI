// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.OsmApi;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.Territories;

@BasicPreferences
@MapWithAISources
@NoExceptions
@OsmApi(OsmApi.APIType.FAKE)
@Projection
@Territories
@Wiremock
class DownloadMapWithAITaskTest {

    @Test
    void testDownloadOsmServerReaderDownloadParamsBoundsProgressMonitor()
            throws InterruptedException, ExecutionException {
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        Future<?> future = task.download(
                new BoundingBoxMapWithAIDownloader(MapWithAIDataUtilsTest.getTestBounds(),
                        MapWithAILayerInfo.getInstance().getLayers().get(0), false),
                new DownloadParams(), MapWithAIDataUtilsTest.getTestBounds(), NullProgressMonitor.INSTANCE);
        future.get();
        assertNotNull(task.getDownloadedData(), "Data should be downloaded");
    }

    @Test
    void testGetConfirmationMessage() {
        DownloadMapWithAITask task = new DownloadMapWithAITask();
        assertAll(
                () -> assertTrue(task.getConfirmationMessage(new URL("https://fake.api")).contains("fake.api"),
                        "We should get a confirmation message"),
                () -> assertNull(task.getConfirmationMessage(null), "The message should be null if the URL is null"));
    }

}
