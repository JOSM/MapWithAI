// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Bounds;
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
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Logging;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIDataUtilsTest {
    /** This is the default MapWithAI URL */
    private static final String DEFAULT_MAPWITHAI_API = "https://www.mapwith.ai/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox={bbox}";

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new MapWithAITestRules().sources().wiremock().preferences().main().projection()
            .fakeAPI().territories();

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
     * Test that getting multiple bboxes does not create an exception
     */
    @Test
    public void testGetDataMultiple() {
        final BBox testBBox = getTestBBox();
        final BBox testBBox2 = new BBox(-108.4495519, 39.095376, -108.4422314, 39.0987811);
        final DataSet ds = new DataSet(MapWithAIDataUtils.getData(Arrays.asList(testBBox, testBBox2)));
        int expectedBounds = 2;
        assertEquals(expectedBounds, ds.getDataSourceBounds().size(), "There should be two data sources");
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
        return getTestBounds().toBBox();
    }

    public static Bounds getTestBounds() {
        Bounds bound = new Bounds(new LatLon(39.0734162, -108.5707107));
        bound.extend(39.0738791, -108.5715723);
        return bound;
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
    public void testMapWithAIURLPreferences() {
        final String fakeUrl = "https://fake.url";
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .noneMatch(map -> fakeUrl.equals(map.getUrl())), "fakeUrl shouldn't be in the current MapWithAI urls");
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("Fake", fakeUrl), true, true);
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> fakeUrl.equals(map.getUrl())), "fakeUrl should have been added");
        final List<MapWithAIInfo> urls = new ArrayList<>(MapWithAIPreferenceHelper.getMapWithAIUrl());
        assertEquals(2, urls.size(), "There should be two urls (fakeUrl and the default)");
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("MapWithAI", DEFAULT_MAPWITHAI_API), true, true);
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .anyMatch(map -> DEFAULT_MAPWITHAI_API.equals(map.getUrl())), "The default URL should exist");
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("Fake2", fakeUrl), true, false);
        assertEquals(1, MapWithAIPreferenceHelper.getMapWithAIUrl().parallelStream()
                .filter(map -> fakeUrl.equals(map.getUrl())).count(), "There should only be one fakeUrl");
    }

    @Test
    public void testSplitBounds() {
        final BBox bbox = new BBox(0, 0, 0.0001, 0.0001);
        for (Double i : Arrays.asList(0.0001, 0.001, 0.01, 0.1)) {
            bbox.add(i, i);
            List<BBox> bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox, 5_000);
            assertEquals(getExpectedNumberOfBBoxes(bbox, 5_000), bboxes.size(),
                    "The bbox should be appropriately reduced");
            checkInBBox(bbox, bboxes);
            checkBBoxesConnect(bbox, bboxes);
        }
    }

    @Test
    public void testDoubleAddLayer() {
        Logging.clearLastErrorAndWarnings();
        assertNull(MapWithAIDataUtils.getLayer(false));
        assertNotNull(MapWithAIDataUtils.getLayer(true));
        assertNotNull(MapWithAIDataUtils.getLayer(true));
        Logging.getLastErrorAndWarnings().stream().filter(str -> !str.contains("Failed to locate image"))
                .forEach(Logging::error);
        assertTrue(Logging.getLastErrorAndWarnings().stream().filter(str -> !str.contains("Failed to locate image"))
                .collect(Collectors.toList()).isEmpty());
    }

    private static int getExpectedNumberOfBBoxes(BBox bbox, int maximumDimensions) {
        double width = MapWithAIDataUtils.getWidth(bbox);
        double height = MapWithAIDataUtils.getHeight(bbox);
        int widthDivisions = (int) Math.ceil(width / maximumDimensions);
        int heightDivisions = (int) Math.ceil(height / maximumDimensions);
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
