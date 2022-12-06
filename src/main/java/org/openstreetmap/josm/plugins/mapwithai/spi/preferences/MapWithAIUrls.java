// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.spi.preferences;

/**
 * Store the MapWithAI URLs
 */
public final class MapWithAIUrls implements IMapWithAIUrls {
    /** The base url */
    private static final String BASE_URL = "https://josm.github.io/MapWithAI/";
    /** The default url for additional conflation servers */
    private static final String DEFAULT_CONFLATION_JSON = BASE_URL + "json/conflation_servers.json";
    /** The default URL for the MapWithAI sources */
    private static final String DEFAULT_MAPWITHAI_SOURCES_JSON = BASE_URL + "json/sources.json";
    /** The default url for the MapWithAI paint style */
    private static final String DEFAULT_PAINT_STYLE_RESOURCE_URL = "https://josm.openstreetmap.de/josmfile?page=Styles/MapWithAI&zip=1";

    private static class InstanceHolder {
        static final MapWithAIUrls INSTANCE = new MapWithAIUrls();
    }

    /**
     * Returns the unique instance.
     *
     * @return the unique instance
     */
    public static MapWithAIUrls getInstance() {
        return MapWithAIUrls.InstanceHolder.INSTANCE;
    }

    @Override
    public String getConflationServerJson() {
        return DEFAULT_CONFLATION_JSON;
    }

    @Override
    public String getMapWithAISourcesJson() {
        return DEFAULT_MAPWITHAI_SOURCES_JSON;
    }

    @Override
    public String getMapWithAIPaintStyle() {
        return DEFAULT_PAINT_STYLE_RESOURCE_URL;
    }

    private MapWithAIUrls() {
        // Hide the constructor
    }
}
