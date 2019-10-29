// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.io.remotecontrol.RequestProcessor;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIArbitraryAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIMoveAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIObject;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIRemoteControl;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIUploadHook;
import org.openstreetmap.josm.plugins.mapwithai.backend.MergeDuplicateWaysAction;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;

public final class MapWithAIPlugin extends Plugin implements Destroyable {
    /** The name of the plugin */
    public static final String NAME = "MapWithAI";
    private static String versionInfo;

    private final PreferenceSetting preferenceSetting;

    private final List<Destroyable> destroyables;

    private static final Map<Class<? extends JosmAction>, Boolean> MENU_ENTRIES = new LinkedHashMap<>();
    static {
        MENU_ENTRIES.put(MapWithAIAction.class, false);
        MENU_ENTRIES.put(MapWithAIArbitraryAction.class, true);
        MENU_ENTRIES.put(MapWithAIMoveAction.class, false);
        MENU_ENTRIES.put(MergeDuplicateWaysAction.class, true);
    }

    public MapWithAIPlugin(PluginInformation info) {
        super(info);

        preferenceSetting = new MapWithAIPreferences();

        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        for (final Entry<Class<? extends JosmAction>, Boolean> entry : MENU_ENTRIES.entrySet()) {
            if (Arrays.asList(dataMenu.getMenuComponents()).parallelStream()
                    .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                    .noneMatch(component -> entry.getKey().equals(component.getAction().getClass()))) {
                try {
                    MainMenu.add(dataMenu, entry.getKey().getDeclaredConstructor().newInstance(), entry.getValue());
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    Logging.debug(e);
                }
            }
        }

        try {
            if (MapPaintStyles.getStyles().getStyleSources().parallelStream()
                    .noneMatch(source -> "resource://styles/standard/mapwithai.mapcss".equals(source.url))) {
                MapCSSStyleSource style = new MapCSSStyleSource("resource://styles/standard/mapwithai.mapcss", NAME,
                        "Show objects probably added with MapWithAI");
                MapPaintStyles.addStyle(style);
            }
        } catch (Exception e) {
            MapWithAIDataUtils.addMapWithAIPaintStyles();
        }

        setVersionInfo(info.localversion);
        RequestProcessor.addRequestHandlerClass("mapwithai", MapWithAIRemoteControl.class);
        destroyables = new ArrayList<>();
        destroyables.add(new MapWithAIUploadHook(info));
        mapFrameInitialized(null, MainApplication.getMap());
        try {
            exportStyleFile("styles/standard/mapwithai.mapcss");
        } catch (IOException e) {
            Logging.debug(e);
        }
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        final Optional<MapWithAIObject> possibleMapWithAIObject = destroyables.parallelStream()
                .filter(MapWithAIObject.class::isInstance).map(MapWithAIObject.class::cast).findFirst();
        final MapWithAIObject mapWithAIObject = possibleMapWithAIObject.orElse(new MapWithAIObject());
        if (oldFrame != null && oldFrame.statusLine != null) {
            mapWithAIObject.removeMapStatus(oldFrame.statusLine);
        }
        if (newFrame != null && newFrame.statusLine != null) {
            mapWithAIObject.addMapStatus(newFrame.statusLine);
        }
        if (!destroyables.contains(mapWithAIObject)) {
            destroyables.add(mapWithAIObject);
        }
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return preferenceSetting;
    }

    /**
     * @return The version information of the plugin
     */
    public static String getVersionInfo() {
        return versionInfo;
    }

    private static void setVersionInfo(String newVersionInfo) {
        versionInfo = newVersionInfo;
    }

    /**
     * Exports the mapCSS file to the preferences directory.
     *
     * @param resourceName resource name
     * @throws IOException if any I/O error occurs
     */
    private void exportStyleFile(String resourceName) throws IOException {
        String sep = System.getProperty("file.separator");
        try (InputStream stream = MapWithAIPlugin.class.getResourceAsStream("/data/" + resourceName)) {
            if (stream == null) {
                Logging.debug("{0}: MapPaint: stream is null", NAME);
                throw new IOException("Cannot get resource \"" + resourceName + "\" from Jar file.");
            }

            String outPath;
            int readBytes;
            byte[] buffer = new byte[4096];

            String valDirPath = Config.getDirs().getUserDataDirectory(true) + sep + "styles";
            File valDir = new File(valDirPath);
            valDir.mkdirs();
            outPath = valDir.getAbsolutePath() + sep + resourceName;

            try (OutputStream resStreamOut = new FileOutputStream(outPath)) {
                while ((readBytes = stream.read(buffer)) > 0) {
                    resStreamOut.write(buffer, 0, readBytes);
                }
            }
        }
    }

    /**
     * This is so that if JOSM ever decides to support updating plugins without
     * restarting, I don't have to do anything (hopefully -- I might have to change
     * the interface and method). Not currently used... (October 16, 2019)
     */
    @Override
    public void destroy() {
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final Map<Action, Component> actions = Arrays.asList(dataMenu.getMenuComponents()).stream()
                .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                .collect(Collectors.toMap(JMenuItem::getAction, component -> component));

        for (final Entry<Action, Component> action : actions.entrySet()) {
            if (MENU_ENTRIES.containsKey(action.getKey().getClass())) {
                dataMenu.remove(action.getValue());
            }
        }

        MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).stream()
        .forEach(layer -> MainApplication.getLayerManager().removeLayer(layer));

        destroyables.forEach(Destroyable::destroy);
    }
}
