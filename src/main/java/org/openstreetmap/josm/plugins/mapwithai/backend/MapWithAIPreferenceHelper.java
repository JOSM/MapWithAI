// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.DoubleProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.spi.preferences.Config;

public final class MapWithAIPreferenceHelper {
    private static final int DEFAULT_MAXIMUM_ADDITION = 100;
    private static final String AUTOSWITCHLAYERS = MapWithAIPlugin.NAME.concat(".autoswitchlayers");
    private static final String MERGEBUILDINGADDRESSES = MapWithAIPlugin.NAME.concat(".mergebuildingaddresses");
    private static final String MAXIMUMSELECTION = MapWithAIPlugin.NAME.concat(".maximumselection");
    private static final DoubleProperty PROPERTY_DUPLICATE_NODE_DISTANCE = new DoubleProperty(
            MapWithAIPlugin.NAME.concat(".duplicatenodedistance"), 0.6);
    private static final IntegerProperty PROPERTY_MAXIMUM_SELECTION = new IntegerProperty(MAXIMUMSELECTION,
            getDefaultMaximumAddition());
    private static final BooleanProperty PROPERTY_MERGEBUILDINGADDRESSES = new BooleanProperty(MERGEBUILDINGADDRESSES,
            true);
    private static final BooleanProperty PROPERTY_AUTOSWITCHLAYERS = new BooleanProperty(AUTOSWITCHLAYERS, true);

    private MapWithAIPreferenceHelper() {
        // Hide the constructor
    }

    /**
     * The default maximum number of objects to add
     *
     * @return {@link MapWithAIPreferenceHelper#DEFAULT_MAXIMUM_ADDITION}
     */
    public static int getDefaultMaximumAddition() {
        return DEFAULT_MAXIMUM_ADDITION;
    }

    /**
     * Get the current MapWithAI urls
     *
     * @return A list of enabled MapWithAI urls (maps have source, parameters,
     *         enabled, and the url)
     */
    public static Collection<MapWithAIInfo> getMapWithAIUrl() {
        MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        if (layer != null) {
            if (!layer.getDownloadedInfo().isEmpty()) {
                return layer.getDownloadedInfo();
            } else if (layer.getMapWithAIUrl() != null) {
                return Collections.singleton(layer.getMapWithAIUrl());
            }
        }
        return MapWithAILayerInfo.getInstance().getLayers();
    }

    /**
     * Get the maximum number of objects that can be added at one time
     *
     * @return The maximum selection. If 0, allow any number.
     */
    public static int getMaximumAddition() {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        Integer defaultReturn = PROPERTY_MAXIMUM_SELECTION.get();
        if ((mapWithAILayer != null) && (mapWithAILayer.getMaximumAddition() != null)) {
            defaultReturn = mapWithAILayer.getMaximumAddition();
        }
        return defaultReturn > DEFAULT_MAXIMUM_ADDITION * 10 ? DEFAULT_MAXIMUM_ADDITION * 10 : defaultReturn;
    }

    /**
     * Check if the user wants to merge buildings and addresses
     *
     * @return {@code true} if we want to automatically merge buildings with
     *         pre-existing addresses
     */
    public static boolean isMergeBuildingAddress() {
        return PROPERTY_MERGEBUILDINGADDRESSES.get();
    }

    /**
     * Check if the user wants to switch layers automatically after adding data.
     *
     * @return {@code true} if we want to automatically switch layers
     */
    public static boolean isSwitchLayers() {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        boolean returnBoolean = PROPERTY_AUTOSWITCHLAYERS.get();
        if ((layer != null) && (layer.isSwitchLayers() != null)) {
            returnBoolean = layer.isSwitchLayers();
        }
        return returnBoolean;
    }

    /**
     * Add a MapWithAI url. If both boolean parameters are false, nothing happens.
     *
     * @param info      The info to add
     * @param enabled   If it should be enabled
     * @param permanent If it should be added permanently
     */
    public static void setMapWithAIUrl(MapWithAIInfo info, boolean enabled, boolean permanent) {
        if (permanent && enabled) {
            MapWithAILayerInfo.getInstance().add(info);
            MapWithAILayerInfo.getInstance().save();
        } else if (enabled && MapWithAIDataUtils.getLayer(false) != null) {
            MapWithAIDataUtils.getLayer(false).setMapWithAIUrl(info);
        }
    }

    /**
     * Set the maximum number of objects that can be added at one time.
     *
     * @param max       The maximum number of objects to select (0 allows any number
     *                  to be selected).
     * @param permanent {@code true} if we want the setting to persist between
     *                  sessions
     */
    public static void setMaximumAddition(int max, boolean permanent) {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        if (permanent) {
            if (getDefaultMaximumAddition() == max) {
                PROPERTY_MAXIMUM_SELECTION.remove();
            } else {
                PROPERTY_MAXIMUM_SELECTION.put(max);
            }
        } else if (mapWithAILayer != null) {
            mapWithAILayer.setMaximumAddition(max);
        }
    }

    /**
     * Set whether or not a we switch from the MapWithAI layer to an OSM data layer
     *
     * @param selected  true if we are going to switch layers
     * @param permanent {@code true} if we want the setting to persist between
     *                  sessions
     */
    public static void setMergeBuildingAddress(boolean selected, boolean permanent) {
        if (permanent) {
            PROPERTY_MERGEBUILDINGADDRESSES.put(selected);
        }
    }

    /**
     * Set whether or not a we switch from the MapWithAI layer to an OSM data layer
     *
     * @param selected  true if we are going to switch layers
     * @param permanent {@code true} if we want the setting to persist between
     *                  sessions
     */
    public static void setSwitchLayers(boolean selected, boolean permanent) {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        if (permanent) {
            PROPERTY_AUTOSWITCHLAYERS.put(selected);
        } else if (layer != null) {
            layer.setSwitchLayers(selected);
        }
    }

    /**
     * Get the maximum distance for a node to be considered a duplicate
     *
     * @return The max distance between nodes for duplicates
     */
    public static double getMaxNodeDistance() {
        return PROPERTY_DUPLICATE_NODE_DISTANCE.get();
    }

    /**
     * Get the tags to replace
     *
     * @return A map of tags to replacement tags (use {@link Tag#ofString} to parse)
     */
    public static Map<String, String> getReplacementTags() {
        final Map<String, String> defaultMap = Collections.emptyMap();
        final List<Map<String, String>> listOfMaps = Config.getPref()
                .getListOfMaps(MapWithAIPlugin.NAME.concat(".replacementtags"), Collections.singletonList(defaultMap));
        return listOfMaps.isEmpty() ? defaultMap : listOfMaps.get(0);
    }

    /**
     * Set the tags to replace
     *
     * @param tagsToReplace set the tags to replace
     */
    public static void setReplacementTags(Map<String, String> tagsToReplace) {
        final List<Map<String, String>> tags = tagsToReplace.isEmpty() ? null
                : Collections.singletonList(new TreeMap<>(tagsToReplace));
        Config.getPref().putListOfMaps(MapWithAIPlugin.NAME.concat(".replacementtags"), tags);
    }

}
