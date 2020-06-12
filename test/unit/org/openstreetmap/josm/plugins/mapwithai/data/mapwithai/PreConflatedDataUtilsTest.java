// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * Test case for {@link PreConflatedDataUtils}
 *
 * @author Taylor Smock
 *
 */
public class PreConflatedDataUtilsTest {
    @Rule
    public JOSMTestRules rules = new JOSMTestRules().preferences().projection();
    private DataSet ds;

    @Before
    public void setUp() {
        MapWithAILayer layer = MapWithAIDataUtils.getLayer(true);
        ds = layer.getDataSet();
        Node node1 = new Node(LatLon.ZERO);
        Node node2 = new Node(LatLon.NORTH_POLE);
        node1.put(PreConflatedDataUtils.getConflatedKey(), "true");
        ds.addPrimitive(node1);
        ds.addPrimitive(node2);
        Config.getPref().put(PreConflatedDataUtils.PREF_KEY, null);
    }

    @Test
    public void testRemoveConflatedData() {
        MapWithAIInfo info = new MapWithAIInfo();
        info.setAlreadyConflatedKey("test_conflation");
        ds.addPrimitive(TestUtils.newNode("test_conflation=test"));
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        PreConflatedDataUtils.removeConflatedData(ds, info);
        assertEquals(0, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        assertEquals(2,
                ds.allPrimitives().stream().filter(p -> p.hasTag(PreConflatedDataUtils.getConflatedKey())).count());
    }

    @Test
    public void testRemoveConflatedDataNoKey() {
        MapWithAIInfo info = new MapWithAIInfo();
        ds.addPrimitive(TestUtils.newNode("test_conflation=test"));
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        PreConflatedDataUtils.removeConflatedData(ds, info);
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        assertEquals(1,
                ds.allPrimitives().stream().filter(p -> p.hasTag(PreConflatedDataUtils.getConflatedKey())).count());
    }

    @Test
    public void testRemoveConflatedDataEmptyKey() {
        MapWithAIInfo info = new MapWithAIInfo();
        info.setAlreadyConflatedKey(" ");
        ds.addPrimitive(TestUtils.newNode("test_conflation=test"));
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        PreConflatedDataUtils.removeConflatedData(ds, info);
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        assertEquals(1,
                ds.allPrimitives().stream().filter(p -> p.hasTag(PreConflatedDataUtils.getConflatedKey())).count());
    }

    @Test
    public void testRemoveConflatedDataNull() {
        ds.addPrimitive(TestUtils.newNode("test_conflation=test"));
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        PreConflatedDataUtils.removeConflatedData(ds, null);
        assertEquals(1, ds.allPrimitives().stream().filter(p -> p.hasTag("test_conflation")).count());
        assertEquals(1,
                ds.allPrimitives().stream().filter(p -> p.hasTag(PreConflatedDataUtils.getConflatedKey())).count());
    }

    @Test
    public void testHideConflatedData() {
        PreConflatedDataUtils.hideConflatedData(ds);
        assertEquals(1, ds.allPrimitives().stream().filter(OsmPrimitive::isDisabled).count());
        Config.getPref().putBoolean(PreConflatedDataUtils.PREF_KEY, false);
        PreConflatedDataUtils.hideConflatedData(ds);
        assertEquals(0, ds.allPrimitives().stream().filter(OsmPrimitive::isDisabled).count());
    }

    @Test
    public void testPreferenceChanged() {
        PreConflatedDataUtils util = new PreConflatedDataUtils();
        PreConflatedDataUtils.hideConflatedData(ds);
        assertEquals(1, ds.allPrimitives().stream().filter(OsmPrimitive::isDisabled).count());
        Config.getPref().putBoolean(PreConflatedDataUtils.PREF_KEY, false);
        assertEquals(0, ds.allPrimitives().stream().filter(OsmPrimitive::isDisabled).count());
        util.destroy();
    }

    @Test
    public void testDestroy() {
        new PreConflatedDataUtils().destroy();
        Config.getPref().putBoolean(PreConflatedDataUtils.PREF_KEY, true);
        assertEquals(0, ds.allPrimitives().stream().filter(OsmPrimitive::isDisabled).count());
    }

}
