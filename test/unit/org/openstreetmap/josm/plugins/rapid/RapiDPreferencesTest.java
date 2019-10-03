// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import javax.swing.SpinnerNumberModel;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDDataUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * @author Taylor Smock
 *
 */
public class RapiDPreferencesTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences().main();

    private RapiDPreferences preferences;

    /**
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        preferences = new RapiDPreferences();
    }

    /**
     * Test method for {@link RapiDPreferences#addGui(PreferenceTabbedPane)}.
     */
    @Test
    public void testAddGui() {
        PreferenceTabbedPane pane = new PreferenceTabbedPane();
        pane.buildGui();
        int tabs = pane.getPluginPreference().getTabPane().getTabCount();

        preferences.addGui(pane);

        Assert.assertEquals(tabs + 1, pane.getPluginPreference().getTabPane().getTabCount());
        Assert.assertEquals(pane.getPluginPreference(), preferences.getTabPreferenceSetting(pane));

        boolean switchLayers = RapiDDataUtils.getSwitchLayers();

        Assert.assertEquals(switchLayers, preferences.switchLayerCheckBox.isSelected());
        preferences.ok();
        Assert.assertEquals(switchLayers, RapiDDataUtils.getSwitchLayers());

        preferences.switchLayerCheckBox.setSelected(!switchLayers);
        Assert.assertNotEquals(!switchLayers, RapiDDataUtils.getSwitchLayers());
        preferences.ok();
        Assert.assertEquals(!switchLayers, RapiDDataUtils.getSwitchLayers());

        Object tmp = preferences.maximumAdditionSpinner.getModel();
        SpinnerNumberModel spinnerModel = null;
        if (tmp instanceof SpinnerNumberModel) {
            spinnerModel = (SpinnerNumberModel) tmp;
        }
        Assert.assertNotNull(spinnerModel);
        Number currentNumber = RapiDDataUtils.getMaximumAddition();
        Assert.assertEquals(currentNumber.intValue(), spinnerModel.getNumber().intValue());
        spinnerModel.setValue(currentNumber.intValue() + 3);
        Assert.assertNotEquals(spinnerModel.getNumber().intValue(), RapiDDataUtils.getMaximumAddition());
        preferences.ok();
        Assert.assertEquals(spinnerModel.getNumber().intValue(), RapiDDataUtils.getMaximumAddition());
    }

    /**
     * Test method for {@link RapiDPreferences#isExpert()}.
     */
    @Test
    public void testIsExpert() {
        Assert.assertFalse(preferences.isExpert());
    }
}
