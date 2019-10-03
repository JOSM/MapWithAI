// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.sources.MapPaintPrefHelper;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDDataUtilsTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    /**
     * This gets data from RapiD. This test may fail if someone adds the data to
     * OSM.
     */
    @Test
    public void testGetData() {
        final BBox testBBox = getTestBBox();
        final DataSet ds = new DataSet(RapiDDataUtils.getData(testBBox));
        Assert.assertEquals(1, ds.getWays().size());
    }

    /**
     * This gets data from RapiD. This test may fail if someone adds the data to
     * OSM.
     */
    @Test
    public void testGetDataCropped() {
        final BBox testBBox = getTestBBox();
        GpxData gpxData = new GpxData();
        gpxData.addWaypoint(new WayPoint(new LatLon(39.0735205, -108.5711561)));
        gpxData.addWaypoint(new WayPoint(new LatLon(39.0736682, -108.5708568)));
        GpxLayer gpx = new GpxLayer(gpxData, DetectTaskingManager.RAPID_CROP_AREA);
        final DataSet originalData = RapiDDataUtils.getData(testBBox);
        MainApplication.getLayerManager().addLayer(gpx);
        final DataSet ds = RapiDDataUtils.getData(testBBox);
        Assert.assertEquals(1, ds.getWays().size());
        Assert.assertEquals(3, ds.getNodes().size());
        Assert.assertEquals(1, originalData.getWays().size());
        Assert.assertEquals(4, originalData.getNodes().size());
    }

    @Test
    public void testAddSourceTags() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        final DataSet ds = new DataSet(way1.firstNode(), way1.lastNode(), way1);
        final String source = "random source";

        Assert.assertNull(way1.get("source"));
        RapiDDataUtils.addSourceTags(ds, "highway", source);
        Assert.assertEquals(source, way1.get("source"));
    }

    public static BBox getTestBBox() {
        final BBox testBBox = new BBox();
        testBBox.add(new LatLon(39.0734162, -108.5707107));
        testBBox.add(new LatLon(39.0738791, -108.5715723));
        return testBBox;
    }

    @Test
    public void testAddPrimitivesToCollection() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0, 0.1)));
        final Collection<OsmPrimitive> collection = new TreeSet<>();
        Assert.assertEquals(0, collection.size());
        RapiDDataUtils.addPrimitivesToCollection(collection, Collections.singletonList(way1));
        Assert.assertEquals(3, collection.size());
    }

    @Test
    public void testRemovePrimitivesFromDataSet() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0, 0.1)));
        final DataSet ds1 = new DataSet();
        for (final Node node : way1.getNodes()) {
            ds1.addPrimitive(node);
        }
        ds1.addPrimitive(way1);

        Assert.assertEquals(3, ds1.allPrimitives().size());
        RapiDDataUtils.removePrimitivesFromDataSet(Collections.singleton(way1));
        Assert.assertEquals(0, ds1.allPrimitives().size());
    }

    @Test
    public void testAddPaintStyle() {
        List<SourceEntry> paintStyles = MapPaintPrefHelper.INSTANCE.get();
        // There are two default paint styles
        Assert.assertEquals(2, paintStyles.size());
        RapiDDataUtils.addRapiDPaintStyles();
        paintStyles = MapPaintPrefHelper.INSTANCE.get();
        Assert.assertEquals(3, paintStyles.size());
        RapiDDataUtils.addRapiDPaintStyles();
        paintStyles = MapPaintPrefHelper.INSTANCE.get();
        Assert.assertEquals(3, paintStyles.size());
        RapiDDataUtils.addRapiDPaintStyles();
    }

    @Test
    public void testRapiDURLPreferences() {
        final String fakeUrl = "https://fake.url";
        Assert.assertEquals(RapiDDataUtils.DEFAULT_RAPID_API, RapiDDataUtils.getRapiDURL());
        RapiDDataUtils.setRapiDUrl(fakeUrl);
        Assert.assertEquals(fakeUrl, RapiDDataUtils.getRapiDURL());
        final List<String> urls = new ArrayList<>(RapiDDataUtils.getRapiDURLs());
        Assert.assertEquals(2, urls.size());
        RapiDDataUtils.setRapiDUrl(RapiDDataUtils.DEFAULT_RAPID_API);
        Assert.assertEquals(RapiDDataUtils.DEFAULT_RAPID_API, RapiDDataUtils.getRapiDURL());
        RapiDDataUtils.setRapiDUrl(fakeUrl);
        Assert.assertEquals(fakeUrl, RapiDDataUtils.getRapiDURL());
        urls.remove(fakeUrl);
        RapiDDataUtils.setRapiDURLs(urls);
        Assert.assertEquals(RapiDDataUtils.DEFAULT_RAPID_API, RapiDDataUtils.getRapiDURL());
    }

    @Test
    public void testSplitBounds() {
        final BBox bbox = new BBox(0, 0, 0.0001, 0.0001);
        List<BBox> bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(1, bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.001, 0.001);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(1, bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.01, 0.01);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(4, bboxes.size());
        checkInBBox(bbox, bboxes);
        checkBBoxesConnect(bbox, bboxes);

        bbox.add(0.1, 0.1);
        bboxes = RapiDDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(144, bboxes.size());
        checkInBBox(bbox, bboxes);
        checkBBoxesConnect(bbox, bboxes);
    }

    private static void checkInBBox(BBox bbox, Collection<BBox> bboxes) {
        for (final BBox tBBox : bboxes) {
            Assert.assertTrue(bbox.bounds(tBBox));
        }
    }

    private static void checkBBoxesConnect(BBox originalBBox, Collection<BBox> bboxes) {
        for (BBox bbox1 : bboxes) {
            boolean bboxFoundConnections = false;
            for (BBox bbox2 : bboxes) {
                if (!bbox1.equals(bbox2)) {
                    bboxFoundConnections = bboxCheckConnections(bbox1, bbox2);
                    if (bboxFoundConnections) {
                        break;
                    }
                }
            }
            if (!bboxFoundConnections) {
                bboxFoundConnections = bboxCheckConnections(bbox1, originalBBox);
            }
            Assert.assertTrue(bboxFoundConnections);
        }
    }

    private static boolean bboxCheckConnections(BBox bbox1, BBox bbox2) {
        int shared = 0;
        for (LatLon bbox1Corner : getBBoxCorners(bbox1)) {
            for (LatLon bbox2Corner : getBBoxCorners(bbox2)) {
                if (bbox1Corner.equalsEpsilon(bbox2Corner)) {
                    shared++;
                }
            }
        }
        return shared >= 2;
    }

    private static LatLon[] getBBoxCorners(BBox bbox) {
        LatLon[] returnLatLons = new LatLon[4];
        returnLatLons[0] = bbox.getTopLeft();
        returnLatLons[1] = new LatLon(bbox.getTopLeftLat(), bbox.getBottomRightLon());
        returnLatLons[2] = bbox.getBottomRight();
        returnLatLons[3] = new LatLon(bbox.getBottomRightLat(), bbox.getTopLeftLon());
        return returnLatLons;
    }
}
