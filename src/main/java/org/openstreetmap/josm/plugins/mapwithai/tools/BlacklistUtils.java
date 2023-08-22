// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import java.io.IOException;

import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Logging;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Check if this version has been blacklisted (i.e., bad data is uploaded)
 *
 * @author Taylor Smock
 *
 */
public final class BlacklistUtils {
    static final String DEFAULT_BLACKLIST_URL = "https://josm.github.io/MapWithAI/json/blacklisted_versions.json";
    private static String blacklistUrl = DEFAULT_BLACKLIST_URL;

    private BlacklistUtils() {
        // Don't instantiate
    }

    /**
     * Check if this version is known to make bad data
     *
     * @return {@code true} if no data should be uploaded or added. Defaults to
     *         {@code true}.
     */
    public static boolean isBlacklisted() {
        final var version = MapWithAIPlugin.getVersionInfo();
        final var blacklist = new CachedFile(blacklistUrl);
        try (var bufferedReader = blacklist.getContentReader(); var reader = Json.createReader(bufferedReader)) {
            final var structure = reader.read();
            if (structure instanceof JsonArray array) {
                return array.stream().filter(v -> v.getValueType() == JsonValue.ValueType.STRING)
                        .map(v -> ((JsonString) v).getString()).anyMatch(version::equals);
            } else if (structure instanceof JsonObject object) {
                return object.containsKey(version);
            }
        } catch (IOException | JsonException e) {
            try {
                blacklist.clear();
            } catch (IOException e1) {
                Logging.error(e1);
            }
            Logging.error(e);
        } finally {
            blacklist.close();
        }
        return true;
    }

    /**
     * Set a new blacklist URL. Should only be used for testing.
     *
     * @param url The url to check.
     */
    static void setBlacklistUrl(String url) {
        blacklistUrl = url;
    }

    /**
     * Check if we can reach the URL for the known-bad version list.
     *
     * @return {@code true} if the configured url is offline
     */
    public static boolean isOffline() {
        return NetworkManager.isOffline(blacklistUrl);
    }
}
