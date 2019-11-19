// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIURLPreferenceTable.URLTableModel;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIURLPreferenceTableTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules();

    /**
     * Test method for
     * {@link MapWithAIURLPreferenceTable#MapWithAIURLPreferenceTable}.
     */
    @Test
    public void testMapWithAIURLPreferenceTable() {
        List<DataUrl> dataUrls = new ArrayList<>(Arrays.asList(DataUrl.emptyData()));
        MapWithAIURLPreferenceTable table = new MapWithAIURLPreferenceTable(dataUrls);
        assertEquals(4, table.getModel().getColumnCount());
        assertEquals(1, table.getModel().getRowCount());
        assertFalse(dataUrls.isEmpty());
        assertSame(dataUrls.get(0).getMap().getOrDefault("source", "no-source-here"),
                table.getModel().getValueAt(0, 0));

        dataUrls.add(0, new DataUrl("no-source", "no-url", true));

        table.fireDataChanged();
        assertEquals(4, table.getModel().getColumnCount());
        assertEquals(2, table.getModel().getRowCount());
        assertFalse(dataUrls.isEmpty());
        assertSame(dataUrls.get(0).getMap().getOrDefault("source", "no-source-here"),
                table.getModel().getValueAt(0, 0));
    }

    /**
     * Test method for {@link MapWithAIURLPreferenceTable#objectify}
     */
    @Test
    public void testObjectify() {
        Map<String, String> map = new TreeMap<>();
        map.put("source", "this-is-a-string");
        map.put("enabled", Boolean.FALSE.toString());
        map.put("timeout", Integer.toString(50));
        map.put("maxnodedistance", Double.toString(1.2));
        Map<String, Object> objectifiedMap = MapWithAIURLPreferenceTable.objectify(map);
        assertTrue(objectifiedMap.get("source") instanceof String);
        assertTrue(objectifiedMap.get("enabled") instanceof Boolean);
        assertTrue(objectifiedMap.get("timeout") instanceof Integer);
        assertTrue(objectifiedMap.get("maxnodedistance") instanceof Double);
    }

    /**
     * Test method for {@link MapWithAIURLPreferenceTable#getSelectedItems}.
     */
    @Test
    public void testGetSelectedItems() {
        List<DataUrl> dataUrls = new ArrayList<>(Arrays.asList(DataUrl.emptyData()));
        MapWithAIURLPreferenceTable table = new MapWithAIURLPreferenceTable(dataUrls);
        table.addRowSelectionInterval(0, 0);
        assertTrue(table.getSelectedItems().parallelStream().allMatch(dataUrls::contains));
        assertEquals(1, table.getSelectedItems().size());
    }

    /**
     * Test method for
     * {@link MapWithAIURLPreferenceTable.URLTableModel#getColumnClass}
     */
    @Test
    public void testGetColumnClasses() {
        List<DataUrl> dataUrls = new ArrayList<>(Arrays.asList(DataUrl.emptyData()));
        MapWithAIURLPreferenceTable table = new MapWithAIURLPreferenceTable(dataUrls);
        for (int i = 0; i < dataUrls.get(0).getDataList().size(); i++) {
            assertEquals(dataUrls.get(0).getDataList().get(i).getClass(), table.getModel().getColumnClass(i));
        }
    }

    /**
     * Test method for {@link MapWithAIURLPreferenceTable.URLTableModel#setValueAt}
     */
    @Test
    public void testSetValueAt() {
        List<DataUrl> dataUrls = new ArrayList<>(Arrays.asList(DataUrl.emptyData()));
        MapWithAIURLPreferenceTable.URLTableModel table = (URLTableModel) new MapWithAIURLPreferenceTable(dataUrls)
                .getModel();
        DataUrl initial = DataUrl.emptyData(); // Don't need to clone the "current" first entry
        dataUrls.add(initial);
        table.setValueAt("New Source", 0, 0);
        assertEquals("New Source", dataUrls.get(0).getDataList().get(0));
        assertEquals(2, dataUrls.size());
        table.setValueAt("", 0, 0);
        assertSame(initial, dataUrls.get(0));
    }
}
