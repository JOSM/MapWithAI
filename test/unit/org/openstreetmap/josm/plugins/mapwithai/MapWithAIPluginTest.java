// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 */
public class MapWithAIPluginTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main();

    public PluginInformation info;
    public MapWithAIPlugin plugin;

    private static final String VERSION = "no-such-version";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        final InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        info = new PluginInformation(in, "MapWithAI", null);
        info.localversion = VERSION;
    }

    /**
     * Test method for {@link MapWithAIPlugin#getPreferenceSetting()}.
     */
    @Test
    public void testGetPreferenceSetting() {
        plugin = new MapWithAIPlugin(info);
        Assert.assertTrue(plugin.getPreferenceSetting() instanceof MapWithAIPreferences);
    }

    /**
     * Test method for {@link MapWithAIPlugin#MapWithAIPlugin(PluginInformation)}.
     */
    @Test
    public void testMapWithAIPlugin() {
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final int originalPaintStyles = MapPaintPrefHelper.INSTANCE.get().size();
        final int dataMenuSize = dataMenu.getMenuComponentCount();
        plugin = new MapWithAIPlugin(info);
        Assert.assertEquals(dataMenuSize + 3, dataMenu.getMenuComponentCount());
        Assert.assertEquals(originalPaintStyles + 1, MapPaintPrefHelper.INSTANCE.get().size());
    }

    /**
     * Test method for {@link MapWithAIPlugin#getVersionInfo()}.
     */
    @Test
    public void testGetVersionInfo() {
        plugin = new MapWithAIPlugin(info); // needs to be called for version info to be initialized.
        Assert.assertEquals(VERSION, MapWithAIPlugin.getVersionInfo());
    }

}