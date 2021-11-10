// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.spi.preferences;

import java.util.Objects;

/**
 * Class to hold the global preferences objects. Modeled after
 * {@link org.openstreetmap.josm.spi.preferences.Config}.
 *
 * @author Taylor Smock
 */
public class MapWithAIConfig {
    private static IMapWithAIUrls urls;

    /**
     * Get class that provides the value of certain URLs
     *
     * @return the global {@link IMapWithAIUrls} instance
     */
    public static IMapWithAIUrls getUrls() {
        return urls;
    }

    /**
     * Install the global URLs provider.
     *
     * @param urls the global URLs provider instance to set (must not be null)
     */
    public static void setUrlsProvider(IMapWithAIUrls urls) {
        MapWithAIConfig.urls = Objects.requireNonNull(urls);
    }
}
