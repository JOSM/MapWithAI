// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.preferences.advanced.ListEditor;
import org.openstreetmap.josm.gui.preferences.advanced.ListListEditor;
import org.openstreetmap.josm.gui.preferences.advanced.MapListEditor;
import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.gui.preferences.advanced.StringEditor;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.spi.preferences.ListListSetting;
import org.openstreetmap.josm.spi.preferences.ListSetting;
import org.openstreetmap.josm.spi.preferences.MapListSetting;
import org.openstreetmap.josm.spi.preferences.Setting;
import org.openstreetmap.josm.spi.preferences.StringSetting;
import org.openstreetmap.josm.tools.GBC;

/**
 * Component for editing list of preferences as a table.
 * @since 6021
 */
public class MapWithAIURLPreferenceTable extends JTable {
    private static final long serialVersionUID = -6221760175084963950L;
    private final URLTableModel model;
    private final transient List<DataUrl> displayData;

    /**
     * Constructs a new {@code PreferencesTable}.
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
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editPreference(MapWithAIURLPreferenceTable.this);
                }
            }
        });
    }

    /**
     * This method should be called when displayed data was changed form external code
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
        List<DataUrl> entries = new ArrayList<>();
        for (int row : getSelectedRows()) {
            DataUrl p = displayData.get(row);
            entries.add(p);
        }
        return entries;
    }

    /**
     * Call this to edit selected row in preferences table
     * @param gui - parent component for messagebox
     * @return true if editing was actually performed during this call
     */
    public boolean editPreference(final JComponent gui) {
        final int column = 3;
        if (getSelectedRowCount() != 1) {
            JOptionPane.showMessageDialog(
                    gui,
                    tr("Please select the row to edit."),
                    tr("Warning"),
                    JOptionPane.WARNING_MESSAGE
                    );
            return false;
        }
        final Object e = model.getValueAt(getSelectedRow(), column);
        Setting<?> stg = e instanceof PrefEntry ? ((PrefEntry) e).getValue() : null;
        if (e instanceof JsonArray) {
            JsonArray array = (JsonArray) e;
            List<Map<String, String>> map = array.stream().filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast).map(obj -> {
                        Map<String, String> tMap = new TreeMap<>();
                        obj.forEach((str, val) -> tMap.put(str, val.toString()));
                        return tMap;
                    }).collect(Collectors.toList());
            stg = new MapListSetting(map);
        }
        boolean ok = false;
        if (stg instanceof StringSetting || e instanceof String || e instanceof Boolean) {
            editCellAt(getSelectedRow(), getSelectedColumn());
            Component editor = getEditorComponent();
            if (editor != null) {
                editor.requestFocus();
            }
        } else if (stg instanceof ListSetting) {
            ok = doEditList(gui, (PrefEntry) e, (ListSetting) stg);
        } else if (stg instanceof ListListSetting) {
            ok = doEditListList(gui, (PrefEntry) e, (ListListSetting) stg);
        } else if (stg instanceof MapListSetting) {
            PrefEntry pref = e instanceof PrefEntry ? (PrefEntry) e : new PrefEntry("Parameters", stg, stg, false);
            ok = doEditMapList(gui, pref, (MapListSetting) stg);
            if (!e.equals(pref) && e instanceof JsonArray) {
                JsonArrayBuilder array = Json.createArrayBuilder();
                ((MapListSetting) pref.getValue()).getValue()
                .forEach(entry -> array.add(Json.createObjectBuilder(objectify(entry)).build()));
                model.setValueAt(array.build(), getSelectedRow(), column);
                fireDataChanged();
            }
        }
        return ok;
    }

    private static Map<String, Object> objectify(Map<String, String> map) {
        Map<String, Object> returnMap = new TreeMap<>();
        for (Entry<String, String> entry : map.entrySet()) {
            Object obj = null;
            if (entry.getValue().equalsIgnoreCase("true") || entry.getValue().equalsIgnoreCase("false")
                    || "enabled".equals(entry.getKey())) {
                obj = Boolean.parseBoolean(entry.getValue());
            } else if (entry.getValue().matches("[0-9.-]+")) {
                try {
                    obj = Double.parseDouble(entry.getValue());
                    obj = Integer.parseInt(entry.getValue());
                } catch (NumberFormatException e) {
                    // do nothing
                }
            } else {
                obj = entry.getValue().replaceAll("(^(\\\\*\\\"+)+|(\\\\*\\\"+)+$)", "");
            }
            returnMap.put(entry.getKey(), obj);
        }
        return returnMap;
    }

    private static boolean doEditList(final JComponent gui, final PrefEntry e, ListSetting lSetting) {
        ListEditor lEditor = new ListEditor(gui, e, lSetting);
        lEditor.showDialog();
        if (lEditor.getValue() == 1) {
            List<String> data = lEditor.getData();
            if (!lSetting.equalVal(data)) {
                e.setValue(new ListSetting(data));
                return true;
            }
        }
        return false;
    }

    private static boolean doEditListList(final JComponent gui, final PrefEntry e, ListListSetting llSetting) {
        ListListEditor llEditor = new ListListEditor(gui, e, llSetting);
        llEditor.showDialog();
        if (llEditor.getValue() == 1) {
            List<List<String>> data = llEditor.getData();
            if (!llSetting.equalVal(data)) {
                e.setValue(new ListListSetting(data));
                return true;
            }
        }
        return false;
    }

    private static boolean doEditMapList(final JComponent gui, final PrefEntry e, MapListSetting mlSetting) {
        MapListEditor mlEditor = new MapListEditor(gui, e, mlSetting);
        mlEditor.showDialog();
        if (mlEditor.getValue() == 1) {
            List<Map<String, String>> data = mlEditor.getData();
            if (!mlSetting.equalVal(data)) {
                e.setValue(new MapListSetting(data));
                return true;
            }
        }
        return false;
    }

    /**
     * Add new preference to the table
     * @param gui - parent component for asking dialogs
     * @return newly created entry or null if adding was cancelled
     */
    public PrefEntry addPreference(final JComponent gui) {
        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JLabel(tr("Key")), GBC.std().insets(0, 0, 5, 0));
        JosmTextField tkey = new JosmTextField("", 50);
        p.add(tkey, GBC.eop().insets(5, 0, 0, 0).fill(GridBagConstraints.HORIZONTAL));

        p.add(new JLabel(tr("Select Setting Type:")), GBC.eol().insets(5, 15, 5, 0));

        JRadioButton rbString = new JRadioButton(tr("Simple"));
        JRadioButton rbList = new JRadioButton(tr("List"));
        JRadioButton rbListList = new JRadioButton(tr("List of lists"));
        JRadioButton rbMapList = new JRadioButton(tr("List of maps"));

        ButtonGroup group = new ButtonGroup();
        group.add(rbString);
        group.add(rbList);
        group.add(rbListList);
        group.add(rbMapList);

        p.add(rbString, GBC.eol());
        p.add(rbList, GBC.eol());
        p.add(rbListList, GBC.eol());
        p.add(rbMapList, GBC.eol());

        rbString.setSelected(true);

        PrefEntry pe = null;
        boolean ok = false;
        if (askAddSetting(gui, p)) {
            if (rbString.isSelected()) {
                StringSetting sSetting = new StringSetting(null);
                pe = new PrefEntry(tkey.getText(), sSetting, sSetting, false);
                ok = doAddSimple(gui, pe, sSetting);
            } else if (rbList.isSelected()) {
                ListSetting lSetting = new ListSetting(null);
                pe = new PrefEntry(tkey.getText(), lSetting, lSetting, false);
                ok = doAddList(gui, pe, lSetting);
            } else if (rbListList.isSelected()) {
                ListListSetting llSetting = new ListListSetting(null);
                pe = new PrefEntry(tkey.getText(), llSetting, llSetting, false);
                ok = doAddListList(gui, pe, llSetting);
            } else if (rbMapList.isSelected()) {
                MapListSetting mlSetting = new MapListSetting(null);
                pe = new PrefEntry(tkey.getText(), mlSetting, mlSetting, false);
                ok = doAddMapList(gui, pe, mlSetting);
            }
        }
        return ok ? pe : null;
    }

    private static boolean askAddSetting(JComponent gui, JPanel p) {
        return new ExtendedDialog(gui, tr("Add setting"), tr("OK"), tr("Cancel"))
                .setContent(p).setButtonIcons("ok", "cancel").showDialog().getValue() == 1;
    }

    private static boolean doAddSimple(final JComponent gui, PrefEntry pe, StringSetting sSetting) {
        StringEditor sEditor = new StringEditor(gui, pe, sSetting);
        sEditor.showDialog();
        if (sEditor.getValue() == 1) {
            String data = sEditor.getData();
            if (!Objects.equals(sSetting.getValue(), data)) {
                pe.setValue(new StringSetting(data));
                return true;
            }
        }
        return false;
    }

    private static boolean doAddList(final JComponent gui, PrefEntry pe, ListSetting lSetting) {
        ListEditor lEditor = new ListEditor(gui, pe, lSetting);
        lEditor.showDialog();
        if (lEditor.getValue() == 1) {
            List<String> data = lEditor.getData();
            if (!lSetting.equalVal(data)) {
                pe.setValue(new ListSetting(data));
                return true;
            }
        }
        return false;
    }

    private static boolean doAddListList(final JComponent gui, PrefEntry pe, ListListSetting llSetting) {
        ListListEditor llEditor = new ListListEditor(gui, pe, llSetting);
        llEditor.showDialog();
        if (llEditor.getValue() == 1) {
            List<List<String>> data = llEditor.getData();
            if (!llSetting.equalVal(data)) {
                pe.setValue(new ListListSetting(data));
                return true;
            }
        }
        return false;
    }

    private static boolean doAddMapList(final JComponent gui, PrefEntry pe, MapListSetting mlSetting) {
        MapListEditor mlEditor = new MapListEditor(gui, pe, mlSetting);
        mlEditor.showDialog();
        if (mlEditor.getValue() == 1) {
            List<Map<String, String>> data = mlEditor.getData();
            if (!mlSetting.equalVal(data)) {
                pe.setValue(new MapListSetting(data));
                return true;
            }
        }
        return false;
    }

    /**
     * Reset selected preferences to their default values
     * @param gui - parent component to display warning messages
     */
    public void resetPreferences(final JComponent gui) {
        if (getSelectedRowCount() == 0) {
            JOptionPane.showMessageDialog(
                    gui,
                    tr("Please select the row to delete."),
                    tr("Warning"),
                    JOptionPane.WARNING_MESSAGE
                    );
            return;
        }
        for (int row : getSelectedRows()) {
            DataUrl e = displayData.get(row);
            e.reset();
        }
        fireDataChanged();
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
            List<Object> setList = new ArrayList<>(displayData.get(row).getDataList());
            if (!o.equals(setList.get(column))) {
                setList.remove(column);
                setList.add(column, o);
                displayData.get(row).setDataList(setList);
                if (o instanceof String && ((String) o).isEmpty()) {
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
