// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.marktr;

import javax.annotation.Nonnull;
import javax.swing.ImageIcon;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.openstreetmap.josm.data.sources.ISourceCategory;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

/**
 * The category for a MapWithAI source (i.e., buildings/highways/addresses/etc.)
 *
 * @author Taylor Smock
 *
 */
public enum MapWithAICategory implements ISourceCategory<MapWithAICategory> {

    BUILDING("data/closedway", "buildings", marktr("Buildings")),
    HIGHWAY("presets/transport/way/way_road", "highways", marktr("Roads")),
    ADDRESS("presets/misc/housenumber_small", "addresses", marktr("Addresses")),
    POWER("presets/power/pole", "pole", marktr("Power")), PRESET("dialogs/search", "presets", marktr("Presets")),
    FEATURED("presets/service/network-wireless", "featured", marktr("Featured")),
    PREVIEW("presets/misc/fixme", "preview", marktr("Preview")), OTHER(null, "other", marktr("Other"));

    private static final Map<ImageSizes, Map<MapWithAICategory, ImageIcon>> iconCache = Collections
            .synchronizedMap(new EnumMap<>(ImageSizes.class));

    private final String category;
    private final String description;
    private final String icon;

    MapWithAICategory(String icon, String category, String description) {
        this.category = category;
        this.icon = icon == null || icon.trim().isEmpty() ? "mapwithai" : icon;
        this.description = description;
    }

    @Override
    public final String getCategoryString() {
        return category;
    }

    @Override
    public final String getDescription() {
        return description;
    }

    @Override
    public final ImageIcon getIcon(ImageSizes size) {
        return iconCache.computeIfAbsent(size, x -> Collections.synchronizedMap(new EnumMap<>(MapWithAICategory.class)))
                .computeIfAbsent(this, x -> ImageProvider.get(x.icon, size));
    }

    /**
     * Get the category from a string
     *
     * @param s The string to get the category from
     * @return The category, if found, else {@link #OTHER}
     */
    @Nonnull
    public static MapWithAICategory fromString(String s) {
        for (MapWithAICategory category : MapWithAICategory.values()) {
            if (category.getCategoryString().equals(s)) {
                return category;
            }
        }
        if (s != null && !s.trim().isEmpty()) {
            // fuzzy match
            String tmp = s.toLowerCase(Locale.ROOT);
            for (MapWithAICategory type : MapWithAICategory.values()) {
                if (tmp.contains(type.getDescription().toLowerCase(Locale.ROOT))
                        || type.getDescription().toLowerCase(Locale.ROOT).contains(tmp)) {
                    return type;
                }
            }
            // Check if it matches a preset
            if (TaggingPresets.getPresetKeys().stream().map(String::toLowerCase)
                    .anyMatch(m -> tmp.contains(m) || m.contains(tmp))) {
                return PRESET;
            }
        }
        return OTHER;
    }

    /**
     * Compare two MapWithAI categories
     */
    public static class DescriptionComparator implements Comparator<MapWithAICategory>, Serializable {

        private static final long serialVersionUID = 9131636715279880580L;

        @Override
        public int compare(MapWithAICategory o1, MapWithAICategory o2) {
            return (o1 == null || o2 == null) ? 1 : o1.getDescription().compareTo(o2.getDescription());
        }
    }

    @Override
    public MapWithAICategory getDefault() {
        return OTHER;
    }

    @Override
    public MapWithAICategory getFromString(String s) {
        return fromString(s);
    }
}
