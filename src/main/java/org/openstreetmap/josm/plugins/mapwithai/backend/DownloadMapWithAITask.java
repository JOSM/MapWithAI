// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.io.UpdatePrimitivesTask;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.tools.Utils;

public class DownloadMapWithAITask extends DownloadOsmTask {
    @Override
    public Future<?> download(OsmServerReader reader, DownloadParams settings, Bounds downloadArea,
            ProgressMonitor progressMonitor) {
        return download(new MapWithAIDownloadTask(settings, reader, progressMonitor, zoomAfterDownload), downloadArea);
    }

    @Override
    public String getConfirmationMessage(URL url) {
        if (url != null) {
            Collection<String> items = new ArrayList<>();
            items.add(tr("OSM Server URL:") + ' ' + url.getHost());
            items.add(tr("Command") + ": " + url.getPath());
            if (url.getQuery() != null) {
                items.add(tr("Request details: {0}", url.getQuery().replaceAll(",\\s*", ", ")));
            }
            return Utils.joinAsHtmlUnorderedList(items);
        }
        return null;
    }

    protected class MapWithAIDownloadTask extends DownloadOsmTask.DownloadTask {

        /**
         * Constructs a new {@code DownloadTask}.
         *
         * @param settings        download settings
         * @param reader          OSM data reader
         * @param progressMonitor progress monitor
         */
        public MapWithAIDownloadTask(DownloadParams settings, OsmServerReader reader, ProgressMonitor progressMonitor) {
            this(settings, reader, progressMonitor, true);
        }

        /**
         * Constructs a new {@code DownloadTask}.
         *
         * @param settings          download settings
         * @param reader            OSM data reader
         * @param progressMonitor   progress monitor
         * @param zoomAfterDownload If true, the map view will zoom to download area
         *                          after download
         */
        public MapWithAIDownloadTask(DownloadParams settings, OsmServerReader reader, ProgressMonitor progressMonitor,
                boolean zoomAfterDownload) {
            super(settings, reader, progressMonitor, zoomAfterDownload);
        }

        @Override
        protected void loadData(String newLayerName, Bounds bounds) {
            MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
            Collection<OsmPrimitive> primitivesToUpdate = searchPrimitivesToUpdate(bounds, layer.getDataSet());
            layer.mergeFrom(dataSet);
            MapFrame map = MainApplication.getMap();
            if (map != null && (zoomAfterDownload
                    || MainApplication.getLayerManager().getLayers().parallelStream().allMatch(layer::equals))) {
                computeBbox(bounds).map(ViewportData::new).ifPresent(map.mapView::zoomTo);
            }
            if (!primitivesToUpdate.isEmpty()) {
                MainApplication.worker.execute(new UpdatePrimitivesTask(layer, primitivesToUpdate));
            }
            layer.onPostDownloadFromServer(bounds);
        }

    }

}
