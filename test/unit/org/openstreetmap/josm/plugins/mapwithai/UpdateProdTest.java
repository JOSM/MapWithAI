// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.junit.Assume.assumeFalse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;

import javax.swing.JOptionPane;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * Test the update prod
 *
 * @author Taylor Smock
 */
public class UpdateProdTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules().preferences();

    @Test
    public void testDoProd() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        String booleanKey = "message.".concat(MapWithAIPlugin.NAME.concat(".ignore_next_version"));
        String intKey = "message.".concat(MapWithAIPlugin.NAME.concat(".ignore_next_version")).concat(".value"); // "message.MapWithAI.ignore_next_version.value";
        Config.getPref().putBoolean(booleanKey, false);
        Config.getPref().putInt(intKey,
                JOptionPane.YES_OPTION);
        assertTrue(UpdateProd.doProd(Integer.MAX_VALUE));
        Config.getPref().putInt(intKey,
                JOptionPane.NO_OPTION);
        assertTrue(UpdateProd.doProd(Integer.MAX_VALUE));
        assertFalse(UpdateProd.doProd(0));
    }

}
