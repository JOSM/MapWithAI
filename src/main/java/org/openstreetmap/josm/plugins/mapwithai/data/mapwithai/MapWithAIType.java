// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import org.openstreetmap.josm.data.sources.ISourceType;

import jakarta.annotation.Nonnull;

/**
 * Type of MapWithAI entry
 *
 * @author Taylor Smock
 */
public enum MapWithAIType implements ISourceType<MapWithAIType> {
    FACEBOOK("facebook"), THIRD_PARTY("thirdParty"), ESRI("esri"), ESRI_FEATURE_SERVER("esriFeatureServer"),
    MAPBOX_VECTOR_TILE("mvt"), PMTILES("pmtiles");

    private final String typeString;

    MapWithAIType(String typeString) {
        this.typeString = typeString;
    }

    @Override
    public String getTypeString() {
        return typeString;
    }

    /**
     * Get the type from a string
     *
     * @param s The string to parse
     * @return The type
     */
    @Nonnull
    public static MapWithAIType fromString(String s) {
        for (MapWithAIType type : MapWithAIType.values()) {
            if (type.getTypeString().equals(s)) {
                return type;
            }
        }
        return MapWithAIType.THIRD_PARTY;
    }

    @Override
    public MapWithAIType getDefault() {
        return THIRD_PARTY;
    }

    @Override
    public MapWithAIType getFromString(String s) {
        return fromString(s);
    }
}
