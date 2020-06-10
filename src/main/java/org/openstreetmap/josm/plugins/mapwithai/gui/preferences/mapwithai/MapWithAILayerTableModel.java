// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;

/**
 * The table model for source layer list
 */
public class MapWithAILayerTableModel extends DefaultTableModel {
    private static final long serialVersionUID = 60378230494588007L;

    /**
     * Constructs a new {@code MapWithAILayerTableModel}.
     */
    public MapWithAILayerTableModel() {
        setColumnIdentifiers(new String[] { tr("Default Source Value"), tr("MapWithAI URL") });
    }

    /**
     * Returns the source info at the given row number.
     *
     * @param row The row number
     * @return The source info at the given row number
     */
    public static MapWithAIInfo getRow(int row) {
        return MapWithAILayerInfo.getInstance().getLayers().get(row);
    }

    /**
     * Adds a new imagery info as the last row.
     *
     * @param i The imagery info to add
     */
    public void addRow(MapWithAIInfo i) {
        MapWithAILayerInfo.getInstance().add(i);
        int p = getRowCount() - 1;
        fireTableRowsInserted(p, p);
    }

    @Override
    public void removeRow(int i) {
        MapWithAILayerInfo.getInstance().remove(getRow(i));
        fireTableRowsDeleted(i, i);
    }

    @Override
    public int getRowCount() {
        return MapWithAILayerInfo.getInstance().getLayers().size();
    }

    @Override
    public Object getValueAt(int row, int column) {
        MapWithAIInfo info = MapWithAILayerInfo.getInstance().getLayers().get(row);
        switch (column) {
        case 0:
            return info.getName();
        case 1:
            return info.getUrl();
        default:
            throw new ArrayIndexOutOfBoundsException(Integer.toString(column));
        }
    }

    @Override
    public void setValueAt(Object o, int row, int column) {
        if (MapWithAILayerInfo.getInstance().getLayers().size() <= row) {
            return;
        }
        MapWithAIInfo info = MapWithAILayerInfo.getInstance().getLayers().get(row);
        switch (column) {
        case 0:
            info.setName((String) o);
            info.clearId();
            break;
        case 1:
            info.setUrl((String) o);
            info.clearId();
            break;
        default:
            throw new ArrayIndexOutOfBoundsException(Integer.toString(column));
        }
    }

    /**
     * Check if the active table contains the MapWithAIInfo object
     *
     * @param info The info to check
     * @return {@code true} if any of the active layers is functionally equal
     */
    public static boolean contains(MapWithAIInfo info) {
        return MapWithAILayerInfo.getInstance().getLayers().stream().anyMatch(info::equalsBaseValues);
    }

    /**
     * Check if the active table does not contain the MapWithAIInfo object
     *
     * @param info The info to check
     * @return {@code true} if none of the active layers is functionally equal
     */
    public static boolean doesNotContain(MapWithAIInfo info) {
        return !contains(info);
    }

    /**
     * Get the index that a specified MapWithAIInfo resides at
     *
     * @param info The MapWithAIInfo to find
     * @return The row index, or -1 if it isn't in the model
     */
    public int getRowIndex(MapWithAIInfo info) {
        for (int j = 0; j < getRowCount(); j++) {
            if (info.equalsBaseValues(getRow(j))) {
                return j;
            }
        }
        return -1;
    }
}
