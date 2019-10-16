// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.io.remotecontrol.RequestProcessor;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIArbitraryAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIMoveAction;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIRemoteControl;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIUploadHook;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;

public final class MapWithAIPlugin extends Plugin implements Destroyable {
    /** The name of the plugin */
    public static final String NAME = "MapWithAI";
    private static String versionInfo;

    private final MapWithAIUploadHook uploadHook;

    private final PreferenceSetting preferenceSetting;

    private static final Map<Class<? extends JosmAction>, Boolean> MENU_ENTRIES = new LinkedHashMap<>();
    static {
        MENU_ENTRIES.put(MapWithAIAction.class, false);
        MENU_ENTRIES.put(MapWithAIArbitraryAction.class, true);
        MENU_ENTRIES.put(MapWithAIMoveAction.class, false);
    }

    public MapWithAIPlugin(PluginInformation info) {
        super(info);

        preferenceSetting = new MapWithAIPreferences();
        uploadHook = new MapWithAIUploadHook(info);

        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        for (final Entry<Class<? extends JosmAction>, Boolean> entry : MENU_ENTRIES.entrySet()) {
            boolean alreadyAdded = false;
            for (final Component component : dataMenu.getMenuComponents()) {
                if (component instanceof JMenuItem
                        && entry.getKey().equals(((JMenuItem) component).getAction().getClass())) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                try {
                    MainMenu.add(dataMenu, entry.getKey().getDeclaredConstructor().newInstance(), entry.getValue());
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    Logging.debug(e);
                }
            }
        }

        MapWithAIDataUtils.addMapWithAIPaintStyles();

        UploadAction.registerUploadHook(uploadHook);

        setVersionInfo(info.localversion);
        RequestProcessor.addRequestHandlerClass("mapwithai", MapWithAIRemoteControl.class);
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
     * This is so that if JOSM ever decides to support updating plugins without
     * restarting, I don't have to do anything (hopefully -- I might have to change
     * the interface and method). Not currently used... (October 16, 2019)
     */
    @Override
    public void destroy() {
        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        final Map<Action, Component> actions = Arrays.asList(dataMenu.getComponents()).stream()
                .filter(component -> component instanceof JMenuItem).map(component -> (JMenuItem) component)
                .collect(Collectors.toMap(JMenuItem::getAction, component -> component));
        for (final Entry<Action, Component> action : actions.entrySet()) {
            if (MENU_ENTRIES.containsKey(action.getKey().getClass())) {
                dataMenu.remove(action.getValue());
            }
        }
        UploadAction.unregisterUploadHook(uploadHook);

        MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).stream()
        .forEach(layer -> MainApplication.getLayerManager().removeLayer(layer));
    }
}
