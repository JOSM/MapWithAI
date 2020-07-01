// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Utils;
import org.xml.sax.SAXException;

public class DownloadMapWithAITask extends DownloadOsmTask {
    private final List<MapWithAIInfo> urls;

    public DownloadMapWithAITask() {
        urls = MapWithAILayerInfo.getInstance().getLayers();
        MapWithAILayerInfo.getInstance().save(); // Save preferences between downloads.
    }

    @Override
    public Future<?> download(OsmServerReader reader, DownloadParams settings, Bounds downloadArea,
            ProgressMonitor progressMonitor) {
        if (!urls.isEmpty()) {
            DownloadTask task = new DownloadTask(settings, tr("MapWithAI Download"), progressMonitor, false, false,
                    downloadArea);
            return MainApplication.worker.submit(task);
        }
        Notification n = new Notification();
        n.setIcon(ImageProvider.get("mapwithai"));
        n.setContent(tr("No MapWithAI layers were selected. Please select at least one."));
        n.show();
        return CompletableFuture.completedFuture(null);
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

    class DownloadTask extends AbstractInternalTask {
        BoundingBoxMapWithAIDownloader downloader;
        final Bounds bounds;
        private List<MapWithAIInfo> relevantUrls;

        DownloadTask(DownloadParams settings, String title, boolean ignoreException, boolean zoomAfterDownload,
                Bounds bounds) {
            this(settings, title, NullProgressMonitor.INSTANCE, ignoreException, zoomAfterDownload, bounds);
        }

        public DownloadTask(DownloadParams settings, String title, ProgressMonitor progressMonitor,
                boolean ignoreException, boolean zoomAfterDownload, Bounds bounds) {
            super(settings, title, progressMonitor, ignoreException, zoomAfterDownload);
            this.bounds = bounds;
        }

        @Override
        protected void cancel() {
            setCanceled(true);
            if (downloader != null) {
                downloader.cancel();
            }
        }

        @Override
        protected void realRun() throws SAXException, IOException, OsmTransferException {
            relevantUrls = urls.stream().filter(i -> i.getBounds() == null || i.getBounds().intersects(bounds))
                    .collect(Collectors.toList());
            ProgressMonitor monitor = getProgressMonitor();
            if (relevantUrls.size() < 5) {
                monitor.indeterminateSubTask(tr("MapWithAI Download"));
            } else {
                monitor.setTicksCount(relevantUrls.size());
            }
            downloadedData = new DataSet();
            for (MapWithAIInfo info : relevantUrls) {
                if (isCanceled()) {
                    break;
                }
                downloader = new BoundingBoxMapWithAIDownloader(bounds, info, false);
                DataSet ds = downloader.parseOsm(monitor.createSubTaskMonitor(1, true));
                downloadedData.mergeFrom(ds);
            }
        }

        @Override
        protected void finish() {
            if (!isCanceled() && !isFailed()) {
                synchronized (DownloadMapWithAITask.DownloadTask.class) {
                    MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
                    layer.getDataSet().mergeFrom(downloadedData);
                    relevantUrls.forEach(layer::addDownloadedInfo);
                }
                GetDataRunnable.cleanup(MapWithAIDataUtils.getLayer(true).getDataSet(), null, null);
            }
        }

    }
}
