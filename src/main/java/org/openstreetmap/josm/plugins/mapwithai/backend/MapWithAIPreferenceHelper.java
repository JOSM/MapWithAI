// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.spi.preferences.Config;

public final class MapWithAIPreferenceHelper {
    /** This is the default MapWithAI URL */
    public static final String DEFAULT_MAPWITHAI_API = "https://www.facebook.com/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox={bbox}";

    /**
     * These are the default parameters for
     * {@link MapWithAIPreferenceHelper#DEFAULT_MAPWITHAI_API}
     */
    public static final String DEFAULT_MAPWITHAI_API_PARAMETERS = "[{\"parameter\": \"result_type=road_building_vector_xml\", \"description\": \"buildings\", \"enabled\": true}]";
    private static final int DEFAULT_MAXIMUM_ADDITION = 5;
    private static final String AUTOSWITCHLAYERS = MapWithAIPlugin.NAME.concat(".autoswitchlayers");
    private static final String MERGEBUILDINGADDRESSES = MapWithAIPlugin.NAME.concat(".mergebuildingaddresses");
    private static final String MAXIMUMSELECTION = MapWithAIPlugin.NAME.concat(".maximumselection");
    private static final String API_CONFIG = MapWithAIPlugin.NAME.concat(".apis");
    private static final String API_MAP_CONFIG = API_CONFIG.concat("map");
    private static final String URL_STRING = "url";
    private static final String SOURCE_STRING = "source";
    private static final String ENABLED_STRING = "enabled";
    private static final String PARAMETERS_STRING = "parameters";

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
     * Get the current MapWithAI urls
     *
     * @return A list of enabled MapWithAI urls (maps have source, parameters,
     *         enabled, and the url)
     */
    public static List<Map<String, String>> getMapWithAIUrl() {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        return (layer != null) && (layer.getMapWithAIUrl() != null)
                ? getMapWithAIURLs().parallelStream().filter(map -> layer.getMapWithAIUrl().equals(map.get(URL_STRING)))
                        .collect(Collectors.toList())
                : getMapWithAIURLs().stream()
                        .filter(map -> Boolean.valueOf(map.getOrDefault(ENABLED_STRING, Boolean.FALSE.toString())))
                        .collect(Collectors.toList());
    }

    /**
     * Get all of the MapWithAI urls (or the default)
     *
     * @return The urls for MapWithAI endpoints (maps have source, parameters,
     *         enabled, and the url)
     */
    public static List<Map<String, String>> getMapWithAIURLs() {
        final List<Map<String, String>> returnMap = Config.getPref().getListOfMaps(API_MAP_CONFIG, new ArrayList<>())
                .stream().map(TreeMap::new).collect(Collectors.toList());
        if (MapWithAIDataUtils.getLayer(false) != null) {
            TreeMap<String, String> layerMap = new TreeMap<>();
            layerMap.put("url", MapWithAIDataUtils.getLayer(false).getMapWithAIUrl());
            if (layerMap.get("url") != null && !layerMap.get("url").trim().isEmpty() && returnMap.parallelStream()
                    .noneMatch(map -> map.getOrDefault("url", "").equals(layerMap.get("url")))) {
                returnMap.add(layerMap);
            }
        }
        return returnMap;
    }

    /**
     * Get the maximum number of objects that can be added at one time
     *
     * @return The maximum selection. If 0, allow any number.
     */
    public static int getMaximumAddition() {
        final MapWithAILayer mapWithAILayer = MapWithAIDataUtils.getLayer(false);
        Integer defaultReturn = Config.getPref().getInt(MAXIMUMSELECTION, getDefaultMaximumAddition());
        if ((mapWithAILayer != null) && (mapWithAILayer.getMaximumAddition() != null)) {
            defaultReturn = mapWithAILayer.getMaximumAddition();
        }
        return defaultReturn > DEFAULT_MAXIMUM_ADDITION * 10 ? DEFAULT_MAXIMUM_ADDITION * 10 : defaultReturn;
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
        if ((layer != null) && (layer.isSwitchLayers() != null)) {
            returnBoolean = layer.isSwitchLayers();
        }
        return returnBoolean;
    }

    /**
     * Set the MapWithAI url
     *
     * @param source    The source tag for the url
     * @param url       The url to set as the default
     * @param enabled   {@code true} if the url should be used for downloads
     * @param permanent {@code true} if we want the setting to persist between
     *                  sessions
     */
    public static void setMapWithAIUrl(String source, String url, boolean enabled, boolean permanent) {
        setMapWithAIUrl(new DataUrl(source, url, permanent), enabled, permanent);
    }

    public static void setMapWithAIUrl(DataUrl dataUrl, boolean enabled, boolean permanent) {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        final String setUrl = dataUrl.getMap().getOrDefault("url", DEFAULT_MAPWITHAI_API);

        if (permanent) {
            final List<Map<String, String>> urls = new ArrayList<>(getMapWithAIURLs());
            Map<String, String> addOrModifyMap = urls.parallelStream()
                    .filter(map -> map.getOrDefault(URL_STRING, "").equals(setUrl)).findFirst().orElse(new TreeMap<>());
            if (addOrModifyMap.isEmpty()) {
                urls.add(addOrModifyMap);
            } else {
                urls.remove(addOrModifyMap);
                addOrModifyMap = new TreeMap<>(addOrModifyMap);
                urls.add(addOrModifyMap);
            }
            addOrModifyMap.putAll(dataUrl.getMap());
            addOrModifyMap.put(ENABLED_STRING, Boolean.toString(enabled));
            setMapWithAIURLs(urls);
        }
        if (layer != null && !permanent && enabled) {
            layer.setMapWithAIUrl(setUrl);
        }
    }

    /**
     * Set the MapWithAI urls
     *
     * @param urls A list of URLs
     * @return true if the configuration changed
     */
    public static boolean setMapWithAIURLs(List<Map<String, String>> urls) {
        final List<Map<String, String>> setUrls = urls.isEmpty() ? new ArrayList<>() : urls;
        if (urls.isEmpty()) {
            final TreeMap<String, String> defaultAPIMap = new TreeMap<>();
            defaultAPIMap.put(URL_STRING, DEFAULT_MAPWITHAI_API);
            defaultAPIMap.put(ENABLED_STRING, Boolean.TRUE.toString());
            defaultAPIMap.put(SOURCE_STRING, MapWithAIPlugin.NAME);
            defaultAPIMap.put(PARAMETERS_STRING, DEFAULT_MAPWITHAI_API_PARAMETERS);
            setUrls.add(defaultAPIMap);
        }
        return Config.getPref().putListOfMaps(API_MAP_CONFIG, setUrls);
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
