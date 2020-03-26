// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences;

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
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAIURLPreferenceTable;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAIURLPreferenceTable.URLTableModel;
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
        assertEquals(4, table.getModel().getColumnCount(), "There should be four columns");
        assertEquals(1, table.getModel().getRowCount(), "There is only one entry");
        assertFalse(dataUrls.isEmpty(), "The backing list should not be empty");
        assertSame(dataUrls.get(0).getMap().getOrDefault("source", "no-source-here"), table.getModel().getValueAt(0, 0),
                "The backing map and the table should have the same entries");

        dataUrls.add(0, new DataUrl("no-source", "no-url", true));

        table.fireDataChanged();
        assertEquals(4, table.getModel().getColumnCount(), "The column count should not change");
        assertEquals(2, table.getModel().getRowCount(), "An additional DataUrl was added");
        assertFalse(dataUrls.isEmpty(), "The backing list should not be empty");
        assertSame(dataUrls.get(0).getMap().getOrDefault("source", "no-source-here"), table.getModel().getValueAt(0, 0),
                "The backing map and table should have the same entries");
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
        assertTrue(objectifiedMap.get("source") instanceof String, "Source should be a string");
        assertTrue(objectifiedMap.get("enabled") instanceof Boolean, "Enabled should be a boolean");
        assertTrue(objectifiedMap.get("timeout") instanceof Integer, "Timeout should be an integer");
        assertTrue(objectifiedMap.get("maxnodedistance") instanceof Double, "Maxnodedistance should be a double");
    }

    /**
     * Test method for {@link MapWithAIURLPreferenceTable#getSelectedItems}.
     */
    @Test
    public void testGetSelectedItems() {
        List<DataUrl> dataUrls = new ArrayList<>(Arrays.asList(DataUrl.emptyData()));
        MapWithAIURLPreferenceTable table = new MapWithAIURLPreferenceTable(dataUrls);
        table.addRowSelectionInterval(0, 0);
        assertTrue(table.getSelectedItems().parallelStream().allMatch(dataUrls::contains),
                "All selected objects should be in dataUrls");
        assertEquals(1, table.getSelectedItems().size(), "There should only be one selected item");
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
            assertEquals(dataUrls.get(0).getDataList().get(i).getClass(), table.getModel().getColumnClass(i),
                    "The classes should match");
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
        assertEquals("New Source", dataUrls.get(0).getDataList().get(0),
                "The source should be set and passed through to the dataUrls");
        assertEquals(2, dataUrls.size(), "There should be two entries still");
        table.setValueAt("", 0, 0);
        assertSame(initial, dataUrls.get(0), "The \"initial\" dataUrl should be sorted to be first");
    }
}
