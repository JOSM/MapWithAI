// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;

import java.awt.geom.Area;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.BoundingBoxDownloader;
import org.openstreetmap.josm.io.GeoJSONReader;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmApiException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;

/**
 * A bounding box downloader for MapWithAI
 *
 * @author Taylor Smock
 */
public class BoundingBoxMapWithAIDownloader extends BoundingBoxDownloader {
    private final String url;
    private final boolean crop;

    private static long lastErrorTime;

    private final Bounds downloadArea;
    private final MapWithAIInfo info;
    private DataConflationSender dcs;

    private static final int DEFAULT_TIMEOUT = 50_000; // 50 seconds

    /**
     * Create a new {@link BoundingBoxMapWithAIDownloader} object
     *
     * @param downloadArea The area to download
     * @param info         The info to use to get the url to download
     * @param crop         Whether or not to crop the download area
     */
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
                + (crop ? "&crop_bbox=" + DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox().toStringCSV(",")
                        : "");
    }

    @Override
    public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {
        long startTime = System.nanoTime();
        try {
            DataSet externalData;
            synchronized (BoundingBoxMapWithAIDownloader.class) {
                externalData = super.parseOsm(progressMonitor);
            }
            if (MapWithAIInfo.THIRD_PARTY_CONFLATE.get() && !this.info.isConflated()
                    && !MapWithAIConflationCategory.conflationUrlFor(this.info.getCategory()).isEmpty()) {
                if (externalData.getDataSourceBounds().isEmpty()) {
                    externalData.addDataSource(new DataSource(this.downloadArea, "External Data"));
                }
                DataSet toConflate = getConflationData(this.downloadArea);
                dcs = new DataConflationSender(this.info.getCategory(), toConflate, externalData);
                dcs.run();
                try {
                    DataSet conflatedData = dcs.get(30, TimeUnit.SECONDS);
                    if (conflatedData != null) {
                        externalData = conflatedData;
                    }
                } catch (InterruptedException e) {
                    Logging.error(e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException e) {
                    Logging.error(e);
                }
            }
            MapPaintUtils.addSourcesToPaintStyle(externalData);
            return externalData;
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
        // Just in case something happens, try again...
        DataSet ds = new DataSet();
        GetDataRunnable runnable = new GetDataRunnable(downloadArea, ds, NullProgressMonitor.INSTANCE);
        runnable.setMapWithAIInfo(info);
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

        MapPaintUtils.addSourcesToPaintStyle(ds);
        return ds;
    }

    /**
     * Get data to send to the conflation server
     *
     * @param bound The bounds that we are sending to the server
     * @return The dataset to send to the server
     */
    private static DataSet getConflationData(Bounds bound) {
        Area area = DataSource.getDataSourceArea(Collections.singleton(new DataSource(bound, "")));
        if (area != null) {
            List<OsmDataLayer> layers = MainApplication
                    .getLayerManager().getLayersOfType(OsmDataLayer.class).stream().filter(l -> l.getDataSet()
                            .getDataSourceBounds().stream().anyMatch(b -> area.contains(bound.asRect())))
                    .collect(Collectors.toList());
            return layers.stream().max(Comparator.comparingInt(l -> l.getDataSet().allPrimitives().size()))
                    .map(OsmDataLayer::getDataSet).orElse(null);
        }
        return null;
    }

    private static void updateLastErrorTime(long time) {
        lastErrorTime = time;
    }

    @Override
    protected DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        DataSet ds;
        String contentType = this.activeConnection.getResponse().getContentType();
        if (contentType.contains("text/json") || contentType.contains("application/json")
                || contentType.contains("application/geo+json")
                // Fall back to Esri Feature Server check. They don't always indicate a json
                // return type. :(
                || this.info.getSourceType() == MapWithAIType.ESRI_FEATURE_SERVER) {
            if (source.markSupported()) {
                source.mark(1024);
                try (JsonParser parser = Json.createParser(source)) {
                    while (parser.hasNext()) {
                        JsonParser.Event event = parser.next();
                        if (event == JsonParser.Event.START_OBJECT) {
                            parser.getObjectStream().filter(entry -> "properties".equals(entry.getKey()))
                                    .filter(entry -> entry.getValue().getValueType() == JsonValue.ValueType.OBJECT)
                                    .map(entry -> entry.getValue().asJsonObject())
                                    .filter(value -> value.containsKey("exceededTransferLimit") && value
                                            .get("exceededTransferLimit").getValueType() == JsonValue.ValueType.TRUE)
                                    .findFirst().ifPresent(val -> {
                                        Logging.error("Could not fully download {0}", this.downloadArea);
                                    });
                        }
                    }
                    source.reset();
                } catch (IOException e) {
                    throw new JosmRuntimeException(e);
                }
            }
            ds = GeoJSONReader.parseDataSet(source, progressMonitor);
            if (info.getReplacementTags() != null) {
                GetDataRunnable.replaceKeys(ds, info.getReplacementTags());
            }
        } else {
            // Fall back to XML parsing
            ds = OsmReader.parseDataSet(source, progressMonitor, OsmReader.Options.CONVERT_UNKNOWN_TO_TAGS,
                    OsmReader.Options.SAVE_ORIGINAL_ID);
        }
        if (url != null && info.getUrl() != null && !info.getUrl().trim().isEmpty()) {
            if (info.getSource() != null) {
                GetDataRunnable.addSourceTag(ds, info.getSource());
            } else {
                GetDataRunnable.addMapWithAISourceTag(ds, getMapWithAISourceTag(info));
            }
        }
        GetDataRunnable.cleanup(ds, downloadArea, info);
        return ds;
    }

    private static String getMapWithAISourceTag(MapWithAIInfo info) {
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

    @Override
    public void cancel() {
        super.cancel();
        if (dcs != null) {
            dcs.cancel(true);
        }
    }
}
