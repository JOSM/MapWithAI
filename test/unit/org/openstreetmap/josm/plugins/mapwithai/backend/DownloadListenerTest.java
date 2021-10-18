// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test class for {@link DownloadListener}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@NoExceptions
@Wiremock
@MapWithAISources
class DownloadListenerTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rule = new MapWithAITestRules().projection().territories();

    @Test
    void testDataSourceChange() {
        DataSet ds = new DataSet();
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(ds, "Test Data", null));
        Bounds bounds = MapWithAIDataUtilsTest.getTestBounds();
        MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);

        MapWithAILayer.ContinuousDownloadAction continuousDownload = new MapWithAILayer.ContinuousDownloadAction(layer);

        // Now defaults to on, so we need to toggle.
        continuousDownload.actionPerformed(null);

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .until(() -> MainApplication.getLayerManager().containsLayer(layer));

        // Test when MapWithAI layer isn't continuous downloading
        ds.addDataSource(new DataSource(bounds, "Test bounds"));
        Awaitility.await().pollDelay(Durations.ONE_HUNDRED_MILLISECONDS).atMost(Durations.ONE_SECOND)
                .until(() -> layer.getDataSet().getDataSources().isEmpty());
        assertTrue(layer.getDataSet().getDataSourceBounds().isEmpty());

        continuousDownload.actionPerformed(null);

        // Test when MapWithAI layer is continuous downloading
        assertTrue(layer.getDataSet().isEmpty());
        ds.addDataSource(new DataSource(bounds, "Test bounds 2"));

        Awaitility.await().atMost(Durations.FIVE_SECONDS)
                .until(() -> MapWithAIDataUtils.getForkJoinPool().isQuiescent());
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> !layer.getDataSet().isEmpty());
        assertFalse(layer.getDataSet().isEmpty());

        MainApplication.getLayerManager().removeLayer(layer);
    }

    @Test
    void testDestroy() {
        DataSet ds = new DataSet();
        DownloadListener listener = new DownloadListener(ds);

        assertNotNull(listener.ds.get());
        listener.destroy();
        assertNull(listener.ds.get());
        listener.destroy();
        assertNull(listener.ds.get());
    }

    @Test
    void testDestroyAll() throws ReflectiveOperationException {
        DataSet ds = new DataSet();
        DownloadListener listener = new DownloadListener(ds);
        Field listenerDs = DownloadListener.class.getDeclaredField("ds");
        listenerDs.setAccessible(true);

        @SuppressWarnings("unchecked")
        WeakReference<DataSet> lds = (WeakReference<DataSet>) listenerDs.get(listener);
        assertNotNull(lds.get());
        DownloadListener.destroyAll();
        assertNull(lds.get());
        DownloadListener.destroyAll();
        assertNull(lds.get());
    }

}
