// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.sources.ExtendedSourceEntry;
import org.openstreetmap.josm.data.preferences.sources.MapPaintPrefHelper;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourceType;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.HttpClient.Response;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * @author Taylor Smock
 *
 */
public final class MapWithAIDataUtils {
    public static final int MAXIMUM_SIDE_DIMENSIONS = 1000; // 1 km
    private static ForkJoinPool forkJoinPool;
    static final Object LAYER_LOCK = new Object();

    private static class GetDataRunnable extends RecursiveTask<DataSet> {
        private static final long serialVersionUID = 258423685658089715L;
        private final transient List<BBox> bbox;
        private final transient DataSet dataSet;
        private final transient PleaseWaitProgressMonitor monitor;

        public GetDataRunnable(BBox bbox, DataSet dataSet, PleaseWaitProgressMonitor monitor) {
            this(Arrays.asList(bbox), dataSet, monitor);
        }

        public GetDataRunnable(List<BBox> bbox, DataSet dataSet, PleaseWaitProgressMonitor monitor) {
            super();
            this.bbox = bbox;
            this.dataSet = dataSet;
            this.monitor = monitor;
        }

        private static DataSet getDataReal(BBox bbox) {
            InputStream inputStream = null;
            final DataSet dataSet = new DataSet();
            String urlString = MapWithAIPreferenceHelper.getMapWithAIUrl();
            if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
                urlString += "&crop_bbox={crop_bbox}";
            }

            dataSet.setUploadPolicy(UploadPolicy.DISCOURAGED);

            try {
                final URL url = new URL(urlString.replace("{bbox}", bbox.toStringCSV(",")).replace("{crop_bbox}",
                        DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(",")));
                final HttpClient client = HttpClient.create(url);
                final StringBuilder defaultUserAgent = new StringBuilder();
                defaultUserAgent.append(client.getHeaders().get("User-Agent"));
                if (defaultUserAgent.toString().trim().length() == 0) {
                    defaultUserAgent.append("JOSM");
                }
                defaultUserAgent.append(tr("/ {0} {1}", MapWithAIPlugin.NAME, MapWithAIPlugin.getVersionInfo()));
                client.setHeader("User-Agent", defaultUserAgent.toString());
                Logging.debug("{0}: Getting {1}", MapWithAIPlugin.NAME, client.getURL().toString());
                final Response response = client.connect();
                inputStream = response.getContent();
                final DataSet mergeData = OsmReader.parseDataSet(inputStream, null);
                dataSet.mergeFrom(mergeData);
                response.disconnect();
            } catch (UnsupportedOperationException | IllegalDataException | IOException e) {
                Logging.debug(e);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (final IOException e) {
                        Logging.debug(e);
                    }
                }
                dataSet.setUploadPolicy(UploadPolicy.BLOCKED);
            }
            return dataSet;
        }

        @Override
        public DataSet compute() {
            final List<BBox> bboxes = reduceBBoxSize(bbox);
            if (bboxes.size() == 1) {
                final DataSet temporaryDataSet = getDataReal(bboxes.get(0));
                synchronized (MapWithAIDataUtils.GetDataRunnable.class) {
                    dataSet.mergeFrom(temporaryDataSet);
                }
            } else {
                final Collection<GetDataRunnable> tasks = bboxes.parallelStream()
                        .map(tBbox -> new GetDataRunnable(tBbox, dataSet, null)).collect(Collectors.toList());
                tasks.forEach(GetDataRunnable::fork);
                tasks.forEach(GetDataRunnable::join);
            }
            if (Objects.nonNull(monitor)) {
                monitor.finishTask();
                monitor.close();
            }

            /* Microsoft buildings don't have a source, so we add one */
            MapWithAIDataUtils.addSourceTags(dataSet, "building", "Microsoft");
            return dataSet;
        }
    }

    private MapWithAIDataUtils() {
        // Hide the constructor
    }

    /**
     * Add a paintstyle from the jar (TODO)
     */
    public static void addMapWithAIPaintStyles() {
        // TODO figure out how to use the one in the jar file
        final ExtendedSourceEntry mapWithAI = new ExtendedSourceEntry(SourceType.MAP_PAINT_STYLE, "mapwithai.mapcss",
                "https://gitlab.com/gokaart/JOSM_MapWithAI/raw/master/src/resources/styles/standard/mapwithai.mapcss");
        final List<SourceEntry> paintStyles = MapPaintPrefHelper.INSTANCE.get();
        for (final SourceEntry paintStyle : paintStyles) {
            if (mapWithAI.url.equals(paintStyle.url)) {
                return;
            }
        }
        paintStyles.add(mapWithAI);
        MapPaintPrefHelper.INSTANCE.put(paintStyles);
    }

    /**
     * Add primitives and their children to a collection
     *
     * @param collection A collection to add the primitives to
     * @param primitives The primitives to add to the collection
     */
    public static void addPrimitivesToCollection(Collection<OsmPrimitive> collection,
            Collection<OsmPrimitive> primitives) {
        final Collection<OsmPrimitive> temporaryCollection = new TreeSet<>();
        for (final OsmPrimitive primitive : primitives) {
            if (primitive instanceof Way) {
                temporaryCollection.addAll(((Way) primitive).getNodes());
            } else if (primitive instanceof Relation) {
                addPrimitivesToCollection(temporaryCollection, ((Relation) primitive).getMemberPrimitives());
            }
            temporaryCollection.add(primitive);
        }
        collection.addAll(temporaryCollection);
    }

    /**
     * Add specified source tags to objects without a source tag that also have a
     * specific key
     *
     * @param dataSet    The {#link DataSet} to look through
     * @param primaryKey The primary key that must be in the {@link OsmPrimitive}
     * @param source     The specified source value (not tag)
     */
    public static void addSourceTags(DataSet dataSet, String primaryKey, String source) {
        dataSet.allPrimitives().stream().filter(p -> p.hasKey(primaryKey) && !p.hasKey("source")).forEach(p -> {
            p.put("source", source);
            p.save();
        });
    }

    /**
     * Get a dataset from the API servers using a bbox
     *
     * @param bbox The bbox from which to get data
     * @return A DataSet with data inside the bbox
     */
    public static DataSet getData(BBox bbox) {
        return getData(Arrays.asList(bbox));
    }

    /**
     * Get a dataset from the API servers using a list bboxes
     *
     * @param bbox The bboxes from which to get data
     * @return A DataSet with data inside the bboxes
     */
    public static DataSet getData(List<BBox> bbox) {
        final DataSet dataSet = new DataSet();
        final List<BBox> realBBoxes = bbox.stream().filter(BBox::isValid).collect(Collectors.toList());
        final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor();
        try {
            EventQueue.invokeAndWait(() -> {
                monitor.setCancelable(Boolean.FALSE);
                monitor.beginTask(tr("Downloading {0} data", MapWithAIPlugin.NAME));
                monitor.indeterminateSubTask(null);
            });
        } catch (InvocationTargetException e) {
            Logging.debug(e);
        } catch (InterruptedException e) {
            Logging.debug(e);
            Thread.currentThread().interrupt();
        }
        getForkJoinPool().invoke(new GetDataRunnable(realBBoxes, dataSet, monitor));

        return dataSet;
    }

    /**
     * @return The {@link ForkJoinPool} for MapWithAI use.
     */
    public static ForkJoinPool getForkJoinPool() {
        if (Objects.isNull(forkJoinPool) || forkJoinPool.isShutdown()) {
            forkJoinPool = Utils.newForkJoinPool(MapWithAIPlugin.NAME.concat(".forkjoinpoolthreads"),
                    MapWithAIPlugin.NAME, Thread.NORM_PRIORITY);
        }
        return forkJoinPool;
    }

    public static double getHeight(BBox bbox) {
        final LatLon bottomRight = bbox.getBottomRight();
        final LatLon topLeft = bbox.getTopLeft();
        final double minx = topLeft.getX();
        final double miny = bottomRight.getY();
        final LatLon bottomLeft = new LatLon(miny, minx);
        // TODO handle poles
        return topLeft.greatCircleDistance(bottomLeft);
    }

    /**
     * Get the first {@link MapWithAILayer} that we can find.
     *
     * @param create true if we want to create a new layer
     * @return A MapWithAILayer, or a new MapWithAILayer if none exist. May return
     *         {@code null} if {@code create} is {@code false}.
     */
    public static MapWithAILayer getLayer(boolean create) {
        final List<MapWithAILayer> mapWithAILayers = MainApplication.getLayerManager()
                .getLayersOfType(MapWithAILayer.class);
        MapWithAILayer layer = null;
        synchronized (LAYER_LOCK) {
            if (mapWithAILayers.isEmpty() && create) {
                layer = new MapWithAILayer(new DataSet(), MapWithAIPlugin.NAME, null);
                MainApplication.getLayerManager().addLayer(layer);
            } else if (!mapWithAILayers.isEmpty()) {
                layer = mapWithAILayers.get(0);
            }
        }
        return layer;
    }

    /**
     * Get data for a {@link MapWithAILayer}
     *
     * @param layer The {@link MapWithAILayer} to add data to
     */
    public static void getMapWithAIData(MapWithAILayer layer) {
        final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
        for (final OsmDataLayer osmLayer : osmLayers) {
            if (!osmLayer.isLocked()) {
                getMapWithAIData(layer, osmLayer);
            }
        }
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer  A pre-existing {@link MapWithAILayer}
     * @param bboxes The bboxes to get the data in
     */
    public static void getMapWithAIData(MapWithAILayer layer, BBox... bboxes) {
        getMapWithAIData(layer, Arrays.asList(bboxes));
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer  A pre-existing {@link MapWithAILayer}
     * @param bboxes The bboxes to get the data in
     */
    public static void getMapWithAIData(MapWithAILayer layer, Collection<BBox> bboxes) {
        final DataSet mapWithAISet = layer.getDataSet();
        final List<BBox> mapWithAIBounds = mapWithAISet.getDataSourceBounds().stream().map(Bounds::toBBox)
                .collect(Collectors.toList());
        final List<BBox> editSetBBoxes = bboxes.stream()
                .filter(bbox -> mapWithAIBounds.stream().noneMatch(tBBox -> tBBox.bounds(bbox)))
                .collect(Collectors.toList());
        getForkJoinPool().execute(() -> {
            layer.getDataSet().clear();
            final DataSet newData = getData(editSetBBoxes);
            final Lock lock = layer.getLock();
            lock.lock();
            try {
                layer.mergeFrom(newData);
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer    A pre-existing {@link MapWithAILayer}
     * @param osmLayer The osm datalayer with a set of bounds
     */
    public static void getMapWithAIData(MapWithAILayer layer, OsmDataLayer osmLayer) {
        getMapWithAIData(layer,
                osmLayer.getDataSet().getDataSourceBounds().stream().map(Bounds::toBBox).collect(Collectors.toList()));
    }

    public static double getWidth(BBox bbox) {
        // Lat is y, Lon is x
        final LatLon bottomRight = bbox.getBottomRight();
        final LatLon topLeft = bbox.getTopLeft();
        final double maxx = bottomRight.getX();
        final double minx = topLeft.getX();
        final double miny = bottomRight.getY();
        final double maxy = topLeft.getY();
        final LatLon bottomLeft = new LatLon(miny, minx);
        final LatLon topRight = new LatLon(maxy, maxx);
        // TODO handle meridian
        return Math.max(bottomRight.greatCircleDistance(bottomLeft), topRight.greatCircleDistance(topLeft));
    }

    /**
     * @param bbox The bbox to reduce to a set maximum dimension
     * @return A list of BBoxes that have a dimension no more than
     *         {@link MAXIMUM_SIDE_DIMENSIONS}
     */
    public static List<BBox> reduceBBoxSize(BBox bbox) {
        final List<BBox> returnBounds = new ArrayList<>();
        final double width = getWidth(bbox);
        final double height = getHeight(bbox);
        final Double widthDivisions = width / MAXIMUM_SIDE_DIMENSIONS;
        final Double heightDivisions = height / MAXIMUM_SIDE_DIMENSIONS;
        final int widthSplits = widthDivisions.intValue() + (widthDivisions - widthDivisions.intValue() > 0 ? 1 : 0);
        final int heightSplits = heightDivisions.intValue()
                + (heightDivisions - heightDivisions.intValue() > 0 ? 1 : 0);

        final double newMinWidths = Math.abs(bbox.getTopLeftLon() - bbox.getBottomRightLon()) / widthSplits;
        final double newMinHeights = Math.abs(bbox.getBottomRightLat() - bbox.getTopLeftLat()) / heightSplits;

        final double minx = bbox.getTopLeftLon();
        final double miny = bbox.getBottomRightLat();
        for (int x = 1; x <= widthSplits; x++) {
            for (int y = 1; y <= heightSplits; y++) {
                final LatLon lowerLeft = new LatLon(miny + newMinHeights * (y - 1), minx + newMinWidths * (x - 1));
                final LatLon upperRight = new LatLon(miny + newMinHeights * y, minx + newMinWidths * x);
                returnBounds.add(new BBox(lowerLeft, upperRight));
            }
        }
        return returnBounds;
    }

    /**
     * @param bboxes The bboxes to reduce to a set maximum dimension
     * @return A list of BBoxes that have a dimension no more than
     *         {@link MAXIMUM_SIDE_DIMENSIONS}
     */
    public static List<BBox> reduceBBoxSize(List<BBox> bboxes) {
        final List<BBox> returnBBoxes = new ArrayList<>();
        bboxes.forEach(bbox -> returnBBoxes.addAll(reduceBBoxSize(bbox)));
        return returnBBoxes;
    }

    /**
     * Remove primitives and their children from a dataset.
     *
     * @param primitives The primitives to remove
     */
    public static void removePrimitivesFromDataSet(Collection<OsmPrimitive> primitives) {
        for (final OsmPrimitive primitive : primitives) {
            if (primitive instanceof Relation) {
                removePrimitivesFromDataSet(((Relation) primitive).getMemberPrimitives());
            } else if (primitive instanceof Way) {
                for (final Node node : ((Way) primitive).getNodes()) {
                    final DataSet ds = node.getDataSet();
                    if (ds != null) {
                        ds.removePrimitive(node);
                    }
                }
            }
            final DataSet ds = primitive.getDataSet();
            if (ds != null) {
                ds.removePrimitive(primitive);
            }
        }
    }

    /**
     * @return The number of objects added from the MapWithAI data layer
     */
    public static Long getAddedObjects() {
        return UndoRedoHandler.getInstance().getUndoCommands().parallelStream()
                .filter(MapWithAIAddCommand.class::isInstance).map(MapWithAIAddCommand.class::cast)
                .mapToLong(MapWithAIAddCommand::getAddedObjects).sum();
    }
}
