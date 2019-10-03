// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.swing.JMenu;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDAction;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDArbitraryAction;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDDataUtils;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDMoveAction;
import org.openstreetmap.josm.tools.Logging;

public final class RapiDPlugin extends Plugin {
    /** The name of the plugin */
    public static final String NAME = "RapiD";
    private static String versionInfo;

    private final PreferenceSetting preferenceSetting;

    private static final LinkedHashMap<Class<? extends JosmAction>, Boolean> MENU_ENTRIES = new LinkedHashMap<>();
    static {
        MENU_ENTRIES.put(RapiDAction.class, false);
        MENU_ENTRIES.put(RapiDMoveAction.class, false);
        MENU_ENTRIES.put(RapiDArbitraryAction.class, true);
    }

    public RapiDPlugin(PluginInformation info) {
        super(info);

        preferenceSetting = new RapiDPreferences();

        final JMenu dataMenu = MainApplication.getMenu().dataMenu;
        for (Entry<Class<? extends JosmAction>, Boolean> entry : MENU_ENTRIES.entrySet()) {
            boolean alreadyAdded = false;
            for (Component component : dataMenu.getMenuComponents()) {
                if (entry.getKey().equals(component.getClass())) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                try {
                    MainMenu.add(dataMenu, entry.getKey().newInstance(), entry.getValue());
                } catch (InstantiationException | IllegalAccessException e) {
                    Logging.debug(e);
                }
            }
        }

        RapiDDataUtils.addRapiDPaintStyles();

        setVersionInfo(info.localversion);
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
}
