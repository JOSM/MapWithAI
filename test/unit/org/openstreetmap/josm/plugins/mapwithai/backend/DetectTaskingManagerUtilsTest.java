// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class DetectTaskingManagerUtilsTest {
    private static final String LAYER_NAME = "Task Boundaries";

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Test
    public void testHasTaskingManagerLayer() {
        Assert.assertFalse(DetectTaskingManagerUtils.hasTaskingManagerLayer());
        MainApplication.getLayerManager().addLayer(new GpxLayer(new GpxData()));
        Assert.assertFalse(DetectTaskingManagerUtils.hasTaskingManagerLayer());
        MainApplication.getLayerManager().addLayer(new GpxLayer(new GpxData(), LAYER_NAME));
        Assert.assertTrue(DetectTaskingManagerUtils.hasTaskingManagerLayer());
    }

    @Test
    public void testGetTaskingManagerLayer() {
        Assert.assertNull(DetectTaskingManagerUtils.getTaskingManagerLayer());
        final GpxLayer layer = new GpxLayer(new GpxData(), LAYER_NAME);
        MainApplication.getLayerManager().addLayer(layer);
        Assert.assertSame(layer, DetectTaskingManagerUtils.getTaskingManagerLayer());
    }

    @Test
    public void testGetTaskingManagerBounds() {
        Assert.assertFalse(DetectTaskingManagerUtils.getTaskingManagerBBox().isInWorld());

        final GpxLayer layer = new GpxLayer(new GpxData(), LAYER_NAME);
        layer.data.addWaypoint(new WayPoint(new LatLon(0, 0)));
        MainApplication.getLayerManager().addLayer(layer);
        Assert.assertEquals(0, DetectTaskingManagerUtils.getTaskingManagerBBox().height(), 0.000001);
        Assert.assertEquals(0, DetectTaskingManagerUtils.getTaskingManagerBBox().width(), 0.000001);

        layer.data.addWaypoint(new WayPoint(new LatLon(1, 1)));
        final BBox bbox = DetectTaskingManagerUtils.getTaskingManagerBBox();
        Assert.assertTrue(bbox.isInWorld());
        Assert.assertTrue(bbox.getBottomRight().equalsEpsilon(new LatLon(0, 1)));
        Assert.assertTrue(bbox.getTopLeft().equalsEpsilon(new LatLon(1, 0)));
    }

    @Test
    public void testCreateTaskingManagerGpxBounds() {
        Assert.assertFalse(DetectTaskingManagerUtils.hasTaskingManagerLayer());

        final BBox bbox = new BBox(0, 0, 1, 1);
        MainApplication.getLayerManager()
                .addLayer(new GpxLayer(DetectTaskingManagerUtils.createTaskingManagerGpxData(bbox),
                        DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA));

        Assert.assertTrue(DetectTaskingManagerUtils.hasTaskingManagerLayer());
        Assert.assertTrue(DetectTaskingManagerUtils.getTaskingManagerBBox().bounds(bbox));
    }
}
