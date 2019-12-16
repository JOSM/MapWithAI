// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.After;
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
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIDataUtilsTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAIPreferenceHelper.setMapWithAIURLs(MapWithAIPreferenceHelper.getMapWithAIURLs().stream().map(map -> {
            map.put("url", getDefaultMapWithAIAPIForTest(
                    map.getOrDefault("url", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)));
            return map;
        }).collect(Collectors.toList()));
        MapWithAIDataUtils.setPaintStyleUrl(
                MapWithAIDataUtils.getPaintStyleUrl().replace(Config.getUrls().getJOSMWebsite(), wireMock.baseUrl()));
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

    private String getDefaultMapWithAIAPIForTest(String url) {
        return getDefaultMapWithAIAPIForTest(url, "https://www.facebook.com");
    }

    private String getDefaultMapWithAIAPIForTest(String url, String wireMockReplace) {
        return url.replace(wireMockReplace, wireMock.baseUrl());
    }

    /**
     * This gets data from MapWithAI. This test may fail if someone adds the data to
     * OSM.
     */
    @Test
    public void testGetData() {
        final BBox testBBox = getTestBBox();
        final DataSet ds = new DataSet(MapWithAIDataUtils.getData(testBBox));
        assertEquals(1, ds.getWays().size(), "There should only be one way in the testBBox");
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
        assertEquals(1, ds.getWays().size(), "There should only be one way in the cropped testBBox");
        assertEquals(3, ds.getNodes().size(), "There should be three nodes in the cropped testBBox");
        assertEquals(1, originalData.getWays().size(), "There should be one way in the testBBox");
        assertEquals(4, originalData.getNodes().size(), "There should be four nodes in the testBBox");
    }

    @Test
    public void testAddSourceTags() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        final DataSet ds = new DataSet(way1.firstNode(), way1.lastNode(), way1);
        final String source = "random source";

        assertNull(way1.get("source"), "The source for the data should not be null");
        MapWithAIDataUtils.addSourceTags(ds, "highway", source);
        assertEquals(source, way1.get("source"), "The source for the data should be the specified source");
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
        MapWithAIDataUtils.addPrimitivesToCollection(collection, Collections.singletonList(way1));
        assertEquals(3, collection.size(), "The way has two child primitives, for a total of three primitives");
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

        assertEquals(3, ds1.allPrimitives().size(), "The DataSet should have three primitives");
        MapWithAIDataUtils.removePrimitivesFromDataSet(Collections.singleton(way1));
        assertEquals(0, ds1.allPrimitives().size(), "All of the primitives should have been removed from the DataSet");
    }

    @Test
    public void testAddPaintStyle() {
        MapWithAIDataUtils.removeMapWithAIPaintStyles();
        Awaitility.await().atMost(Durations.FIVE_SECONDS)
                .until(() -> !MapWithAIDataUtils.checkIfMapWithAIPaintStyleExists());
        List<StyleSource> paintStyles = MapPaintStyles.getStyles().getStyleSources();
        int initialSize = paintStyles.size();
        for (int i = 0; i < 10; i++) {
            MapWithAIDataUtils.addMapWithAIPaintStyles();
            paintStyles = MapPaintStyles.getStyles().getStyleSources();
            assertEquals(initialSize + 1, paintStyles.size(),
                    "The paintstyle should have been added, but only one of it");
        }
    }

    @Test
    public void testMapWithAIURLPreferences() {
        final String fakeUrl = "https://fake.url";
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream().noneMatch(
                map -> fakeUrl.equals(map.get("url"))), "fakeUrl shouldn't be in the current MapWithAI urls");
        MapWithAIPreferenceHelper.setMapWithAIUrl("Fake", fakeUrl, true, true);
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> fakeUrl.equals(map.get("url"))), "fakeUrl should have been added");
        final List<Map<String, String>> urls = new ArrayList<>(MapWithAIPreferenceHelper.getMapWithAIURLs());
        assertEquals(2, urls.size(), "There should be two urls (fakeUrl and the default)");
        MapWithAIPreferenceHelper.setMapWithAIUrl("MapWithAI", MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API, true,
                true);
        assertTrue(
                MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                        .anyMatch(map -> MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API.equals(map.get("url"))),
                "The default URL should exist");
        MapWithAIPreferenceHelper.setMapWithAIUrl("Fake2", fakeUrl, true, true);
        assertEquals(1, MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .filter(map -> fakeUrl.equals(map.get("url"))).count(), "There should only be one fakeUrl");
        MapWithAIPreferenceHelper.setMapWithAIURLs(urls.parallelStream()
                .filter(map -> !fakeUrl.equalsIgnoreCase(map.getOrDefault("url", ""))).collect(Collectors.toList()));
        assertEquals(1, MapWithAIPreferenceHelper.getMapWithAIUrl().size(),
                "The MapWithAI URLs should have been reset (essentially)");
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> getDefaultMapWithAIAPIForTest(MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)
                        .equals(map.get("url"))),
                "The MapWithAI URLs should have been reset");
    }

    @Test
    public void testSplitBounds() {
        final BBox bbox = new BBox(0, 0, 0.0001, 0.0001);
        for (Double i : Arrays.asList(0.0001, 0.001, 0.01, 0.1)) {
            bbox.add(i, i);
            List<BBox> bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox);
            assertEquals(getExpectedNumberOfBBoxes(bbox), bboxes.size(), "The bbox should be appropriately reduced");
            checkInBBox(bbox, bboxes);
            checkBBoxesConnect(bbox, bboxes);
        }
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
            assertTrue(bbox.bounds(tBBox), "The bboxes should all be inside the original bbox");
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
            assertTrue(bboxFoundConnections, "The bbox should connect to other bboxes");
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
