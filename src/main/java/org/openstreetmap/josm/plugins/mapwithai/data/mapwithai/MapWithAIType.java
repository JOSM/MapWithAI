// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import org.openstreetmap.josm.data.sources.ISourceType;

/**
 * Type of MapWithAI entry
 *
 * @author Taylor Smock
 */
public enum MapWithAIType implements ISourceType<MapWithAIType> {
    FACEBOOK("facebook"), THIRD_PARTY("thirdParty"), ESRI("esri"), ESRI_FEATURE_SERVER("esriFeatureServer");

    private final String typeString;

    MapWithAIType(String typeString) {
        this.typeString = typeString;
    }

    @Override
    public String getTypeString() {
        return typeString;
    }

    public static MapWithAIType fromString(String s) {
        for (MapWithAIType type : MapWithAIType.values()) {
            if (type.getTypeString().equals(s)) {
                return type;
            }
        }
        return null;
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
