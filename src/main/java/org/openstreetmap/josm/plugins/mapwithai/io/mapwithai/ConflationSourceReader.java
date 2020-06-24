// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;

public class ConflationSourceReader implements Closeable {

    private final String source;
    private CachedFile cachedFile;
    private boolean fastFail;

    /**
     * Constructs a {@code ConflationSourceReader} from a given filename, URL or
     * internal resource.
     *
     * @param source can be:
     *               <ul>
     *               <li>relative or absolute file name</li>
     *               <li>{@code file:///SOME/FILE} the same as above</li>
     *               <li>{@code http://...} a URL. It will be cached on disk.</li>
     *               <li>{@code resource://SOME/FILE} file from the classpath
     *               (usually in the current *.jar)</li>
     *               <li>{@code josmdir://SOME/FILE} file inside josm user data
     *               directory (since r7058)</li>
     *               <li>{@code josmplugindir://SOME/FILE} file inside josm plugin
     *               directory (since r7834)</li>
     *               </ul>
     */
    public ConflationSourceReader(String source) {
        this.source = source;
    }

    /**
     * Parses MapWithAI entry sources
     *
     * @param jsonObject The json of the data sources
     * @return The parsed entries
     */
    public static Map<MapWithAICategory, List<String>> parseJson(JsonObject jsonObject) {
        return jsonObject.entrySet().stream().flatMap(i -> parse(i).stream())
                .collect(Collectors.groupingBy(p -> p.a, Collectors.mapping(p -> p.b, Collectors.toList())));
    }

    /**
     * Parses MapWithAI source.
     *
     * @return list of source info
     * @throws IOException if any I/O error occurs
     */
    public Map<MapWithAICategory, List<String>> parse() throws IOException {
        Map<MapWithAICategory, List<String>> entries = Collections.emptyMap();
        cachedFile = new CachedFile(source);
        cachedFile.setFastFail(fastFail);
        try (JsonReader reader = Json.createReader(cachedFile.setMaxAge(CachedFile.DAYS)
                .setCachingStrategy(CachedFile.CachingStrategy.IfModifiedSince).getContentReader())) {
            JsonStructure struct = reader.read();
            if (JsonValue.ValueType.OBJECT == struct.getValueType()) {
                JsonObject jsonObject = struct.asJsonObject();
                entries = parseJson(jsonObject);
            }
            return entries;
        }
    }

    private static List<Pair<MapWithAICategory, String>> parse(Map.Entry<String, JsonValue> entry) {
        if (JsonValue.ValueType.OBJECT == entry.getValue().getValueType()) {
            JsonObject object = entry.getValue().asJsonObject();
            String url = object.getString("url", null);
            List<MapWithAICategory> categories = object.getJsonArray("categories").getValuesAs(JsonString.class)
                    .stream().map(JsonString::toString).map(MapWithAICategory::fromString).collect(Collectors.toList());
            return categories.stream().map(c -> new Pair<>(c, url)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

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

}
