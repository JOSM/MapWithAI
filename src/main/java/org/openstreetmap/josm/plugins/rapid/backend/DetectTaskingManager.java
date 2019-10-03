// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;

/**
 * @author Taylor Smock
 *
 */
public class DetectTaskingManager {
    public static final String RAPID_CROP_AREA = tr("{0}: Crop Area", RapiDPlugin.NAME);
    public static final List<Pattern> patterns = new ArrayList<>();
    static {
        patterns.add(Pattern.compile("^Task Boundaries.*$"));
        patterns.add(Pattern.compile("^" + RAPID_CROP_AREA + "$"));
    }

    private DetectTaskingManager() {
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
     *         {@link DetectTaskingManager#patterns} or {@code null}.
     */
    public static Layer getTaskingManagerLayer() {
        Layer returnLayer = null;
        List<Layer> layers = MainApplication.getLayerManager().getLayers();
        for (Pattern pattern : patterns) {
            Optional<Layer> layer = layers.parallelStream()
                    .filter(tlayer -> pattern.matcher(tlayer.getName()).matches())
                    .findFirst();
            if (layer.isPresent()) {
                returnLayer = layer.get();
                break;
            }
        }
        return returnLayer;
    }

    /**
     * @return A {@link Bound} made from a tasking manager layer, or one that is not
     *         valid.
     */
    public static BBox getTaskingManagerBBox() {
        BBox returnBBox = new BBox();
        Layer layer = getTaskingManagerLayer();
        if (layer instanceof GpxLayer) {
            GpxLayer gpxLayer = (GpxLayer) layer;
            Bounds realBounds = gpxLayer.data.recalculateBounds();
            returnBBox.add(realBounds.toBBox());
        }
        return returnBBox;
    }

    public static GpxData createTaskingManagerGpxData(BBox bbox) {
        final GpxData data = new GpxData();
        GpxRoute route = new GpxRoute();
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
