// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
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
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * @author Taylor Smock
 *
 */
public final class MapWithAIDataUtils {
    public static final int MAXIMUM_SIDE_DIMENSIONS = 10_000; // RapiD is about 1km, max is 10km
    private static ForkJoinPool forkJoinPool;
    static final Object LAYER_LOCK = new Object();

    private MapWithAIDataUtils() {
        // Hide the constructor
    }

    /**
     * Add a paintstyle from the jar
     */
    public static void addMapWithAIPaintStyles() {
        // Remove old url's that were automatically added -- remove after Jan 01, 2020
        final List<String> oldUrls = Arrays.asList(
                "https://gitlab.com/gokaart/JOSM_MapWithAI/raw/master/src/resources/styles/standard/mapwithai.mapcss",
                "https://gitlab.com/smocktaylor/rapid/raw/master/src/resources/styles/standard/rapid.mapcss");
        MapPaintStyles.getStyles().getStyleSources().parallelStream().filter(style -> oldUrls.contains(style.url))
        .forEach(MapPaintStyles::removeStyle);

        if (MapPaintStyles.getStyles().getStyleSources().parallelStream()
                .noneMatch(source -> "resource://styles/standard/mapwithai.mapcss".equals(source.url))) {
            final MapCSSStyleSource style = new MapCSSStyleSource("resource://styles/standard/mapwithai.mapcss",
                    MapWithAIPlugin.NAME, "MapWithAI");
            MapPaintStyles.addStyle(style);
        }
    }

    /**
     * Remove MapWithAI paint styles
     */
    public static void removeMapWithAIPaintStyles() {
        MapPaintStyles.getStyles().getStyleSources().parallelStream()
        .filter(source -> "resource://styles/standard/mapwithai.mapcss".equals(source.url))
        .forEach(MapPaintStyles::removeStyle);
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
        final List<BBox> realBBoxes = bbox.stream().filter(BBox::isValid).distinct().collect(Collectors.toList());
        final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor();
        monitor.setCancelable(Boolean.FALSE);
        getForkJoinPool().invoke(new GetDataRunnable(realBBoxes, dataSet, monitor));
        monitor.finishTask();
        monitor.close();

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
     * @return true if data was downloaded
     */
    public static boolean getMapWithAIData(MapWithAILayer layer) {
        final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class)
                .stream().filter(obj -> !MapWithAILayer.class.isInstance(obj)).collect(Collectors.toList());
        boolean gotData = false;
        for (final OsmDataLayer osmLayer : osmLayers) {
            if (!osmLayer.isLocked()) {
                if (getMapWithAIData(layer, osmLayer)) {
                    gotData = true;
                }
            }
        }
        return gotData;
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer    A pre-existing {@link MapWithAILayer}
     * @param osmLayer The osm datalayer with a set of bounds
     * @return true if data was downloaded
     */
    public static boolean getMapWithAIData(MapWithAILayer layer, OsmDataLayer osmLayer) {
        return getMapWithAIData(layer,
                osmLayer.getDataSet().getDataSourceBounds().stream().map(Bounds::toBBox).collect(Collectors.toList()));
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer  A pre-existing {@link MapWithAILayer}
     * @param bboxes The bboxes to get the data in
     * @return true if data was downloaded
     */
    public static boolean getMapWithAIData(MapWithAILayer layer, BBox... bboxes) {
        return getMapWithAIData(layer, Arrays.asList(bboxes));
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer  A pre-existing {@link MapWithAILayer}
     * @param bboxes The bboxes to get the data in
     * @return true if data was downloaded
     */
    public static boolean getMapWithAIData(MapWithAILayer layer, Collection<BBox> bboxes) {
        final DataSet mapWithAISet = layer.getDataSet();
        final List<BBox> mapWithAIBounds = mapWithAISet.getDataSourceBounds().stream().map(Bounds::toBBox)
                .collect(Collectors.toList());
        final List<BBox> editSetBBoxes = bboxes.stream()
                .filter(bbox -> mapWithAIBounds.stream().noneMatch(tBBox -> tBBox.bounds(bbox)))
                .collect(Collectors.toList());
        final List<BBox> toDownload = reduceBBox(mapWithAIBounds, editSetBBoxes);
        if (!toDownload.isEmpty()) {
            getForkJoinPool().execute(() -> {
                final DataSet newData = getData(toDownload);
                final Lock lock = layer.getLock();
                lock.lock();
                try {
                    layer.mergeFrom(newData);
                } finally {
                    lock.unlock();
                }
            });
        }
        return !toDownload.isEmpty();
    }

    private static List<BBox> reduceBBox(List<BBox> alreadyDownloaded, List<BBox> wantToDownload) {
        final List<BBox> alreadyDownloadedReduced = new ArrayList<>(alreadyDownloaded);
        int aDRSize = -1;
        do {
            aDRSize = alreadyDownloadedReduced.size();
            for (int i = 0; i < alreadyDownloadedReduced.size(); i++) {
                final BBox bbox1 = alreadyDownloadedReduced.get(i);
                for (int j = 0; j < alreadyDownloadedReduced.size(); j++) {
                    final BBox bbox2 = alreadyDownloadedReduced.get(j);
                    if (!bbox1.equals(bbox2) && bboxesShareSide(bbox1, bbox2)) {
                        bbox1.add(bbox2);
                        alreadyDownloadedReduced.remove(bbox2);
                    }
                }
            }
        } while (aDRSize != alreadyDownloadedReduced.size());
        for (final BBox bbox : wantToDownload) {
            for (final BBox downloaded : alreadyDownloaded) {
                if (bboxesAreFunctionallyEqual(bbox, downloaded, null)) {
                    Logging.debug("YEP");
                }
            }
        }
        return wantToDownload.parallelStream()
                .filter(bbox1 -> alreadyDownloadedReduced.parallelStream()
                        .noneMatch(bbox2 -> bboxesAreFunctionallyEqual(bbox1, bbox2, 0.000_02)))
                .collect(Collectors.toList());
    }

    // TODO replace with {@link BBox.bboxesAreFunctionallyEqual} when version bumped to >r15483
    private static boolean bboxesAreFunctionallyEqual(BBox bbox1, BBox bbox2, Double maxDifference) {
        final double diff = Optional.ofNullable(maxDifference).orElse(LatLon.MAX_SERVER_PRECISION);
        return (bbox1 != null && bbox2 != null && Math.abs(bbox1.getBottomRightLat() - bbox2.getBottomRightLat()) < diff
                && Math.abs(bbox1.getBottomRightLon() - bbox2.getBottomRightLon()) < diff
                && Math.abs(bbox1.getTopLeftLat() - bbox2.getTopLeftLat()) < diff
                && Math.abs(bbox1.getTopLeftLon() - bbox2.getTopLeftLon()) < diff);
    }

    private static boolean bboxesShareSide(BBox bbox1, BBox bbox2) {
        final List<Double> bbox1Lons = Arrays.asList(bbox1.getTopLeftLon(), bbox1.getBottomRightLon());
        final List<Double> bbox1Lats = Arrays.asList(bbox1.getTopLeftLat(), bbox1.getBottomRightLat());
        final List<Double> bbox2Lons = Arrays.asList(bbox2.getTopLeftLon(), bbox2.getBottomRightLon());
        final List<Double> bbox2Lats = Arrays.asList(bbox2.getTopLeftLat(), bbox2.getBottomRightLat());
        final Long lonDupeCount = bbox1Lons.parallelStream()
                .filter(lon -> bbox2Lons.parallelStream().anyMatch(lon2 -> Double.compare(lon, lon2) == 0)).count();
        final Long latDupeCount = bbox1Lats.parallelStream()
                .filter(lat -> bbox2Lats.parallelStream().anyMatch(lat2 -> Double.compare(lat, lat2) == 0)).count();
        return (lonDupeCount + latDupeCount) > 1;
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
