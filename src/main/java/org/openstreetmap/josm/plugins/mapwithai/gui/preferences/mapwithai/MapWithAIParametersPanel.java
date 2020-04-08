// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.tuple.Pair;
import org.openstreetmap.josm.tools.GBC;

public class MapWithAIParametersPanel extends JPanel {

    private final class ParametersTableModel extends AbstractTableModel {
        @Override
        public String getColumnName(int column) {
            switch (column) {
            case 0:
                return tr("Parameter name");
            case 1:
                return tr("Parameter value");
            case 2:
                return tr("Enabled");
            default:
                return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
            case 2:
                return Boolean.class;
            default:
                return String.class;
            }
        }

        @Override
        public int getRowCount() {
            return headers.size() + 1;
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int row, int col) {
            if (row < headers.size()) {
                return headers.get(row)[col];
            }
            if (String.class.equals(getColumnClass(col))) {
                return "";
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (row < headers.size()) {
                Object[] headerRow = headers.get(row);
                headerRow[col] = value;
                if ("".equals(headerRow[0]) && "".equals(headerRow[1])) {
                    headers.remove(row);
                    fireTableRowsDeleted(row, row);
                }

            } else if (row == headers.size()) {
                Object[] entry = { "", "", null };
                entry[col] = value;
                headers.add(entry);
                fireTableRowsInserted(row + 1, row + 1);
            }
            fireTableCellUpdated(row, col);
        }
    }

    private List<Object[]> headers;
    private ParametersTableModel model;

    /**
     * Creates empty table
     */
    public MapWithAIParametersPanel() {
        this(new ConcurrentHashMap<>());
    }

    /**
     * Create table prefilled with headers
     *
     * @param headers contents of table
     */
    public MapWithAIParametersPanel(Map<String, Pair<String, Boolean>> headers) {
        super(new GridBagLayout());
        this.headers = getHeadersAsVector(headers);
        this.model = new ParametersTableModel();
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        add(new JScrollPane(table), GBC.eol().fill());
    }

    private static List<Object[]> getHeadersAsVector(Map<String, Pair<String, Boolean>> headers) {
        return headers.entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
                .map(e -> new Object[] { e.getKey(), e.getValue().getLeft(), e.getValue().getRight() })
                .collect(Collectors.toList());
    }

    /**
     * @return headers provided by user
     */
    public Map<String, Pair<String, Boolean>> getParameters() {
        return headers.stream().distinct()
                .collect(Collectors.toMap(x -> (String) x[0], x -> Pair.of((String) x[1], (Boolean) x[2])));
    }

    public void setParameters(JsonArray parameters) {
        int i = 0;
        for (JsonObject obj : parameters.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                .collect(Collectors.toList())) {
            model.setValueAt(obj.getString("parameter"), i, 1);
            model.setValueAt(obj.getString("description", ""), i, 0);
            model.setValueAt(obj.getBoolean("enabled", false), i, 2);
        }
        model.fireTableDataChanged();
    }

    public void addListener(TableModelListener l) {
        model.addTableModelListener(l);
    }

    public TableModel getModel() {
        return model;
    }

}
