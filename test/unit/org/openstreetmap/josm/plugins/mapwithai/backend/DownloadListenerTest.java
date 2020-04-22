// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class DownloadListenerTest {
    @Rule
    public JOSMTestRules rule = new MapWithAITestRules().sources().wiremock().preferences().projection();

    @Test
    public void testDataSourceChange()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        DataSet ds = new DataSet();
        DownloadListener listener = new DownloadListener(ds);
        Bounds bounds = MapWithAIDataUtilsTest.getTestBounds();
        MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
        Awaitility.await().atMost(Durations.ONE_SECOND)
                .until(() -> MainApplication.getLayerManager().containsLayer(layer));
        // Test when MapWithAI layer isn't continuous downloading
        ds.addDataSource(new DataSource(bounds, "Test bounds"));
        listener.dataSourceChange(null);
        Awaitility.await().atLeast(Durations.ONE_HUNDRED_MILLISECONDS).atMost(Durations.ONE_SECOND)
                .until(() -> layer.getDataSet().getDataSources().isEmpty());
        assertTrue(layer.getDataSet().getDataSourceBounds().isEmpty());

        MapWithAILayer.ContinuousDownloadAction continuousDownload = new MapWithAILayer.ContinuousDownloadAction(layer);
        continuousDownload.actionPerformed(null);

        // Test when MapWithAI layer isn't continuous downloading
        ds.addDataSource(new DataSource(bounds, "Test bounds 2"));

        layer.getDataSet().isEmpty();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> !layer.getDataSet().isEmpty());
        assertFalse(layer.getDataSet().isEmpty());

        MainApplication.getLayerManager().removeLayer(layer);

        Field listenerDs = DownloadListener.class.getDeclaredField("ds");
        listenerDs.setAccessible(true);
        @SuppressWarnings("unchecked")
        WeakReference<DataSet> lds = (WeakReference<DataSet>) listenerDs.get(listener);
        assertNotNull(lds.get());
        ds.addDataSource(new DataSource(bounds, "Test bounds 3"));
        assertNull(lds.get());
    }

    @Test
    public void testDestroy()
            throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        DataSet ds = new DataSet();
        DownloadListener listener = new DownloadListener(ds);
        Field listenerDs = DownloadListener.class.getDeclaredField("ds");
        listenerDs.setAccessible(true);

        @SuppressWarnings("unchecked")
        WeakReference<DataSet> lds = (WeakReference<DataSet>) listenerDs.get(listener);
        assertNotNull(lds.get());
        listener.destroy();
        assertNull(lds.get());
        listener.destroy();
        assertNull(lds.get());
    }

    @Test
    public void testDestroyAll()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
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
