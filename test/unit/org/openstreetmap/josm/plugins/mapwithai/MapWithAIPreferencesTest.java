// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import javax.swing.SpinnerNumberModel;

import org.junit.Assert;
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
    public JOSMTestRules test = new JOSMTestRules().preferences().main();

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
     */
    @Test
    public void testAddGui() {
        final PreferenceTabbedPane pane = new PreferenceTabbedPane();
        pane.buildGui();
        final int tabs = pane.getPluginPreference().getTabPane().getTabCount();

        preferences.addGui(pane);

        Assert.assertEquals(tabs + 1, pane.getPluginPreference().getTabPane().getTabCount());
        Assert.assertEquals(pane.getPluginPreference(), preferences.getTabPreferenceSetting(pane));

        final boolean switchLayers = MapWithAIPreferenceHelper.isSwitchLayers();

        Assert.assertEquals(switchLayers, preferences.getSwitchLayerCheckBox().isSelected());
        preferences.ok();
        Assert.assertEquals(switchLayers, MapWithAIPreferenceHelper.isSwitchLayers());

        preferences.getSwitchLayerCheckBox().setSelected(!switchLayers);
        Assert.assertNotEquals(!switchLayers, MapWithAIPreferenceHelper.isSwitchLayers());
        preferences.ok();
        Assert.assertEquals(!switchLayers, MapWithAIPreferenceHelper.isSwitchLayers());

        final Object tmp = preferences.getMaximumAdditionSpinner().getModel();
        SpinnerNumberModel spinnerModel = null;
        if (tmp instanceof SpinnerNumberModel) {
            spinnerModel = (SpinnerNumberModel) tmp;
        }
        Assert.assertNotNull(spinnerModel);
        final Number currentNumber = MapWithAIPreferenceHelper.getMaximumAddition();
        Assert.assertEquals(currentNumber.intValue(), spinnerModel.getNumber().intValue());
        spinnerModel.setValue(currentNumber.intValue() + 3);
        Assert.assertNotEquals(spinnerModel.getNumber().intValue(), MapWithAIPreferenceHelper.getMaximumAddition());
        preferences.ok();
        Assert.assertEquals(spinnerModel.getNumber().intValue(), MapWithAIPreferenceHelper.getMaximumAddition());

        Assert.assertNotNull(preferences.getPossibleMapWithAIApiUrl().getSelectedItem());
    }

    /**
     * Test method for {@link MapWithAIPreferences#isExpert()}.
     */
    @Test
    public void testIsExpert() {
        Assert.assertFalse(preferences.isExpert());
    }
}
