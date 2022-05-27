// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.time.Instant;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.GpxRoute;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;

/**
 * Various methods to simplify detection of tasking manager tasks
 *
 * @author Taylor Smock
 *
 */
final class DetectTaskingManagerUtils {
    public static final String MAPWITHAI_CROP_AREA = tr("{0}: Crop Area", MapWithAIPlugin.NAME);
    private static final Pattern[] PATTERNS = { Pattern.compile("^Task Boundaries.*$"),
            Pattern.compile("^" + MAPWITHAI_CROP_AREA + "$"), Pattern.compile("^Boundary for task:.*$") };

    private DetectTaskingManagerUtils() {
        // Hide since this is going to be a static class
    }

    /**
     * Check for a tasking manager layer
     *
     * @return True if there is a tasking manager layer
     */
    public static boolean hasTaskingManagerLayer() {
        return getTaskingManagerLayer() != null;
    }

    /**
     * Get a tasking manager layer
     *
     * @return A {@link Layer} that matches a pattern defined in
     *         {@link DetectTaskingManagerUtils#PATTERNS} or {@code null}.
     */
    public static Layer getTaskingManagerLayer() {
        return MainApplication.getLayerManager().getLayers().stream().filter(tlayer -> Stream.of(PATTERNS).parallel()
                .anyMatch(pattern -> pattern.matcher(tlayer.getName()).matches())).findFirst().orElse(null);
    }

    /**
     * Get the bounds from the tasking manager layer
     *
     * @return A {@link Bounds} made from a tasking manager layer, or one that is
     *         not valid.
     */
    public static Bounds getTaskingManagerBounds() {
        Bounds returnBounds = new Bounds(0, 0, 0, 0);
        final Layer layer = getTaskingManagerLayer();
        if (layer instanceof GpxLayer) {
            final GpxLayer gpxLayer = (GpxLayer) layer;
            final Bounds realBounds = gpxLayer.data.recalculateBounds();
            if (returnBounds.isCollapsed()) {
                returnBounds = realBounds;
            } else {
                returnBounds.extend(realBounds);
            }
        } else if (layer instanceof OsmDataLayer && ((OsmDataLayer) layer).getDataSet().getWays().size() == 1) {
            final BBox bbox = ((OsmDataLayer) layer).getDataSet().getWays().iterator().next().getBBox();
            returnBounds.extend(bbox.getBottomRight());
            returnBounds.extend(bbox.getTopLeft());
        }
        return returnBounds;
    }

    /**
     * Create a GpxData that can be used to define a crop area
     *
     * @param bounds A bounds to crop data to
     * @return A gpx layer that can be used to crop data from MapWithAI
     */
    public static GpxData createTaskingManagerGpxData(Bounds bounds) {
        final GpxData data = new GpxData();
        final GpxRoute route = new GpxRoute();
        route.routePoints.add(new WayPoint(bounds.getMin()));
        route.routePoints.add(new WayPoint(new LatLon(bounds.getMaxLat(), bounds.getMinLon())));
        route.routePoints.add(new WayPoint(bounds.getMax()));
        route.routePoints.add(new WayPoint(new LatLon(bounds.getMinLat(), bounds.getMaxLon())));
        route.routePoints.add(route.routePoints.iterator().next());
        route.routePoints.forEach(waypoint -> waypoint.setInstant(Instant.EPOCH));
        data.addRoute(route);
        return data;
    }
}
