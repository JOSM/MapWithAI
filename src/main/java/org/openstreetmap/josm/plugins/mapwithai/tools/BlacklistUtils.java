// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import java.io.BufferedReader;
import java.io.IOException;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Logging;

/**
 * Check if this version has been blacklisted (i.e., bad data is uploaded)
 *
 * @author Taylor Smock
 *
 */
public class BlacklistUtils {
    static final String DEFAULT_BLACKLIST_URL = "https://gokaart.gitlab.io/JOSM_MapWithAI/json/blacklisted_versions.json";
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
        String version = MapWithAIPlugin.getVersionInfo();
        CachedFile blacklist = new CachedFile(blacklistUrl);
        try (BufferedReader bufferedReader = blacklist.getContentReader();
                JsonReader reader = Json.createReader(bufferedReader)) {
            JsonStructure structure = reader.read();
            if (structure.getValueType() == JsonValue.ValueType.ARRAY) {
                JsonArray array = (JsonArray) structure;
                return array.stream().filter(v -> v.getValueType() == JsonValue.ValueType.STRING)
                        .map(v -> ((JsonString) v).getString()).anyMatch(version::equals);
            } else if (structure.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject object = (JsonObject) structure;
                return object.keySet().contains(version);
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
