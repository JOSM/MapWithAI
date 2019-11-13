// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai;

import org.openstreetmap.josm.gui.preferences.advanced.PrefEntry;
import org.openstreetmap.josm.spi.preferences.Setting;

/**
 * @author Taylor Smock
 *
 */
public class TriPrefEntry extends PrefEntry {

    public TriPrefEntry(String key, Setting<?> value, Setting<?> defaultValue, boolean isDefault) {
        super(key, value, defaultValue, isDefault);
    }

}
