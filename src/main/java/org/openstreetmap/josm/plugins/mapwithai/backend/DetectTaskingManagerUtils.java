// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.GpxRoute;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;

/**
 * @author Taylor Smock
 *
 */
final class DetectTaskingManagerUtils {
    public static final String MAPWITHAI_CROP_AREA = tr("{0}: Crop Area", MapWithAIPlugin.NAME);
    private static final List<Pattern> PATTERNS = new ArrayList<>();
    static {
        PATTERNS.add(Pattern.compile("^Task Boundaries.*$"));
        PATTERNS.add(Pattern.compile("^" + MAPWITHAI_CROP_AREA + "$"));
    }

    private DetectTaskingManagerUtils() {
        // Hide since this is going to be a static class
    }

    /**
     * @return True if there is a tasking manager layer
     */
    public static boolean hasTaskingManagerLayer() {
        return getTaskingManagerLayer() != null;
    }

    /**
     * @return A {@link Layer} that matches a pattern defined in
     *         {@link DetectTaskingManagerUtils#PATTERNS} or {@code null}.
     */
    public static Layer getTaskingManagerLayer() {
        return MainApplication.getLayerManager().getLayers().parallelStream().filter(
                tlayer -> PATTERNS.parallelStream().anyMatch(pattern -> pattern.matcher(tlayer.getName()).matches()))
                .findFirst().orElse(null);
    }

    /**
     * @return A {@link BBox} made from a tasking manager layer, or one that is not
     *         valid.
     */
    public static BBox getTaskingManagerBBox() {
        final BBox returnBBox = new BBox();
        final Layer layer = getTaskingManagerLayer();
        if (layer instanceof GpxLayer) {
            final GpxLayer gpxLayer = (GpxLayer) layer;
            final Bounds realBounds = gpxLayer.data.recalculateBounds();
            returnBBox.add(realBounds.toBBox());
        }
        return returnBBox;
    }

    /**
     * @param bbox A bbox to crop data to
     * @return A gpx layer that can be used to crop data from MapWithAI
     */
    public static GpxData createTaskingManagerGpxData(BBox bbox) {
        final GpxData data = new GpxData();
        final GpxRoute route = new GpxRoute();
        route.routePoints.add(new WayPoint(bbox.getBottomRight()));
        route.routePoints.add(new WayPoint(new LatLon(bbox.getBottomRightLat(), bbox.getTopLeftLon())));
        route.routePoints.add(new WayPoint(bbox.getTopLeft()));
        route.routePoints.add(new WayPoint(new LatLon(bbox.getTopLeftLat(), bbox.getBottomRightLon())));
        route.routePoints.add(route.routePoints.iterator().next());
        route.routePoints.forEach(waypoint -> waypoint.setTime(0));
        data.addRoute(route);
        return data;
    }
}
