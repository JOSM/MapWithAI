// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.BoundingBoxDownloader;
import org.openstreetmap.josm.io.GeoJSONReader;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmApiException;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.OsmReaderCustom;
import org.openstreetmap.josm.tools.HttpClient;

class BoundingBoxMapWithAIDownloader extends BoundingBoxDownloader {
    private final String url;
    private final boolean crop;

    private static long lastErrorTime;

    private final Bounds downloadArea;
    private final MapWithAIInfo info;

    private static final int DEFAULT_TIMEOUT = 50_000; // 50 seconds

    public BoundingBoxMapWithAIDownloader(Bounds downloadArea, MapWithAIInfo info, boolean crop) {
        super(downloadArea);
        this.info = info;
        this.url = info.getUrlExpanded();
        this.crop = crop;
        this.downloadArea = downloadArea;
    }

    @Override
    protected String getRequestForBbox(double lon1, double lat1, double lon2, double lat2) {
        return url.replace("{bbox}", Double.toString(lon1) + ',' + lat1 + ',' + lon2 + ',' + lat2)
                .replace("{xmin}", Double.toString(lon1)).replace("{ymin}", Double.toString(lat1))
                .replace("{xmax}", Double.toString(lon2)).replace("{ymax}", Double.toString(lat2))
                + (crop ? "&crop_bbox=" + DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(",") : "");
    }

    @Override
    public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {
        long startTime = System.nanoTime();
        try {
            return super.parseOsm(progressMonitor);
        } catch (OsmApiException e) {
            if (!(e.getResponseCode() == 504 && (System.nanoTime() - lastErrorTime) < 120_000_000_000L)) {
                throw e;
            }
        } catch (OsmTransferException e) {
            if (e.getCause() instanceof SocketTimeoutException && (System.nanoTime() - startTime) > 30_000_000_000L) {
                updateLastErrorTime(System.nanoTime());
                Notification note = new Notification();
                GuiHelper.runInEDT(() -> note.setContent(tr(
                        "Attempting to download data in the background. This may fail or succeed in a few minutes.")));
                GuiHelper.runInEDT(note::show);
            } else {
                throw e;
            }
        }
        DataSet ds = new DataSet();
        GetDataRunnable runnable = new GetDataRunnable(downloadArea.toBBox(), ds, NullProgressMonitor.INSTANCE);
        MainApplication.worker.execute(() -> {
            try {
                // It seems that the server has issues if I make a request soon
                // after the failing request due to a timeout.
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
            runnable.compute();
        });
        return ds;
    }

    private static void updateLastErrorTime(long time) {
        lastErrorTime = time;
    }

    @Override
    protected DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        DataSet ds;
        String contentType = this.activeConnection.getResponse().getHeaderField("Content-Type");
        if (contentType.contains("text/xml")) {
            ds = OsmReaderCustom.parseDataSet(source, progressMonitor, true);
        } else if (MapWithAIType.ESRI_FEATURE_SERVER == this.info.getSourceType()) {
            ds = GeoJSONReader.parseDataSet(source, progressMonitor);
            if (info.getReplacementTags() != null) {
                GetDataRunnable.replaceKeys(ds, info.getReplacementTags());
            }
        } else {
            // Fall back to XML parsing
            ds = OsmReaderCustom.parseDataSet(source, progressMonitor, true);
        }
        if (url != null && info.getUrl() != null && !info.getUrl().trim().isEmpty()) {
            GetDataRunnable.addMapWithAISourceTag(ds, getSourceTag(info));
        }
        GetDataRunnable.cleanup(ds, downloadArea);
        return ds;
    }

    private static String getSourceTag(MapWithAIInfo info) {
        return info.getName() == null ? MapWithAIPlugin.NAME : info.getName();
    }

    /**
     * Returns the name of the download task to be displayed in the
     * {@link ProgressMonitor}.
     *
     * @return task name
     */
    @Override
    protected String getTaskName() {
        return tr("Contacting {0} Server...", MapWithAIPlugin.NAME);
    }

    @Override
    protected String getBaseUrl() {
        return url;
    }

    @Override
    protected void adaptRequest(HttpClient request) {
        final StringBuilder defaultUserAgent = new StringBuilder();
        request.setReadTimeout(DEFAULT_TIMEOUT);
        defaultUserAgent.append(request.getHeaders().get("User-Agent"));
        if (defaultUserAgent.toString().trim().length() == 0) {
            defaultUserAgent.append("JOSM");
        }
        defaultUserAgent.append(tr("/ {0} {1}", MapWithAIPlugin.NAME, MapWithAIPlugin.getVersionInfo()));
        request.setHeader("User-Agent", defaultUserAgent.toString());
    }
}
