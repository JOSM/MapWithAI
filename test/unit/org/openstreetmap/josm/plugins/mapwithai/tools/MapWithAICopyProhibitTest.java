// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.CopyAction;
import org.openstreetmap.josm.actions.PasteAction;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import mockit.Mock;
import mockit.MockUp;

/**
 * Test class for {@link MapWithAICopyProhibit}
 *
 * @author Taylor Smock
 */
@BasicPreferences
class MapWithAICopyProhibitTest {
    private static class BlacklistUtilsMock extends MockUp<BlacklistUtils> {
        @Mock
        public static boolean isBlacklisted() {
            return false;
        }
    }

    // preferences for nodes, main for actions, projection for mapview
    @RegisterExtension
    JOSMTestRules josmTestRules = new JOSMTestRules().main().projection();

    @Test
    void testDestroyable() {
        MapWithAICopyProhibit mapWithAICopyProhibit = new MapWithAICopyProhibit();
        assertDoesNotThrow(
                () -> MainApplication.getLayerManager().removeActiveLayerChangeListener(mapWithAICopyProhibit));
        MainLayerManager layerManager = MainApplication.getLayerManager();
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                () -> layerManager.removeActiveLayerChangeListener(mapWithAICopyProhibit));
        assertEquals("Attempted to remove listener that was not in list: " + mapWithAICopyProhibit,
                illegalArgumentException.getMessage());
        layerManager.addActiveLayerChangeListener(mapWithAICopyProhibit);
        mapWithAICopyProhibit.destroy();
        illegalArgumentException = assertThrows(IllegalArgumentException.class,
                () -> layerManager.removeActiveLayerChangeListener(mapWithAICopyProhibit));
        assertEquals("Attempted to remove listener that was not in list: " + mapWithAICopyProhibit,
                illegalArgumentException.getMessage());
    }

    @Test
    void testCopyProhibit() {
        TestUtils.assumeWorkingJMockit();
        new WindowMocker();
        new BlacklistUtilsMock();

        MainLayerManager layerManager = MainApplication.getLayerManager();
        OsmDataLayer osmDataLayer = new OsmDataLayer(new DataSet(), "TEST", null);
        MapWithAILayer mapWithAILayer = new MapWithAILayer(new DataSet(), "TEST", null);
        layerManager.addLayer(osmDataLayer);
        layerManager.addLayer(mapWithAILayer);
        DataSet mapWithAIDataSet = mapWithAILayer.getDataSet();
        Node testNode = new Node(LatLon.ZERO);
        mapWithAIDataSet.addPrimitive(testNode);
        mapWithAIDataSet.setSelected(testNode);
        layerManager.setActiveLayer(mapWithAILayer);

        CopyAction copyAction = new CopyAction();
        copyAction.actionPerformed(null);
        PasteAction pasteAction = new PasteAction();

        assertEquals(1, mapWithAIDataSet.allPrimitives().size());
        pasteAction.actionPerformed(null);
        assertEquals(2, mapWithAIDataSet.allPrimitives().size());
        pasteAction.actionPerformed(null);
        assertEquals(3, mapWithAIDataSet.allPrimitives().size());

        layerManager.setActiveLayer(osmDataLayer);
        assertEquals(0, osmDataLayer.getDataSet().allPrimitives().size());
        for (int i = 0; i < 10; i++) {
            pasteAction.actionPerformed(null);
            assertEquals(0, osmDataLayer.getDataSet().allPrimitives().size());
        }
    }
}
