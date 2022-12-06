// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import org.openstreetmap.josm.command.ChangePropertyKeyCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;
import org.openstreetmap.josm.tools.Destroyable;

/**
 * Show/hide conflated objects. This depends upon the server indicating that an
 * object is conflated.
 *
 * @author Taylor Smock
 *
 */
public class PreConflatedDataUtils implements PreferenceChangedListener, Destroyable {
    /** The preference key that determines if objects are shown/hidden */
    protected static final String PREF_KEY = "mapwithai.conflated.hide";
    /**
     * If this config value is true, completely hide the conflated item instead of
     * just greying it out
     */
    protected static final String PREF_KEY_FULL = PREF_KEY + ".full";
    /**
     * The key added to objects to indicate that they have been conflated. May show
     * object or be true/false
     */
    private static final String CONFLATED_KEY = "mapwithai:conflated";

    /**
     * Create a new util object
     */
    public PreConflatedDataUtils() {
        Config.getPref().addKeyPreferenceChangeListener(PREF_KEY, this);
        Config.getPref().addKeyPreferenceChangeListener(PREF_KEY_FULL, this);
    }

    /**
     * Despite the name, this method changes the conflated tag to a standard tag,
     * and then hides the data.
     *
     * @param dataSet The dataset to look through
     * @param info    The info with the key to use to convert to the
     *                mapwithai:conflated tag
     */
    public static void removeConflatedData(DataSet dataSet, MapWithAIInfo info) {
        if (info != null && info.getAlreadyConflatedKey() != null && !info.getAlreadyConflatedKey().trim().isEmpty()) {
            String key = info.getAlreadyConflatedKey();
            dataSet.allPrimitives().stream().filter(p -> p.hasKey(key))
                    .forEach(p -> new ChangePropertyKeyCommand(p, key, getConflatedKey()).executeCommand());
            hideConflatedData(dataSet);
        }
    }

    /**
     * Hide conflated data.
     *
     * @param dataSet The dataset to show/hide data in
     */
    public static void hideConflatedData(DataSet dataSet) {
        boolean hide = Config.getPref().getBoolean(PREF_KEY, true);
        boolean fullHide = Config.getPref().getBoolean(PREF_KEY_FULL, false);
        dataSet.allPrimitives().stream().filter(p -> p.hasKey(getConflatedKey())).forEach(p -> {
            if (hide) {
                p.setDisabledState(fullHide);
            } else {
                p.unsetDisabledState();
            }
        });
    }

    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).stream().map(MapWithAILayer::getDataSet)
                .distinct().forEach(PreConflatedDataUtils::hideConflatedData);
    }

    @Override
    public void destroy() {
        Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY, this);
        Config.getPref().removeKeyPreferenceChangeListener(PREF_KEY_FULL, this);
    }

    /**
     * Get the key used to indicate that an object is already conflated.
     *
     * @return the conflatedKey
     */
    public static String getConflatedKey() {
        return CONFLATED_KEY;
    }
}
