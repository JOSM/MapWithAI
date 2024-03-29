// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import java.awt.GridBagLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Pair;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Parameters panel for adding MapWithAI URLs
 *
 * @author Taylor Smock
 *
 */
class MapWithAIParametersPanel extends JPanel {

    private final class ParametersTableModel extends AbstractTableModel {
        private final Set<Integer> disabledRows = new HashSet<>();

        @Override
        public String getColumnName(int column) {
            return switch (column) {
            case 0 -> tr("Parameter name");
            case 1 -> tr("Parameter value");
            case 2 -> tr("Enabled");
            default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 2) {
                return Boolean.class;
            }
            return String.class;
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
            return !disabledRows.contains(row);
        }

        /**
         * Prevent a row from being edited
         *
         * @param row The row that shouldn't be editable
         * @return See {@link Set#add}
         */
        public boolean disableRowEdits(int row) {
            return disabledRows.add(row);
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

    private final List<Object[]> headers;
    private final ParametersTableModel model;

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
        final var table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        add(new JScrollPane(table), GBC.eol().fill());
    }

    private static List<Object[]> getHeadersAsVector(Map<String, Pair<String, Boolean>> headers) {
        return headers.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new Object[] { e.getKey(), e.getValue().a, e.getValue().b }).collect(Collectors.toList());
    }

    /**
     * HTTP parameters (so {@code ?param1=value1&param2=value2})
     *
     * @return parameters provided by user
     */
    public Map<String, Pair<String, Boolean>> getParameters() {
        return headers.stream().distinct()
                .collect(Collectors.toMap(x -> (String) x[0], x -> new Pair<>((String) x[1], (Boolean) x[2])));
    }

    /**
     * These are the current parameters for the info object.
     *
     * @param parameters The initial parameters to show in the dialog
     */
    public void setParameters(JsonArray parameters) {
        var i = 0;
        for (JsonObject obj : parameters.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                .toList()) {
            model.setValueAt(obj.getString("parameter"), i, 1);
            model.setValueAt(obj.getString("description", ""), i, 0);
            model.setValueAt(obj.getBoolean("enabled", false), i, 2);
            final var permanent = obj.getBoolean("permanent", false);
            if (permanent) {
                model.disableRowEdits(i);
            }
            i++;
        }
        model.fireTableDataChanged();
    }

    /**
     * Add a listener to the model ({@code model.addTableModelListener})
     *
     * @param l A TableModelListener for the backing model
     */
    public void addListener(TableModelListener l) {
        model.addTableModelListener(l);
    }

    /**
     * Get the model that is displayed in the panel.
     *
     * @return The table model used to display parameters
     */
    public TableModel getModel() {
        return model;
    }
}
