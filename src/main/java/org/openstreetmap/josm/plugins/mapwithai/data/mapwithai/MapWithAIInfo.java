// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.StringReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;
import javax.swing.ImageIcon;

import org.openstreetmap.josm.data.StructUtils.StructEntry;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.sources.ISourceCategory;
import org.openstreetmap.josm.data.sources.ISourceType;
import org.openstreetmap.josm.data.sources.SourceInfo;
import org.openstreetmap.josm.data.sources.SourcePreferenceEntry;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAIPreferenceEntry;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Logging;

public class MapWithAIInfo extends
        SourceInfo<MapWithAIInfo.MapWithAICategory, MapWithAIInfo.MapWithAIType, ImageryInfo.ImageryBounds, MapWithAIInfo.MapWithAIPreferenceEntry> {

    /**
     * Type of MapWithAI entry
     */
    public enum MapWithAIType implements ISourceType<MapWithAIType> {
        FACEBOOK("facebook"), THIRD_PARTY("thirdParty"), ESRI("esri"), ESRI_FEATURE_SERVER("esriFeatureServer");

        private final String typeString;

        MapWithAIType(String typeString) {
            this.typeString = typeString;
        }

        @Override
        public String getTypeString() {
            return typeString;
        }

        public static MapWithAIType fromString(String s) {
            for (MapWithAIType type : MapWithAIType.values()) {
                if (type.getTypeString().equals(s)) {
                    return type;
                }
            }
            return null;
        }

        @Override
        public MapWithAIType getDefault() {
            return THIRD_PARTY;
        }

        @Override
        public MapWithAIType getFromString(String s) {
            return fromString(s);
        }
    }

    public enum MapWithAICategory implements ISourceCategory<MapWithAICategory> {

        BUILDING("data/closedway", "buildings", marktr("Buildings")),
        HIGHWAY("presets/transport/way/way_road", "highways", marktr("Roads")),
        ADDRESS("presets/misc/housenumber_small", "addresses", marktr("Addresses")),
        POWER("presets/power/pole", "pole", marktr("Power")), PRESET("dialogs/search", "presets", marktr("Presets")),
        FEATURED("presets/service/network-wireless.svg", "featured", marktr("Featured")),
        OTHER(null, "other", marktr("Other"));

        private static final Map<ImageSizes, Map<MapWithAICategory, ImageIcon>> iconCache = Collections
                .synchronizedMap(new EnumMap<>(ImageSizes.class));

        private final String category;
        private final String description;
        private final String icon;

        MapWithAICategory(String icon, String category, String description) {
            this.category = category;
            this.icon = icon == null || icon.trim().isEmpty() ? "mapwithai" : icon;
            this.description = description;
        }

        @Override
        public final String getCategoryString() {
            return category;
        }

        @Override
        public final String getDescription() {
            return description;
        }

        @Override
        public final ImageIcon getIcon(ImageSizes size) {
            return iconCache
                    .computeIfAbsent(size, x -> Collections.synchronizedMap(new EnumMap<>(MapWithAICategory.class)))
                    .computeIfAbsent(this, x -> ImageProvider.get(x.icon, size));
        }

        public static MapWithAICategory fromString(String s) {
            for (MapWithAICategory category : MapWithAICategory.values()) {
                if (category.getCategoryString().equals(s)) {
                    return category;
                }
            }
            if (s != null && !s.trim().isEmpty()) {
                // fuzzy match
                String tmp = s.toLowerCase(Locale.ROOT);
                for (MapWithAICategory type : MapWithAICategory.values()) {
                    if (tmp.contains(type.getDescription().toLowerCase(Locale.ROOT))
                            || type.getDescription().toLowerCase(Locale.ROOT).contains(tmp)) {
                        return type;
                    }
                }
                // Check if it matches a preset
                if (TaggingPresets.getPresetKeys().stream().map(String::toLowerCase)
                        .anyMatch(m -> tmp.contains(m) || m.contains(tmp))) {
                    return PRESET;
                }
            }
            return OTHER;
        }

        public static class DescriptionComparator implements Comparator<MapWithAICategory> {

            @Override
            public int compare(MapWithAICategory o1, MapWithAICategory o2) {
                return (o1 == null || o2 == null) ? 1 : o1.getDescription().compareTo(o2.getDescription());
            }
        }

        @Override
        public MapWithAICategory getDefault() {
            return OTHER;
        }

        @Override
        public MapWithAICategory getFromString(String s) {
            return fromString(s);
        }
    }

    private List<MapWithAICategory> categories;
    private JsonArray parameters;
    private Map<String, String> replacementTags;
    private boolean conflate;
    private String conflationUrl;
    private JsonArray conflationParameters;

    /**
     * when adding a field, also adapt the: {@link #MapWithAIPreferenceEntry
     * MapWithAIPreferenceEntry object}
     * {@link #MapWithAIPreferenceEntry#MapWithAIPreferenceEntry(MapWithAIInfo)
     * MapWithAIPreferenceEntry constructor}
     * {@link #MapWithAIInfo(MapWithAIPreferenceEntry) ImageryInfo constructor}
     * {@link #MapWithAIInfo(ImageryInfo) MapWithAIInfo constructor}
     * {@link #equalsPref(MapWithAIPreferenceEntry) equalsPref method}
     **/

    public static class MapWithAIPreferenceEntry extends SourcePreferenceEntry<MapWithAIInfo> {
        @StructEntry
        String parameters;
        @StructEntry
        Map<String, String> replacementTags;
        @StructEntry
        boolean conflate;
        @StructEntry
        String conflationUrl;
        @StructEntry
        String conflationParameters;
        @StructEntry
        List<String> categories;

        /**
         * Constructs a new empty {@MapWithAIPreferenceEntry}
         */
        public MapWithAIPreferenceEntry() {
            // Do nothing
        }

        /**
         * Constructs a new {@code ImageryPreferenceEntry} from a given
         * {@code ImageryInfo}.
         *
         * @param i The corresponding imagery info
         */
        public MapWithAIPreferenceEntry(MapWithAIInfo i) {
            super(i);
            if (i.parameters != null) {
                parameters = i.parameters.toString();
            }
            if (i.conflationParameters != null) {
                conflationParameters = i.conflationParameters.toString();
            }
            if (i.replacementTags != null) {
                replacementTags = i.replacementTags;
            }
            conflate = i.conflate;
            conflationUrl = i.conflationUrl;
            if (i.categories != null) {
                categories = i.categories.stream().map(MapWithAICategory::getCategoryString)
                        .collect(Collectors.toList());
            }
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder("MapWithAIPreferenceEntry [name=").append(name);
            if (id != null) {
                s.append(" id=").append(id);
            }
            s.append(']');
            return s.toString();
        }
    }

    public static class MapWithAIInfoCategoryComparator implements Comparator<MapWithAIInfo> {

        @Override
        public int compare(MapWithAIInfo o1, MapWithAIInfo o2) {
            return (Objects.nonNull(o1.getCategory()) || Objects.nonNull(o2.getCategory()) ? 1
                    : Objects.compare(o1.getCategory(), o2.getCategory(),
                            new MapWithAICategory.DescriptionComparator()));
        }
    }

    public MapWithAIInfo() {
        this((String) null);
    }

    public MapWithAIInfo(String name) {
        this(name, null);
    }

    public MapWithAIInfo(String name, String baseUrl) {
        this(name, baseUrl, null);
    }

    public MapWithAIInfo(String name, String baseUrl, String id) {
        super();
        setName(name);
        setUrl(baseUrl);
        setId(id);
        setSourceType(MapWithAIType.THIRD_PARTY);
    }

    /**
     * Constructs a new {@code MapWithAIInfo} with given name, url, id, extended and
     * EULA URLs.
     *
     * @param name                   The entry name
     * @param url                    The entry URL
     * @param type                   The entry source type. If null, OTHER will be
     *                               used as default
     * @param eulaAcceptanceRequired The EULA URL
     * @param id                     tile id
     * @throws IllegalArgumentException if type refers to an unknown service type
     */
    public MapWithAIInfo(String name, String url, String type, String eulaAcceptanceRequired, String id) {
        super(name, url, id);
        MapWithAIType t = MapWithAIType.fromString(type);
        this.setEulaAcceptanceRequired(eulaAcceptanceRequired);
        if (t != null) {
            super.setSourceType(t);
        } else if (type != null && !type.isEmpty()) {
            throw new IllegalArgumentException("unknown type: " + type);
        } else {
            super.setSourceType(MapWithAIType.THIRD_PARTY.getDefault());
        }
    }

    public MapWithAIInfo(MapWithAIPreferenceEntry e) {
        this(e.name, e.url, e.id);
        CheckParameterUtil.ensureParameterNotNull(e.name, "name");
        CheckParameterUtil.ensureParameterNotNull(e.url, "url");
        setDescription(e.description);
        setCookies(e.cookies);
        setEulaAcceptanceRequired(e.eula);
        if (e.parameters != null) {
            try (JsonParser parser = Json.createParser(new StringReader(e.parameters))) {
                if (parser.hasNext() && JsonParser.Event.START_ARRAY.equals(parser.next())) {
                    setParameters(parser.getArray());
                }
            }
        }
        if (e.replacementTags != null) {
            setReplacementTags(e.replacementTags);
        }
        setSourceType(MapWithAIType.fromString(e.type));
        if (getSourceType() == null) {
            throw new IllegalArgumentException("unknown type");
        }
        if (e.bounds != null) {
            bounds = new ImageryBounds(e.bounds, ",");
            if (e.shapes != null) {
                try {
                    for (String s : e.shapes.split(";", -1)) {
                        bounds.addShape(new Shape(s, ","));
                    }
                } catch (IllegalArgumentException ex) {
                    Logging.warn(ex);
                }
            }
        }
        setAttributionText(e.attribution_text);
        setAttributionLinkURL(e.attribution_url);
        setPermissionReferenceURL(e.permission_reference_url);
        setAttributionImage(e.logo_image);
        setAttributionImageURL(e.logo_url);
        setDate(e.date);
        setTermsOfUseText(e.terms_of_use_text);
        setTermsOfUseURL(e.terms_of_use_url);
        setCountryCode(e.country_code);
        setIcon(e.icon);
        setCategory(MapWithAICategory.fromString(e.category));
        if (e.categories != null) {
            setAdditionalCategories(
                    e.categories.stream().map(MapWithAICategory::fromString).distinct().collect(Collectors.toList()));
        }
        setConflation(e.conflate);
        setConflationUrl(e.conflationUrl);
        if (e.conflationParameters != null) {
            try (JsonParser parser = Json.createParser(new StringReader(e.conflationParameters))) {
                if (parser.hasNext() && JsonParser.Event.START_ARRAY.equals(parser.next())) {
                    setConflationParameters(parser.getArray());
                }
            }
        }
    }

    public MapWithAIInfo(MapWithAIInfo i) {
        this(i.name, i.url, i.id);
        setCookies(i.cookies);

        this.origName = i.origName;
        this.langName = i.langName;
        setDefaultEntry(i.defaultEntry);
        setEulaAcceptanceRequired(i.getEulaAcceptanceRequired());
        setBounds(i.getBounds());
        setDescription(i.getDescription());
        this.langDescription = i.langDescription;
        this.attributionText = i.attributionText;
        this.privacyPolicyURL = i.privacyPolicyURL;
        this.permissionReferenceURL = i.permissionReferenceURL;
        this.attributionLinkURL = i.attributionLinkURL;
        this.attributionImage = i.attributionImage;
        this.attributionImageURL = i.attributionImageURL;
        this.termsOfUseText = i.termsOfUseText;
        this.termsOfUseURL = i.termsOfUseURL;
        this.sourceType = i.sourceType;
        this.countryCode = i.countryCode;
        this.date = i.date;
        this.setIcon(i.icon);
        this.setCustomHttpHeaders(i.customHttpHeaders);
        this.category = i.category;
        this.categories = i.categories;
        this.replacementTags = i.replacementTags;
        this.conflate = i.conflate;
        this.conflationUrl = i.conflationUrl;
        this.conflationParameters = i.conflationParameters;
    }

    public boolean equalsPref(MapWithAIInfo other) {
        if (other == null) {
            return false;
        }

        // CHECKSTYLE.OFF: BooleanExpressionComplexity
        return super.equalsPref(other) && Objects.equals(this.replacementTags, other.replacementTags)
                && Objects.equals(this.conflationUrl, other.conflationUrl)
                && Objects.equals(this.conflationParameters, other.conflationParameters)
                && Objects.equals(this.categories, other.categories);
        // CHECKSTYLE.ON: BooleanExpressionComplexity
    }

    private static final Map<String, String> localizedCountriesCache = new HashMap<>();
    static {
        localizedCountriesCache.put("", tr("Worldwide"));
    }

    public void setCategory(MapWithAICategory category) {
        this.category = category;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MapWithAICategory getCategory() {
        return category;
    }

    public void setParameters(JsonArray parameters) {
        this.parameters = parameters;
    }

    public JsonArray getParameters() {
        return parameters;
    }

    /**
     * Get the parameters as a string (for URL usage)
     *
     * @return The string to be appended to the url
     */
    public List<String> getParametersString() {
        return getParametersString(parameters);
    }

    private static List<String> getParametersString(JsonArray parameters) {
        return parameters == null ? Collections.emptyList()
                : parameters.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                        .filter(map -> map.getBoolean("enabled", false)).filter(map -> map.containsKey("parameter"))
                        .map(map -> map.getString("parameter")).collect(Collectors.toList());

    }

    /**
     * Get the conflation parameters as a string
     *
     * @return The conflation parameters to be appended to the url
     */
    public List<String> getConflationParameterString() {
        return getParametersString(this.conflationParameters);
    }

    public String getUrlExpanded() {
        StringBuilder sb;
        if (conflate && Config.getPref().getBoolean("mapwithai.third_party.conflate", true)) {
            sb = getConflationUrl();
        } else {
            sb = getNonConflatedUrl();
        }
        return sb.toString();
    }

    private StringBuilder getConflationUrl() {
        if (conflationUrl == null) {
            return getNonConflatedUrl();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(conflationUrl.replace("{id}", this.id));

        List<String> parametersString = getConflationParameterString();
        if (!parametersString.isEmpty()) {
            sb.append('&').append(String.join("&", parametersString));
        }
        return sb;
    }

    private StringBuilder getNonConflatedUrl() {
        StringBuilder sb = new StringBuilder();
        if (url != null && !url.trim().isEmpty()) {
            sb.append(url);
            if (MapWithAIType.ESRI_FEATURE_SERVER.equals(sourceType)) {
                if (!url.endsWith("/")) {
                    sb.append('/');
                }
                sb.append("query?geometryType=esriGeometryEnvelope&geometry={bbox}&inSR=4326&f=geojson&outfields=*");
            }

            List<String> parametersString = getParametersString();
            if (!parametersString.isEmpty()) {
                sb.append('&').append(String.join("&", parametersString));
            }
        }
        return sb;
    }

    /**
     * Set the required replacement tags
     *
     * @param replacementTags The tags to replace
     */
    public void setReplacementTags(Map<String, String> replacementTags) {
        this.replacementTags = replacementTags;
    }

    /**
     * @return The required replacement tags (run first)
     */
    public Map<String, String> getReplacementTags() {
        return replacementTags;
    }

    /**
     * @param conflation If true, try to use the conflation URL
     */
    public void setConflation(boolean conflation) {
        this.conflate = conflation;
    }

    /**
     * @param conflationUrl Set the conflation url to use. null will disable, but
     *                      you should use {@link MapWithAIInfo#setConflation}.
     */
    public void setConflationUrl(String conflationUrl) {
        this.conflationUrl = conflationUrl;
    }

    /**
     * @param parameters Set the conflation parameters
     */
    public void setConflationParameters(JsonArray parameters) {
        this.conflationParameters = parameters;
    }

    /**
     * Set any additional categories
     *
     * @param categories The categories to set
     */
    public void setAdditionalCategories(List<MapWithAICategory> categories) {
        this.categories = categories;
    }

    /**
     * @return Any additional categories
     */
    public List<MapWithAICategory> getAdditionalCategories() {
        return this.categories != null ? Collections.unmodifiableList(this.categories) : Collections.emptyList();
    }
}
