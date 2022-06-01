// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JOptionPane;

import java.awt.geom.Area;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * Various utility methods
 *
 * @author Taylor Smock
 *
 */
public final class MapWithAIDataUtils {
    /** The maximum dimensions for MapWithAI data (in kilometers) */
    public static final int MAXIMUM_SIDE_DIMENSIONS = 10_000; // RapiD is about 1 km, max is 10 km, but 10 km causes
    // timeouts
    private static final int TOO_MANY_BBOXES = 4;
    private static ForkJoinPool forkJoinPool;
    static final Object LAYER_LOCK = new Object();

    private MapWithAIDataUtils() {
        // Hide the constructor
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
     * Get a dataset from the API servers using a list bounds
     *
     * @param bounds            The bounds from which to get data
     * @param maximumDimensions The maximum dimensions to try to download at any one
     *                          time
     * @return A DataSet with data inside the bounds
     */
    public static DataSet getData(Collection<Bounds> bounds, int maximumDimensions) {
        final DataSet dataSet = new DataSet();
        final List<Bounds> realBounds = bounds.stream().filter(b -> !b.isOutOfTheWorld()).distinct()
                .flatMap(bound -> MapWithAIDataUtils.reduceBoundSize(bound, maximumDimensions).stream())
                .collect(Collectors.toList());
        if (!MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty()) {
            if ((bounds.size() < TOO_MANY_BBOXES) || confirmBigDownload(realBounds)) {
                final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor();
                monitor.beginTask(tr("Downloading {0} Data", MapWithAIPlugin.NAME), realBounds.size());
                try {
                    List<MapWithAIInfo> urls = new ArrayList<>(MapWithAIPreferenceHelper.getMapWithAIUrl());
                    final List<ForkJoinTask<DataSet>> downloadedDataSets = new ArrayList<>();
                    for (final Bounds bound : realBounds) {
                        for (MapWithAIInfo url : urls) {
                            if (url.getUrl() != null && !Utils.isBlank(url.getUrl())) {
                                ForkJoinTask<DataSet> ds = download(monitor, bound, url, maximumDimensions);
                                downloadedDataSets.add(ds);
                                MapWithAIDataUtils.getForkJoinPool().execute(ds);
                            }
                        }
                    }
                    mergeDataSets(dataSet, downloadedDataSets);
                } finally {
                    monitor.finishTask();
                    monitor.close();
                }
            }
        } else {
            final Notification noUrls = GuiHelper.runInEDTAndWaitAndReturn(
                    () -> MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty() ? new Notification(tr(
                            "There are no defined URLs. Attempting to add the appropriate servers.\nPlease try again."))
                            : new Notification(tr("No URLS are enabled")));
            Objects.requireNonNull(noUrls);
            noUrls.setDuration(Notification.TIME_DEFAULT);
            noUrls.setIcon(JOptionPane.INFORMATION_MESSAGE);
            noUrls.setHelpTopic(ht("Plugin/MapWithAI#Preferences"));
            GuiHelper.runInEDT(noUrls::show);
            if (MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty()
                    && MapWithAILayerInfo.getInstance().getDefaultLayers().isEmpty()) {
                MapWithAILayerInfo.getInstance().loadDefaults(true, MapWithAIDataUtils.getForkJoinPool(), false,
                        () -> Logging.info("MapWithAI Sources: Initialized sources"));
            }
        }
        return dataSet;
    }

    /**
     * Download an area
     *
     * @param monitor           The monitor to update
     * @param bound             The bounds that are being downloading
     * @param mapWithAIInfo     The source of the data
     * @param maximumDimensions The maximum dimensions to download
     * @return A future that will have downloaded the data
     */
    public static ForkJoinTask<DataSet> download(ProgressMonitor monitor, Bounds bound, MapWithAIInfo mapWithAIInfo,
            int maximumDimensions) {
        return ForkJoinTask.adapt(() -> {
            BoundingBoxMapWithAIDownloader downloader = new BoundingBoxMapWithAIDownloader(bound, mapWithAIInfo,
                    DetectTaskingManagerUtils.hasTaskingManagerLayer());
            try {
                return downloader.parseOsm(monitor.createSubTaskMonitor(1, false));
            } catch (OsmTransferException e) {
                if (e.getCause() instanceof SocketTimeoutException && maximumDimensions > MAXIMUM_SIDE_DIMENSIONS / 10
                        && maximumDimensions / 2f > 0.5) {
                    return getData(Collections.singleton(bound), maximumDimensions / 2);
                }
                throw e;
            }
        });
    }

    /**
     * Merge datasets
     *
     * @param original        The original dataset
     * @param dataSetsToMerge The datasets to merge (futures)
     */
    private static void mergeDataSets(final DataSet original, final List<ForkJoinTask<DataSet>> dataSetsToMerge) {
        for (ForkJoinTask<DataSet> ds : dataSetsToMerge) {
            try {
                original.mergeFrom(ds.join());
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IllegalDataException) {
                    Notification notification = new Notification();
                    notification.setContent(tr("MapWithAI servers may be down."));
                    GuiHelper.runInEDT(notification::show);
                } else {
                    Notification notification = new Notification();
                    GuiHelper.runInEDT(() -> notification.setContent(e.getLocalizedMessage()));
                    GuiHelper.runInEDT(notification::show);
                }
            }
        }
    }

    private static boolean confirmBigDownload(List<Bounds> realBounds) {
        ConfirmBigDownload confirmation = new ConfirmBigDownload(realBounds);
        GuiHelper.runInEDTAndWait(confirmation);
        return confirmation.confirmed();
    }

    private static class ConfirmBigDownload implements Runnable {
        Boolean bool;
        List<?> realBounds;

        public ConfirmBigDownload(List<?> realBounds) {
            this.realBounds = realBounds;
        }

        @Override
        public void run() {
            bool = ConditionalOptionPaneUtil.showConfirmationDialog(MapWithAIPlugin.NAME.concat(".alwaysdownload"),
                    null,
                    tr("You are going to make {0} requests to the MapWithAI server. This may take some time. <br /> Continue?",
                            realBounds.size()),
                    null, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_OPTION);
        }

        public boolean confirmed() {
            return bool;
        }
    }

    /**
     * Get a ForkJoinPool that is safe for use in Webstart
     *
     * @return The {@link ForkJoinPool} for MapWithAI use.
     */
    public static ForkJoinPool getForkJoinPool() {
        if (Utils.isRunningWebStart() || System.getSecurityManager() != null) {
            if (Objects.isNull(forkJoinPool) || forkJoinPool.isShutdown()) {
                forkJoinPool = Utils.newForkJoinPool(MapWithAIPlugin.NAME.concat(".forkjoinpoolthreads"),
                        MapWithAIPlugin.NAME, Thread.NORM_PRIORITY);
            }
            return forkJoinPool;
        }
        return ForkJoinPool.commonPool();
    }

    /**
     * Get the height of a bounds
     *
     * @param bounds The bounds with lat/lon information
     * @return The height in meters (see {@link LatLon#greatCircleDistance})
     */
    public static double getHeight(Bounds bounds) {
        final LatLon topRight = bounds.getMax();
        final LatLon bottomLeft = bounds.getMin();
        final double minx = bottomLeft.getX();
        final double maxY = topRight.getY();
        final LatLon topLeft = new LatLon(maxY, minx);
        return bottomLeft.greatCircleDistance(topLeft);
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
            GuiHelper.runInEDTAndWait(() -> MainApplication.getLayerManager().addLayer(tLayer));
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
                .stream().filter(obj -> !(obj instanceof MapWithAILayer)).collect(Collectors.toList());
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
        return getMapWithAIData(layer, osmLayer.getDataSet().getDataSourceBounds());
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer  A pre-existing {@link MapWithAILayer}
     * @param bounds The bounds to get the data in
     * @return true if data was downloaded
     */
    public static boolean getMapWithAIData(MapWithAILayer layer, Bounds... bounds) {
        return getMapWithAIData(layer, Arrays.asList(bounds));
    }

    /**
     * Get the data for MapWithAI
     *
     * @param layer  A pre-existing {@link MapWithAILayer}
     * @param bounds The bounds to get the data in
     * @return true if data was downloaded
     */
    public static boolean getMapWithAIData(MapWithAILayer layer, Collection<Bounds> bounds) {
        final DataSet mapWithAISet = layer.getDataSet();
        Area area = mapWithAISet.getDataSourceArea();
        final List<Bounds> toDownload = area == null ? new ArrayList<>(bounds)
                : bounds.stream().filter(Objects::nonNull).filter(tBounds -> !area.contains(tBounds.asRect()))
                        .collect(Collectors.toList());
        if (!toDownload.isEmpty()) {
            getForkJoinPool().execute(() -> {
                final DataSet newData = getData(toDownload, MAXIMUM_SIDE_DIMENSIONS);
                final Lock lock = layer.getLock();
                lock.lock();
                try {
                    mapWithAISet.mergeFrom(newData);
                    GetDataRunnable.cleanup(mapWithAISet, null, null);
                } finally {
                    lock.unlock();
                }
                toDownload.forEach(layer::onPostDownloadFromServer);
            });
        }
        return !toDownload.isEmpty();
    }

    /**
     * Get the width of a bounds
     *
     * @param bounds The bounds to get the width of
     * @return See {@link LatLon#greatCircleDistance}
     */
    public static double getWidth(Bounds bounds) {
        // Lat is y, Lon is x
        final LatLon bottomLeft = bounds.getMin();
        final LatLon topRight = bounds.getMax();
        final double minX = bottomLeft.getX();
        final double maxX = topRight.getX();
        final double minY = bottomLeft.getY();
        final double maxY = topRight.getY();
        final LatLon bottomRight = new LatLon(minY, maxX);
        final LatLon topLeft = new LatLon(maxY, minX);
        return Math.max(bottomLeft.greatCircleDistance(bottomRight), topLeft.greatCircleDistance(topRight));
    }

    /**
     * Reduce a bound to the specified dimensions, returning a list of bounds.
     *
     * @param bound             The bound to reduce to a set maximum dimension
     * @param maximumDimensions The maximum side dimensions of the bound
     * @return A list of Bounds that have a dimension no more than
     *         {@code maximumDimensions}
     */
    public static List<Bounds> reduceBoundSize(Bounds bound, int maximumDimensions) {
        final List<Bounds> returnBounds = new ArrayList<>();
        final double width = getWidth(bound);
        final double height = getHeight(bound);
        final double widthDivisions = width / maximumDimensions;
        final double heightDivisions = height / maximumDimensions;
        final int widthSplits = (int) widthDivisions + ((widthDivisions - Math.floor(widthDivisions)) > 0 ? 1 : 0);
        final int heightSplits = (int) heightDivisions + ((heightDivisions - Math.floor(heightDivisions)) > 0 ? 1 : 0);

        final double newMinWidths = Math.abs(bound.getMaxLon() - bound.getMinLon()) / widthSplits;
        final double newMinHeights = Math.abs(bound.getMaxLat() - bound.getMinLat()) / heightSplits;

        final double minx = bound.getMinLon();
        final double miny = bound.getMinLat();
        for (int x = 1; x <= widthSplits; x++) {
            for (int y = 1; y <= heightSplits; y++) {
                final LatLon lowerLeft = new LatLon(miny + (newMinHeights * (y - 1)), minx + (newMinWidths * (x - 1)));
                final LatLon upperRight = new LatLon(miny + (newMinHeights * y), minx + (newMinWidths * x));
                returnBounds.add(new Bounds(lowerLeft, upperRight));
            }
        }
        return returnBounds.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Reduce a list of bounds to {@link MapWithAIDataUtils#MAXIMUM_SIDE_DIMENSIONS}
     *
     * @param bounds The bounds to reduce to a set maximum dimension
     * @return A list of Bounds that have a dimension no more than
     *         {@link MapWithAIDataUtils#MAXIMUM_SIDE_DIMENSIONS}
     */
    public static List<Bounds> reduceBoundSize(List<Bounds> bounds) {
        return reduceBoundSize(bounds, MAXIMUM_SIDE_DIMENSIONS);
    }

    /**
     * Reduce a list of bounds to a specified size
     *
     * @param bounds            The bounds to reduce to a set maximum dimension
     * @param maximumDimensions The maximum width/height dimensions
     * @return A list of Bounds that have a dimension no more than the
     *         {@code maximumDimensions}
     */
    public static List<Bounds> reduceBoundSize(List<Bounds> bounds, int maximumDimensions) {
        final List<Bounds> returnBounds = new ArrayList<>();
        bounds.forEach(bound -> returnBounds.addAll(reduceBoundSize(bound, maximumDimensions)));
        return returnBounds.stream().distinct().collect(Collectors.toList());
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
     * Get the number of whole objects added from the MapWithAI layer. A whole
     * object is an object with tags or not a member of another object.
     *
     * @return The number of objects added from the MapWithAI data layer
     */
    public static Long getAddedObjects() {
        return Optional
                .ofNullable(GuiHelper.runInEDTAndWaitAndReturn(() -> UndoRedoHandler.getInstance().getUndoCommands()))
                .map(commands -> commands.stream().filter(MapWithAIAddCommand.class::isInstance)
                        .map(MapWithAIAddCommand.class::cast).mapToLong(MapWithAIAddCommand::getAddedObjects).sum())
                .orElse(0L);
    }

    /**
     * Get source tags for objects added from the MapWithAI data layer
     *
     * @return The source tags for Objects added from the MapWithAI data layer
     */
    public static List<String> getAddedObjectsSource() {
        return Optional
                .ofNullable(GuiHelper.runInEDTAndWaitAndReturn(() -> UndoRedoHandler.getInstance().getUndoCommands()))
                .map(commands -> commands.stream().filter(MapWithAIAddCommand.class::isInstance)
                        .map(MapWithAIAddCommand.class::cast).flatMap(com -> com.getSourceTags().stream()).distinct()
                        .collect(Collectors.toList()))
                .orElseGet(Collections::emptyList);
    }
}
