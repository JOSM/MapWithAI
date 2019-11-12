// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.spi.preferences.Config;

public final class MapWithAIPreferenceHelper {
    public static final String DEFAULT_MAPWITHAI_API = "https://www.facebook.com/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox={bbox}";
    private static final int DEFAULT_MAXIMUM_ADDITION = 5;
    private static final String AUTOSWITCHLAYERS = MapWithAIPlugin.NAME.concat(".autoswitchlayers");
    private static final String MERGEBUILDINGADDRESSES = MapWithAIPlugin.NAME.concat(".mergebuildingaddresses");
    private static final String MAXIMUMSELECTION = MapWithAIPlugin.NAME.concat(".maximumselection");

    private MapWithAIPreferenceHelper() {
        // Hide the constructor
    }

    /**
     * @return The default maximum number of objects to add.
     */
    public static int getDefaultMaximumAddition() {
        return DEFAULT_MAXIMUM_ADDITION;
    }

    /**
     * Get the current MapWithAI url
     *
     * @return A MapWithAI url
     */
    public static String getMapWithAIUrl() {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        String url = Config.getPref().get(MapWithAIPlugin.NAME.concat(".current_api"), DEFAULT_MAPWITHAI_API);
        if (layer == null || layer.getMapWithAIUrl() == null) {
            final List<String> urls = getMapWithAIURLs();
            if (!urls.contains(url)) {
                url = DEFAULT_MAPWITHAI_API;
                setMapWithAIUrl(DEFAULT_MAPWITHAI_API, true);
            }
        } else {
            url = layer.getMapWithAIUrl();
        }
        return url;
    }

    /**
     * Get the MapWithAI urls (or the default)
     *
     * @return The urls for MapWithAI endpoints
     */
    public static List<String> getMapWithAIURLs() {
        return Config.getPref().getList(MapWithAIPlugin.NAME.concat(".apis"),
                new ArrayList<>(Arrays.asList(DEFAULT_MAPWITHAI_API)));
    }

    /**
     * Get the maximum number of objects that can be added at one time
     *
     * @return The maximum selection. If 0, allow any number.
     */
    public static int getMaximumAddition() {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        Integer defaultReturn = Config.getPref().getInt(MAXIMUMSELECTION,
                getDefaultMaximumAddition());
        if (mapWithAILayer != null && mapWithAILayer.getMaximumAddition() != null) {
            defaultReturn = mapWithAILayer.getMaximumAddition();
        }
        return defaultReturn;
    }

    /**
     * @return {@code true} if we want to automatically merge buildings with
     *         pre-existing addresses
     */
    public static boolean isMergeBuildingAddress() {
        return Config.getPref().getBoolean(MERGEBUILDINGADDRESSES, true);
    }

    /**
     * @return {@code true} if we want to automatically switch layers
     */
    public static boolean isSwitchLayers() {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        boolean returnBoolean = Config.getPref().getBoolean(AUTOSWITCHLAYERS, true);
        if (layer != null && layer.isSwitchLayers() != null) {
            returnBoolean = layer.isSwitchLayers();
        }
        return returnBoolean;
    }

    /**
     * Set the MapWithAI url
     *
     * @param url       The url to set as the default
     * @param permanent {@code true} if we want the setting to persist between
     *                  sessions
     */
    public static void setMapWithAIUrl(String url, boolean permanent) {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        String setUrl = url;
        if (permanent) {
            final List<String> urls = new ArrayList<>(getMapWithAIURLs());
            if (!urls.contains(url)) {
                urls.add(url);
                setMapWithAIURLs(urls);
            }
            if (DEFAULT_MAPWITHAI_API.equals(url)) {
                setUrl = "";
            }
            Config.getPref().put(MapWithAIPlugin.NAME.concat(".current_api"), setUrl);
        } else if (layer != null) {
            layer.setMapWithAIUrl(setUrl);
        }
    }

    /**
     * Set the MapWithAI urls
     *
     * @param urls A list of URLs
     */
    public static void setMapWithAIURLs(List<String> urls) {
        Config.getPref().putList(MapWithAIPlugin.NAME.concat(".apis"), urls);
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
                Config.getPref().put(MAXIMUMSELECTION, null);
            } else {
                Config.getPref().putInt(MAXIMUMSELECTION, max);
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
            if (selected) {
                Config.getPref().put(MERGEBUILDINGADDRESSES, null);
            } else {
                Config.getPref().putBoolean(MERGEBUILDINGADDRESSES, selected);
            }
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
            if (selected) {
                Config.getPref().put(AUTOSWITCHLAYERS, null);
            } else {
                Config.getPref().putBoolean(AUTOSWITCHLAYERS, selected);
            }
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
        return Config.getPref().getDouble(MapWithAIPlugin.NAME.concat(".duplicatenodedistance"), 0.6);
    }

    /**
     * @return A map of tags to replacement tags (use {@link Tag#ofString} to parse)
     */
    public static Map<String, String> getReplacementTags() {
        final Map<String, String> defaultMap = Collections.emptyMap();
        final List<Map<String, String>> listOfMaps = Config.getPref()
                .getListOfMaps(MapWithAIPlugin.NAME.concat(".replacementtags"), Arrays.asList(defaultMap));
        return listOfMaps.isEmpty() ? defaultMap : listOfMaps.get(0);
    }

    /**
     * @param tagsToReplace set the tags to replace
     */
    public static void setReplacementTags(Map<String, String> tagsToReplace) {
        final List<Map<String, String>> tags = tagsToReplace.isEmpty() ? null
                : Arrays.asList(new TreeMap<>(tagsToReplace));
        Config.getPref().putListOfMaps(MapWithAIPlugin.NAME.concat(".replacementtags"), tags);
    }
}
