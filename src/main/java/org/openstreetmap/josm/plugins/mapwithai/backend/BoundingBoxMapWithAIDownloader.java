// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.InputStream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.BoundingBoxDownloader;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.HttpClient;

public class BoundingBoxMapWithAIDownloader extends BoundingBoxDownloader {
    private final String url;
    private final boolean crop;

    private final Bounds downloadArea;

    private static final int DEFAULT_TIMEOUT = 50_000; // 50 seconds

    public BoundingBoxMapWithAIDownloader(Bounds downloadArea, String url, boolean crop) {
        super(downloadArea);
        this.url = url;
        this.crop = crop;
        this.downloadArea = downloadArea;
    }

    @Override
    protected String getRequestForBbox(double lon1, double lat1, double lon2, double lat2) {
        return url.replace("{bbox}", Double.toString(lon1) + ',' + lat1 + ',' + lon2 + ',' + lat2)
                + (crop ? "&crop_bbox=" + DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(",") : "");
    }

    @Override
    protected DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        DataSet ds;
        synchronized (BoundingBoxMapWithAIDownloader.class) {
            ds = OsmReaderCustom.parseDataSet(source, progressMonitor, true);
        }
        GetDataRunnable.addMapWithAISourceTag(ds, MapWithAIPreferenceHelper.getMapWithAIUrl().stream()
                .filter(map -> map.getOrDefault("url", "no-url").equals(url))
                .map(map -> map.getOrDefault("source", MapWithAIPlugin.NAME)).findFirst().orElse(MapWithAIPlugin.NAME));
        GetDataRunnable.cleanup(ds, downloadArea);
        return ds;
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
