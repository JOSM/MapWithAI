// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.List;

import javax.swing.SpinnerNumberModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIPreferencesTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().timeout(100_000);

    private MapWithAIPreferences preferences;

    /**
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        preferences = new MapWithAIPreferences();
    }

    /**
     * Test method for {@link MapWithAIPreferences#addGui(PreferenceTabbedPane)}.
     *
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    @Test
    public void testAddGui()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final PreferenceTabbedPane pane = new PreferenceTabbedPane();
        pane.buildGui();
        Field tab = PreferenceTabbedPane.class.getDeclaredField("tabs");
        tab.setAccessible(true);
        int initialSize = ((List<?>) tab.get(pane)).size();
        preferences.addGui(pane);

        assertEquals(initialSize + 1, ((List<?>) tab.get(pane)).size(), "Preferences wasn't added");

        final boolean switchLayers = MapWithAIPreferenceHelper.isSwitchLayers();

        assertEquals(switchLayers, preferences.getSwitchLayerCheckBox().isSelected(),
                "The default for switching layers is true");
        preferences.ok();
        assertEquals(switchLayers, MapWithAIPreferenceHelper.isSwitchLayers(),
                "The default for switching layers is true");

        preferences.getSwitchLayerCheckBox().setSelected(!switchLayers);
        assertNotEquals(!switchLayers, MapWithAIPreferenceHelper.isSwitchLayers(), "OK hasn't been selected yet");
        preferences.ok();
        assertEquals(!switchLayers, MapWithAIPreferenceHelper.isSwitchLayers(),
                "We deselected switchLayers, so it should be off");

        final Object tmp = preferences.getMaximumAdditionSpinner().getModel();
        SpinnerNumberModel spinnerModel = null;
        if (tmp instanceof SpinnerNumberModel) {
            spinnerModel = (SpinnerNumberModel) tmp;
        }
        assertNotNull(spinnerModel, "The spinner model should be a SpinnerNumberModel");
        final Number currentNumber = MapWithAIPreferenceHelper.getMaximumAddition();
        assertEquals(currentNumber.intValue(), spinnerModel.getNumber().intValue(),
                "The default additions should be the current setting");
        spinnerModel.setValue(currentNumber.intValue() + 3);
        assertNotEquals(spinnerModel.getNumber().intValue(), MapWithAIPreferenceHelper.getMaximumAddition(),
                "We've increased the max add by three, but have not selected OK, so it should still be the default");
        preferences.ok();
        assertEquals(spinnerModel.getNumber().intValue(), MapWithAIPreferenceHelper.getMaximumAddition(),
                "OK has been selected, so the max adds have been updated");
    }

    /**
     * Test method for {@link MapWithAIPreferences#isExpert()}.
     */
    @Test
    public void testIsExpert() {
        assertFalse(preferences.isExpert(), "This is not an expert only preference panel");
    }
}
