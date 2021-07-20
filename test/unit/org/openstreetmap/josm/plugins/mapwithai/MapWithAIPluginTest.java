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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAIPreferences;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 */
@BasicPreferences
class MapWithAIPluginTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new MapWithAITestRules().sources().wiremock().main().projection();

    PluginInformation info;
    MapWithAIPlugin plugin;

    private static final String VERSION = "no-such-version";

    /**
     * Set up the tests
     *
     * @throws java.lang.Exception if something goes wrong
     */
    @BeforeEach
    void setUp() throws Exception {
        new WindowMocker();
        final InputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        info = new PluginInformation(in, "MapWithAI", null);
        info.localversion = VERSION;
    }

    @AfterEach
    void tearDown() {
        if (plugin != null) {
            plugin.destroy();
            plugin = null;
        }
    }

    /**
     * Test method for {@link MapWithAIPlugin#getPreferenceSetting()}.
     */
    @Test
    void testGetPreferenceSetting() {
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
    void testMapWithAIPlugin() throws ReflectiveOperationException {
        Field menuEntries = MapWithAIPlugin.class.getDeclaredField("MENU_ENTRIES");
        menuEntries.setAccessible(true);
        // + 1 comes from the preferences panel
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final int dataMenuSize = dataMenu.getMenuComponentCount();
        final int addedMenuItems = ((Map<?, ?>) menuEntries.get(plugin)).size() + 1;
        plugin = new MapWithAIPlugin(info);
        assertEquals(dataMenuSize + 1, dataMenu.getMenuComponentCount(), "Menu items were not added");
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
                    .until(() -> existed == MapPaintUtils.checkIfMapWithAIPaintStyleExists());
            assertEquals(Config.getPref().getBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS) ? 1 : 0,
                    MapPaintStyles.getStyles().getStyleSources().parallelStream()
                            .filter(source -> source.url != null && source.name.contains("MapWithAI")).count(),
                    "The paint style was added multiple times");
        }

        for (int i = 0; i < 3; i++) {
            plugin = new MapWithAIPlugin(info);
            assertEquals(dataMenuSize + 1, dataMenu.getMenuComponentCount(),
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
    void testGetVersionInfo() {
        plugin = new MapWithAIPlugin(info); // needs to be called for version info to be initialized.
        assertEquals(VERSION, MapWithAIPlugin.getVersionInfo(), "We didn't get the expected version");
    }

}
