// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import javax.swing.ImageIcon;

import org.openstreetmap.gui.jmapviewer.interfaces.Attributed;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.josm.data.StructUtils.StructEntry;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.data.imagery.Shape;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAIPreferenceEntry;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.Logging;

public class MapWithAIInfo implements Comparable<MapWithAIInfo>, Attributed {
    /**
     * Type of MapWithAI entry
     */
    public enum MapWithAIType {
        FACEBOOK("facebook"), THIRD_PARTY("thirdParty");

        private final String typeString;

        MapWithAIType(String typeString) {
            this.typeString = typeString;
        }

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
    }

    public enum MapWithAICategory {
        BUILDING("data/closedway", "buildings", marktr("Buildings")),
        HIGHWAY("presets/transport/way/way_road", "highways", marktr("Roads")),
        ADDRESS("presets/misc/housenumber_small", "addresses", marktr("Addresses")),
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

        public String getCategoryString() {
            return category;
        }

        public ImageIcon getIcon(ImageProvider.ImageSizes size) {
            return iconCache
                    .computeIfAbsent(size, x -> Collections.synchronizedMap(new EnumMap<>(MapWithAICategory.class)))
                    .computeIfAbsent(this, x -> ImageProvider.get(x.icon, size));
        }

        public String getDescription() {
            return description;
        }

        public static MapWithAICategory fromString(String s) {
            for (MapWithAICategory category : MapWithAICategory.values()) {
                if (category.getCategoryString().equals(s)) {
                    return category;
                }
            }
            return null;
        }
    }

    /**
     * original name of the service entry in case of translation call, for multiple
     * languages English when possible
     */
    private String origName;
    /** (original) language of the translated name entry */
    private String langName;
    /** whether this is a entry activated by default or not */
    private boolean defaultEntry;
    /**
     * Whether this service requires a explicit EULA acceptance before it can be
     * activated
     */
    private String eulaAcceptanceRequired;
    /** Type of the MapWithAI service */
    private MapWithAIType mapwithaiType = MapWithAIType.THIRD_PARTY;
    /** data boundaries, displayed in preferences and used for automatic download */
    private ImageryInfo.ImageryBounds bounds;
    /**
     * description of the imagery entry, should contain notes what type of data it
     * is
     */
    private String description;
    /** language of the description entry */
    private String langDescription;
    /** Text of a text attribution displayed when using the service */
    private String attributionText;
    /** Link to the privacy policy of the operator */
    private String privacyPolicyURL;
    /** Link to a reference stating the permission for OSM usage */
    private String permissionReferenceURL;
    /** Link behind the text attribution displayed when using the service */
    private String attributionLinkURL;
    /** Image of a graphical attribution displayed when using the service */
    private String attributionImage;
    /** Link behind the graphical attribution displayed when using the service */
    private String attributionImageURL;
    /** Text with usage terms displayed when using the service */
    private String termsOfUseText;
    /** Link behind the text with usage terms displayed when using the service */
    private String termsOfUseURL;
    /** country code of the service (for country specific service) */
    private String countryCode = "";
    /**
     * creation date of the data (in the form YYYY-MM-DD;YYYY-MM-DD, where DD and MM
     * as well as a second date are optional).
     */
    private String date;
    /** icon used in menu */
    private String icon;
    /** category of the service */
    private MapWithAICategory category;
    /** The name of the service */
    private String name;
    /** The id of the service */
    private String id;
    /** The url of the service */
    private String url;
    private MapWithAIType type;

    /**
     * when adding a field, also adapt the: {@link #MapWithAIPreferenceEntry
     * MapWithAIPreferenceEntry object}
     * {@link #MapWithAIPreferenceEntry#MapWithAIPreferenceEntry(MapWithAIInfo)
     * MapWithAIPreferenceEntry constructor}
     * {@link #MapWithAIInfo(MapWithAIPreferenceEntry) ImageryInfo constructor}
     * {@link #MapWithAIInfo(ImageryInfo) MapWithAIInfo constructor}
     * {@link #equalsPref(MapWithAIPreferenceEntry) equalsPref method}
     **/

    public static class MapWithAIPreferenceEntry {
        @StructEntry
        String name;
        @StructEntry
        String d;
        @StructEntry
        String id;
        @StructEntry
        String type;
        @StructEntry
        String url;
        @StructEntry
        String eula;
        @StructEntry
        String attribution_text;
        @StructEntry
        String attribution_url;
        @StructEntry
        String permission_reference_url;
        @StructEntry
        String logo_image;
        @StructEntry
        String logo_url;
        @StructEntry
        String terms_of_use_text;
        @StructEntry
        String terms_of_use_url;
        @StructEntry
        String country_code = "";
        @StructEntry
        String date;
        @StructEntry
        String cookies;
        @StructEntry
        String bounds;
        @StructEntry
        String shapes;
        @StructEntry
        String icon;
        @StructEntry
        String description;
        @StructEntry
        String category;

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
            name = i.name;
            id = i.id;
            type = i.mapwithaiType.getTypeString();
            url = i.url;
            eula = i.eulaAcceptanceRequired;
            attribution_text = i.attributionText;
            attribution_url = i.attributionLinkURL;
            permission_reference_url = i.permissionReferenceURL;
            date = i.date;
            logo_image = i.attributionImage;
            logo_url = i.attributionImageURL;
            terms_of_use_text = i.termsOfUseText;
            terms_of_use_url = i.termsOfUseURL;
            country_code = i.countryCode;
            icon = intern(i.icon);
            description = i.description;
            category = i.category != null ? i.category.getCategoryString() : null;
            if (i.bounds != null) {
                bounds = i.bounds.encodeAsString(",");
                StringBuilder shapesString = new StringBuilder();
                for (Shape s : i.bounds.getShapes()) {
                    if (shapesString.length() > 0) {
                        shapesString.append(';');
                    }
                    shapesString.append(s.encodeAsString(","));
                }
                if (shapesString.length() > 0) {
                    shapes = shapesString.toString();
                }
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
        this.name = name;
        this.url = baseUrl;
        this.id = id;
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
        MapWithAIType t = MapWithAIType.fromString(type);
        this.eulaAcceptanceRequired = eulaAcceptanceRequired;
        if (t != null) {
            this.type = t;
        } else if (type != null && !type.isEmpty()) {
            throw new IllegalArgumentException("unknown type: " + type);
        }
    }

    public MapWithAIInfo(MapWithAIPreferenceEntry e) {
        this(e.name, e.url, e.id);
        CheckParameterUtil.ensureParameterNotNull(e.name, "name");
        CheckParameterUtil.ensureParameterNotNull(e.url, "url");
        description = e.description;
        eulaAcceptanceRequired = e.eula;
        type = MapWithAIType.fromString(e.type);
        if (type == null) {
            throw new IllegalArgumentException("unknown type");
        }
        if (e.bounds != null) {
            bounds = new ImageryBounds(e.bounds, ",");
            if (e.shapes != null) {
                try {
                    for (String s : e.shapes.split(";")) {
                        bounds.addShape(new Shape(s, ","));
                    }
                } catch (IllegalArgumentException ex) {
                    Logging.warn(ex);
                }
            }
        }
        attributionText = intern(e.attribution_text);
        attributionLinkURL = e.attribution_url;
        permissionReferenceURL = e.permission_reference_url;
        attributionImage = e.logo_image;
        attributionImageURL = e.logo_url;
        date = e.date;
        termsOfUseText = e.terms_of_use_text;
        termsOfUseURL = e.terms_of_use_url;
        countryCode = intern(e.country_code);
        icon = intern(e.icon);
        category = MapWithAICategory.fromString(e.category);
    }

    public MapWithAIInfo(MapWithAIInfo i) {
        this(i.name, i.url, i.id);
        this.origName = i.origName;
        this.langName = i.langName;
        this.defaultEntry = i.defaultEntry;
        this.eulaAcceptanceRequired = null;
        this.bounds = i.bounds;
        this.description = i.description;
        this.langDescription = i.langDescription;
        this.attributionText = i.attributionText;
        this.privacyPolicyURL = i.privacyPolicyURL;
        this.permissionReferenceURL = i.permissionReferenceURL;
        this.attributionLinkURL = i.attributionLinkURL;
        this.attributionImage = i.attributionImage;
        this.attributionImageURL = i.attributionImageURL;
        this.termsOfUseText = i.termsOfUseText;
        this.termsOfUseURL = i.termsOfUseURL;
        this.countryCode = i.countryCode;
        this.date = i.date;
        this.icon = intern(i.icon);
        this.category = i.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, type);
    }

    public boolean equalsPref(MapWithAIInfo other) {
        if (other == null) {
            return false;
        }

        // CHECKSTYLE.OFF: BooleanExpressionComplexity
        return Objects.equals(this.name, other.name) && Objects.equals(this.id, other.id)
                && Objects.equals(this.url, other.url)
                && Objects.equals(this.eulaAcceptanceRequired, other.eulaAcceptanceRequired)
                && Objects.equals(this.bounds, other.bounds)
                && Objects.equals(this.attributionText, other.attributionText)
                && Objects.equals(this.attributionLinkURL, other.attributionLinkURL)
                && Objects.equals(this.permissionReferenceURL, other.permissionReferenceURL)
                && Objects.equals(this.attributionImageURL, other.attributionImageURL)
                && Objects.equals(this.attributionImage, other.attributionImage)
                && Objects.equals(this.termsOfUseText, other.termsOfUseText)
                && Objects.equals(this.termsOfUseURL, other.termsOfUseURL)
                && Objects.equals(this.countryCode, other.countryCode) && Objects.equals(this.date, other.date)
                && Objects.equals(this.icon, other.icon) && Objects.equals(this.description, other.description)
                && Objects.equals(this.category, other.category);
        // CHECKSTYLE.ON: BooleanExpressionComplexity
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MapWithAIInfo that = (MapWithAIInfo) o;
        return type == that.type && Objects.equals(url, that.url);
    }

    private static final Map<String, String> localizedCountriesCache = new HashMap<>();
    static {
        localizedCountriesCache.put("", tr("Worldwide"));
    }

    @Override
    public String toString() {
        // Used in preferences filtering, so must be efficient
        return new StringBuilder(name).append('[').append(countryCode)
                // appending the localized country in toString() allows us to filter imagery
                // preferences table with it!
                .append("] ('").append(ImageryInfo.getLocalizedCountry(countryCode)).append(')').append(" - ")
                .append(url).append(" - ").append(type).toString();
    }

    @Override
    public int compareTo(MapWithAIInfo in) {
        int i = countryCode.compareTo(in.countryCode);
        if (i == 0) {
            i = name.toLowerCase(Locale.ENGLISH).compareTo(in.name.toLowerCase(Locale.ENGLISH));
        }
        if (i == 0) {
            i = url.compareTo(in.url);
        }
        return i;
    }

    public boolean equalsBaseValues(MapWithAIInfo in) {
        return url.equals(in.url);
    }

    /**
     * Request name of the tile source
     *
     * @return name of the tile source
     */
    public final String getName() {
        return name;
    }

    /**
     * Request URL of the tile source
     *
     * @return url of the tile source
     */
    public final String getUrl() {
        return url;
    }

    /**
     * Request ID of the tile source. Id can be null. This gets the configured id as
     * is. Due to a user error, this may not be unique.
     *
     * @return id of the tile source
     */
    public final String getId() {
        return id;
    }

    /**
     * Sets the tile URL.
     *
     * @param url tile URL
     */
    public final void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets the tile name.
     *
     * @param name tile name
     */
    public final void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the tile id.
     *
     * @param id tile id
     */
    public final void setId(String id) {
        this.id = id;
    }

    private static String intern(String string) {
        return string == null ? null : string.intern();
    }

    /**
     * Sets the imagery polygonial bounds.
     *
     * @param b The imagery bounds (non-rectangular)
     */
    public void setBounds(ImageryBounds b) {
        this.bounds = b;
    }

    /**
     * Returns the imagery polygonial bounds.
     *
     * @return The imagery bounds (non-rectangular)
     */
    public ImageryBounds getBounds() {
        return bounds;
    }

    @Override
    public boolean requiresAttribution() {
        return attributionText != null || attributionLinkURL != null || attributionImage != null
                || termsOfUseText != null || termsOfUseURL != null;
    }

    @Override
    public String getAttributionText(int zoom, ICoordinate topLeft, ICoordinate botRight) {
        return attributionText;
    }

    @Override
    public String getAttributionLinkURL() {
        return attributionLinkURL;
    }

    /**
     * Return the permission reference URL.
     *
     * @return The url
     * @see #setPermissionReferenceURL
     * @since 11975
     */
    public String getPermissionReferenceURL() {
        return permissionReferenceURL;
    }

    /**
     * Return the privacy policy URL.
     *
     * @return The url
     * @see #setPrivacyPolicyURL
     * @since 16127
     */
    public String getPrivacyPolicyURL() {
        return privacyPolicyURL;
    }

    @Override
    public Image getAttributionImage() {
        ImageIcon i = ImageProvider.getIfAvailable(attributionImage);
        if (i != null) {
            return i.getImage();
        }
        return null;
    }

    /**
     * Return the raw attribution logo information (an URL to the image).
     *
     * @return The url text
     * @since 12257
     */
    public String getAttributionImageRaw() {
        return attributionImage;
    }

    @Override
    public String getAttributionImageURL() {
        return attributionImageURL;
    }

    @Override
    public String getTermsOfUseText() {
        return termsOfUseText;
    }

    @Override
    public String getTermsOfUseURL() {
        return termsOfUseURL;
    }

    /**
     * Set the attribution text
     *
     * @param text The text
     * @see #getAttributionText(int, ICoordinate, ICoordinate)
     */
    public void setAttributionText(String text) {
        attributionText = intern(text);
    }

    /**
     * Set the attribution image
     *
     * @param url The url of the image.
     * @see #getAttributionImageURL()
     */
    public void setAttributionImageURL(String url) {
        attributionImageURL = url;
    }

    /**
     * Set the image for the attribution
     *
     * @param res The image resource
     * @see #getAttributionImage()
     */
    public void setAttributionImage(String res) {
        attributionImage = res;
    }

    /**
     * Sets the URL the attribution should link to.
     *
     * @param url The url.
     * @see #getAttributionLinkURL()
     */
    public void setAttributionLinkURL(String url) {
        attributionLinkURL = url;
    }

    /**
     * Sets the permission reference URL.
     *
     * @param url The url.
     * @see #getPermissionReferenceURL()
     * @since 11975
     */
    public void setPermissionReferenceURL(String url) {
        permissionReferenceURL = url;
    }

    /**
     * Sets the privacy policy URL.
     *
     * @param url The url.
     * @see #getPrivacyPolicyURL()
     * @since 16127
     */
    public void setPrivacyPolicyURL(String url) {
        privacyPolicyURL = url;
    }

    /**
     * Sets the text to display to the user as terms of use.
     *
     * @param text The text
     * @see #getTermsOfUseText()
     */
    public void setTermsOfUseText(String text) {
        termsOfUseText = text;
    }

    /**
     * Sets a url that links to the terms of use text.
     *
     * @param text The url.
     * @see #getTermsOfUseURL()
     */
    public void setTermsOfUseURL(String text) {
        termsOfUseURL = text; // 950
    }

    public void clearId() {
        if (this.id != null) {
            Collection<String> newAddedIds = new TreeSet<>(Config.getPref().getList("imagery.layers.addedIds"));
            newAddedIds.add(this.id);
            Config.getPref().putList("imagery.layers.addedIds", new ArrayList<>(newAddedIds));
        }
        setId(null);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public MapWithAICategory getCategory() {
        return category;
    }

    public String getToolTipText() {
        StringBuilder res = new StringBuilder(getName());
        boolean html = false;
        if (category != null && category.getDescription() != null) {
            res.append("<br>").append(tr("Imagery category: {0}", category.getDescription()));
            html = true;
        }
        if (html) {
            res.insert(0, "<html>").append("</html>");
        }
        return res.toString();
    }

}
