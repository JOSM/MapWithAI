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

public class MapWithAIConflationCategory {
    private static final Map<MapWithAICategory, List<String>> CONFLATION_URLS = new EnumMap<>(MapWithAICategory.class);
    private static final String EMPTY_URL = "";
    protected static final String DEFAULT_CONFLATION_JSON = "https://gokaart.gitlab.io/JOSM_MapWithAI/json/conflation_servers.json";
    static {
        initialize();
    }

    static void initialize() {
        try (ConflationSourceReader reader = new ConflationSourceReader(DEFAULT_CONFLATION_JSON)) {
            CONFLATION_URLS.putAll(reader.parse());
        } catch (IOException e) {
            Logging.error(e);
        }
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
}
