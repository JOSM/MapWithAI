// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.gui.preferences.advanced.MapListEditor;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.spi.preferences.MapListSetting;
import org.openstreetmap.josm.spi.preferences.Setting;
import org.openstreetmap.josm.spi.preferences.StringSetting;
import org.openstreetmap.josm.tools.Logging;

/**
 * Component for editing list of preferences as a table.
 *
 * @since 6021
 */
public class MapWithAIURLPreferenceTable extends JTable {
    private static final long serialVersionUID = -6221760175084963950L;
    private final URLTableModel model;
    private final transient List<DataUrl> displayData;

    /**
     * Constructs a new {@code PreferencesTable}.
     *
     * @param displayData The list of preferences entries to display
     */
    public MapWithAIURLPreferenceTable(List<DataUrl> displayData) {
        this.displayData = displayData;
        model = new URLTableModel();
        setModel(model);
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JosmTextField()));
        getColumnModel().getColumn(0)
                .setPreferredWidth(getFontMetrics(getFont()).stringWidth(MapWithAIPlugin.NAME) + 5);
        getColumnModel().getColumn(0).setMaxWidth(getColumnModel().getColumn(0).getPreferredWidth() * 2);
        getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JosmTextField()));
        getColumnModel().getColumn(2).setMaxWidth(getFontMetrics(getFont()).stringWidth("ENABLED"));
        getColumnModel().getColumn(3).setMaxWidth(getFontMetrics(getFont()).stringWidth("Parameters"));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editPreference(MapWithAIURLPreferenceTable.this);
                }
            }
        });
    }

    /**
     * This method should be called when displayed data was changed form external
     * code
     */
    public void fireDataChanged() {
        model.fireTableDataChanged();
    }

    /**
     * The list of currently selected rows
     *
     * @return newly created list of DataUrl
     */
    public List<DataUrl> getSelectedItems() {
        final List<DataUrl> entries = new ArrayList<>();
        for (final int row : getSelectedRows()) {
            final DataUrl p = displayData.get(row);
            entries.add(p);
        }
        return entries;
    }

    /**
     * Call this to edit selected row in preferences table
     *
     * @param gui - parent component for messagebox
     * @return true if editing was actually performed during this call
     */
    public boolean editPreference(final JComponent gui) {
        final int column = 3;
        if (getSelectedRowCount() != 1) {
            JOptionPane.showMessageDialog(gui, tr("Please select the row to edit."), tr("Warning"),
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        final Object e = model.getValueAt(getSelectedRow(), column);
        Setting<?> stg = e instanceof PrefEntry ? ((PrefEntry) e).getValue() : null;
        if (e instanceof JsonArray) {
            final JsonArray array = (JsonArray) e;
            final List<Map<String, String>> map = array.stream().filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast).map(obj -> {
                        final Map<String, String> tMap = new TreeMap<>();
                        obj.forEach((str, val) -> tMap.put(str, val.toString()));
                        return tMap;
                    }).collect(Collectors.toList());
            stg = new MapListSetting(map);
        }
        boolean ok = false;
        if ((stg instanceof StringSetting) || (e instanceof String) || (e instanceof Boolean)) {
            editCellAt(getSelectedRow(), getSelectedColumn());
            final Component editor = getEditorComponent();
            if (editor != null) {
                editor.requestFocus();
            }
        } else if (stg instanceof MapListSetting) {
            final PrefEntry pref = e instanceof PrefEntry ? (PrefEntry) e
                    : new PrefEntry("Parameters", stg, stg, false);
            ok = doEditMapList(gui, pref, (MapListSetting) stg);
            if (!e.equals(pref) && (e instanceof JsonArray)) {
                final JsonArrayBuilder array = Json.createArrayBuilder();
                ((MapListSetting) pref.getValue()).getValue()
                        .forEach(entry -> array.add(Json.createObjectBuilder(objectify(entry)).build()));
                model.setValueAt(array.build(), getSelectedRow(), column);
                fireDataChanged();
            }
        }
        return ok;
    }

    /**
     * Convert a map of Map&lt;String, String&gt; to a map of Map&lt;String,
     * Object&gt;
     *
     * @param map The map of strings to strings to convert (e.g., "1"-&gt;int 1,
     *            "true" -&gt; boolean true, etc)
     * @return A converted map
     */
    public static Map<String, Object> objectify(Map<String, String> map) {
        final Map<String, Object> returnMap = new TreeMap<>();
        for (final Entry<String, String> entry : map.entrySet()) {
            Object obj = null;
            if (entry.getValue().equalsIgnoreCase("true") || entry.getValue().equalsIgnoreCase("false")
                    || "enabled".equals(entry.getKey())) {
                obj = Boolean.parseBoolean(entry.getValue());
            } else if (entry.getValue().matches("[0-9.-]+")) {
                try {
                    obj = Double.parseDouble(entry.getValue());
                    obj = Integer.parseInt(entry.getValue());
                } catch (final NumberFormatException e) {
                    Logging.trace("{0}: {1}", MapWithAIPlugin.NAME, e);
                }
            } else {
                obj = entry.getValue().replaceAll("(^(\\\\*\\\"+)+|(\\\\*\\\"+)+$)", "");
            }
            returnMap.put(entry.getKey(), obj);
        }
        return returnMap;
    }

    private static boolean doEditMapList(final JComponent gui, final PrefEntry e, MapListSetting mlSetting) {
        final MapListEditor mlEditor = new MapListEditor(gui, e, mlSetting);
        mlEditor.showDialog();
        if (mlEditor.getValue() == 1) {
            final List<Map<String, String>> data = mlEditor.getData();
            if (!mlSetting.equalVal(data)) {
                e.setValue(new MapListSetting(data));
                return true;
            }
        }
        return false;
    }

    final class URLTableModel extends DefaultTableModel {
        private static final long serialVersionUID = 2435772137483913278L;

        URLTableModel() {
            super();
            setColumnIdentifiers(new String[] { tr("Source"), tr("URL"), tr("Enabled"), tr("Parameters") });
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return getRowCount() == 0 ? Object.class : getValueAt(0, column).getClass();
        }

        @Override
        public int getRowCount() {
            return displayData.size();
        }

        @Override
        public Object getValueAt(int row, int column) {
            return displayData.get(row).getDataList().get(column);
        }

        @Override
        public void setValueAt(Object o, int row, int column) {
            final List<Object> setList = new ArrayList<>(displayData.get(row).getDataList());
            if (!o.equals(setList.get(column))) {
                setList.remove(column);
                setList.add(column, o);
                displayData.get(row).setDataList(setList);
                if ((o instanceof String) && ((String) o).isEmpty()) {
                    displayData.get(row).reset();
                    displayData.remove(row);
                    fireTableRowsDeleted(row, row);
                } else {
                    fireTableCellUpdated(row, column);
                }
            }
        }
    }
}
