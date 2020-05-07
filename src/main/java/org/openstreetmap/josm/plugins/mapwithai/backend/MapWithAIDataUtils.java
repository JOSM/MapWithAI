// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * @author Taylor Smock
 *
 */
public final class MapWithAIDataUtils {
    /** THe maximum dimensions for MapWithAI data (in kilometers) */
    public static final int MAXIMUM_SIDE_DIMENSIONS = 10_000; // RapiD is about 1km, max is 10km, but 10km causes
    // timeouts
    private static final int TOO_MANY_BBOXES = 4;
    private static ForkJoinPool forkJoinPool;
    static final Object LAYER_LOCK = new Object();

    /** The default url for the MapWithAI paint style */
    public static final String DEFAULT_PAINT_STYLE_RESOURCE_URL = "https://josm.openstreetmap.de/josmfile?page=Styles/MapWithAI&zip=1";

    private static String paintStyleResourceUrl = DEFAULT_PAINT_STYLE_RESOURCE_URL;

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
                "https://gitlab.com/smocktaylor/rapid/raw/master/src/resources/styles/standard/rapid.mapcss",
                "resource://styles/standard/mapwithai.mapcss");
        new ArrayList<>(MapPaintStyles.getStyles().getStyleSources()).parallelStream()
                .filter(style -> oldUrls.contains(style.url)).forEach(MapPaintStyles::removeStyle);

        if (!checkIfMapWithAIPaintStyleExists()) {
            final MapCSSStyleSource style = new MapCSSStyleSource(paintStyleResourceUrl, MapWithAIPlugin.NAME,
                    "MapWithAI");
            MapPaintStyles.addStyle(style);
        }
    }

    /**
     * @return true if a MapWithAI paint style exists
     */
    public static boolean checkIfMapWithAIPaintStyleExists() {
        return MapPaintStyles.getStyles().getStyleSources().parallelStream().filter(MapCSSStyleSource.class::isInstance)
                .map(MapCSSStyleSource.class::cast).anyMatch(source -> paintStyleResourceUrl.equals(source.url));
    }

    /**
     * Remove MapWithAI paint styles
     */
    public static void removeMapWithAIPaintStyles() {
        new ArrayList<>(MapPaintStyles.getStyles().getStyleSources()).parallelStream()
                .filter(source -> paintStyleResourceUrl.equals(source.url))
                .forEach(style -> GuiHelper.runInEDT(() -> MapPaintStyles.removeStyle(style)));
    }

    /**
     * @return get the MapWithAI Paint style
     */
    public static StyleSource getMapWithAIPaintStyle() {
        return MapPaintStyles.getStyles().getStyleSources().parallelStream()
                .filter(source -> paintStyleResourceUrl.equals(source.url)).findAny().orElse(null);
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
        return getData(Arrays.asList(bbox), MAXIMUM_SIDE_DIMENSIONS);
    }

    /**
     * Get a dataset from the API servers using a bbox
     *
     * @param bbox              The bbox from which to get data
     * @param maximumDimensions The maximum dimensions to try to download at any one
     *                          time
     * @return A DataSet with data inside the bbox
     */
    public static DataSet getData(BBox bbox, int maximumDimensions) {
        return getData(Arrays.asList(bbox), maximumDimensions);
    }

    /**
     *
     * Get a dataset from the API servers using a list bboxes
     *
     * @param bbox The bboxes from which to get data
     * @return A DataSet with data inside the bboxes
     */
    public static DataSet getData(List<BBox> bbox) {
        return getData(bbox, MAXIMUM_SIDE_DIMENSIONS);
    }

    /**
     * Get a dataset from the API servers using a list bboxes
     *
     * @param bbox              The bboxes from which to get data
     * @param maximumDimensions The maximum dimensions to try to download at any one
     *                          time
     * @return A DataSet with data inside the bboxes
     */
    public static DataSet getData(List<BBox> bbox, int maximumDimensions) {
        final DataSet dataSet = new DataSet();
        final List<BBox> realBBoxes = bbox.stream().filter(BBox::isValid).distinct().collect(Collectors.toList());
        final List<Bounds> realBounds = realBBoxes.stream()
                .flatMap(tBBox -> MapWithAIDataUtils.reduceBBoxSize(tBBox, maximumDimensions).stream())
                .map(MapWithAIDataUtils::bboxToBounds).collect(Collectors.toList());
        if (!MapWithAILayerInfo.instance.getLayers().isEmpty()) {
            if ((realBBoxes.size() < TOO_MANY_BBOXES) || confirmBigDownload(realBBoxes)) {
                final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor();
                monitor.beginTask(tr("Downloading {0} Data", MapWithAIPlugin.NAME), realBounds.size());
                realBounds.parallelStream()
                        .forEach(bound -> new ArrayList<>(MapWithAIPreferenceHelper.getMapWithAIUrl()).parallelStream()
                                .filter(i -> i.getUrl() != null && !i.getUrl().trim().isEmpty()).forEach(i -> {
                                    BoundingBoxMapWithAIDownloader downloader = new BoundingBoxMapWithAIDownloader(
                                            bound, i, DetectTaskingManagerUtils.hasTaskingManagerLayer());
                                    try {
                                        DataSet ds = downloader.parseOsm(monitor.createSubTaskMonitor(1, false));
                                        synchronized (MapWithAIDataUtils.class) {
                                            dataSet.mergeFrom(ds);
                                        }
                                    } catch (OsmTransferException e) {
                                        Logging.error(e);
                                        if (maximumDimensions > MAXIMUM_SIDE_DIMENSIONS / 10) {
                                            dataSet.mergeFrom(getData(bound.toBBox(), maximumDimensions / 2));
                                        }
                                    }
                                }));
                monitor.finishTask();
                monitor.close();
            }
        } else {
            final Notification noUrls = GuiHelper.runInEDTAndWaitAndReturn(
                    () -> MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty() ? new Notification(tr(
                            "There are no defined URLs. Attempting to add the appropriate servers.\nPlease try again."))
                            : new Notification(tr("No URLS are enabled")));
            noUrls.setDuration(Notification.TIME_DEFAULT);
            noUrls.setIcon(JOptionPane.INFORMATION_MESSAGE);
            noUrls.setHelpTopic(ht("Plugin/MapWithAI#Preferences"));
            GuiHelper.runInEDT(noUrls::show);
            if (MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty()
                    && MapWithAILayerInfo.instance.getDefaultLayers().isEmpty()) {
                MapWithAILayerInfo.instance.loadDefaults(true, MainApplication.worker, false,
                        () -> Logging.info("MapWithAI Sources: Initialized sources"));
            }
        }
        return dataSet;
    }

    private static boolean confirmBigDownload(List<BBox> realBBoxes) {
        ConfirmBigDownload confirmation = new ConfirmBigDownload(realBBoxes);
        GuiHelper.runInEDTAndWait(confirmation);
        return confirmation.confirmed();
    }

    private static class ConfirmBigDownload implements Runnable {
        Boolean bool;
        List<BBox> realBBoxes;

        public ConfirmBigDownload(List<BBox> realBBoxes) {
            this.realBBoxes = realBBoxes;
        }

        @Override
        public void run() {
            bool = ConditionalOptionPaneUtil.showConfirmationDialog(MapWithAIPlugin.NAME.concat(".alwaysdownload"),
                    null,
                    tr("You are going to make {0} requests to the MapWithAI server. This may take some time. <br /> Continue?",
                            realBBoxes.size()),
                    null, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_OPTION);
        }

        public boolean confirmed() {
            return bool;
        }
    }

    private static Bounds bboxToBounds(BBox bbox) {
        Bounds bound = new Bounds(bbox.getBottomRight());
        bound.extend(bbox.getTopLeft());
        return bound;
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

    /**
     * Get the height of a bbox
     *
     * @param bbox The bbox with lat/lon information
     * @return The height in meters (see {@link LatLon#greatCircleDistance})
     */
    public static double getHeight(BBox bbox) {
        final LatLon bottomRight = bbox.getBottomRight();
        final LatLon topLeft = bbox.getTopLeft();
        final double minx = topLeft.getX();
        final double miny = bottomRight.getY();
        final LatLon bottomLeft = new LatLon(miny, minx);
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
            } else if (!mapWithAILayers.isEmpty()) {
                layer = mapWithAILayers.get(0);
            }
        }

        final MapWithAILayer tLayer = layer;
        if (!MainApplication.getLayerManager().getLayers().contains(tLayer) && create) {
            GuiHelper.runInEDT(() -> MainApplication.getLayerManager().addLayer(tLayer));
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
            if (!osmLayer.isLocked() && getMapWithAIData(layer, osmLayer)) {
                gotData = true;
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
                    mapWithAISet.mergeFrom(newData);
                    GetDataRunnable.cleanup(mapWithAISet, null);
                } finally {
                    lock.unlock();
                }
                toDownload.stream().map(MapWithAIDataUtils::bboxToBounds).forEach(layer::onPostDownloadFromServer);
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
                    if (!bbox1.bboxIsFunctionallyEqual(bbox2, null) && bboxesShareSide(bbox1, bbox2)) {
                        bbox1.add(bbox2);
                        alreadyDownloadedReduced.remove(bbox2);
                    }
                }
            }
        } while (aDRSize != alreadyDownloadedReduced.size());
        return removeDuplicateBBoxes(wantToDownload, alreadyDownloadedReduced);
    }

    private static List<BBox> removeDuplicateBBoxes(List<BBox> wantToDownload, List<BBox> alreadyDownloaded) {
        for (final BBox bbox : wantToDownload) {
            for (final BBox downloaded : alreadyDownloaded) {
                if (downloaded.bboxIsFunctionallyEqual(downloaded, null)) {
                    Logging.debug("{0}: It looks like we already downloaded {1}", MapWithAIPlugin.NAME,
                            bbox.toStringCSV(","));
                }
            }
        }
        return wantToDownload.parallelStream()
                .filter(bbox1 -> alreadyDownloaded.parallelStream()
                        .noneMatch(bbox2 -> bbox2.bboxIsFunctionallyEqual(bbox1, 0.000_02)))
                .collect(Collectors.toList());

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
        return Math.max(bottomRight.greatCircleDistance(bottomLeft), topRight.greatCircleDistance(topLeft));
    }

    /**
     * @param bbox              The bbox to reduce to a set maximum dimension
     * @param maximumDimensions The maximum side dimensions of the bbox
     * @return A list of BBoxes that have a dimension no more than
     *         {@code maximumDimensions}
     */
    public static List<BBox> reduceBBoxSize(BBox bbox, int maximumDimensions) {
        final List<BBox> returnBounds = new ArrayList<>();
        final double width = getWidth(bbox);
        final double height = getHeight(bbox);
        final Double widthDivisions = width / maximumDimensions;
        final Double heightDivisions = height / maximumDimensions;
        final int widthSplits = widthDivisions.intValue() + ((widthDivisions - widthDivisions.intValue()) > 0 ? 1 : 0);
        final int heightSplits = heightDivisions.intValue()
                + ((heightDivisions - heightDivisions.intValue()) > 0 ? 1 : 0);

        final double newMinWidths = Math.abs(bbox.getTopLeftLon() - bbox.getBottomRightLon()) / widthSplits;
        final double newMinHeights = Math.abs(bbox.getBottomRightLat() - bbox.getTopLeftLat()) / heightSplits;

        final double minx = bbox.getTopLeftLon();
        final double miny = bbox.getBottomRightLat();
        for (int x = 1; x <= widthSplits; x++) {
            for (int y = 1; y <= heightSplits; y++) {
                final LatLon lowerLeft = new LatLon(miny + (newMinHeights * (y - 1)), minx + (newMinWidths * (x - 1)));
                final LatLon upperRight = new LatLon(miny + (newMinHeights * y), minx + (newMinWidths * x));
                returnBounds.add(new BBox(lowerLeft, upperRight));
            }
        }
        return returnBounds.stream().distinct().collect(Collectors.toList());
    }

    /**
     * @param bboxes The bboxes to reduce to a set maximum dimension
     * @return A list of BBoxes that have a dimension no more than
     *         {@link MAXIMUM_SIDE_DIMENSIONS}
     */
    public static List<BBox> reduceBBoxSize(List<BBox> bboxes) {
        return reduceBBoxSize(bboxes, MAXIMUM_SIDE_DIMENSIONS);
    }

    /**
     * @param bboxes            The bboxes to reduce to a set maximum dimension
     * @param maximumDimensions The maximum width/height dimensions
     * @return A list of BBoxes that have a dimension no more than the
     *         {@code maximumDimensions}
     */
    public static List<BBox> reduceBBoxSize(List<BBox> bboxes, int maximumDimensions) {
        final List<BBox> returnBBoxes = new ArrayList<>();
        bboxes.forEach(bbox -> returnBBoxes.addAll(reduceBBoxSize(bbox, maximumDimensions)));
        return returnBBoxes.stream().distinct().collect(Collectors.toList());
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
        return GuiHelper.runInEDTAndWaitAndReturn(() -> UndoRedoHandler.getInstance().getUndoCommands())
                .parallelStream().filter(MapWithAIAddCommand.class::isInstance).map(MapWithAIAddCommand.class::cast)
                .mapToLong(MapWithAIAddCommand::getAddedObjects).sum();
    }

    /**
     * @return The source tags for Objects added from the MapWithAI data layer
     */
    public static List<String> getAddedObjectsSource() {
        return GuiHelper.runInEDTAndWaitAndReturn(() -> UndoRedoHandler.getInstance().getUndoCommands())
                .parallelStream().filter(MapWithAIAddCommand.class::isInstance).map(MapWithAIAddCommand.class::cast)
                .flatMap(com -> com.getSourceTags().stream()).distinct().collect(Collectors.toList());
    }

    /**
     * Set the URL for the MapWithAI paint style
     *
     * @param paintUrl The paint style for MapWithAI
     */
    public static void setPaintStyleUrl(String paintUrl) {
        paintStyleResourceUrl = paintUrl;
    }

    /**
     * Get the url for the paint style for MapWithAI
     *
     * @return The url for the paint style
     */
    public static String getPaintStyleUrl() {
        return paintStyleResourceUrl;
    }
}
