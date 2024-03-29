// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DataSourceChangeEvent;
import org.openstreetmap.josm.data.osm.DataSourceListener;
import org.openstreetmap.josm.data.osm.event.DataSourceAddedEvent;
import org.openstreetmap.josm.tools.Destroyable;

/**
 * This class listens for download events, and then gets new data
 *
 * @author Taylor Smock
 *
 */
public final class DownloadListener implements DataSourceListener, Destroyable {

    final WeakReference<DataSet> ds;
    private static final double BBOX_SIMILARITY_DEGREES = 0.001;
    private static final Collection<DownloadListener> LISTENERS = new HashSet<>();

    /**
     * Listen to downloads in a dataset
     *
     * @param dataSet The dataset to listen to
     */
    public DownloadListener(DataSet dataSet) {
        Objects.requireNonNull(dataSet, "DataSet cannot be null");
        ds = new WeakReference<>(dataSet);
        dataSet.addDataSourceListener(this);
        LISTENERS.add(this);
    }

    @Override
    public void dataSourceChange(DataSourceChangeEvent event) {
        if (event instanceof DataSourceAddedEvent) {
            final var layer = MapWithAIDataUtils.getLayer(false);
            if (layer == null) {
                destroy();
                return;
            }
            if (layer.downloadContinuous()) {
                final var bounds = DataSource.getDataSourceBounds(event.getSource().getDataSources());
                bounds.removeIf(a -> layer.getDataSet().getDataSourceBounds().stream().map(Bounds::toBBox)
                        .anyMatch(b -> b.bboxIsFunctionallyEqual(a.toBBox(), BBOX_SIMILARITY_DEGREES)));
                MapWithAIDataUtils.getMapWithAIData(layer, bounds);

            }
        }
    }

    @Override
    public void destroy() {
        final var realDs = ds.get();
        if (realDs != null) {
            // Should be added, so no exception should be thrown
            realDs.removeDataSourceListener(this);
            ds.clear();
            LISTENERS.remove(this);
        }
    }

    /**
     * Destroy all download listeners for MapWithAI
     */
    public static void destroyAll() {
        new HashSet<>(LISTENERS).forEach(DownloadListener::destroy);
    }
}
