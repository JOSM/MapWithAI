// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.CountryUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.tools.Territories;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

/**
 * Reader to parse the list of available MapWithAI servers from an JSON
 * definition file.
 * <p>
 * The format is specified in the <a href=
 * "https://github.com/JOSM/MapWithAI/blob/pages/json/sources.json">MapWithAI
 * source</a>.
 */
public class MapWithAISourceReader extends CommonSourceReader<List<MapWithAIInfo>> implements Closeable {
    /**
     * Constructs a {@code MapWithAISourceReader} from a given filename, URL or
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
    public MapWithAISourceReader(String source) {
        super(source);
    }

    /**
     * Parses MapWithAI entry sources
     *
     * @param parser The json of the data sources
     * @return The parsed entries
     */
    @Override
    public List<MapWithAIInfo> parseJson(JsonParser parser) {
        return parser.getObjectStream().map(MapWithAISourceReader::parse).collect(Collectors.toList());
    }

    private static MapWithAIInfo parse(Map.Entry<String, JsonValue> entry) {
        final var name = entry.getKey();
        if (JsonValue.ValueType.OBJECT == entry.getValue().getValueType()) {
            final var values = entry.getValue().asJsonObject();
            final var url = values.getString("url", "");
            final var type = values.getString("type", MapWithAIType.values()[0].getDefault().getTypeString());
            final var categories = values
                    .getString("category", MapWithAICategory.values()[0].getDefault().getCategoryString())
                    .split(";", -1);
            final var eula = values.getString("eula", "");
            final var conflation = values.getBoolean("conflate", false);
            final var conflationUrl = values.getString("conflationUrl", null);
            final var id = values.getString("id", name.replace(" ", "_"));
            final var alreadyConflatedKey = values.getString("conflated_key", null);
            final var countries = values.getOrDefault("countries", JsonValue.EMPTY_JSON_OBJECT);
            final var bounds = getBounds(countries);
            final var info = new MapWithAIInfo(name, url, type, eula, id);
            info.setDefaultEntry(values.getBoolean("default", false));
            info.setParameters(values.getJsonArray("parameters"));
            info.setConflationParameters(values.getJsonArray("conflationParameters"));
            info.setConflation(conflation);
            info.setConflationUrl(conflationUrl);
            info.setSource(values.getString("source", null));
            info.setAlreadyConflatedKey(alreadyConflatedKey);
            info.setAttributionText(values.getString("provider", null));
            if (categories.length > 0) {
                info.setCategory(MapWithAICategory.fromString(categories[0]));
                if (categories.length > 1) {
                    info.setAdditionalCategories(Stream.of(categories).skip(1).map(MapWithAICategory::fromString)
                            .collect(Collectors.toList()));
                }
            }
            if (values.containsKey("conflation_ignore_categories")) {
                final var ignore = values.getJsonArray("conflation_ignore_categories");
                for (MapWithAICategory cat : ignore.getValuesAs(JsonString.class).stream().map(JsonString::getString)
                        .map(MapWithAICategory::fromString).toList()) {
                    info.addConflationIgnoreCategory(cat);
                }
            }
            if (values.containsKey("terms_of_use_url")) {
                info.setTermsOfUseURL(values.getString("terms_of_use_url"));
            }
            if (values.containsKey("privacy_policy_url")) {
                info.setPrivacyPolicyURL(values.getString("privacy_policy_url"));
            }
            if (!bounds.isEmpty()) {
                ImageryBounds bound = bounds.get(0);
                bounds.remove(0);
                bounds.forEach(bound::extend);
                bounds.forEach(b -> b.getShapes().forEach(bound::addShape));
                info.setBounds(bound);
            }
            return info;
        }
        return new MapWithAIInfo(name);
    }

    private static List<ImageryInfo.ImageryBounds> getBounds(JsonValue countries) {
        if (JsonValue.ValueType.OBJECT == countries.getValueType()) {
            Set<String> codes = Territories.getKnownIso3166Codes();
            List<ImageryBounds> bounds = new ArrayList<>();
            for (Map.Entry<String, JsonValue> country : countries.asJsonObject().entrySet()) {
                if (codes.contains(country.getKey())) {
                    CountryUtils.getCountryShape(country.getKey()).ifPresent(bounds::add);
                }
            }
            return bounds;
        }
        return new ArrayList<>();
    }
}
