// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.PluginException;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAIPluginMock;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Territories;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * Test class for {@link MapWithAIUploadHook}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@Main
@MapWithAISources
@Projection
@Territories
@Wiremock
class MapWithAIUploadHookTest {

    private PluginInformation info;
    private MapWithAILayer aiLayer;
    private Way way1;
    private Way way2;
    private MapWithAIMoveAction action;
    private MapWithAIUploadHook hook;

    @BeforeAll
    static void beforeAll() {
        new MapWithAIPluginMock();
    }

    @BeforeEach
    void setUp() throws PluginException, IOException {
        try (InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))) {
            info = new PluginInformation(in, "MapWithAI", null);
            info.localversion = "no-such-version";
        }

        aiLayer = MapWithAIDataUtils.getLayer(true);
        OsmDataLayer osmLayer = new OsmDataLayer(new DataSet(), "no-name", null);
        MainApplication.getLayerManager().addLayer(osmLayer);

        way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        way1.getNodes().forEach(aiLayer.getDataSet()::addPrimitive);
        aiLayer.getDataSet().addPrimitive(way1);

        way2 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        way2.getNodes().forEach(aiLayer.getDataSet()::addPrimitive);
        aiLayer.getDataSet().addPrimitive(way2);

        action = new MapWithAIMoveAction();
        hook = new MapWithAIUploadHook(info);
    }

    /**
     * Test method for {@link MapWithAIUploadHook#modifyChangesetTags(Map)}.
     */
    @Test
    void testModifyChangesetTags() {
        Map<String, String> tags = new TreeMap<>();
        hook.modifyChangesetTags(tags);
        assertTrue(tags.isEmpty(), "Tags should be empty due to no primitives being added");

        aiLayer.getDataSet().setSelected(way1);
        action.actionPerformed(null);

        hook.modifyChangesetTags(tags);
        assertEquals(2, tags.size(), "Tags should not be empty due to adding primitives");
        assertEquals(Integer.toString(1), tags.get("mapwithai"),
                "mapwithai should equal 1, due to adding one primitive");
        assertTrue(
                Arrays.asList(tags.get("mapwithai:options").split(";")).contains("version=".concat(info.localversion)),
                "The version should be the localversion");

        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("False URL", "false-url"), true, false);

        tags.clear();

        aiLayer.getDataSet().addSelected(way2.firstNode());
        action.actionPerformed(null);
        hook.modifyChangesetTags(tags);

        assertEquals(Integer.toString(2), tags.get("mapwithai"), "Two objects have been added");
        assertEquals(2, tags.size(), "Tags should not be empty due to adding primitives");
        List<String> split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        assertEquals(2, split.size(), "There should be another option in mapwithai:options");
        assertTrue(split.contains("version=".concat(info.localversion)), "The version should match the local version");
        assertTrue(split.contains("url_ids=false-url"), "The false-url should be shown in the changeset tag");

        final int newMaxAdd = MapWithAIPreferenceHelper.getDefaultMaximumAddition() + 1;
        MapWithAIPreferenceHelper.setMaximumAddition(newMaxAdd, false);
        tags.clear();
        hook.modifyChangesetTags(tags);
        split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        assertEquals(3, split.size(), "There should be three ; in mapwithai:options");
        assertTrue(split.contains("version=".concat(info.localversion)), "The version should match the local version");
        assertTrue(split.contains("url_ids=false-url"), "The false-url should be shown in the changeset tag");
        assertTrue(split.contains("maxadd=" + newMaxAdd), "The maxadd should be " + newMaxAdd);

        Bounds tBounds = new Bounds(0, 1, 1, 0);
        MainApplication.getLayerManager()
                .addLayer(new GpxLayer(DetectTaskingManagerUtils.createTaskingManagerGpxData(tBounds),
                        DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA));

        tags.clear();
        hook.modifyChangesetTags(tags);
        split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        assertEquals(4, split.size(), "There should be four ; in mapwithai:options");
        assertTrue(split.contains("version=".concat(info.localversion)), "The version should match the local version");
        assertTrue(split.contains("url_ids=false-url"), "The false-url should be shown in the changeset tag");
        assertTrue(split.contains("maxadd=" + newMaxAdd), "The maxadd should be " + newMaxAdd);
        assertTrue(split.contains("task=".concat(tBounds.toBBox().toStringCSV(","))),
                "There should be a task in the mapwithai:options");
    }

    @Test
    void testLongUrls() {
        aiLayer.getDataSet().addSelected(way2.firstNode());
        action.actionPerformed(null);
        String superLongURL = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        MapWithAIPreferenceHelper.setMapWithAIUrl(new MapWithAIInfo("False URL", superLongURL), true, false);
        Map<String, String> tags = new TreeMap<>();
        hook.modifyChangesetTags(tags);
        assertTrue(tags.values().stream().mapToInt(String::length).max().orElse(256) <= 255);
        String fullTags = tags.entrySet().stream().filter(e -> e.getKey().contains("mapwithai:options"))
                .map(Map.Entry::getValue).collect(Collectors.joining());
        String url = fullTags.split("url_ids=", -1)[1];
        assertEquals(superLongURL, url);
    }

    @Test
    void testEmptyUrl() {
        MapWithAILayerInfo.getInstance().clear();
        // While this should never happen without significant effort on the part of the
        // user, we still handle this case.
        aiLayer.getDataSet().addSelected(way2.firstNode());
        action.actionPerformed(null);
        MapWithAIPreferenceHelper.setMapWithAIUrl(null, true, false);
        Map<String, String> tags = new TreeMap<>();
        hook.modifyChangesetTags(tags);
        assertTrue(tags.values().stream().noneMatch(s -> s.contains("url")));
    }
}
