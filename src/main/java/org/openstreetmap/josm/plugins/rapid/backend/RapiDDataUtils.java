// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Future;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.sources.ExtendedSourceEntry;
import org.openstreetmap.josm.data.preferences.sources.MapPaintPrefHelper;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourceType;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.HttpClient.Response;
import org.openstreetmap.josm.tools.Logging;

/**
 * @author Taylor Smock
 *
 */
public final class RapiDDataUtils {
    public static final String DEFAULT_RAPID_API = "https://www.facebook.com/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&result_type=road_building_vector_xml&bbox={bbox}";
    // TODO add &crop_bbox={bbox} if there are bounds from a HOT Tasking Manager
    public static final int MAXIMUM_SIDE_DIMENSIONS = 10000; // 10 km

    private RapiDDataUtils() {
        // Hide the constructor
    }

    /**
     * Get a dataset from the API servers using a bbox
     *
     * @param bbox The bbox from which to get data
     * @return A DataSet with data inside the bbox
     */
    public static DataSet getData(BBox bbox) {
        final DataSet dataSet = new DataSet();
        if (bbox.isValid()) {
            final List<Future<?>> futures = new ArrayList<>();
            for (final BBox tbbox : reduceBBoxSize(bbox)) {
                futures.add(MainApplication.worker.submit(new GetDataRunnable(tbbox, dataSet)));
            }
            for (final Future<?> future : futures) {
                try {
                    int count = 0;
                    while (!future.isDone() && !future.isCancelled() && count < 100) {
                        Thread.sleep(100);
                        count++;
                    }
                } catch (final InterruptedException e) {
                    Logging.debug(e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        return dataSet;
    }

    private static class GetDataRunnable implements Runnable {
        private final BBox bbox;
        private final DataSet dataSet;

        public GetDataRunnable(BBox bbox, DataSet dataSet) {
            this.bbox = bbox;
            this.dataSet = dataSet;
        }

        @Override
        public void run() {
            final DataSet temporaryDataSet = getDataReal(getBbox());
            synchronized (RapiDDataUtils.GetDataRunnable.class) {
                getDataSet().mergeFrom(temporaryDataSet);
            }
        }

        /**
         * @return The {@code BBox} associated with this object
         */
        public BBox getBbox() {
            return bbox;
        }

        /**
         * @return The {@code DataSet} associated with this object
         */
        public DataSet getDataSet() {
            return dataSet;
        }

        private static DataSet getDataReal(BBox bbox) {
            InputStream inputStream = null;
            final DataSet dataSet = new DataSet();
            final String urlString = getRapiDURL();
            try {
                final URL url = new URL(urlString.replace("{bbox}", bbox.toStringCSV(",")));
                final HttpClient client = HttpClient.create(url);
                final StringBuilder defaultUserAgent = new StringBuilder();
                defaultUserAgent.append(client.getHeaders().get("User-Agent"));
                if (defaultUserAgent.toString().trim().length() == 0) {
                    defaultUserAgent.append("JOSM");
                }
                defaultUserAgent.append(tr("/ {0} {1}", RapiDPlugin.NAME, RapiDPlugin.getVersionInfo()));
                client.setHeader("User-Agent", defaultUserAgent.toString());
                Logging.debug("{0}: Getting {1}", RapiDPlugin.NAME, client.getURL().toString());
                final Response response = client.connect();
                inputStream = response.getContent();
                dataSet.mergeFrom(OsmReader.parseDataSet(inputStream, null));
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
            }
            return dataSet;
        }
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
     * Get the current RapiD url
     *
     * @return A RapiD url
     */
    public static String getRapiDURL() {
        final List<String> urls = getRapiDURLs();
        String url = Config.getPref().get(RapiDPlugin.NAME.concat(".current_api"), DEFAULT_RAPID_API);
        if (!urls.contains(url)) {
            url = DEFAULT_RAPID_API;
            setRapiDUrl(DEFAULT_RAPID_API);
        }
        return url;
    }

    /**
     * Set the RapiD url
     *
     * @param url The url to set as the default
     */
    public static void setRapiDUrl(String url) {
        final List<String> urls = getRapiDURLs();
        if (!urls.contains(url)) {
            urls.add(url);
            setRapiDURLs(urls);
        }
        Config.getPref().put(RapiDPlugin.NAME.concat(".current_api"), url);
    }

    /**
     * Set the RapiD urls
     *
     * @param urls A list of URLs
     */
    public static void setRapiDURLs(List<String> urls) {
        Config.getPref().putList(RapiDPlugin.NAME.concat(".apis"), urls);
    }

    /**
     * Get the RapiD urls (or the default)
     *
     * @return The urls for RapiD endpoints
     */
    public static List<String> getRapiDURLs() {
        return Config.getPref().getList(RapiDPlugin.NAME.concat(".apis"),
                new ArrayList<>(Arrays.asList(DEFAULT_RAPID_API)));
    }

    /**
     * Add a paintstyle from the jar (TODO)
     */
    public static void addRapiDPaintStyles() {
        // TODO figure out how to use the one in the jar file
        final ExtendedSourceEntry rapid = new ExtendedSourceEntry(SourceType.MAP_PAINT_STYLE, "rapid.mapcss",
                "https://gitlab.com/smocktaylor/rapid/raw/master/src/resources/styles/standard/rapid.mapcss");
        final List<SourceEntry> paintStyles = MapPaintPrefHelper.INSTANCE.get();
        for (final SourceEntry paintStyle : paintStyles) {
            if (rapid.url.equals(paintStyle.url)) {
                return;
            }
        }
        paintStyles.add(rapid);
        MapPaintPrefHelper.INSTANCE.put(paintStyles);
    }

    /**
     * Set whether or not a we switch from the RapiD layer to an OSM data layer
     *
     * @param selected true if we are going to switch layers
     */
    public static void setSwitchLayers(boolean selected) {
        Config.getPref().putBoolean(RapiDPlugin.NAME.concat(".autoswitchlayers"), selected);
    }

    /**
     * @return {@code true} if we want to automatically switch layers
     */
    public static boolean isSwitchLayers() {
        return Config.getPref().getBoolean(RapiDPlugin.NAME.concat(".autoswitchlayers"), true);
    }

    /**
     * Get the maximum number of objects that can be added at one time
     *
     * @return The maximum selection. If 0, allow any number.
     */
    public static int getMaximumAddition() {
        return Config.getPref().getInt(RapiDPlugin.NAME.concat(".maximumselection"), 50);
    }

    /**
     * Set the maximum number of objects that can be added at one time.
     *
     * @param max The maximum number of objects to select (0 allows any number to be
     *            selected).
     */
    public static void setMaximumAddition(int max) {
        Config.getPref().putInt(RapiDPlugin.NAME.concat(".maximumselection"), max);
    }

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

    public static double getHeight(BBox bbox) {
        final LatLon bottomRight = bbox.getBottomRight();
        final LatLon topLeft = bbox.getTopLeft();
        final double minx = topLeft.getX();
        final double miny = bottomRight.getY();
        final LatLon bottomLeft = new LatLon(miny, minx);
        // TODO handle poles
        return topLeft.greatCircleDistance(bottomLeft);
    }
}
