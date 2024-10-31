// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JOptionPane;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.vectortile.mapbox.MVTTile;
import org.openstreetmap.josm.data.imagery.vectortile.mapbox.MapboxVectorTileSource;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.vector.VectorNode;
import org.openstreetmap.josm.data.vector.VectorPrimitive;
import org.openstreetmap.josm.data.vector.VectorRelation;
import org.openstreetmap.josm.data.vector.VectorRelationMember;
import org.openstreetmap.josm.data.vector.VectorWay;
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
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.plugins.pmtiles.data.imagery.PMTilesImageryInfo;
import org.openstreetmap.josm.plugins.pmtiles.gui.layers.PMTilesImageSource;
import org.openstreetmap.josm.plugins.pmtiles.lib.DirectoryCache;
import org.openstreetmap.josm.plugins.pmtiles.lib.Header;
import org.openstreetmap.josm.plugins.pmtiles.lib.PMTiles;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

/**
 * A bounding box downloader for MapWithAI
 *
 * @author Taylor Smock
 */
public class BoundingBoxMapWithAIDownloader extends BoundingBoxDownloader {
    private final String url;
    private final boolean crop;
    private final int start;

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
        this(downloadArea, info, crop, 0);
    }

    /**
     * Create a new {@link BoundingBoxMapWithAIDownloader} object
     *
     * @param downloadArea The area to download
     * @param info         The info to use to get the url to download
     * @param crop         Whether or not to crop the download area
     * @param start        The number of objects to skip (Esri only please)
     */
    private BoundingBoxMapWithAIDownloader(Bounds downloadArea, MapWithAIInfo info, boolean crop, int start) {
        super(downloadArea);
        this.info = info;
        this.url = info.getUrlExpanded();
        this.crop = crop;
        this.downloadArea = downloadArea;
        this.start = start;
    }

    @Override
    protected String getRequestForBbox(double lon1, double lat1, double lon2, double lat2) {
        if (url.contains("{x}") && url.contains("{y}") && url.contains("{z}")) {
            final var tile = TileXYZ.tileFromBBox(lon1, lat1, lon2, lat2);
            return getRequestForTile(tile);
        }
        var current = url.replace("{bbox}", Double.toString(lon1) + ',' + lat1 + ',' + lon2 + ',' + lat2)
                .replace("{xmin}", Double.toString(lon1)).replace("{ymin}", Double.toString(lat1))
                .replace("{xmax}", Double.toString(lon2)).replace("{ymax}", Double.toString(lat2));
        boolean hasQuery = !Optional.ofNullable(URI.create(current).getRawQuery()).map(String::isEmpty).orElse(true);

        if (crop) {
            current += (hasQuery ? '&' : '?') + "crop_bbox="
                    + DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox().toStringCSV(",");
            hasQuery = true;
        }
        if (this.info.getSourceType() == MapWithAIType.ESRI_FEATURE_SERVER && !this.info.isConflated()) {
            current += (hasQuery ? '&' : '?') + "resultOffset=" + this.start;
        }
        return current;
    }

    private String getRequestForTile(TileXYZ tile) {
        return url.replace("{x}", Long.toString(tile.x())).replace("{y}", Long.toString(tile.y())).replace("{z}",
                Long.toString(tile.z()));
    }

    @Override
    public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {
        long startTime = System.nanoTime();
        try {
            var externalData = super.parseOsm(progressMonitor);
            // Don't call conflate code unnecessarily
            if ((this.info.getSourceType() != MapWithAIType.ESRI_FEATURE_SERVER || this.start == 0)
                    && Boolean.TRUE.equals(MapWithAIInfo.THIRD_PARTY_CONFLATE.get()) && !this.info.isConflated()
                    && !MapWithAIConflationCategory.conflationUrlFor(this.info.getCategory()).isEmpty()) {
                if (externalData.getDataSourceBounds().isEmpty()) {
                    externalData.addDataSource(new DataSource(this.downloadArea, "External Data"));
                }
                final var toConflate = getConflationData(this.downloadArea);
                dcs = new DataConflationSender(this.info.getCategory(), toConflate, externalData);
                dcs.run();
                try {
                    final var conflatedData = dcs.get(30, TimeUnit.SECONDS);
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
                final var note = new Notification();
                GuiHelper.runInEDT(() -> note.setContent(tr(
                        "Attempting to download data in the background. This may fail or succeed in a few minutes.")));
                GuiHelper.runInEDT(note::show);
            } else if (e.getCause() instanceof IllegalDataException) {
                final Instant lastUpdated;
                final var now = Instant.now();
                synchronized (BoundingBoxMapWithAIDownloader.class) {
                    lastUpdated = Instant.ofEpochSecond(Config.getPref().getLong("mapwithai.layerinfo.lastupdated", 0));
                    Config.getPref().putLong("mapwithai.layerinfo.lastupdated", now.getEpochSecond());
                }
                // Only force an update if the last update time is sufficiently old.
                if (now.toEpochMilli() - lastUpdated.toEpochMilli() > TimeUnit.MINUTES.toMillis(10)) {
                    MapWithAILayerInfo.getInstance().loadDefaults(true, MapWithAIDataUtils.getForkJoinPool(), false,
                            () -> GuiHelper.runInEDT(() -> {
                                final var notification = new Notification(tr(
                                        "MapWithAI layers reloaded. Removing and re-adding the MapWithAI layer may be necessary."));
                                notification.setIcon(JOptionPane.INFORMATION_MESSAGE);
                                notification.setDuration(Notification.TIME_LONG);
                                notification.show();
                            }));
                }
            } else {
                throw e;
            }
        }
        // Just in case something happens, try again...
        final var ds = new DataSet();
        final var runnable = new GetDataRunnable(downloadArea, ds, NullProgressMonitor.INSTANCE);
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
        final var area = DataSource.getDataSourceArea(Collections.singleton(new DataSource(bound, "")));
        if (area != null) {
            final var layers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream().filter(
                    l -> l.getDataSet().getDataSourceBounds().stream().anyMatch(b -> area.contains(bound.asRect())))
                    .toList();
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
        final var contentType = this.activeConnection.getResponse().getContentType();
        if (this.info.getSourceType() == MapWithAIType.PMTILES
                || this.info.getSourceType() == MapWithAIType.MAPBOX_VECTOR_TILE) {
            ds = readMvt(source, progressMonitor);
        } else if (Arrays.asList("text/json", "application/json", "application/geo+json").contains(contentType)
                // Fall back to Esri Feature Server check. They don't always indicate a json
                // return type. :(
                || (this.info.getSourceType() == MapWithAIType.ESRI_FEATURE_SERVER && !this.info.isConflated())) {
            ds = readJson(source, progressMonitor);
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

    private DataSet readMvt(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        final DataSet ds;
        final TileSource tileSource;
        final List<TileXYZ> tiles;
        final Header header;
        final DirectoryCache cachedDirectories;
        if (this.info.getSourceType() == MapWithAIType.PMTILES) {
            try {
                header = PMTiles.readHeader(URI.create(this.url));
                final var root = PMTiles.readRootDirectory(header);
                tiles = TileXYZ.tilesFromBBox(header.maxZoom(), this.downloadArea).toList();
                cachedDirectories = new DirectoryCache(root);
                tileSource = new PMTilesImageSource(new PMTilesImageryInfo(header));
            } catch (IOException e) {
                throw new IllegalDataException(e);
            }
        } else {
            header = null;
            cachedDirectories = null;
            // Assume the source is added by the user
            final int zoom;
            if (this.info.getMaxZoom() == 0) {
                var z = 18;
                for (; z > 0; z--) {
                    final var tileOptional = TileXYZ.tilesFromBBox(z, this.downloadArea).findFirst();
                    if (tileOptional.isPresent()) {
                        final var tile = tileOptional.get();
                        try {
                            final var responseHeader = java.net.http.HttpClient.newBuilder()
                                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build()
                                    .send(HttpRequest.newBuilder().uri(URI.create(this.getRequestForTile(tile)))
                                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                                            HttpResponse.BodyHandlers.discarding());
                            if (responseHeader.statusCode() >= 200 && responseHeader.statusCode() < 300) {
                                break;
                            }
                        } catch (IOException e) {
                            Logging.trace(e);
                        } catch (InterruptedException e) {
                            Logging.trace(e);
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                }
                zoom = z;
            } else {
                zoom = this.info.getMaxZoom();
            }
            tiles = TileXYZ.tilesFromBBox(zoom, this.downloadArea).toList();
            tileSource = new MapboxVectorTileSource(new ImageryInfo(this.url, this.url));
        }
        ds = new DataSet();
        final var currentBounds = new Bounds(this.downloadArea);
        progressMonitor.beginTask(tr("Downloading data"), 2 * tiles.size());
        for (TileXYZ tileXYZ : tiles) {
            try {
                final var hilbert = PMTiles.convertToHilbert(tileXYZ.z(), tileXYZ.x(), tileXYZ.y());
                final var data = this.info.getSourceType() == MapWithAIType.PMTILES
                        ? new ByteArrayInputStream(PMTiles.readData(header, hilbert, cachedDirectories))
                        : getInputStream(getRequestForTile(tileXYZ), progressMonitor.createSubTaskMonitor(1, true));
                final var dataSet = loadTile(tileSource, tileXYZ, data);
                ds.mergeFrom(dataSet, progressMonitor.createSubTaskMonitor(1, true));
                tileXYZ.expandBounds(currentBounds);
            } catch (OsmTransferException | IOException e) {
                progressMonitor.finishTask();
                throw new IllegalDataException(e);
            }
        }
        progressMonitor.finishTask();
        ds.addDataSource(new DataSource(currentBounds, this.url));
        return ds;
    }

    private static DataSet loadTile(TileSource tileSource, TileXYZ tileXYZ, InputStream actualSource)
            throws IllegalDataException {

        final var tile = new MVTTile(tileSource, tileXYZ.x(), tileXYZ.y(), tileXYZ.z());
        try {
            tile.loadImage(actualSource);
        } catch (IOException e) {
            throw new IllegalDataException(e);
        }
        final var ds = new DataSet();
        final var primitiveMap = new HashMap<PrimitiveId, OsmPrimitive>(tile.getData().getAllPrimitives().size());
        for (Class<? extends VectorPrimitive> clazz : Arrays.asList(VectorNode.class, VectorWay.class,
                VectorRelation.class)) {
            for (VectorPrimitive p : Utils.filteredCollection(tile.getData().getAllPrimitives(), clazz)) {
                final OsmPrimitive osmPrimitive;
                if (p instanceof VectorNode node) {
                    osmPrimitive = new Node(node.getCoor());
                    osmPrimitive.putAll(node.getKeys());
                } else if (p instanceof VectorWay way) {
                    final var tWay = new Way();
                    for (VectorNode node : way.getNodes()) {
                        tWay.addNode((Node) primitiveMap.get(node));
                    }
                    tWay.putAll(way.getKeys());
                    osmPrimitive = tWay;
                } else if (p instanceof VectorRelation vectorRelation) {
                    final var tRelation = new Relation();
                    for (VectorRelationMember member : vectorRelation.getMembers()) {
                        tRelation.addMember(new RelationMember(member.getRole(), primitiveMap.get(member.getMember())));
                    }
                    tRelation.putAll(vectorRelation.getKeys());
                    osmPrimitive = tRelation;
                } else {
                    throw new IllegalDataException("Unknown vector data type: " + p);
                }
                ds.addPrimitive(osmPrimitive);
                primitiveMap.put(p, osmPrimitive);
            }
        }
        return ds;
    }

    private DataSet readJson(InputStream source, ProgressMonitor progressMonitor) throws IllegalDataException {
        final DataSet ds;
        // Rather unfortunately, we need to read the entire json in order to figure out
        // if we need to make additional calls
        try (var reader = Json.createReader(source)) {
            final var structure = reader.read();
            try (var bais = new ByteArrayInputStream(structure.toString().getBytes(StandardCharsets.UTF_8))) {
                ds = GeoJSONReader.parseDataSet(bais, progressMonitor);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            /* We should only call this from the "root" call */
            if (this.start == 0 && structure.getValueType() == JsonValue.ValueType.OBJECT) {
                final var serverObj = structure.asJsonObject();
                final boolean exceededTransferLimit = serverObj.entrySet().stream()
                        .filter(entry -> "properties".equals(entry.getKey())
                                && entry.getValue().getValueType() == JsonValue.ValueType.OBJECT)
                        .map(Map.Entry::getValue).map(JsonValue::asJsonObject)
                        .map(obj -> obj.getBoolean("exceededTransferLimit", false)).findFirst().orElse(false);
                if (exceededTransferLimit && this.info.getSourceType() == MapWithAIType.ESRI_FEATURE_SERVER) {
                    final int size = serverObj.getJsonArray("features").size();
                    final var other = this.getAdditionalEsriData(progressMonitor,
                            this.getRequestForBbox(this.lon1, this.lat1, this.lon2, this.lat2), size);
                    ds.mergeFrom(other, progressMonitor.createSubTaskMonitor(0, false));
                }
            }
        }
        if (info.getReplacementTags() != null) {
            GetDataRunnable.replaceKeys(ds, info.getReplacementTags());
        }
        return ds;
    }

    private DataSet getAdditionalEsriData(ProgressMonitor progressMonitor, String baseUrl, int size) {
        final var returnDs = new DataSet();
        try {
            final var client = HttpClient.create(new URL(baseUrl + "&returnCountOnly=true"));
            int objects = Integer.MIN_VALUE;
            try (var is = client.connect().getContent(); var parser = Json.createParser(is)) {
                while (parser.hasNext()) {
                    final var event = parser.next();
                    if (event == JsonParser.Event.START_OBJECT) {
                        final var objCount = parser.getObjectStream()
                                .filter(entry -> "properties".equals(entry.getKey()))
                                .map(entry -> entry.getValue().asJsonObject())
                                .mapToInt(properties -> properties.getInt("count")).findFirst();
                        if (objCount.isPresent()) {
                            objects = objCount.getAsInt();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                client.disconnect();
            }
            // Zero indexed. Esri uses 2000 as the limit. 0-1999 is 2000 objects, so we want
            // to start at 2000 for the next round.
            try {
                progressMonitor.beginTask(tr("Downloading additional data"), objects);
                // We have already downloaded some of the objects. Set the ticks.
                progressMonitor.worked(size);
                while (progressMonitor.getTicks() < progressMonitor.getTicksCount() - 1) {
                    final var next = new BoundingBoxMapWithAIDownloader(this.downloadArea, this.info, this.crop,
                            this.start + size).parseOsm(progressMonitor.createSubTaskMonitor(0, false));
                    progressMonitor.worked((int) next.allPrimitives().stream().filter(IPrimitive::isTagged).count());
                    returnDs.mergeFrom(next);
                }
            } catch (OsmTransferException e) {
                throw new JosmRuntimeException(e);
            } finally {
                progressMonitor.finishTask();
            }
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
        return returnDs;
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
        final var defaultUserAgent = new StringBuilder();
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
