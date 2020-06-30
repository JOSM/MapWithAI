// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;

import org.openstreetmap.josm.data.StructUtils.StructEntry;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.sources.SourceInfo;
import org.openstreetmap.josm.data.sources.SourcePreferenceEntry;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Logging;

public class MapWithAIInfo extends
        SourceInfo<MapWithAICategory, MapWithAIType, ImageryInfo.ImageryBounds, MapWithAIInfo.MapWithAIPreferenceEntry> {

    private List<MapWithAICategory> categories;
    private JsonArray parameters;
    private Map<String, String> replacementTags;
    private boolean conflate;
    private String conflationUrl;
    /**
     * The preferred source string for the source. This is added as a source tag on
     * the object _and_ is added to the changeset tags.
     */
    private String source;
    private JsonArray conflationParameters;
    private String alreadyConflatedKey;
    /** This is for categories that cannot be conflated */
    private ArrayList<MapWithAICategory> conflationIgnoreCategory;

    /**
     * when adding a field, also adapt the: {@link #MapWithAIPreferenceEntry
     * MapWithAIPreferenceEntry object}
     * {@link MapWithAIPreferenceEntry#MapWithAIPreferenceEntry(MapWithAIInfo)
     * MapWithAIPreferenceEntry constructor}
     * {@link MapWithAIInfo#MapWithAIInfo(MapWithAIPreferenceEntry) ImageryInfo
     * constructor} {@link MapWithAIInfo#MapWithAIInfo(ImageryInfo) MapWithAIInfo
     * constructor} {@link MapWithAIInfo#equalsPref(MapWithAIPreferenceEntry)
     * equalsPref method}
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
        String categories;
        @StructEntry
        String alreadyConflatedKey;
        @StructEntry
        String source;

        /**
         * Constructs a new empty {@link MapWithAIPreferenceEntry}
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
            source = i.source;
            conflate = i.conflate;
            conflationUrl = i.conflationUrl;
            if (i.categories != null) {
                categories = i.categories.stream().map(MapWithAICategory::getCategoryString)
                        .collect(Collectors.joining(";"));
            }
            alreadyConflatedKey = i.alreadyConflatedKey;
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
                if (parser.hasNext() && JsonParser.Event.START_ARRAY == parser.next()) {
                    setParameters(parser.getArray());
                }
            }
        }
        setSource(e.source);
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
            setAdditionalCategories(Stream.of(e.categories.split(";", -1)).map(MapWithAICategory::fromString).distinct()
                    .collect(Collectors.toList()));
        }
        setConflation(e.conflate);
        setConflationUrl(e.conflationUrl);
        if (e.conflationParameters != null) {
            try (JsonParser parser = Json.createParser(new StringReader(e.conflationParameters))) {
                if (parser.hasNext() && JsonParser.Event.START_ARRAY == parser.next()) {
                    setConflationParameters(parser.getArray());
                }
            }
        }
        alreadyConflatedKey = e.alreadyConflatedKey;
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
        setSource(i.getSource());
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
        this.alreadyConflatedKey = i.alreadyConflatedKey;
    }

    public boolean equalsPref(MapWithAIInfo other) {
        if (other == null) {
            return false;
        }

        // CHECKSTYLE.OFF: BooleanExpressionComplexity
        return super.equalsPref(other) && Objects.equals(this.replacementTags, other.replacementTags)
                && Objects.equals(this.conflationUrl, other.conflationUrl)
                && Objects.equals(this.conflationParameters, other.conflationParameters)
                && Objects.equals(this.categories, other.categories)
                && Objects.equals(this.alreadyConflatedKey, other.alreadyConflatedKey)
                && (this.source == null || other.source == null || Objects.equals(this.source, other.source))
                && ((this.parameters == null && other.parameters == null)
                        || Objects.equals(this.parameters, other.parameters));
        // CHECKSTYLE.ON: BooleanExpressionComplexity
    }

    private static final Map<String, String> localizedCountriesCache = new HashMap<>();
    static {
        localizedCountriesCache.put("", tr("Worldwide"));
    }

    /**
     * Set the desired source for this object. This is added to source tags on
     * objects and is reported via the source changeset tag.
     *
     * @param source The desired source
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Get the source string to be used for sources on objects and should be used in
     * the source changeset tag.
     *
     * @return The desired source tag, or {@code null}.
     */
    public String getSource() {
        return this.source;
    }

    /**
     * Set the MapWithAI category
     *
     * @param category (i.e., buildings, featured, preview, addresses, etc)
     */
    public void setCategory(MapWithAICategory category) {
        this.category = category;
    }

    /**
     * Set the description for the info (may be shown in a mouse hover)
     *
     * @param description The description for the source
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the MapWithAI category
     *
     * @return The primary category (i.e., buildings, featured, preview, addresses,
     *         etc)
     */
    public MapWithAICategory getCategory() {
        return category == null ? MapWithAICategory.OTHER.getDefault() : category;
    }

    public void setParameters(JsonArray parameters) {
        this.parameters = parameters != null ? Json.createArrayBuilder(parameters).build() : null;
    }

    public JsonArray getParameters() {
        return parameters != null ? Json.createArrayBuilder(parameters).build() : null;
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
            if (MapWithAIType.ESRI_FEATURE_SERVER == sourceType) {
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
     * Get the requested tags to replace. These should be run before user requested
     * replacements.
     *
     * @return The required replacement tags
     */
    public Map<String, String> getReplacementTags() {
        return replacementTags;
    }

    /**
     * Set whether or not we should perform conflation using the specified
     * conflation URL
     *
     * @param conflation If true, try to use the conflation URL
     */
    public void setConflation(boolean conflation) {
        this.conflate = conflation;
    }

    /**
     * Check if this source is being automatically conflated
     *
     * @return {@code true} if it should be returned already conflated
     */
    public boolean isConflated() {
        return this.conflate;
    }

    /**
     * Set the URL to use for conflation purposes.
     *
     * @param conflationUrl Set the conflation url to use. null will disable, but
     *                      you should use {@link MapWithAIInfo#setConflation}.
     */
    public void setConflationUrl(String conflationUrl) {
        this.conflationUrl = conflationUrl;
    }

    /**
     * Set any parameters that are required/useful for the conflation URL
     *
     * @param parameters Set the conflation parameters
     */
    public void setConflationParameters(JsonArray parameters) {
        this.conflationParameters = parameters != null ? Json.createArrayBuilder(parameters).build() : null;
    }

    /**
     * Set any additional categories
     *
     * @param categories The categories to set
     */
    public void setAdditionalCategories(List<MapWithAICategory> categories) {
        this.categories = categories != null ? new ArrayList<>(categories) : null;
    }

    /**
     * Set additional categories (you can duplicate the primary category here, but
     * you shouldn't)
     *
     * @return Any additional categories
     */
    public List<MapWithAICategory> getAdditionalCategories() {
        return this.categories != null ? Collections.unmodifiableList(this.categories) : Collections.emptyList();
    }

    /**
     * Set the key that indicates an object is already conflated, and if so, to what
     *
     * @param key The key returned by the server indicating the conflation object
     */
    public void setAlreadyConflatedKey(String key) {
        alreadyConflatedKey = key;
    }

    /**
     * Get the key that indicates an object is already conflated, and if so, to what
     * Please note that it may be `true`/`false` instead of an object id.
     *
     * return The key returned by the server indicating the conflation object
     */
    public String getAlreadyConflatedKey() {
        return alreadyConflatedKey;
    }

    /**
     * Add categories that should not be sent to this MapWithAIInfo's conflation
     * service
     *
     * @param cat The category that cannot be conflated
     */
    public void addConflationIgnoreCategory(MapWithAICategory cat) {
        if (this.conflationIgnoreCategory == null) {
            this.conflationIgnoreCategory = new ArrayList<>();
        }
        this.conflationIgnoreCategory.add(cat);
    }

    /**
     * Get categories that should not be sent to the conflation service
     *
     * @return An unmodifiable list of non-conflation categories
     */
    public List<MapWithAICategory> getConflationIgnoreCategory() {
        if (this.conflationIgnoreCategory == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(this.conflationIgnoreCategory);
    }

    /**
     * Get a string usable for toolbars
     *
     * @return Currently, the name of the source.
     */
    public String getToolbarName() {
        return this.getName();
    }
}
