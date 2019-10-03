// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.swing.JMenu;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.preferences.sources.MapPaintPrefHelper;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * @author Taylor Smock
 */
public class RapiDPluginTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules().preferences().main();

    public PluginInformation info;
    public RapiDPlugin plugin;

    private static final String VERSION = "no-such-version";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        final InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        info = new PluginInformation(in, "Rapid", null);
        info.localversion = VERSION;
    }

    /**
     * Test method for {@link RapiDPlugin#getPreferenceSetting()}.
     */
    @Test
    public void testGetPreferenceSetting() {
        plugin = new RapiDPlugin(info);
        Assert.assertTrue(plugin.getPreferenceSetting() instanceof RapiDPreferences);
    }

    /**
     * Test method for {@link RapiDPlugin#RapiDPlugin(PluginInformation)}.
     */
    @Test
    public void testRapiDPlugin() {
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final int originalPaintStyles = MapPaintPrefHelper.INSTANCE.get().size();
        final int dataMenuSize = dataMenu.getMenuComponentCount();
        plugin = new RapiDPlugin(info);
        Assert.assertEquals(dataMenuSize + 3, dataMenu.getMenuComponentCount());
        Assert.assertEquals(originalPaintStyles + 1, MapPaintPrefHelper.INSTANCE.get().size());
    }

    /**
     * Test method for {@link RapiDPlugin#getVersionInfo()}.
     */
    @Test
    public void testGetVersionInfo() {
        plugin = new RapiDPlugin(info); // needs to be called for version info to be initialized.
        Assert.assertEquals(VERSION, RapiDPlugin.getVersionInfo());
    }

}
