// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import javax.swing.JOptionPane;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.mockers.OpenBrowserMocker;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test the update prod
 *
 * @author Taylor Smock
 */
public class UpdateProdTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rule = new JOSMTestRules().preferences();

    @Test
    public void testDoProd() throws ReflectiveOperationException {
        TestUtils.assumeWorkingJMockit();
        new OpenBrowserMocker();
        if (GraphicsEnvironment.isHeadless()) {
            new WindowMocker();
        }
        Field version = Version.class.getDeclaredField("version");
        version.setAccessible(true);
        version.setInt(Version.getInstance(), 15000);
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
