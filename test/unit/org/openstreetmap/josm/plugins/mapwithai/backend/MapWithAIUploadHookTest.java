/**
 *
 */
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
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
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIUploadHookTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().main().projection();

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
        Assert.assertTrue(tags.isEmpty());
        hook.modifyChangesetTags(tags);
        Assert.assertTrue(tags.isEmpty());

        aiLayer.getDataSet().setSelected(way1);
        MapWithAIMoveAction action = new MapWithAIMoveAction();
        action.actionPerformed(null);

        hook.modifyChangesetTags(tags);
        Assert.assertEquals(2, tags.size());
        Assert.assertEquals(Integer.toString(1), tags.get("mapwithai"));
        Assert.assertTrue(
                Arrays.asList(tags.get("mapwithai:options").split(";")).contains("version=".concat(info.localversion)));

        MapWithAIPreferenceHelper.setMapWithAIUrl("False URL", "false-url", true, true);

        tags.clear();

        aiLayer.getDataSet().addSelected(way2.firstNode());
        action.actionPerformed(null);
        hook.modifyChangesetTags(tags);

        Assert.assertEquals(Integer.toString(2), tags.get("mapwithai"));
        Assert.assertEquals(2, tags.size());
        List<String> split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        Assert.assertEquals(2, split.size());
        Assert.assertTrue(split.contains("version=".concat(info.localversion)));
        Assert.assertTrue(split.contains("url=false-url"));

        MapWithAIPreferenceHelper.setMaximumAddition(20, false);
        tags.clear();
        hook.modifyChangesetTags(tags);
        split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        Assert.assertEquals(3, split.size());
        Assert.assertTrue(split.contains("version=".concat(info.localversion)));
        Assert.assertTrue(split.contains("url=false-url"));
        Assert.assertTrue(split.contains("maxadd=20"));

        BBox tBBox = new BBox(1, 0, 0, 1);
        MainApplication.getLayerManager()
                .addLayer(new GpxLayer(DetectTaskingManagerUtils.createTaskingManagerGpxData(tBBox),
                        DetectTaskingManagerUtils.MAPWITHAI_CROP_AREA));

        tags.clear();
        hook.modifyChangesetTags(tags);
        split = Arrays.asList(tags.get("mapwithai:options").split(";"));
        Assert.assertEquals(4, split.size());
        Assert.assertTrue(split.contains("version=".concat(info.localversion)));
        Assert.assertTrue(split.contains("url=false-url"));
        Assert.assertTrue(split.contains("maxadd=20"));
        Assert.assertTrue(split.contains("task=".concat(tBBox.toStringCSV(","))));
    }
}
