// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.Serial;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.StructUtils.StructEntry;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.sources.SourceBounds;
import org.openstreetmap.josm.data.sources.SourceInfo;
import org.openstreetmap.josm.data.sources.SourcePreferenceEntry;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

/**
 * The information needed to download external data
 */
public class MapWithAIInfo extends
        SourceInfo<MapWithAICategory, MapWithAIType, ImageryInfo.ImageryBounds, MapWithAIInfo.MapWithAIPreferenceEntry> {

    private static final String PARAMETER_STRING = "parameter";
    private List<MapWithAICategory> categories;
    private JsonArray parameters;
    private Supplier<Map<String, String>> replacementTagsSupplier;
    private Map<String, String> replacementTags;
    private boolean conflate;
    private String conflationUrl;
    public static final BooleanProperty THIRD_PARTY_CONFLATE = new BooleanProperty("mapwithai.third_party.conflate",
            true);

    /**
     * The preferred source string for the source. This is added as a source tag on
     * the object _and_ is added to the changeset tags.
     */
    private String source;
    private JsonArray conflationParameters;
    private String alreadyConflatedKey;
    /** This is for categories that cannot be conflated */
    private List<MapWithAICategory> conflationIgnoreCategory;

    /**
     * when adding a field, also adapt the: {@link #MapWithAIPreferenceEntry
     * MapWithAIPreferenceEntry object}
     * {@link MapWithAIPreferenceEntry#MapWithAIPreferenceEntry(MapWithAIInfo)
     * MapWithAIPreferenceEntry constructor}
     * {@link MapWithAIInfo#MapWithAIInfo(MapWithAIPreferenceEntry) ImageryInfo
     * constructor} {@link MapWithAIInfo#MapWithAIInfo MapWithAIInfo constructor}
     * {@link MapWithAIInfo#equalsPref equalsPref method}
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
                parameters = i.getParameters().toString();
            }
            if (i.conflationParameters != null) {
                conflationParameters = i.conflationParameters.toString();
            }
            if (i.getReplacementTags() != null) {
                this.replacementTags = i.getReplacementTags();
            }
            source = i.source;
            conflate = i.conflate;
            conflationUrl = i.conflationUrl;
            if (i.categories != null) {
                categories = i.categories.stream().map(MapWithAICategory::getCategoryString)
                        .collect(Collectors.joining(";"));
            }
            alreadyConflatedKey = i.alreadyConflatedKey;
            if (i.bounds != null && this.shapes != null && this.shapes.length() > Byte.MAX_VALUE) {
                List<String> parts = new ArrayList<>(i.bounds.getShapes().size());
                for (Shape s : i.bounds.getShapes()) {
                    Optional<String> country = CountryUtils.shapeToCountry(s);
                    if (country.isPresent()) {
                        if (!parts.contains(country.get())) {
                            parts.add(country.get());
                        }
                    } else {
                        parts.add(s.encodeAsString(","));
                    }
                }
                if (!parts.isEmpty()) {
                    shapes = String.join(";", parts);
                }
            }
        }

        @Override
        public String toString() {
            final var s = new StringBuilder("MapWithAIPreferenceEntry [name=").append(name);
            if (id != null) {
                s.append(" id=").append(id);
            }
            s.append(']');
            return s.toString();
        }
    }

    /**
     * Compare MapWithAI info
     */
    public static class MapWithAIInfoCategoryComparator implements Comparator<MapWithAIInfo>, Serializable {

        @Serial
        private static final long serialVersionUID = -7992892476979310835L;

        @Override
        public int compare(MapWithAIInfo o1, MapWithAIInfo o2) {
            return (Objects.nonNull(o1.getCategory()) || Objects.nonNull(o2.getCategory()) ? 1
                    : Objects.compare(o1.getCategory(), o2.getCategory(),
                            new MapWithAICategory.DescriptionComparator()));
        }
    }

    /**
     * Create a simple info object
     */
    public MapWithAIInfo() {
        this((String) null);
    }

    /**
     * Create a simple info object
     *
     * @param name The name of the source
     */
    public MapWithAIInfo(String name) {
        this(name, null);
    }

    /**
     * Create a simple info object
     *
     * @param name    The name of the source
     * @param baseUrl The URL of the source
     */
    public MapWithAIInfo(String name, String baseUrl) {
        this(name, baseUrl, null);
    }

    /**
     * Create a simple info object
     *
     * @param name    The name of the source
     * @param baseUrl The URL of the source
     * @param id      The unique ID of the source
     */
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
        this(name, url, id);
        this.setEulaAcceptanceRequired(eulaAcceptanceRequired);
        super.setSourceType(MapWithAIType.fromString(type));
    }

    /**
     * Create an info object from a preference entry
     *
     * @param e The preference entry to copy from
     */
    public MapWithAIInfo(MapWithAIPreferenceEntry e) {
        this(e.name, e.url, e.id);
        CheckParameterUtil.ensureParameterNotNull(e.name, "name");
        CheckParameterUtil.ensureParameterNotNull(e.url, "url");
        setDescription(e.description);
        setCookies(e.cookies);
        setEulaAcceptanceRequired(e.eula);
        if (e.parameters != null) {
            try (var parser = Json.createParser(new StringReader(e.parameters))) {
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
                        if (s.matches("[\\d,.-]+")) {
                            bounds.addShape(new Shape(s, ","));
                        } else {
                            CountryUtils.getCountryShape(s).map(SourceBounds::getShapes)
                                    .orElseThrow(IllegalStateException::new).forEach(bounds::addShape);
                        }
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

    /**
     * Copy from another info object
     *
     * @param i The object to copy from
     */
    public MapWithAIInfo(MapWithAIInfo i) {
        this(i.name, i.url, i.id);
        this.alreadyConflatedKey = i.alreadyConflatedKey;
        this.attributionImage = i.attributionImage;
        this.attributionImageURL = i.attributionImageURL;
        this.attributionLinkURL = i.attributionLinkURL;
        this.attributionText = i.attributionText;
        this.bounds = i.bounds;
        this.categories = i.categories;
        this.category = i.category;
        this.categoryOriginalString = i.categoryOriginalString;
        this.conflate = i.conflate;
        this.conflationIgnoreCategory = i.conflationIgnoreCategory;
        this.conflationUrl = i.conflationUrl;
        this.cookies = i.cookies;
        this.countryCode = i.countryCode;
        this.date = i.date;
        this.defaultEntry = i.defaultEntry;
        this.defaultLayers = i.defaultLayers;
        this.description = i.description;
        this.eulaAcceptanceRequired = i.eulaAcceptanceRequired;
        this.icon = i.icon;
        this.langDescription = i.langDescription;
        this.langName = i.langName;
        this.maxZoom = i.maxZoom;
        this.minZoom = i.minZoom;
        this.modTileFeatures = i.modTileFeatures;
        this.noTileChecksums = i.noTileChecksums;
        this.noTileHeaders = i.noTileHeaders;
        this.origName = i.origName;
        this.permissionReferenceURL = i.permissionReferenceURL;
        this.privacyPolicyURL = i.privacyPolicyURL;
        this.setConflationParameters(i.conflationParameters);
        this.setCustomHttpHeaders(i.getCustomHttpHeaders());
        this.setIcon(i.icon);
        this.setMetadataHeaders(i.getMetadataHeaders());
        this.setParameters(i.getParameters());
        this.setReplacementTags(i.getReplacementTags());
        this.source = i.source;
        this.sourceType = i.sourceType;
        this.termsOfUseText = i.termsOfUseText;
        this.termsOfUseURL = i.termsOfUseURL;
        this.tileSize = i.tileSize;

    }

    /**
     * Check if this equals the other object as far as the preferences go
     *
     * @param other The other object to check against
     * @return {@code true} if the object is effectively equal
     */
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
                && compareParameters(this.parameters, other.parameters);
        // CHECKSTYLE.ON: BooleanExpressionComplexity
    }

    private static boolean compareParameters(JsonArray first, JsonArray second) {
        if (Objects.equals(first, second)) {
            return true;
        }
        if (first != null && second != null && first.size() == second.size()) {
            for (var value : Utils.filteredCollection(first, JsonObject.class)) {
                if (value.containsKey(PARAMETER_STRING)
                        && value.get(PARAMETER_STRING).getValueType() == JsonValue.ValueType.STRING
                        && Utils.filteredCollection(second, JsonObject.class).stream()
                                .filter(obj -> obj.containsKey(PARAMETER_STRING)
                                        && obj.get(PARAMETER_STRING).getValueType() == JsonValue.ValueType.STRING)
                                .map(obj -> obj.getString(PARAMETER_STRING))
                                .noneMatch(obj -> value.getString(PARAMETER_STRING).equals(obj))) {
                    return false;
                }
            }
            return true;
        }
        return false;
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
                        .filter(map -> map.getBoolean("enabled", false))
                        .filter(map -> map.containsKey(PARAMETER_STRING)).map(map -> map.getString(PARAMETER_STRING))
                        .collect(Collectors.toList());

    }

    /**
     * Get the conflation parameters as a string
     *
     * @return The conflation parameters to be appended to the url
     */
    public List<String> getConflationParameterString() {
        return getParametersString(this.conflationParameters);
    }

    /**
     * Check if this source will have a valid URL when {@link #getUrlExpanded()} is
     * called
     *
     * @return {@code true} if this source will have a valid url
     */
    public boolean hasValidUrl() {
        return this.url != null || (this.isConflated() && this.conflationUrl != null);
    }

    public String getUrlExpanded() {
        if (this.isConflated()) {
            return getConflationUrl().toString();
        } else {
            return getNonConflatedUrl().toString();
        }
    }

    private StringBuilder getConflationUrl() {
        if (conflationUrl == null) {
            return getNonConflatedUrl();
        }
        final var sb = new StringBuilder();
        if (this.conflationUrl.contains("{id}") && this.id != null) {
            sb.append(conflationUrl.replace("{id}", this.id));
        } else if (this.conflationUrl.contains("{id}")) {
            // We need to trigger synchronization. This means that the current download
            // may won't be conflated.
            // But this should automatically correct the behavior for the next attempt.
            final var mwli = MapWithAILayerInfo.getInstance();
            mwli.load(false, () -> {
                final var defaultLayer = mwli.getAllDefaultLayers().stream().filter(this::equals).findFirst();
                if (defaultLayer.isPresent()) {
                    this.id = defaultLayer.get().id;
                } else {
                    final var newInfo = mwli.getAllDefaultLayers().stream()
                            .filter(layer -> Objects.equals(this.url, layer.url) && Objects.nonNull(layer.id))
                            .findFirst().orElse(this);
                    this.id = newInfo.id;
                }
            });
            return getNonConflatedUrl();
        } else {
            sb.append(conflationUrl);
        }

        List<String> parametersString = getConflationParameterString();
        if (!parametersString.isEmpty()) {
            sb.append('&').append(String.join("&", parametersString));
        }
        return sb;
    }

    private StringBuilder getNonConflatedUrl() {
        final var sb = new StringBuilder();
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
     * Set the required replacement tags (as a supplier -- used only to avoid making
     * unnecessary requests)
     *
     * @param replacementTagsSupplier The supplier to get the tags to replace
     */
    public void setReplacementTags(final Supplier<Map<String, String>> replacementTagsSupplier) {
        synchronized (this) {
            this.replacementTagsSupplier = replacementTagsSupplier;
        }
        this.replacementTags = null;
    }

    /**
     * Get the requested tags to replace. These should be run before user requested
     * replacements.
     *
     * @return The required replacement tags
     */
    public Map<String, String> getReplacementTags() {
        if (this.replacementTags != null) {
            return this.replacementTags;
        }
        synchronized (this) {
            if (this.replacementTagsSupplier != null) {
                this.replacementTags = this.replacementTagsSupplier.get();
                this.replacementTagsSupplier = null;
            }
        }
        return this.replacementTags;
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
        return this.conflate && THIRD_PARTY_CONFLATE.get();
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
        this.conflationParameters = parameters;
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
     * Check if this layer has a specified category
     *
     * @param category The category to look for
     * @return {@code true} if this layer has the specified category
     */
    public boolean hasCategory(MapWithAICategory category) {
        return this.category == category || (this.categories != null && this.categories.contains(category));
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
     * @return The key returned by the server indicating the conflation object
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
