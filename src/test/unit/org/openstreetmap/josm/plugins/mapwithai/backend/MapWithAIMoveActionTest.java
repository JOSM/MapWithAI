// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.commands.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.commands.DuplicateCommand;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAIPluginMock;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MissingConnectionTagsMocker;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Territories;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mockit.Mock;
import mockit.MockUp;

@BasicPreferences
@Projection
@Wiremock
class MapWithAIMoveActionTest {
    private MapWithAIMoveAction moveAction;
    private DataSet mapWithAIData;
    private OsmDataLayer osmLayer;
    private Way way1;
    private Way way2;
    private Node way1LastNode;
    private Node way2LastNode;

    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new MapWithAITestRules().main().territories().assertionsInEDT();

    @BeforeAll
    static void beforeAll() {
        new MapWithAIPluginMock();
        // TODO remove with @Territories annotation
        Territories.initialize();
    }

    @BeforeEach
    void setUp() {
        moveAction = new MapWithAIMoveAction();
        final DataSet osmData = new DataSet();
        mapWithAIData = new DataSet();
        way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0.1, 0.1)));
        way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(-0.1, -0.1)),
                new Node(new LatLon(0.1, 0.1)));
        way1.getNodes().forEach(node -> mapWithAIData.addPrimitive(node));
        way2.getNodes().forEach(osmData::addPrimitive);
        osmData.addPrimitive(way2);
        mapWithAIData.addPrimitive(way1);
        way2.setOsmId(1, 1);
        Objects.requireNonNull(way2.firstNode()).setOsmId(1, 1);
        Objects.requireNonNull(way2.lastNode()).setOsmId(2, 1);

        osmLayer = new OsmDataLayer(osmData, "osm", null);
        final MapWithAILayer mapWithAILayer = new MapWithAILayer(mapWithAIData, "MapWithAI", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        MainApplication.getLayerManager().addLayer(mapWithAILayer);
        MainApplication.getLayerManager().setActiveLayer(mapWithAILayer);
        way1LastNode = way1.lastNode();
        way2LastNode = way2.lastNode();
        assertNotNull(way1LastNode);
        assertNotNull(way2LastNode);
    }

    @Test
    void testMoveAction() {
        new MissingConnectionTagsMocker();

        mapWithAIData.addSelected(way1);
        moveAction.actionPerformed(null);
        assertEquals(osmLayer, MainApplication.getLayerManager().getActiveLayer(),
                "Current layer should be the OMS layer");
        assertNotNull(osmLayer.getDataSet().getPrimitiveById(way1), "way1 should have been added to the OSM layer");
        while (UndoRedoHandler.getInstance().hasUndoCommands()) {
            UndoRedoHandler.getInstance().undo();
        }
        assertNull(osmLayer.getDataSet().getPrimitiveById(way1), "way1 should have been removed from the OSM layer");
    }

    @Test
    void testMoveEmptyAction() {
        assertDoesNotThrow(() -> moveAction.actionPerformed(null));
    }

    @Test
    void testConflationDupeKeyRemoval() {
        new MissingConnectionTagsMocker();
        mapWithAIData.unlock();
        way1LastNode.put(DuplicateCommand.KEY, "n" + way2LastNode.getUniqueId());
        mapWithAIData.lock();
        mapWithAIData.addSelected(way1);
        final DataSet ds = osmLayer.getDataSet();

        moveAction.actionPerformed(null);
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> ds.getPrimitiveById(way1) != null);
        final Node way1LastNodeDs = ((Way) ds.getPrimitiveById(way1)).lastNode();
        final Node way2LastNodeDs = ((Way) ds.getPrimitiveById(way2)).lastNode();
        assertNotNull(way1LastNodeDs);
        assertNotNull(way2LastNodeDs);
        assertEquals(way1LastNodeDs, way2LastNodeDs, "The duplicate node should have been replaced");
        assertFalse(way2LastNodeDs.hasKey(DuplicateCommand.KEY), "The dupe key should no longer exist");
        assertFalse(way1LastNodeDs.hasKey(DuplicateCommand.KEY), "The dupe key should no longer exist");

        UndoRedoHandler.getInstance().undo();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> !way2LastNodeDs.hasKey(DuplicateCommand.KEY));
        assertFalse(way2LastNode.hasKey(DuplicateCommand.KEY), "The dupe key should no longer exist");
        assertTrue(way1LastNode.hasKey(DuplicateCommand.KEY), "The dupe key should no longer exist");
    }

    @Test
    void testConflationConnKeyRemoval() {
        new MissingConnectionTagsMocker();
        mapWithAIData.unlock();
        final Node way2FirstNode = way2.firstNode();
        assertNotNull(way2FirstNode);
        way1LastNode.put(ConnectedCommand.KEY,
                "w" + way2.getUniqueId() + ",n" + way2LastNode.getUniqueId() + ",n" + way2FirstNode.getUniqueId());
        mapWithAIData.lock();
        mapWithAIData.addSelected(way1);

        moveAction.actionPerformed(null);
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> way1.isDeleted());
        assertFalse(way2LastNode.hasKey(ConnectedCommand.KEY), "The conn key should have been removed");
        assertFalse(way2FirstNode.hasKey(ConnectedCommand.KEY), "The conn key should have been removed");
        assertFalse(way2.getNode(1).hasKey(ConnectedCommand.KEY), "The conn key should have been removed");
        assertTrue(way1.isDeleted(), "way1 should be deleted when added");

        UndoRedoHandler.getInstance().undo();
        Awaitility.await().atMost(Durations.ONE_SECOND).until(() -> !way1.isDeleted() && !way1LastNode.isDeleted());
        assertFalse(way2LastNode.hasKey(ConnectedCommand.KEY), "The conn key shouldn't exist");
        assertTrue(way1LastNode.hasKey(ConnectedCommand.KEY), "The conn key should exist");
        assertFalse(way1LastNode.isDeleted(), "way1 should no longer be deleted");
    }

    private static class NotificationMocker extends MockUp<Notification> {
        boolean shown;

        @Mock
        void show() {
            shown = true;
        }
    }

    @Test
    void testMaxAddNotification() {
        TestUtils.assumeWorkingJMockit();
        new WindowMocker();
        new MissingConnectionTagsMocker();

        NotificationMocker notification = new NotificationMocker();
        DataSet ds = MapWithAIDataUtils.getLayer(true).getDataSet();
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "TEST", null));
        MapWithAIPreferenceHelper.setMaximumAddition(1, false);
        for (int i = 0; i < 40; i++) {
            ds.addPrimitive(new Node(LatLon.ZERO));
        }
        for (int i = 0; i < 11; i++) {
            GuiHelper
                    .runInEDTAndWaitWithException(() -> ds.setSelected(ds.allNonDeletedPrimitives().iterator().next()));
            moveAction.actionPerformed(null);
        }
        assertTrue(notification.shown);
        notification.shown = false;
    }

    /**
     * This is a non-regression test. There used to be an AssertionError when an
     * address and building were added, and then was undone. See
     * <a href="https://gitlab.com/gokaart/JOSM_MapWithAI/-/issues/79">Issue #79</a>
     */
    @Test
    void testBuildingAndAddressAdd() {
        DataSet ds = MapWithAIDataUtils.getLayer(true).getDataSet();
        Way building = TestUtils.newWay("building=yes", new Node(new LatLon(38.236811, -104.62571)),
                new Node(new LatLon(38.236811, -104.625493)), new Node(new LatLon(38.236716, -104.625493)),
                new Node(new LatLon(38.236716, -104.62571)));
        building.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(building);
        building.addNode(building.firstNode());
        Node address = TestUtils.newNode(
                "addr:city=Pueblo addr:housenumber=1901 addr:postcode=81004 addr:street=\"Lake Avenue\" mapwithai:source=\"Statewide Aggregate Addresses in Colorado 2019 (Public)\"");
        address.setCoor(new LatLon(38.2367599, -104.6255641));
        ds.addPrimitive(address);

        DataSet ds2 = new DataSet();
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(ds2, "TEST LAYER", null));

        // The building/address need to be selected separately
        // This is due to only allowing 1 additional selection at a time.
        ds.setSelected(building);
        ds.addSelected(address);
        GuiHelper.runInEDTAndWaitWithException(() -> moveAction.actionPerformed(null));
        while (UndoRedoHandler.getInstance().hasUndoCommands()) {
            assertDoesNotThrow(() -> UndoRedoHandler.getInstance().undo());
        }
    }

    @Test
    void testAddSimplifiedWay() {
        Node ma1 = new Node(new LatLon(39.1210737, -108.6162804));
        Node ma2 = new Node(new LatLon(39.1210363, -108.6162804));
        Node ma3 = new Node(new LatLon(39.1210196, -108.6162804));
        Node ma4 = new Node(new LatLon(39.1209364, -108.6162804));
        Node ma5 = new Node(new LatLon(39.1208031, -108.6163033));
        ma5.put("dupe", "n7041074564");
        Way ma1w = TestUtils.newWay("highway=residential mapwithai:source=MapWithAI source=digitalglobe", ma1, ma2, ma3,
                ma4, ma5);

        Node osm1 = new Node(new LatLon(39.1208025, -108.6173585));
        osm1.setOsmId(176255324L, 5);
        Node osm2 = TestUtils.newNode("maxspeed=\"35 mph\" traffic_sign:forward=yes traffic_sign=maxspeed");
        osm2.setCoor(new LatLon(39.1208031, -108.6163033));
        osm2.setOsmId(7041074564L, 1);
        Node osm3 = TestUtils.newNode("crossing=uncontrolled highway=crossing");
        osm3.setCoor(new LatLon(39.1208035, -108.6155962));
        osm3.setOsmId(7050673431L, 1);
        Way osm1w = TestUtils.newWay(
                "highway=unclassified lanes=2 maxspeed=\"35 mph\" name=\"H Road\" ref=H surface=asphalt", osm1, osm2,
                osm3);
        osm1w.setOsmId(753597166L, 6);

        DataSet osm = new DataSet();
        osm1w.getNodes().forEach(osm::addPrimitive);
        osm.addPrimitive(osm1w);

        DataSet mapwithai = new DataSet();
        ma1w.getNodes().forEach(mapwithai::addPrimitive);
        mapwithai.addPrimitive(ma1w);

        OsmDataLayer osmDataLayer = new OsmDataLayer(osm, "OSM Layer", null);
        MapWithAILayer mapwithaiLayer = new MapWithAILayer(mapwithai, "MapWithAI", null);

        MainApplication.getLayerManager().addLayer(osmDataLayer);
        MainApplication.getLayerManager().addLayer(mapwithaiLayer);
        MainApplication.getLayerManager().setActiveLayer(mapwithaiLayer);
        mapwithai.setSelected(Collections.singleton(ma1w));

        Config.getPref().put("simplify-way.auto.remember", "yes");
        Logging.clearLastErrorAndWarnings();
        assertDoesNotThrow(() -> this.moveAction.actionPerformed(null));
        assertTrue(Logging.getLastErrorAndWarnings().isEmpty(),
                Logging.getLastErrorAndWarnings().isEmpty() ? "" : Logging.getLastErrorAndWarnings().get(0));
        assertTrue(osm.allPrimitives().size() > 4);
        assertTrue(osm.allPrimitives().stream().anyMatch(p -> p.hasTag("source", "digitalglobe")));
    }

    /**
     * Non-regression test for #22760: IAE when merging address and building
     * features
     */
    @Test
    void testNonRegression22760() {
        final Node originalAddrNode = TestUtils.newNode("addr:city=Topeka\naddr:housenumber=1824\naddr:postcode=66615\n"
                + "addr:street=Southwest Stutley Road\nbuilding=residential\nlbcs:activity:code=1100\n"
                + "lbcs:activity:name=Household activities\nlbcs:function:code=1101\nlbcs:function:name=Single family residence (detached)");
        final Way mwaiBuilding1 = TestUtils.newWay(
                "building=yes mapwithai:source=MapWithAI source=microsoft/BuildingFootprints",
                new Node(new LatLon(39.0331214, -95.7912026)), new Node(new LatLon(39.0331129, -95.7909617)),
                new Node(new LatLon(39.0331516, -95.7909595)), new Node(new LatLon(39.0331495, -95.7909006)),
                new Node(new LatLon(39.0332057, -95.7908973)), new Node(new LatLon(39.0332074, -95.7909467)),
                new Node(new LatLon(39.0332864, -95.7909421)), new Node(new LatLon(39.0332909, -95.7910693)),
                new Node(new LatLon(39.0331869, -95.7910754)), new Node(new LatLon(39.0331912, -95.7911985)));
        // This is an exact duplicate of building 1
        final Way mwaiBuilding2 = TestUtils.newWay("", mwaiBuilding1.getNodes().toArray(new Node[0]));
        final Node mwaiAddrNode = TestUtils.newNode(
                "addr:city=Unincorporated\n" + "addr:housenumber=1824\n" + "addr:postcode=66615\n" + "addr:state=KS\n"
                        + "addr:street=Southwest Stutley Road\n" + "building=yes\n" + "source=esri_USDOT_Kansas");
        final DataSet osm = new DataSet();
        final DataSet mwai = new DataSet();
        final OsmDataLayer osmLayer = new OsmDataLayer(osm, "testNonRegression22760", null);
        final MapWithAILayer mapWithAILayer = new MapWithAILayer(mwai, "testNonRegression22760", null);
        MainApplication.getLayerManager().addLayer(osmLayer);
        MainApplication.getLayerManager().addLayer(mapWithAILayer);
        MainApplication.getLayerManager().setActiveLayer(mapWithAILayer);
        originalAddrNode.setCoor(new LatLon(39.0331818, -95.7910286));
        originalAddrNode.setOsmId(2081834687, 3);
        mwaiAddrNode.setCoor(new LatLon(39.0331883, -95.7910057));
        mwaiBuilding1.addNode(mwaiBuilding1.firstNode());
        mwaiBuilding2.setNodes(mwaiBuilding1.getNodes());
        mwaiBuilding2.putAll(mwaiBuilding1.getKeys());
        osm.addPrimitive(originalAddrNode);
        mwai.addPrimitiveRecursive(mwaiBuilding1);
        mwai.addPrimitive(mwaiBuilding2);
        mwai.addPrimitive(mwaiAddrNode);
        UndoRedoHandler.getInstance()
                .add(DeleteCommand.delete(Collections.singletonList(originalAddrNode), true, true));
        while (mwai.getSelected().size() < mwai.allNonDeletedPrimitives().size()) {
            GuiHelper.runInEDTAndWait(() -> mwai.setSelected(mwai.allPrimitives()));
        }

        final List<LogRecord> logs = new ArrayList<>();
        final Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {
                // Do nothing
            }

            @Override
            public void close() {
                // Do nothing
            }
        };
        Logging.getLogger().addHandler(testHandler);
        try {
            this.moveAction.actionPerformed(null);
            assertAll(logs.stream().map(logRecord -> () -> fail(logRecord.getThrown())));
        } finally {
            Logging.getLogger().removeHandler(testHandler);
        }
        assertEquals(2, osm.getWays().size(), "Both buildings should be added, even though one is a duplicate. "
                + "If deduplicate buildings in the future, this test should be updated.");
        final Way actualWay = osm.getWays().stream().filter(way -> way.getNumKeys() > 3).findFirst()
                .orElseThrow(() -> new AssertionError("Could not find way"));
        // Just spot check the tags.
        assertEquals("Unincorporated", actualWay.get("addr:city"));
        assertEquals("yes", actualWay.get("building"));
        assertEquals(10, osm.getNodes().stream().filter(node -> !node.isDeleted()).count());
        assertAll(osm.getNodes().stream().filter(node -> !node.isDeleted())
                .map(node -> () -> assertTrue(node.getParentWays().contains(actualWay))));
    }
}
