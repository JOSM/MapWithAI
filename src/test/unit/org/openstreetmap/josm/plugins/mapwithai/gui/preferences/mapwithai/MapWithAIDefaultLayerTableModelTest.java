// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * Test class for {@link MapWithAIDefaultLayerTableModel}
 *
 * @author Taylor Smock
 */
@MapWithAISources
@Projection
@Wiremock
class MapWithAIDefaultLayerTableModelTest {
    @Test
    void testGetRow() {
        List<MapWithAIInfo> infos = MapWithAILayerInfo.getInstance().getAllDefaultLayers();
        assertFalse(infos.isEmpty());
        for (int i = 0; i < infos.size(); i++) {
            assertSame(infos.get(i), MapWithAIDefaultLayerTableModel.getRow(i));
        }
    }

    @Test
    void testRetrieval() {
        List<MapWithAIInfo> infos = new ArrayList<>(MapWithAILayerInfo.getInstance().getAllDefaultLayers());
        assertFalse(infos.isEmpty());
        Collections.shuffle(infos);

        MapWithAIInfo info = infos.get(0);

        MapWithAIDefaultLayerTableModel model = new MapWithAIDefaultLayerTableModel();
        int row = IntStream.range(0, model.getRowCount())
                .filter(i -> info.equals(MapWithAIDefaultLayerTableModel.getRow(i))).findAny().orElse(-1);
        assertNotEquals(-1, row);
        IntStream.rangeClosed(0, model.getColumnCount()).forEach(column -> {
            assertDoesNotThrow(() -> model.getValueAt(row, column), info.getName());
            assertFalse(model.isCellEditable(row, column), info.getName());
            assertDoesNotThrow(() -> model.getColumnClass(column));
        });
    }
}
