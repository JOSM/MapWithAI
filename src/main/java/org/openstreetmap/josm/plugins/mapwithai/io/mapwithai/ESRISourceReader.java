// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.jcs3.access.CacheAccess;
import org.openstreetmap.josm.data.cache.JCSCacheManager;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.preferences.LongProperty;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

/**
 * Take a {@link MapWithAIType#ESRI} layer and convert it to a list of "true"
 * layers.
 */
public class ESRISourceReader {
    private static final int INITIAL_SEARCH = 100;
    /** The cache storing ESRI source information (json) */
    public static final CacheAccess<String, String> SOURCE_CACHE = JCSCacheManager.getCache("mapwithai:esrisources", 5,
            50_000, new File(Config.getDirs().getCacheDirectory(true), "mapwithai").getPath());
    private static final String ACCESS_INFORMATION = "accessInformation";
    private final MapWithAIInfo source;
    private boolean fastFail;
    private final List<MapWithAICategory> ignoreConflationCategories;
    private static final String JSON_QUERY_PARAM = "?f=json";
    private static final LongProperty MIRROR_MAXTIME = new LongProperty("mirror.maxtime", TimeUnit.DAYS.toSeconds(7));

    private final JsonProvider jsonProvider = JsonProvider.provider();

    /**
     * Constructs a {@code ImageryReader} from a given filename, URL or internal
     * resource.
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
    public ESRISourceReader(MapWithAIInfo source) {
        this.source = source;
        this.ignoreConflationCategories = source.getConflationIgnoreCategory();
    }

    /**
     * Parses MapWithAI source.
     *
     * @return list of source info
     * @throws IOException if any I/O error occurs
     */
    public List<ForkJoinTask<MapWithAIInfo>> parse() throws IOException {
        final var startReplace = Pattern.compile("\\{start}");
        final var search = "/search" + JSON_QUERY_PARAM + "&sortField=added&sortOrder=desc&num=" + INITIAL_SEARCH
                + "&start={start}";
        final var group = source.getId();
        var url = source.getUrl();
        if (!url.endsWith("/")) {
            url = url.concat("/");
        }

        final var information = new ArrayList<ForkJoinTask<MapWithAIInfo>>();

        final var next = new AtomicInteger(1);
        final var searchUrl = new AtomicReference<>(
                startReplace.matcher(search).replaceAll(Integer.toString(next.get())));

        while (next.get() != -1) {
            final var finalUrl = url + "content/groups/" + group + searchUrl.get();
            final var jsonString = getJsonString(finalUrl, TimeUnit.SECONDS.toMillis(MIRROR_MAXTIME.get()) / 7,
                    this.fastFail);
            if (jsonString == null) {
                continue;
            }
            try (var parser = jsonProvider
                    .createParser(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))) {
                /* Do nothing */
                if (parser.hasNext() && parser.next() == JsonParser.Event.START_OBJECT) {
                    parser.getObjectStream().forEach(entry -> {
                        if ("nextStart".equals(entry.getKey()) && entry.getValue()instanceof JsonNumber number) {
                            next.set(number.intValue());
                            searchUrl.set(startReplace.matcher(search).replaceAll(Integer.toString(next.get())));
                        } else if ("results".equals(entry.getKey()) && entry.getValue()instanceof JsonArray features) {
                            for (var feature : features.getValuesAs(JsonObject.class)) {
                                information.add(parse(feature));
                            }
                        }
                    });
                }
            } catch (ClassCastException e) {
                Logging.error(e);
                next.set(-1);
            }
        }
        for (var future : information) {
            try {
                future.join();
                future.get(1, TimeUnit.MINUTES);
            } catch (InterruptedException interruptedException) {
                Logging.warn(interruptedException);
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                Logging.warn(e);
            }
        }
        return information;
    }

    private ForkJoinTask<MapWithAIInfo> parse(JsonObject feature) {
        // Use the initial esri server information to keep conflation info
        final var newInfo = new MapWithAIInfo(source);
        newInfo.setId(feature.getString("id"));
        ForkJoinTask<MapWithAIInfo> future;
        if ("Feature Service".equals(feature.getString("type", ""))) {
            future = ForkJoinTask.adapt(() -> newInfo.setUrl(featureService(newInfo, feature.getString("url"))),
                    newInfo);
        } else {
            newInfo.setUrl(feature.getString("url"));
            future = ForkJoinTask.adapt(() -> newInfo);
        }
        MapWithAIDataUtils.getForkJoinPool().execute(future);
        newInfo.setName(feature.getString("title", feature.getString("name")));
        final var extent = feature.getJsonArray("extent").getValuesAs(JsonArray.class).stream()
                .flatMap(array -> array.getValuesAs(JsonNumber.class).stream()).map(JsonNumber::doubleValue)
                .map(Object::toString).toArray(String[]::new);
        final var imageryBounds = new ImageryBounds(String.join(",", extent[1], extent[0], extent[3], extent[2]), ",");
        newInfo.setBounds(imageryBounds);
        newInfo.setSourceType(MapWithAIType.ESRI_FEATURE_SERVER);
        newInfo.setTermsOfUseText(feature.getString("licenseInfo", null));
        if (feature.containsKey("thumbnail")) {
            newInfo.setAttributionImageURL(
                    source.getUrl() + "content/items/" + newInfo.getId() + "/info/" + feature.getString("thumbnail"));
        }
        if (feature.containsKey("groupCategories")) {
            final var categories = feature.getJsonArray("groupCategories").getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString).map(s -> s.replace("/Categories/", ""))
                    .map(MapWithAICategory::fromString).collect(Collectors.toCollection(ArrayList::new));
            final var category = categories.stream().filter(c -> MapWithAICategory.FEATURED != c).findFirst()
                    .orElse(MapWithAICategory.OTHER);
            newInfo.setCategory(category);
            categories.remove(category);
            newInfo.setAdditionalCategories(categories);
        }

        if (this.ignoreConflationCategories.contains(newInfo.getCategory())) {
            newInfo.setConflation(false);
        }
        if (feature.containsKey(ACCESS_INFORMATION)
                && feature.get(ACCESS_INFORMATION).getValueType() != JsonValue.ValueType.NULL) {
            newInfo.setAttributionText(feature.getString(ACCESS_INFORMATION));
        }
        newInfo.setDescription(feature.getString("snippet"));
        if (newInfo.getSource() != null) {
            final var sourceTag = new StringBuilder(newInfo.getSource());
            if (!sourceTag.toString().endsWith("/")) {
                sourceTag.append('/');
            }
            sourceTag.append(feature.getString("name", newInfo.getId()));
            newInfo.setSource(sourceTag.toString());
        }
        newInfo.setTermsOfUseURL("https://wiki.openstreetmap.org/wiki/Esri/ArcGIS_Datasets#License");
        return future;
    }

    /**
     * Get the json string for a URL
     *
     * @param url           The URL to get
     * @param fastFail      Fail fast (1 second)
     * @param defaultMaxAge the default max age for the response to be cached
     * @return The json string, or {@code null}.
     */
    @Nullable
    private static String getJsonString(@Nonnull final String url, final long defaultMaxAge, final boolean fastFail) {
        var jsonString = SOURCE_CACHE.get(url);
        if (jsonString == null) {
            HttpClient client = null;
            try {
                client = HttpClient.create(new URL(url));
                if (fastFail) {
                    client.setReadTimeout(1000);
                }
                final var response = client.connect();
                jsonString = response.fetchContent();
                if (jsonString != null && response.getResponseCode() < 400 && response.getResponseCode() >= 200) {
                    // getExpiration returns milliseconds
                    final long expirationTime = response.getExpiration();
                    final var elementAttributes = SOURCE_CACHE.getDefaultElementAttributes();
                    if (expirationTime > 0) {
                        elementAttributes.setMaxLife(response.getExpiration());
                    } else {
                        elementAttributes.setMaxLife(defaultMaxAge);
                    }
                    SOURCE_CACHE.put(url, jsonString, elementAttributes);
                }
            } catch (final IOException e) {
                Logging.error(e);
            } finally {
                if (client != null) {
                    client.disconnect();
                }
            }
        }
        return jsonString;
    }

    /**
     * Get feature service information
     *
     * @param mapwithaiInfo The info to update
     * @param url           The url to get
     * @return The base url for the feature service
     */
    @Nullable
    private String featureService(@Nonnull MapWithAIInfo mapwithaiInfo, @Nonnull String url) {
        final var toGet = url.endsWith(JSON_QUERY_PARAM) ? url : url + JSON_QUERY_PARAM;
        final var jsonString = getJsonString(toGet, TimeUnit.SECONDS.toMillis(MIRROR_MAXTIME.get()), this.fastFail);
        if (jsonString == null) {
            return null;
        }

        try (var reader = Json.createReader(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))) {
            final var info = reader.readObject();
            final var layers = info.getJsonArray("layers");
            // This fixes #20551
            if (layers == null || layers.stream().noneMatch(Objects::nonNull)) {
                return null;
            }
            // TODO use all the layers?
            final var layer = layers.stream().filter(Objects::nonNull).findFirst().orElse(JsonValue.EMPTY_JSON_OBJECT)
                    .asJsonObject();
            if (layer.containsKey("id")) {
                String partialUrl = (url.endsWith("/") ? url : url + "/") + layer.getInt("id");
                mapwithaiInfo.setReplacementTags(() -> getReplacementTags(partialUrl));

                return partialUrl;
            }
        } catch (JsonParsingException e) {
            Logging.error(e);
        }
        return null;
    }

    /**
     * Get the replacement tags for a feature service
     *
     * @param layerUrl The service url
     * @return A map of replacement tags
     */
    @Nonnull
    private Map<String, String> getReplacementTags(@Nonnull String layerUrl) {
        final var toGet = layerUrl.endsWith(JSON_QUERY_PARAM) ? layerUrl : layerUrl.concat(JSON_QUERY_PARAM);
        final var jsonString = getJsonString(toGet, TimeUnit.SECONDS.toMillis(MIRROR_MAXTIME.get()), this.fastFail);
        if (jsonString != null) {
            try (var reader = Json
                    .createReader(new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8)))) {
                return reader.readObject().getJsonArray("fields").getValuesAs(JsonObject.class).stream()
                        .collect(Collectors.toMap(o -> o.getString("name"), ESRISourceReader::getReplacementTag));
            }
        }
        return Collections.emptyMap();
    }

    private static String getReplacementTag(JsonObject tag) {
        if (tag.getBoolean("editable", true) && !"esriFieldTypeOID".equals(tag.getString("type", null))) {
            return tag.getString("alias", "");
        }
        return "";
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
}
