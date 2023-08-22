// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * @author Taylor Smock
 *
 */
@BasicPreferences
@Main
@Projection
class DetectTaskingManagerUtilsTest {
    private static final String LAYER_NAME = "Task Boundaries";

    @Test
    void testHasTaskingManagerLayer() {
        assertFalse(DetectTaskingManagerUtils.hasTaskingManagerLayer(), "No TM layer exists yet");
        MainApplication.getLayerManager().addLayer(new GpxLayer(new GpxData()));
        assertFalse(DetectTaskingManagerUtils.hasTaskingManagerLayer(), "No TM layer exists yet");
        MainApplication.getLayerManager().addLayer(new GpxLayer(new GpxData(), LAYER_NAME));
        assertTrue(DetectTaskingManagerUtils.hasTaskingManagerLayer(), "A TM layer exists yet");
    }

    @Test
    void testGetTaskingManagerLayer() {
        assertNull(DetectTaskingManagerUtils.getTaskingManagerLayer(), "No TM layer exists yet");
        final GpxLayer layer = new GpxLayer(new GpxData(), LAYER_NAME);
        MainApplication.getLayerManager().addLayer(layer);
        assertSame(layer, DetectTaskingManagerUtils.getTaskingManagerLayer(), "The TM layer was added");
    }

    @Test
    void testGetTaskingManagerBounds() {
        assertTrue(DetectTaskingManagerUtils.getTaskingManagerBounds().isCollapsed(), "No TM layer exists yet");

        final GpxLayer layer = new GpxLayer(new GpxData(), LAYER_NAME);
        layer.data.addWaypoint(new WayPoint(new LatLon(0, 0)));
        MainApplication.getLayerManager().addLayer(layer);
        assertEquals(0, DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox().height(), 0.000001,
                "The TM layer only has one point");
        assertEquals(0, DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox().width(), 0.000001,
                "The TM layer only has one point");

        layer.data.addWaypoint(new WayPoint(new LatLon(1, 1)));
        final BBox bbox = DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox();
        assertTrue(bbox.isInWorld(), "A TM layer exists");
        assertTrue(bbox.getBottomRight().equalsEpsilon((ILatLon) new LatLon(0, 1)),
                "The bottom right should be at (0, 1)");
        assertTrue(bbox.getTopLeft().equalsEpsilon((ILatLon) new LatLon(1, 0)), "The top left should be at (1, 0)");
    }

    @Test
    void testCreateTaskingManagerGpxBounds() {
        assertFalse(DetectTaskingManagerUtils.hasTaskingManagerLayer(), "No TM layer exists yet");

        final Bounds bounds = new Bounds(0, 0, 1, 1);
        MainApplication.getLayerManager()
                .addLayer(new GpxLayer(DetectTaskingManagerUtils.createTaskingManagerGpxData(bounds),
                        DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA));

        assertTrue(DetectTaskingManagerUtils.hasTaskingManagerLayer(), "A TM layer exists");
        assertTrue(DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox().bounds(bounds.toBBox()),
                "The TM layer should bound itself");
    }
}
