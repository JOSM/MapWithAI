// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;

/**
 * The table model for the default imagery layer list
 */
class MapWithAIDefaultLayerTableModel extends DefaultTableModel {
    private static final long serialVersionUID = -2966437364160797385L;
    private final List<Class<?>> columnTypes;
    private final transient List<Function<MapWithAIInfo, Object>> columnDataRetrieval;

    /**
     * Constructs a new {@code MapWithAIDefaultLayerTableModel}.
     */
    public MapWithAIDefaultLayerTableModel() {
        setColumnIdentifiers(new String[] { "", tr("MapWithAI Data Source Name (Default)"), tr("Type"),
                tr("MapWithAI URL (Default)"), tr("Provider") });
        columnTypes = Stream.of(MapWithAICategory.class, MapWithAIInfo.class, List.class, String.class, String.class)
                .collect(Collectors.toCollection(ArrayList::new));
        columnDataRetrieval = new ArrayList<>();
        columnDataRetrieval.add(info -> Optional.ofNullable(info.getCategory()).orElse(MapWithAICategory.OTHER));
        columnDataRetrieval.add(info -> info);
        columnDataRetrieval.add(info -> {
            List<String> categories = Stream
                    .concat(Stream.of(info.getCategory()), info.getAdditionalCategories().stream())
                    .filter(Objects::nonNull).map(MapWithAICategory::getDescription).collect(Collectors.toList());
            return categories.isEmpty() ? Collections.singletonList(MapWithAICategory.OTHER.getDescription())
                    : categories;
        });
        columnDataRetrieval.add(MapWithAIInfo::getUrl);
        columnDataRetrieval.add(i -> i.getAttributionText(0, null, null));
    }

    /**
     * Returns the imagery info at the given row number.
     *
     * @param row The row number
     * @return The imagery info at the given row number
     */
    public static MapWithAIInfo getRow(int row) {
        return MapWithAILayerInfo.getInstance().getAllDefaultLayers().get(row);
    }

    @Override
    public void removeRow(int row) {
        columnTypes.remove(row);
        columnDataRetrieval.remove(row);
        super.removeRow(row);
    }

    @Override
    public int getRowCount() {
        return MapWithAILayerInfo.getInstance().getAllDefaultLayers().size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex < columnTypes.size()) {
            return columnTypes.get(columnIndex);
        }
        return super.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int row, int column) {
        MapWithAIInfo info = MapWithAILayerInfo.getInstance().getAllDefaultLayers().get(row);
        if (column < columnDataRetrieval.size()) {
            return columnDataRetrieval.get(column).apply(info);
        }
        return null;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
