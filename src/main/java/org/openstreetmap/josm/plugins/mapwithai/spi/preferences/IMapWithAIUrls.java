// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.spi.preferences;

/**
 * Interface for a provider of certain URLs. Modelled after
 * {@link org.openstreetmap.josm.spi.preferences.IUrls}.
 *
 * @author Taylor Smock
 */
public interface IMapWithAIUrls {

    /**
     * Get the conflation server json URL
     *
     * @return The URL with additional conflation servers
     */
    String getConflationServerJson();

    /**
     * Get the URL for MapWithAI sources
     *
     * @return The URL with source information
     */
    String getMapWithAISourcesJson();

    /**
     * Get the URL for the MapWithAI paintstyle
     *
     * @return The URL to use to get the paint style
     */
    String getMapWithAIPaintStyle();
}
