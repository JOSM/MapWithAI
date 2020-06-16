// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import javax.swing.JMenu;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAIPreferences;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 */
public class MapWithAIPluginTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new MapWithAITestRules().wiremock().preferences().main();

    public PluginInformation info;
    public MapWithAIPlugin plugin;

    private static final String VERSION = "no-such-version";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        new WindowMocker();
        final InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        info = new PluginInformation(in, "MapWithAI", null);
        info.localversion = VERSION;
    }

    @After
    public void tearDown() {
        if (plugin != null) {
            plugin.destroy();
            plugin = null;
        }
    }

    /**
     * Test method for {@link MapWithAIPlugin#getPreferenceSetting()}.
     */
    @Test
    public void testGetPreferenceSetting() {
        plugin = new MapWithAIPlugin(info);
        assertTrue(plugin.getPreferenceSetting() instanceof MapWithAIPreferences,
                "We didn't get the expected Preference class");
    }

    /**
     * Test method for {@link MapWithAIPlugin#MapWithAIPlugin(PluginInformation)}.
     *
     * @throws SecurityException        see {@link java.lang.Class#getDeclaredField}
     * @throws NoSuchFieldException     see {@link java.lang.Class#getDeclaredField}
     * @throws IllegalAccessException   see {@link java.lang.reflect.Field#get}
     * @throws IllegalArgumentException see {@link java.lang.reflect.Field#get}
     */
    @Test
    public void testMapWithAIPlugin()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field menuEntries = MapWithAIPlugin.class.getDeclaredField("MENU_ENTRIES");
        menuEntries.setAccessible(true);
        final int addedMenuItems = ((Map<?, ?>) menuEntries.get(plugin)).size();
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final int dataMenuSize = dataMenu.getMenuComponentCount();
        plugin = new MapWithAIPlugin(info);
        assertEquals(dataMenuSize + addedMenuItems, dataMenu.getMenuComponentCount(), "Menu items were not added");
        assertEquals(1,
                MapPaintStyles.getStyles().getStyleSources().parallelStream()
                        .filter(source -> source.url != null && source.name.contains("MapWithAI")).count(),
                "The paint style was not added");
        plugin.destroy();

        for (boolean existed : Arrays.asList(false, true)) { // false, true order is important
            plugin = new MapWithAIPlugin(info);
            Config.getPref().putBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS, existed);
            plugin.destroy();
            assertEquals(dataMenuSize, dataMenu.getMenuComponentCount(),
                    "Menu items were added after they were already added");
            Awaitility.await().atMost(Durations.FIVE_SECONDS)
                    .until(() -> existed == MapWithAIDataUtils.checkIfMapWithAIPaintStyleExists());
            assertEquals(Config.getPref().getBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS) ? 1 : 0,
                    MapPaintStyles.getStyles().getStyleSources().parallelStream()
                            .filter(source -> source.url != null && source.name.contains("MapWithAI")).count(),
                    "The paint style was added multiple times");
        }

        for (int i = 0; i < 3; i++) {
            plugin = new MapWithAIPlugin(info);
            assertEquals(dataMenuSize + addedMenuItems, dataMenu.getMenuComponentCount(),
                    "The menu items were added multiple times");
            assertEquals(1,
                    MapPaintStyles.getStyles().getStyleSources().parallelStream()
                            .filter(source -> source.url != null && source.name.contains("MapWithAI")).count(),
                    "The paint style was added multiple times");
            plugin.destroy();
            plugin = null; // required to avoid teardown
        }
    }

    /**
     * Test method for {@link MapWithAIPlugin#getVersionInfo()}.
     */
    @Test
    public void testGetVersionInfo() {
        plugin = new MapWithAIPlugin(info); // needs to be called for version info to be initialized.
        assertEquals(VERSION, MapWithAIPlugin.getVersionInfo(), "We didn't get the expected version");
    }

}
