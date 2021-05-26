// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.PluginException;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIUploadHookTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new MapWithAITestRules().sources().wiremock().main().projection().preferences()
            .territories();

    /**
     * Test method for {@link MapWithAIUploadHook#modifyChangesetTags(Map)}.
     *
     * @throws PluginException
     */
    @Test
    public void testModifyChangesetTags() throws PluginException {
        final InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        PluginInformation info = new PluginInformation(in, "MapWithAI", null);
        info.localversion = "no-such-version";

        MapWithAIUploadHook hook = new MapWithAIUploadHook(info);

        MapWithAILayer aiLayer = MapWithAIDataUtils.getLayer(true);
        OsmDataLayer osmLayer = new OsmDataLayer(new DataSet(), "no-name", null);
        MainApplication.getLayerManager().addLayer(osmLayer);

        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        way1.getNodes().forEach(aiLayer.getDataSet()::addPrimitive);
        aiLayer.getDataSet().addPrimitive(way1);

        Way way2 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
        way2.getNodes().forEach(aiLayer.getDataSet()::addPrimitive);
        aiLayer.getDataSet().addPrimitive(way2);

        Map<String, String> tags = new TreeMap<>();
        hook.modifyChangesetTags(tags);
        assertTrue(tags.isEmpty(), "Tags should be empty due to no primitives being added");

        aiLayer.getDataSet().setSelected(way1);
        MapWithAIMoveAction action = new MapWithAIMoveAction();
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

        MapWithAIPreferenceHelper.setMaximumAddition(20, false);
        tags.clear();
        hook.modifyChangesetTags(tags);
        split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        assertEquals(3, split.size(), "There should be three ; in mapwithai:options");
        assertTrue(split.contains("version=".concat(info.localversion)), "The version should match the local version");
        assertTrue(split.contains("url_ids=false-url"), "The false-url should be shown in the changeset tag");
        assertTrue(split.contains("maxadd=20"), "The maxadd should be 20");

        BBox tBBox = new BBox(1, 0, 0, 1);
        MainApplication.getLayerManager()
                .addLayer(new GpxLayer(DetectTaskingManagerUtils.createTaskingManagerGpxData(tBBox),
                        DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA));

        tags.clear();
        hook.modifyChangesetTags(tags);
        split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        assertEquals(4, split.size(), "There should be four ; in mapwithai:options");
        assertTrue(split.contains("version=".concat(info.localversion)), "The version should match the local version");
        assertTrue(split.contains("url_ids=false-url"), "The false-url should be shown in the changeset tag");
        assertTrue(split.contains("maxadd=20"), "The maxadd should be 20");
        assertTrue(split.contains("task=".concat(tBBox.toStringCSV(","))),
                "There should be a task in the mapwithai:options");
    }
}
