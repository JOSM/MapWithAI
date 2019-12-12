package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Territories;

public class DataAvailability {
    /** This points to a list of default sources that can be used with MapWithAI */
    public static final String DEFAULT_SERVER_URL = "https://gokaart.gitlab.io/JOSM_MapWithAI/json/sources.json";
    /** A map of tag -&gt; message of possible data types */
    protected static final Map<String, String> POSSIBLE_DATA_POINTS = new TreeMap<>();

    private static final String PROVIDES = "provides";

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
    static {
        DATA_SOURCES.add(MapWithAIAvailability.class);
    }
    /**
     * A map of countries to a map of available types
     * ({@code Map<Country, Map<Type, IsAvailable>>}
     */
    protected static final Map<String, Map<String, Boolean>> COUNTRIES = new HashMap<>();

    private static class InstanceHelper {
        static DataAvailability instance = new DataAvailability();
    }

    protected DataAvailability() {
        if (DataAvailability.class.equals(this.getClass())) {
            initialize();
        }
    }

    /**
     * Initialize the class
     */
    private static void initialize() {
        try (CachedFile jsonFile = new CachedFile(DEFAULT_SERVER_URL);
                JsonParser jsonParser = Json.createParser(jsonFile.getContentReader());) {
            jsonFile.setMaxAge(604_800);
            jsonParser.next();
            JsonObject jsonObject = jsonParser.getObject();
            boolean initializePreferences = MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty();
            for (Entry<String, JsonValue> entry : jsonObject.entrySet()) {
                if (initializePreferences) {
                    DataUrl url = new DataUrl(entry.getKey(), entry.getValue().asJsonObject().getString("url", ""),
                            false, entry.getValue().asJsonObject().getJsonArray("parameters").toString());
                    MapWithAIPreferenceHelper.setMapWithAIUrl(url, false, true);
                }
                Logging.error("{0}: {1}", entry.getKey(), entry.getValue());
                if (JsonValue.ValueType.OBJECT.equals(entry.getValue().getValueType())
                        && entry.getValue().asJsonObject().containsKey("countries")) {
                    JsonValue countries = entry.getValue().asJsonObject().get("countries");
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
        if (JsonValue.ValueType.ARRAY.equals(countries.getValueType())) {
            parseCountriesArray(countriesMap, countries.asJsonArray(), information);
        } else if (JsonValue.ValueType.OBJECT.equals(countries.getValueType())) {
            parseCountriesObject(countriesMap, countries.asJsonObject(), information);
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
        for (Entry<String, JsonValue> entry : countryObject.entrySet()) {
            Map<String, Boolean> providesMap = countriesMap.getOrDefault(entry.getKey(), new TreeMap<>());
            countriesMap.putIfAbsent(entry.getKey(), providesMap);
            if (JsonValue.ValueType.ARRAY.equals(entry.getValue().getValueType())) {
                for (String provide : entry.getValue().asJsonArray().parallelStream()
                        .filter(c -> JsonValue.ValueType.STRING.equals(c.getValueType())).map(JsonValue::toString)
                        .map(DataAvailability::stripQuotes).collect(Collectors.toList())) {
                    providesMap.put(provide, true);
                }
            }
            if (providesMap.isEmpty() && JsonValue.ValueType.OBJECT.equals(information.getValueType())
                    && information.asJsonObject().containsKey(PROVIDES)
                    && JsonValue.ValueType.ARRAY.equals(information.asJsonObject().get(PROVIDES).getValueType())) {
                for (String provide : information.asJsonObject().getJsonArray(PROVIDES).stream()
                        .filter(val -> JsonValue.ValueType.STRING.equals(val.getValueType())).map(JsonValue::toString)
                        .map(DataAvailability::stripQuotes).collect(Collectors.toList())) {
                    providesMap.put(provide, Boolean.TRUE);
                }
            }
        }
    }

    /**
     * Parse a JsonArray for countries
     *
     * @param countriesMap The countries map (will be modified)
     * @param countryArray The country array (JsonArray)
     * @param information  The information for the source
     */
    private static void parseCountriesArray(Map<String, Map<String, Boolean>> countriesMap, JsonArray countryArray,
            JsonValue information) {
        List<String> array = countryArray.parallelStream()
                .filter(c -> JsonValue.ValueType.STRING.equals(c.getValueType())).map(JsonValue::toString)
                .map(DataAvailability::stripQuotes).collect(Collectors.toList());
        if (JsonValue.ValueType.OBJECT.equals(information.getValueType())
                && information.asJsonObject().containsKey(PROVIDES)) {
            List<String> provides = information.asJsonObject().getJsonArray(PROVIDES).parallelStream()
                    .filter(p -> JsonValue.ValueType.STRING.equals(p.getValueType())).map(JsonValue::toString)
                    .map(DataAvailability::stripQuotes).collect(Collectors.toList());
            for (String countryValue : array) {
                for (String provide : provides) {
                    Map<String, Boolean> providesMap = countriesMap.getOrDefault(countryValue, new TreeMap<>());
                    countriesMap.putIfAbsent(countryValue, providesMap);
                    providesMap.put(provide, true);
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
        return string.replaceAll("(^\"|\"$)", "");
    }

    /**
     * @return the unique instance
     */
    public static DataAvailability getInstance() {
        if (InstanceHelper.instance == null || COUNTRIES.isEmpty()) {
            InstanceHelper.instance = new DataAvailability();
        }
        return InstanceHelper.instance;
    }

    /**
     * @param bbox An area that may have data
     * @return True if one of the corners of the {@code bbox} is in a country with
     *         available data.
     */
    public boolean hasData(BBox bbox) {
        final List<LatLon> corners = new ArrayList<>();
        corners.add(bbox.getBottomRight());
        corners.add(new LatLon(bbox.getBottomRightLat(), bbox.getTopLeftLon()));
        corners.add(bbox.getTopLeft());
        corners.add(new LatLon(bbox.getTopLeftLat(), bbox.getBottomRightLon()));
        return corners.parallelStream().anyMatch(this::hasData);
    }

    /**
     * @param latLon A point that may have data from MapWithAI
     * @return true if it is in an ares with data from MapWithAI
     */
    public boolean hasData(LatLon latLon) {
        boolean returnBoolean = false;
        for (final Entry<String, Map<String, Boolean>> entry : COUNTRIES.entrySet()) {
            Logging.debug(entry.getKey());
            if (Territories.isIso3166Code(entry.getKey(), latLon)) {
                returnBoolean = entry.getValue().entrySet().parallelStream().anyMatch(Entry::getValue);
                break;
            }
        }
        return returnBoolean;
    }

    /**
     * Get data types that may be visible around a point
     *
     * @param latLon The point of interest
     * @return A map that may have available data types (or be empty)
     */
    public static Map<String, Boolean> getDataTypes(LatLon latLon) {
        return COUNTRIES.entrySet().parallelStream().filter(entry -> Territories.isIso3166Code(entry.getKey(), latLon))
                .map(Entry::getValue).findFirst().orElse(Collections.emptyMap());
    }

    /**
     * Get the URL that this class is responsible for
     *
     * @return The url (e.g., example.com/addresses/{bbox}), or null if generic
     */
    public String getUrl() {
        return null;
    }
}