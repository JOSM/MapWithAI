// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;

/**
 * @author Taylor Smock
 *
 */
public class DetectTaskingManager {
    public static final List<Pattern> patterns = new ArrayList<>();
    static {
        patterns.add(Pattern.compile("^Task Boundaries.*$"));
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
            List<Bounds> bounds = gpxLayer.data.getDataSourceBounds();
            bounds.forEach(bound -> returnBBox.add(bound.toBBox()));
        }
        return returnBBox;
    }
}
