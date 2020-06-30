// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai.MapWithAIProvidersPanel.AreaListener;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.ListenerList;

/**
 * Test class for {@link MapWithAIProvidersPanel}
 *
 * @author Taylor Smock
 */
public class MapWithAIProvidersPanelTest {
    @Rule
    public JOSMTestRules rule = new MapWithAITestRules().sources().wiremock().projection();

    private MapWithAIProvidersPanel mapwithaiProvidersPanel;

    @Before
    public void setUp() {
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
    public void testSetCurrentBounds()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
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

}
