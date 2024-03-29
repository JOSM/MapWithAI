// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JMenu;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.MapWithAIPreferences;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.plugins.mapwithai.tools.MapPaintUtils;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

/**
 * Test class for {@link MapWithAIPlugin}
 *
 * @author Taylor Smock
 */
@BasicPreferences
@Main
@MapWithAISources
@NoExceptions
@Projection
@Wiremock
class MapWithAIPluginTest {
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
        plugin = new MapWithAIPlugin(info);
        assertEquals(dataMenuSize + 1, dataMenu.getMenuComponentCount(), "Menu items were not added");
        assertEquals(1,
                MapPaintStyles.getStyles().getStyleSources().stream()
                        .filter(source -> source.url != null && source.name.contains("MapWithAI")).count(),
                "The paint style was not added");
        plugin.addDownloadSelection(Collections.emptyList());
        plugin.destroy();

        for (boolean existed : Arrays.asList(false, true)) { // false, true order is important
            plugin = new MapWithAIPlugin(info);
            Config.getPref().putBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS, existed);
            plugin.addDownloadSelection(Collections.emptyList());
            plugin.destroy();
            assertEquals(dataMenuSize, dataMenu.getMenuComponentCount(),
                    "Menu items were added after they were already added");
            Awaitility.await().atMost(Durations.FIVE_SECONDS)
                    .until(() -> existed == MapPaintUtils.checkIfMapWithAIPaintStyleExists());
            assertEquals(Config.getPref().getBoolean(MapWithAIPlugin.PAINTSTYLE_PREEXISTS) ? 1 : 0,
                    MapPaintStyles.getStyles().getStyleSources().stream()
                            .filter(source -> source.url != null && source.name.contains("MapWithAI")).count(),
                    "The paint style was added multiple times");
        }

        for (int i = 0; i < 3; i++) {
            plugin = new MapWithAIPlugin(info);
            plugin.addDownloadSelection(Collections.emptyList());
            assertEquals(dataMenuSize + 1, dataMenu.getMenuComponentCount(),
                    "The menu items were added multiple times");
            assertEquals(1,
                    MapPaintStyles.getStyles().getStyleSources().stream()
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
