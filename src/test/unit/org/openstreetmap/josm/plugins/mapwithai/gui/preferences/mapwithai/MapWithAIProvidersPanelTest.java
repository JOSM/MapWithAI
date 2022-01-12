// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai.MapWithAIProvidersPanel.AreaListener;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.ListenerList;

/**
 * Test class for {@link MapWithAIProvidersPanel}
 *
 * @author Taylor Smock
 */
@Wiremock
@MapWithAISources
class MapWithAIProvidersPanelTest {
    @RegisterExtension
    JOSMTestRules rule = new MapWithAITestRules().projection();

    private MapWithAIProvidersPanel mapwithaiProvidersPanel;

    @BeforeEach
    void setUp() {
        JComponent jcomponent = new JPanel();
        mapwithaiProvidersPanel = new MapWithAIProvidersPanel(jcomponent, MapWithAIProvidersPanel.Options.values());
    }

    /**
     * Test method for {@link MapWithAIProvidersPanel#setCurrentBounds(Bounds)}.
     *
     * @throws SecurityException        If there is an issue with the security
     *                                  manager
     * @throws NoSuchFieldException     If there is an issue getting the field
     *                                  (update the name!)
     * @throws IllegalAccessException   If there is an issue with the security
     *                                  manager
     * @throws IllegalArgumentException If there is an issue getting the field
     *                                  (update the test!)
     */
    @Test
    void testSetCurrentBounds() throws ReflectiveOperationException {
        Field areaListenersField = mapwithaiProvidersPanel.getClass().getDeclaredField("areaListeners");
        areaListenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ListenerList<AreaListener> areaListeners = (ListenerList<AreaListener>) areaListenersField
                .get(mapwithaiProvidersPanel);
        Bounds bounds = new Bounds(0, 0, 0, 0);
        areaListeners.addListener(b -> bounds.extend(b));
        Bounds toSet = new Bounds(0, 0, 0.01, 0.01);
        assertFalse(bounds.toBBox().bounds(toSet.toBBox()));
        mapwithaiProvidersPanel.setCurrentBounds(toSet);
        assertTrue(bounds.toBBox().bounds(toSet.toBBox()));
    }

    /**
     * Non-regression test for
     * <a href="https://josm.openstreetmap.de/ticket/19473">#19473</a>. While this
     * test has never failed, it tests the only code paths that should be able to
     * produce the NPE.
     *
     * @throws SecurityException        If there is an issue with the security
     *                                  manager
     * @throws NoSuchFieldException     If there is an issue getting the field
     *                                  (update the name!)
     * @throws IllegalAccessException   If there is an issue with the security
     *                                  manager
     * @throws IllegalArgumentException If there is an issue getting the field
     *                                  (update the test!)
     */
    @Test
    void testTicket19473() throws ReflectiveOperationException {
        mapwithaiProvidersPanel = new MapWithAIProvidersPanel(new JPanel());
        Field defaultTableField = MapWithAIProvidersPanel.class.getDeclaredField("defaultTable");
        JTable defaultTable = (JTable) defaultTableField.get(mapwithaiProvidersPanel);
        MapWithAIInfo info = MapWithAILayerInfo.getInstance().getDefaultLayers().get(0);
        info.setAdditionalCategories(Collections.singletonList(null));
        checkTable(defaultTable);

        info.setAdditionalCategories(null);
        checkTable(defaultTable);
    }

    private static void checkTable(JTable table) {
        IntStream.range(0, table.getRowCount())
                .forEach(row -> IntStream.range(0, table.getColumnCount()).forEach(column -> {
                    assertDoesNotThrow(() -> table.getValueAt(row, column));
                }));
    }
}
