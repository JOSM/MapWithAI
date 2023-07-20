// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JOptionPane;

import java.awt.GraphicsEnvironment;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.AssumeRevision;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.mockers.OpenBrowserMocker;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

/**
 * Test the update prod
 *
 * @author Taylor Smock
 */
@AssumeRevision("Revision: 15000\n")
@BasicPreferences
class UpdateProdTest {
    @Test
    void testDoProd() {
        TestUtils.assumeWorkingJMockit();
        new OpenBrowserMocker();
        if (GraphicsEnvironment.isHeadless()) {
            new WindowMocker();
        }
        String booleanKey = "message.".concat(MapWithAIPlugin.NAME.concat(".ignore_next_version"));
        String intKey = "message.".concat(MapWithAIPlugin.NAME.concat(".ignore_next_version")).concat(".value"); // "message.MapWithAI.ignore_next_version.value";
        Config.getPref().putBoolean(booleanKey, false);
        Config.getPref().putInt(intKey, JOptionPane.YES_OPTION);
        assertTrue(UpdateProd.doProd(Integer.MAX_VALUE), "An update is required");
        Config.getPref().putInt(intKey, JOptionPane.NO_OPTION);
        assertTrue(UpdateProd.doProd(Integer.MAX_VALUE), "An update is required");
        assertFalse(UpdateProd.doProd(0), "An update is not required");
    }

}
