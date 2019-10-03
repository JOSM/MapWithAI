// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * @author Taylor Smock
 *
 */
public class DetectTaskingManagerTest {
    private static final String LAYER_NAME = "Task Boundaries";

    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Test
    public void testHasTaskingManagerLayer() {
        Assert.assertFalse(DetectTaskingManager.hasTaskingManagerLayer());
        MainApplication.getLayerManager().addLayer(new GpxLayer(new GpxData()));
        Assert.assertFalse(DetectTaskingManager.hasTaskingManagerLayer());
        MainApplication.getLayerManager().addLayer(new GpxLayer(new GpxData(), LAYER_NAME));
        Assert.assertTrue(DetectTaskingManager.hasTaskingManagerLayer());
    }

    @Test
    public void testGetTaskingManagerLayer() {
        Assert.assertNull(DetectTaskingManager.getTaskingManagerLayer());
        GpxLayer layer = new GpxLayer(new GpxData(), LAYER_NAME);
        MainApplication.getLayerManager().addLayer(layer);
        Assert.assertSame(layer, DetectTaskingManager.getTaskingManagerLayer());
    }

    @Test
    public void testGetTaskingManagerBounds() {
        Assert.assertFalse(DetectTaskingManager.getTaskingManagerBBox().isInWorld());

        GpxLayer layer = new GpxLayer(new GpxData(), LAYER_NAME);
        layer.data.addWaypoint(new WayPoint(new LatLon(0, 0)));
        MainApplication.getLayerManager().addLayer(layer);
        Assert.assertEquals(0, DetectTaskingManager.getTaskingManagerBBox().height(), 0.000001);
        Assert.assertEquals(0, DetectTaskingManager.getTaskingManagerBBox().width(), 0.000001);

        layer.data.addWaypoint(new WayPoint(new LatLon(1, 1)));
        BBox bbox = DetectTaskingManager.getTaskingManagerBBox();
        Assert.assertTrue(bbox.isInWorld());
        Assert.assertTrue(bbox.getBottomRight().equalsEpsilon(new LatLon(0, 1)));
        Assert.assertTrue(bbox.getTopLeft().equalsEpsilon(new LatLon(1, 0)));
    }
}
