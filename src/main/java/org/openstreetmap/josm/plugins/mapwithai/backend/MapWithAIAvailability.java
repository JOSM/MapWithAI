// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Territories;

public final class MapWithAIAvailability {
    private static String rapidReleases = "https://github.com/facebookmicrosites/Open-Mapping-At-Facebook/raw/master/data/rapid_releases.geojson";
    private static final Map<String, Map<String, Boolean>> COUNTRIES = new HashMap<>();
    private static final Map<String, String> POSSIBLE_DATA_POINTS = new TreeMap<>();
    private static final Map<String, String> COUNTRY_NAME_FIX = new HashMap<>();

    private static class InstanceHelper {
        static MapWithAIAvailability instance = new MapWithAIAvailability();
    }

    static {
        COUNTRY_NAME_FIX.put("Egypt", "Egypt, Arab Rep.");
        COUNTRY_NAME_FIX.put("Dem. Rep. Congo", "Congo, Dem. Rep.");
        POSSIBLE_DATA_POINTS.put("roads", "RapiD roads available");
        POSSIBLE_DATA_POINTS.put("buildings", "MS buildings available");
    }

    private MapWithAIAvailability() {
        try (CachedFile cachedRapidReleases = new CachedFile(rapidReleases);
                JsonParser parser = Json.createParser(cachedRapidReleases.getContentReader())) {
            cachedRapidReleases.setMaxAge(604800);
            parser.next();
            final Stream<Entry<String, JsonValue>> entries = parser.getObjectStream();
            final Optional<Entry<String, JsonValue>> objects = entries
                    .filter(entry -> "objects".equals(entry.getKey())).findFirst();
            if (objects.isPresent()) {
                final JsonObject value = objects.get().getValue().asJsonObject();
                final JsonObject centroid = value.getJsonObject("rapid_releases_1011_centroid");
                final JsonArray countries = centroid.getJsonArray("geometries");
                COUNTRIES.clear();
                COUNTRIES.putAll(parseForCountries(countries));
            }
        } catch (IOException e) {
            Logging.debug(e);
        }
    }

    /**
     * @return the unique instance
     */
    public static MapWithAIAvailability getInstance() {
        if (COUNTRIES.isEmpty()) {
            InstanceHelper.instance = new MapWithAIAvailability();
        }
        return InstanceHelper.instance;
    }

    private static Map<String, Map<String, Boolean>> parseForCountries(JsonArray countries) {
        Map<String, Map<String, Boolean>> returnCountries = new TreeMap<>();
        for (int i = 0; i < countries.size(); i++) {
            final JsonObject country = countries.getJsonObject(i).getJsonObject("properties");
            final String countryName = cornerCaseNames(country.getString("Country"));
            DataSet territories;
            try {
                territories = Territories.getDataSet();
            } catch (NullPointerException e) {
                Territories.initialize();
                territories = Territories.getDataSet();
            }
            final Optional<OsmPrimitive> realCountry = territories.allPrimitives().parallelStream()
                    .filter(primitive -> countryName.equalsIgnoreCase(primitive.get("name:en")))
                    .findFirst();
            if (realCountry.isPresent()) {
                final String key = realCountry.get().get("ISO3166-1:alpha2");
                // We need to handle cases like Alaska more elegantly
                final Map<String, Boolean> data = returnCountries.getOrDefault(key, new TreeMap<>());
                for (final Entry<String, String> entry : POSSIBLE_DATA_POINTS.entrySet()) {
                    final boolean hasData = "yes".equals(country.getString(entry.getValue()));
                    if (hasData || !data.containsKey(entry.getKey())) {
                        data.put(entry.getKey(), hasData);
                    }
                }
                returnCountries.put(key, data);
            } else {
                Logging.error(tr("{0}: We couldn''t find {1}", MapWithAIPlugin.NAME, countryName));
            }
        }
        return returnCountries;
    }

    private static String cornerCaseNames(String name) {
        String returnName = name;
        if (COUNTRY_NAME_FIX.containsKey(name)) {
            returnName = COUNTRY_NAME_FIX.get(name);
        }
        return returnName;
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
    public Map<String, Boolean> getDataTypes(LatLon latLon) {
        return COUNTRIES.entrySet().parallelStream().filter(entry -> Territories.isIso3166Code(entry.getKey(), latLon))
                .map(Entry::getValue).findFirst().orElse(Collections.emptyMap());
    }

    /**
     * @return A map of possible data types with their messages
     */
    public static Map<String, String> getPossibleDataTypesAndMessages() {
        return POSSIBLE_DATA_POINTS;
    }

    /**
     * @param url The URL where the MapWithAI data releases are.
     */
    public static void setReleaseUrl(String url) {
        rapidReleases = url;
    }
}
