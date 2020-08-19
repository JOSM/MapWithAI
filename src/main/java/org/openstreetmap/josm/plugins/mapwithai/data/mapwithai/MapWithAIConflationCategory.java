// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.ConflationSourceReader;
import org.openstreetmap.josm.tools.Logging;

public final class MapWithAIConflationCategory {
    private static final Map<MapWithAICategory, List<String>> CONFLATION_URLS = new EnumMap<>(MapWithAICategory.class);
    private static final String EMPTY_URL = "";
    private static final String DEFAULT_CONFLATION_JSON = "https://gokaart.gitlab.io/JOSM_MapWithAI/json/conflation_servers.json";
    private static String conflationJson = DEFAULT_CONFLATION_JSON;
    static {
        initialize();
    }

    static void initialize() {
        CONFLATION_URLS.clear();
        try (ConflationSourceReader reader = new ConflationSourceReader(conflationJson)) {
            CONFLATION_URLS.putAll(reader.parse());
        } catch (IOException e) {
            Logging.error(e);
        }
    }

    private MapWithAIConflationCategory() {
        // Hide the constructor
    }

    /**
     * Get a conflation URL for a specific category
     *
     * @param category The category for conflation
     * @return The URL to use for conflation
     */
    public static String conflationUrlFor(MapWithAICategory category) {
        return CONFLATION_URLS.getOrDefault(category, Collections.emptyList()).stream().findFirst().orElse(EMPTY_URL);
    }

    /**
     * Add a conflation URL for a specific category
     *
     * @param category The category for conflation
     * @param url      The URL to use for conflation
     */
    public static void addConflationUrlFor(MapWithAICategory category, String url) {
        Collection<String> list = CONFLATION_URLS.computeIfAbsent(category, i -> new ArrayList<>(1));
        list.add(url);
    }

    /**
     * Set the URL to use to get conflation servers
     *
     * @param url The URL to use
     */
    public static void setConflationJsonLocation(String url) {
        conflationJson = url;
        initialize();
    }

    /**
     * Reset the conflation json location to the default location
     */
    public static void resetConflationJsonLocation() {
        setConflationJsonLocation(DEFAULT_CONFLATION_JSON);
    }

    /**
     * Get the current conflation json location
     *
     * @return The URL that is used to build conflation information
     */
    public static String getConflationJsonLocation() {
        return conflationJson;
    }
}
