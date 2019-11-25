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

public class BoundingBoxMapWithAIDownloader extends BoundingBoxDownloader {
    private final String url;
    private final boolean crop;

    private final Bounds downloadArea;

    public BoundingBoxMapWithAIDownloader(Bounds downloadArea, String url, boolean crop) {
        super(downloadArea);
        this.url = url;
        this.crop = crop;
        this.downloadArea = downloadArea;
    }

    @Override
    protected String getRequestForBbox(double lon1, double lat1, double lon2, double lat2) {
        return url.replace("{bbox}", Double.toString(lon1) + ',' + lat1 + ',' + lon2 + ',' + lat2)
                + (crop ? "crop_bbox=" + lon1 + ',' + lat1 + ',' + lon2 + ',' + lat2 : "");
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
}
