// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmApiException;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;
import org.xml.sax.SAXException;

/**
 * Download data from MapWithAI
 */
public class DownloadMapWithAITask extends DownloadOsmTask {
    private final List<MapWithAIInfo> urls;

    /**
     * Show common notifications/issues
     */
    private static final class Notifications {
        private Notifications() {
            // Hide constructor
        }

        /**
         * Show a notification that no data sources that are enabled have data in the
         * area.
         */
        public static void showEmptyNotification() {
            GuiHelper.runInEDT(Notifications::realShowEmptyNotification);
        }

        private static void realShowEmptyNotification() {
            final var n = new Notification();
            n.setIcon(ImageProvider.get("mapwithai"));
            n.setContent(tr("No enabled data sources have potential data in this area"));
            n.show();
        }

        /**
         * Show a notification that no MapWithAI layers are enabled.
         */
        public static void showNoLayerNotification() {
            GuiHelper.runInEDT(Notifications::realShowNoLayerNotification);
        }

        private static void realShowNoLayerNotification() {
            final var n = new Notification();
            n.setIcon(ImageProvider.get("mapwithai"));
            n.setContent(tr("No MapWithAI layers were selected. Please select at least one."));
            GuiHelper.runInEDT(n::show);
        }
    }

    /**
     * Create a new download task
     */
    public DownloadMapWithAITask() {
        urls = MapWithAILayerInfo.getInstance().getLayers();
        MapWithAILayerInfo.getInstance().save(); // Save preferences between downloads.
    }

    @Override
    public Future<?> download(OsmServerReader reader, DownloadParams settings, Bounds downloadArea,
            ProgressMonitor progressMonitor) {
        if (!urls.isEmpty()) {
            final var task = new DownloadTask(settings, tr("MapWithAI Download"), progressMonitor, false, false,
                    downloadArea);
            return MainApplication.worker.submit(task);
        }
        Notifications.showNoLayerNotification();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getConfirmationMessage(URL url) {
        if (url != null) {
            final var items = new ArrayList<>();
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
        List<ForkJoinTask<DataSet>> downloader;
        final Bounds bounds;
        private List<MapWithAIInfo> relevantUrls;

        public DownloadTask(DownloadParams settings, String title, ProgressMonitor progressMonitor,
                boolean ignoreException, boolean zoomAfterDownload, Bounds bounds) {
            super(settings, title, progressMonitor, ignoreException, zoomAfterDownload);
            this.bounds = bounds;
        }

        @Override
        protected void cancel() {
            setCanceled(true);
            if (downloader != null) {
                downloader.forEach(task -> task.cancel(true));
            }
        }

        @Override
        protected void realRun() throws SAXException, IOException, OsmTransferException {
            relevantUrls = urls.stream().filter(i -> i.getBounds() == null || i.getBounds().intersects(bounds))
                    .collect(Collectors.toList());
            final var monitor = getProgressMonitor();
            if (relevantUrls.isEmpty()) {
                Notifications.showEmptyNotification();
                return;
            }
            if (relevantUrls.size() < 5) {
                monitor.indeterminateSubTask(tr("MapWithAI Download"));
            } else {
                monitor.setTicksCount(relevantUrls.size());
            }
            downloadedData = new DataSet();
            final var pool = MapWithAIDataUtils.getForkJoinPool();
            this.downloader = new ArrayList<>(relevantUrls.size());
            for (MapWithAIInfo info : relevantUrls) {
                if (isCanceled()) {
                    break;
                }
                this.downloader.add(pool.submit(MapWithAIDataUtils.download(this.progressMonitor, bounds, info,
                        MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS)));
            }
            for (var task : this.downloader) {
                try {
                    DownloadMapWithAITask.this.downloadedData.mergeFrom(task.get(),
                            monitor.createSubTaskMonitor(1, false));
                } catch (CancellationException e) {
                    Logging.trace(e);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    this.downloader.forEach(t -> t.cancel(true));
                    throw new IOException(e);
                } catch (ExecutionException e) {
                    // Throw the "original" exception type, if at all possible.
                    Throwable current = e;
                    while (current.getCause() != null && !current.equals(current.getCause())) {
                        current = current.getCause();
                    }
                    if (current instanceof OsmApiException ex) {
                        final var here = new OsmApiException(ex.getResponseCode(), ex.getErrorHeader(),
                                ex.getErrorBody(), ex.getAccessedUrl(), ex.getLogin(), ex.getContentType());
                        here.initCause(e);
                        here.setUrl(here.getAccessedUrl());
                        throw here;
                    }
                    if (current instanceof OsmTransferException) {
                        throw new OsmTransferException(current.getMessage(), e);
                    }
                    throw new IOException(e);
                }
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
