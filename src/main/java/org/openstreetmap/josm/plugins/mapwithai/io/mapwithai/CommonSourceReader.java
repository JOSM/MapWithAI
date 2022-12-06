// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import java.io.IOException;
import java.util.Optional;

import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Utils;

/**
 * Read sources for MapWithAI
 *
 * @param <T> The expected type
 */
public abstract class CommonSourceReader<T> implements AutoCloseable {
    private final String source;
    private CachedFile cachedFile;
    private boolean fastFail;
    private boolean clearCache;

    CommonSourceReader(String source) {
        this.source = source;
    }

    /**
     * Parses MapWithAI source information.
     *
     * @return list of source info
     * @throws IOException if any I/O error occurs
     */
    public Optional<T> parse() throws IOException {
        this.cachedFile = new CachedFile(this.source);
        if (this.clearCache) {
            this.cachedFile.clear();
            this.cachedFile = new CachedFile(this.source);
        }
        this.cachedFile.setFastFail(this.fastFail);
        try (JsonReader reader = Json.createReader(cachedFile.setMaxAge(CachedFile.DAYS)
                .setCachingStrategy(CachedFile.CachingStrategy.IfModifiedSince).getContentReader())) {
            JsonStructure struct = reader.read();
            if (JsonValue.ValueType.OBJECT == struct.getValueType()) {
                JsonObject jsonObject = struct.asJsonObject();
                return Optional.ofNullable(this.parseJson(jsonObject));
            }
            return Optional.empty();
        }
    }

    /**
     * Parses MapWithAI entry sources
     *
     * @param jsonObject The json of the data sources
     * @return The parsed entries
     */
    public abstract T parseJson(JsonObject jsonObject);

    /**
     * Sets whether opening HTTP connections should fail fast, i.e., whether a
     * {@link HttpClient#setConnectTimeout(int) low connect timeout} should be used.
     *
     * @param fastFail whether opening HTTP connections should fail fast
     * @see CachedFile#setFastFail(boolean)
     */
    public void setFastFail(boolean fastFail) {
        this.fastFail = fastFail;
    }

    @Override
    public void close() throws IOException {
        Utils.close(cachedFile);
    }

    /**
     * Indicate if cache should be ignored
     *
     * @param clearCache {@code true} to ignore cache
     */
    public void setClearCache(boolean clearCache) {
        this.clearCache = clearCache;
    }
}
