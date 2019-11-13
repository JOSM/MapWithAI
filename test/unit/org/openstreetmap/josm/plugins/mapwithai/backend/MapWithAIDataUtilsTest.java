// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
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
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIDataUtilsTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().usingFilesUnderDirectory("test/resources/wiremock"));
    WireMockServer wireMockServer;

    @Before
    public void setUp() {
        MapWithAIPreferenceHelper.setMapWithAIURLs(MapWithAIPreferenceHelper.getMapWithAIURLs().stream().map(map -> {
            map.put("url", getDefaultMapWithAIAPIForTest(
                    map.getOrDefault("url", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API_BUILDING)));
            return map;
        }).collect(Collectors.toList()));
    }

    private String getDefaultMapWithAIAPIForTest(String url) {
        return getDefaultMapWithAIAPIForTest(url, "https://www.facebook.com");
    }

    private String getDefaultMapWithAIAPIForTest(String url, String wireMockReplace) {
        return url.replace(wireMockReplace, wireMockRule.baseUrl());
    }

    /**
     * This gets data from MapWithAI. This test may fail if someone adds the data to
     * OSM.
     */
    @Test
    public void testGetData() {
        final BBox testBBox = getTestBBox();
        final DataSet ds = new DataSet(MapWithAIDataUtils.getData(testBBox));
        Assert.assertEquals(2, ds.getWays().size());
    }

    /**
     * This gets data from MapWithAI. This test may fail if someone adds the data to
     * OSM.
     */
    @Test
    public void testGetDataCropped() {
        final BBox testBBox = getTestBBox();
        final GpxData gpxData = new GpxData();
        gpxData.addWaypoint(new WayPoint(new LatLon(39.0735205, -108.5711561)));
        gpxData.addWaypoint(new WayPoint(new LatLon(39.0736682, -108.5708568)));
        final GpxLayer gpx = new GpxLayer(gpxData, DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA);
        final DataSet originalData = MapWithAIDataUtils.getData(testBBox);
        MainApplication.getLayerManager().addLayer(gpx);
        final DataSet ds = MapWithAIDataUtils.getData(testBBox);
        Assert.assertEquals(1, ds.getWays().size());
        Assert.assertEquals(3, ds.getNodes().size());
        Assert.assertEquals(2, originalData.getWays().size());
        Assert.assertEquals(4, originalData.getNodes().size());
    }

    @Test
    public void testAddSourceTags() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        final DataSet ds = new DataSet(way1.firstNode(), way1.lastNode(), way1);
        final String source = "random source";

        Assert.assertNull(way1.get("source"));
        MapWithAIDataUtils.addSourceTags(ds, "highway", source);
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
        MapWithAIDataUtils.addPrimitivesToCollection(collection, Collections.singletonList(way1));
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
        MapWithAIDataUtils.removePrimitivesFromDataSet(Collections.singleton(way1));
        Assert.assertEquals(0, ds1.allPrimitives().size());
    }

    @Test
    public void testAddPaintStyle() {
        List<StyleSource> paintStyles = MapPaintStyles.getStyles().getStyleSources();
        int initialSize = paintStyles.size();
        // There are two default paint styles
        Assert.assertEquals(initialSize, paintStyles.size());
        MapWithAIDataUtils.addMapWithAIPaintStyles();
        paintStyles = MapPaintStyles.getStyles().getStyleSources();
        Assert.assertEquals(initialSize + 1, paintStyles.size());
        MapWithAIDataUtils.addMapWithAIPaintStyles();
        paintStyles = MapPaintStyles.getStyles().getStyleSources();
        Assert.assertEquals(initialSize + 1, paintStyles.size());
        MapWithAIDataUtils.addMapWithAIPaintStyles();
    }

    @Test
    public void testMapWithAIURLPreferences() {
        final String fakeUrl = "https://fake.url";
        Assert.assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .noneMatch(map -> fakeUrl.equals(map.get("url"))));
        MapWithAIPreferenceHelper.setMapWithAIUrl("Fake", fakeUrl, true, true);
        Assert.assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> fakeUrl.equals(map.get("url"))));
        final List<Map<String, String>> urls = new ArrayList<>(MapWithAIPreferenceHelper.getMapWithAIURLs());
        Assert.assertEquals(2, urls.size());
        MapWithAIPreferenceHelper.setMapWithAIUrl("MapWithAI", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API, true,
                true);
        Assert.assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API.equals(map.get("url"))));
        MapWithAIPreferenceHelper.setMapWithAIUrl("Fake2", fakeUrl, true, true);
        Assert.assertEquals(1, MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .filter(map -> fakeUrl.equals(map.get("url"))).count());
        MapWithAIPreferenceHelper.setMapWithAIURLs(urls.parallelStream()
                .filter(map -> !fakeUrl.equalsIgnoreCase(map.getOrDefault("url", ""))).collect(Collectors.toList()));
        Assert.assertEquals(1, MapWithAIPreferenceHelper.getMapWithAIUrl().size());
        Assert.assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> getDefaultMapWithAIAPIForTest(MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)
                        .equals(map.get("url"))));
    }

    @Test
    public void testSplitBounds() {
        final BBox bbox = new BBox(0, 0, 0.0001, 0.0001);
        List<BBox> bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(getExpectedNumberOfBBoxes(bbox), bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.001, 0.001);
        bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(getExpectedNumberOfBBoxes(bbox), bboxes.size());
        checkInBBox(bbox, bboxes);

        bbox.add(0.01, 0.01);
        bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(getExpectedNumberOfBBoxes(bbox), bboxes.size());
        checkInBBox(bbox, bboxes);
        checkBBoxesConnect(bbox, bboxes);

        bbox.add(0.1, 0.1);
        bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox);
        Assert.assertEquals(getExpectedNumberOfBBoxes(bbox), bboxes.size());
        checkInBBox(bbox, bboxes);
        checkBBoxesConnect(bbox, bboxes);
    }

    private static int getExpectedNumberOfBBoxes(BBox bbox) {
        double width = MapWithAIDataUtils.getWidth(bbox);
        double height = MapWithAIDataUtils.getHeight(bbox);
        int widthDivisions = (int) Math.ceil(width / MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS);
        int heightDivisions = (int) Math.ceil(height / MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS);
        return widthDivisions * heightDivisions;
    }

    private static void checkInBBox(BBox bbox, Collection<BBox> bboxes) {
        for (final BBox tBBox : bboxes) {
            Assert.assertTrue(bbox.bounds(tBBox));
        }
    }

    private static void checkBBoxesConnect(BBox originalBBox, Collection<BBox> bboxes) {
        for (final BBox bbox1 : bboxes) {
            boolean bboxFoundConnections = false;
            for (final BBox bbox2 : bboxes) {
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
        for (final LatLon bbox1Corner : getBBoxCorners(bbox1)) {
            for (final LatLon bbox2Corner : getBBoxCorners(bbox2)) {
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
