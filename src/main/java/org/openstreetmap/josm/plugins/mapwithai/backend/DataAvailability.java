// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Territories;
import org.openstreetmap.josm.tools.Utils;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Check for data availability in an area
 */
public class DataAvailability {
    /** A map of tag -&gt; message of possible data types */
    static final Map<String, String> POSSIBLE_DATA_POINTS = new TreeMap<>();

    private static final String PROVIDES = "provides";
    private static final int SEVEN_DAYS_IN_SECONDS = 604_800;

    /**
     * Map&lt;Source,
     * Map&lt;(url|parameters|countries|license|osm_compatible|permission_url),
     * Object&gt;&gt;
     */
    protected static final Map<String, Map<String, Object>> COUNTRY_MAP = new HashMap<>();
    /**
     * This holds classes that can give availability of data for a specific service
     */
    private static final List<Class<? extends DataAvailability>> DATA_SOURCES = new ArrayList<>();

    private static DataAvailability instance;

    /**
     * A map of countries to a map of available types
     * ({@code Map<Country, Map<Type, IsAvailable>>}
     */
    static final Map<String, Map<String, Boolean>> COUNTRIES = new HashMap<>();

    protected DataAvailability() {
        if (DataAvailability.class.equals(this.getClass())) {
            initialize();
        }
    }

    /**
     * Initialize the class
     */
    private static void initialize() {
        try (var jsonFile = new CachedFile(MapWithAIConfig.getUrls().getMapWithAISourcesJson());
                var jsonParser = Json.createParser(jsonFile.getContentReader())) {
            jsonFile.setMaxAge(SEVEN_DAYS_IN_SECONDS);
            jsonParser.next();
            final var jsonObject = jsonParser.getObject();
            for (var entry : jsonObject.entrySet()) {
                Logging.debug("{0}: {1}", entry.getKey(), entry.getValue());
                if (JsonValue.ValueType.OBJECT == entry.getValue().getValueType()
                        && entry.getValue().asJsonObject().containsKey("countries")) {
                    final var countries = entry.getValue().asJsonObject().get("countries");
                    parseCountries(COUNTRIES, countries, entry.getValue());
                }
            }
        } catch (JsonException | IOException e) {
            Logging.debug(e);
        }

        DATA_SOURCES.forEach(clazz -> {
            try {
                clazz.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                Logging.debug(e);
            }
        });
    }

    /**
     * Parse a JSON Value for country information
     *
     * @param countriesMap The countries map (will be modified)
     * @param countries    The country object (JsonObject)
     * @param information  The information for the source
     */
    private static void parseCountries(Map<String, Map<String, Boolean>> countriesMap, JsonValue countries,
            JsonValue information) {
        if (JsonValue.ValueType.OBJECT == countries.getValueType()) {
            parseCountriesObject(countriesMap, countries.asJsonObject(), information);
        } else {
            Logging.error("MapWithAI: Check format of countries map from MapWithAI");
        }
    }

    /**
     * Parse a JsonObject for countries
     *
     * @param countriesMap  The countries map (will be modified)
     * @param countryObject The country object (JsonObject)
     * @param information   The information for the source
     */
    private static void parseCountriesObject(Map<String, Map<String, Boolean>> countriesMap, JsonObject countryObject,
            JsonValue information) {
        for (Map.Entry<String, JsonValue> entry : countryObject.entrySet()) {
            Map<String, Boolean> providesMap = countriesMap.getOrDefault(entry.getKey(), new TreeMap<>());
            countriesMap.putIfAbsent(entry.getKey(), providesMap);
            if (JsonValue.ValueType.ARRAY == entry.getValue().getValueType()) {
                for (String provide : entry.getValue().asJsonArray().stream()
                        .filter(c -> JsonValue.ValueType.STRING == c.getValueType()).map(JsonValue::toString)
                        .map(DataAvailability::stripQuotes).toList()) {
                    providesMap.put(provide, true);
                }
            }
            if (providesMap.isEmpty() && JsonValue.ValueType.OBJECT == information.getValueType()
                    && information.asJsonObject().containsKey(PROVIDES)
                    && JsonValue.ValueType.ARRAY == information.asJsonObject().get(PROVIDES).getValueType()) {
                for (String provide : information.asJsonObject().getJsonArray(PROVIDES).stream()
                        .filter(val -> JsonValue.ValueType.STRING == val.getValueType()).map(JsonValue::toString)
                        .map(DataAvailability::stripQuotes).toList()) {
                    providesMap.put(provide, Boolean.TRUE);
                }
            }
        }
    }

    /**
     * Strip double quotes (") from a string
     *
     * @param string A string that may have quotes at the beginning, the end, or
     *               both
     * @return A string that doesn't have quotes at the beginning or end
     */
    public static String stripQuotes(String string) {
        return string.replaceAll("((^\")|(\"$))", "");
    }

    /**
     * Get the global instance that should be used to check for data availability
     *
     * @return the unique instance
     */
    public static DataAvailability getInstance() {
        if (instance == null || COUNTRIES.isEmpty() || MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty()) {
            instance = new DataAvailability();
        }
        return instance;
    }

    /**
     * Check if a bounds may have data
     *
     * @param bounds An area that may have data
     * @return True if one of the corners of the {@code bounds} is in a country with
     *         available data.
     */
    public boolean hasData(Bounds bounds) {
        final List<LatLon> corners = new ArrayList<>();
        corners.add(bounds.getMin());
        corners.add(new LatLon(bounds.getMinLat(), bounds.getMaxLon()));
        corners.add(bounds.getMax());
        corners.add(new LatLon(bounds.getMaxLat(), bounds.getMinLon()));
        corners.add(bounds.getCenter());
        return corners.stream().anyMatch(this::hasData);
    }

    /**
     * Check if a latlon point may have data
     *
     * @param latLon A point that may have data from MapWithAI
     * @return true if it is in an ares with data from MapWithAI
     */
    public boolean hasData(LatLon latLon) {
        for (final Map.Entry<String, Map<String, Boolean>> entry : COUNTRIES.entrySet()) {
            Logging.debug(entry.getKey());
            if (Territories.isIso3166Code(entry.getKey(), latLon)) {
                return entry.getValue().entrySet().stream().anyMatch(Map.Entry::getValue);
            }
        }
        return false;
    }

    /**
     * Get data types that may be visible around a point
     *
     * @param latLon The point of interest
     * @return A map that may have available data types (or be empty)
     */
    public static Map<String, Boolean> getDataTypes(LatLon latLon) {
        return COUNTRIES.entrySet().stream().filter(entry -> Territories.isIso3166Code(entry.getKey(), latLon))
                .map(Map.Entry::getValue).findFirst().orElse(Collections.emptyMap());
    }

    /**
     * Get the URL that this class is responsible for
     *
     * @return The url (e.g., example.com/addresses/{bbox}), or null if generic
     */
    public String getUrl() {
        return null;
    }

    /**
     * Get the Terms Of Use Url
     *
     * @return The url or ""
     */
    public String getTermsOfUseUrl() {
        return "";
    }

    /**
     * Get the Privacy Policy Url
     *
     * @return The url or the TOS url (sometimes a privacy policy is in the TOS).
     *         The TOS url may be an empty string.
     */
    public String getPrivacyPolicyUrl() {
        return getTermsOfUseUrl();
    }

    /**
     * Get the terms of use for all specially handled URL's
     *
     * @return List of terms of use urls
     */
    public static List<String> getTermsOfUse() {
        return Stream.concat(MapWithAILayerInfo.getInstance().getLayers().stream().map(MapWithAIInfo::getTermsOfUseURL),
                DATA_SOURCES.stream().map(clazz -> {
                    try {
                        return clazz.getConstructor().newInstance().getTermsOfUseUrl();
                    } catch (ReflectiveOperationException e) {
                        Logging.debug(e);
                    }
                    return "";
                })).filter(Objects::nonNull).filter(str -> !Utils.removeWhiteSpaces(str).isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Get the privacy policy for all specially handled URL's
     *
     * @return List of privacy policy urls
     */
    public static List<String> getPrivacyPolicy() {
        return Stream
                .concat(MapWithAILayerInfo.getInstance().getLayers().stream().map(MapWithAIInfo::getPrivacyPolicyURL),
                        DATA_SOURCES.stream().map(clazz -> {
                            try {
                                return clazz.getConstructor().newInstance().getPrivacyPolicyUrl();
                            } catch (ReflectiveOperationException e) {
                                Logging.debug(e);
                            }
                            return "";
                        }))
                .filter(Objects::nonNull).filter(str -> !Utils.removeWhiteSpaces(str).isEmpty()).distinct()
                .collect(Collectors.toList());
    }
}
