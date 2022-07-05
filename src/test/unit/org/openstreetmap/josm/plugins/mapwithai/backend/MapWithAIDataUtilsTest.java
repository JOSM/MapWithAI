// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertAll;
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.Logging;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test class for {@link MapWithAIDataUtils}
 *
 * @author Taylor Smock
 */
@NoExceptions
@BasicPreferences
@Wiremock
@MapWithAISources
public class MapWithAIDataUtilsTest {
    /** This is the default MapWithAI URL */
    private static final String DEFAULT_MAPWITHAI_API = "https://www.mapwith.ai/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox={bbox}";

    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    static JOSMTestRules test = new MapWithAITestRules().main().projection().fakeAPI().territories();

    /**
     * This gets data from MapWithAI. This test may fail if someone adds the data to
     * OSM.
     */
    @Test
    void testGetData() {
        final Bounds testBBox = getTestBounds();
        final DataSet ds = new DataSet(MapWithAIDataUtils.getData(Collections.singleton(testBBox),
                MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS));
        assertEquals(1, ds.getWays().size(), "There should only be one way in the testBBox");
    }

    /**
     * Test that getting multiple bboxes does not create an exception
     */
    @Test
    void testGetDataMultiple() {
        final Bounds testBounds1 = getTestBounds();
        final Bounds testBounds2 = new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314);
        final DataSet ds = new DataSet(MapWithAIDataUtils.getData(Arrays.asList(testBounds1, testBounds2),
                MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS));
        int expectedBounds = 2;
        assertEquals(expectedBounds, ds.getDataSourceBounds().size(), "There should be two data sources");
    }

    /**
     * This gets data from MapWithAI. This test may fail if someone adds the data to
     * OSM. Bounds: 39.0735906;-108.5710852;39.0736112;-108.5707442
     */
    @Test
    @Disabled("Flaky -- sometimes it passes, sometimes it doesn't")
    void testGetDataCropped() {
        final Bounds testBounds = getTestBounds();
        final GpxData gpxData = new GpxData();
        gpxData.addWaypoint(new WayPoint(new LatLon(39.0735205, -108.5711561)));
        gpxData.addWaypoint(new WayPoint(new LatLon(39.0736682, -108.5708568)));
        final GpxLayer gpx = new GpxLayer(gpxData, DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA);
        final DataSet originalData = MapWithAIDataUtils.getData(Collections.singleton(testBounds),
                MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS);
        assertAll(() -> assertEquals(1, originalData.getWays().size(), "There should be one way in the testBBox"),
                () -> assertEquals(4, originalData.getNodes().size(), "There should be four nodes in the testBBox"));
        MainApplication.getLayerManager().addLayer(gpx);
        final DataSet ds = MapWithAIDataUtils.getData(Collections.singleton(testBounds),
                MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS);
        assertAll(() -> assertEquals(1, ds.getWays().size(), "There should only be one way in the cropped testBBox"),
                () -> assertEquals(4, ds.getNodes().size(),
                        "There should be four nodes in the cropped testBBox due to the return"));
    }

    @Test
    void testAddSourceTags() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.1, 0.1)));
        final DataSet ds = new DataSet(way1.firstNode(), way1.lastNode(), way1);
        final String source = "random source";

        assertNull(way1.get("source"), "The source for the data should not be null");
        MapWithAIDataUtils.addSourceTags(ds, "highway", source);
        assertEquals(source, way1.get("source"), "The source for the data should be the specified source");
    }

    public static Bounds getTestBounds() {
        Bounds bound = new Bounds(new LatLon(39.0734162, -108.5707107));
        bound.extend(39.0738791, -108.5715723);
        return bound;
    }

    @Test
    void testAddPrimitivesToCollection() {
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0, 0.1)));
        final Collection<OsmPrimitive> collection = new TreeSet<>();
        MapWithAIDataUtils.addPrimitivesToCollection(collection, Collections.singletonList(way1));
        assertEquals(3, collection.size(), "The way has two child primitives, for a total of three primitives");
    }

    @Test
    void testRemovePrimitivesFromDataSet() {
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
    void testMapWithAIURLPreferences() {
        final String fakeUrl = "https://fake.url";
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().stream().noneMatch(map -> fakeUrl.equals(map.getUrl())),
                "fakeUrl shouldn't be in the current MapWithAI urls");
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("Fake", fakeUrl), true, true);
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().stream().anyMatch(map -> fakeUrl.equals(map.getUrl())),
                "fakeUrl should have been added");
        final List<MapWithAIInfo> urls = new ArrayList<>(MapWithAIPreferenceHelper.getMapWithAIUrl());
        assertEquals(2, urls.size(), "There should be two urls (fakeUrl and the default)");
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("MapWithAI", DEFAULT_MAPWITHAI_API), true, true);
        assertTrue(MapWithAIPreferenceHelper.getMapWithAIUrl().stream()
                .anyMatch(map -> DEFAULT_MAPWITHAI_API.equals(map.getUrl())), "The default URL should exist");
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("Fake2", fakeUrl), true, false);
        assertEquals(1, MapWithAIPreferenceHelper.getMapWithAIUrl().stream().filter(map -> fakeUrl.equals(map.getUrl()))
                .count(), "There should only be one fakeUrl");
    }

    @Test
    void testSplitBounds() {
        final Bounds bounds = new Bounds(0, 0, 0.0001, 0.0001);
        for (Double i : Arrays.asList(0.0001, 0.001, 0.01, 0.1)) {
            bounds.extend(i, i);
            List<BBox> bboxes = MapWithAIDataUtils.reduceBoundSize(bounds, 5_000).stream().map(Bounds::toBBox)
                    .collect(Collectors.toList());
            assertEquals(getExpectedNumberOfBBoxes(bounds), bboxes.size(), "The bbox should be appropriately reduced");
            checkInBBox(bounds.toBBox(), bboxes);
            checkBBoxesConnect(bounds.toBBox(), bboxes);
        }
    }

    @Test
    void testDoubleAddLayer() {
        Logging.clearLastErrorAndWarnings();
        assertNull(MapWithAIDataUtils.getLayer(false));
        assertNotNull(MapWithAIDataUtils.getLayer(true));
        assertNotNull(MapWithAIDataUtils.getLayer(true));
        Logging.getLastErrorAndWarnings().stream().filter(str -> !str.contains("Failed to locate image"))
                .forEach(Logging::error);
        assertEquals(0, Logging.getLastErrorAndWarnings().stream()
                .filter(str -> !str.contains("Failed to locate image")).count());
    }

    private static int getExpectedNumberOfBBoxes(Bounds bbox) {
        double width = MapWithAIDataUtils.getWidth(bbox);
        double height = MapWithAIDataUtils.getHeight(bbox);
        int widthDivisions = (int) Math.ceil(width / 5000);
        int heightDivisions = (int) Math.ceil(height / 5000);
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
