// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.pmtiles.lib.PMTiles;
import org.openstreetmap.josm.tools.Logging;

import jakarta.annotation.Nullable;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

/**
 * Read data from overture sources
 */
public class OvertureSourceReader extends CommonSourceReader<List<MapWithAIInfo>> implements Closeable {
    private final MapWithAIInfo source;

    public OvertureSourceReader(MapWithAIInfo source) {
        super(source.getUrl());
        this.source = source;
    }

    @Override
    public List<MapWithAIInfo> parseJson(JsonParser jsonParser) {
        final var jsonObject = jsonParser.getObject();
        if (jsonObject.containsKey("releases")) {
            return parseRoot(jsonObject);
        }
        // The STAC catalog (https://stac.overturemaps.org/catalog.json), see #24875
        if ("Catalog".equals(jsonObject.getString("type", null)) && jsonObject.containsKey("links")) {
            return parseStacRoot(jsonObject);
        }
        return Collections.emptyList();
    }

    /**
     * Create the sources for the latest release from the STAC catalog. The root
     * catalog links to the releases, a release links to its themes, and a theme
     * links to its tiles ({@code "rel": "pmtiles"}).
     * <p>
     * Overture only keeps the last two releases, so the sources keep the same id
     * between releases. This means that user entries are updated to the new
     * release instead of being dropped.
     *
     * @param root The root catalog
     * @return The sources for the latest release
     */
    private List<MapWithAIInfo> parseStacRoot(JsonObject root) {
        final var latest = root.getString("latest", null);
        final var releases = getLinks(root, "child").toList();
        var release = latest == null ? null
                : releases.stream().filter(link -> link.getString("href").contains('/' + latest + '/')).findFirst()
                        .orElse(null);
        if (release == null) {
            release = releases.stream().filter(link -> link.getBoolean("latest", false)).findFirst().orElse(null);
        }
        if (release == null) {
            Logging.warn("MapWithAI: No overture release found in {0}", this.source.getUrl());
            return Collections.emptyList();
        }
        // The title is "<release> Overture Release"
        final var releaseId = latest != null ? latest : release.getString("title", "").split(" ", 2)[0];
        final var info = new ArrayList<MapWithAIInfo>(6);
        try {
            final var releaseCatalog = readObject(release.getString("href"));
            if (releaseCatalog == null) {
                return info;
            }
            for (var theme : getLinks(releaseCatalog, "child").toList()) {
                final var themeCatalog = readObject(theme.getString("href"));
                if (themeCatalog == null) {
                    continue;
                }
                final var themeId = themeCatalog.getString("id", theme.getString("title", ""));
                final var tiles = getLinks(themeCatalog, "pmtiles").findFirst();
                if (tiles.isEmpty()) {
                    Logging.warn("MapWithAI: Overture theme {0} has no tiles in release {1}", themeId, releaseId);
                    continue;
                }
                final var themeInfo = buildSource(URI.create(tiles.get().getString("href")), releaseId, themeId);
                if (themeInfo != null) {
                    themeInfo.setId(this.source.getName() + ": " + themeId);
                    info.add(themeInfo);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            Logging.warn("MapWithAI: Could not read the overture catalog for release {0}: {1}", releaseId,
                    e.getMessage());
            Logging.trace(e);
        }
        return info;
    }

    /**
     * Get the links of a STAC object with a specific relation
     *
     * @param stac The STAC object (catalog or collection)
     * @param rel  The relation to look for
     * @return The links with an {@code href}
     */
    private static Stream<JsonObject> getLinks(JsonObject stac, String rel) {
        final var links = stac.get("links");
        if (links instanceof JsonArray array) {
            return array.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
                    .filter(link -> rel.equals(link.getString("rel", null)) && link.containsKey("href")
                            && link.get("href").getValueType() == JsonValue.ValueType.STRING);
        }
        return Stream.empty();
    }

    private List<MapWithAIInfo> parseRoot(JsonObject jsonObject) {
        final var info = new ArrayList<MapWithAIInfo>(6 * 4);
        final var releases = jsonObject.get("releases");
        final var baseUri = URI.create(this.source.getUrl()).resolve("./"); // safe since we created an URI from the source to get to this point
        if (releases instanceof JsonArray rArray) {
            rArray.parallelStream().flatMap(value -> parseReleases(baseUri, value)).filter(Objects::nonNull)
                    .forEachOrdered(info::add);
        }
        info.trimToSize();
        return info;
    }

    private Stream<MapWithAIInfo> parseReleases(URI baseUri, JsonValue value) {
        if (value instanceof JsonObject release && release.containsKey("release_id") && release.containsKey("files")) {
            final var id = release.get("release_id");
            final var files = release.get("files");
            if (id instanceof JsonString sId && files instanceof JsonArray fArray) {
                final String releaseId = sId.getString();
                return fArray.parallelStream().map(file -> parseFile(baseUri, releaseId, file));
            }
        }
        return Stream.empty();
    }

    /**
     * Parse the individual file from the files array
     * @param baseUri The base URI (if the href is relative)
     * @param releaseId The release id to differentiate it from other releases with the same theme
     * @param file The file object
     * @return The info, if it was parsed. Otherwise {@code null}.
     */
    @Nullable
    private MapWithAIInfo parseFile(URI baseUri, String releaseId, JsonValue file) {
        if (file instanceof JsonObject fObj && fObj.containsKey("theme") && fObj.containsKey("href")) {
            final JsonValue vTheme = fObj.get("theme");
            final JsonValue vHref = fObj.get("href");
            try {
                if (vTheme instanceof JsonString sTheme && vHref instanceof JsonString href) {
                    final var theme = sTheme.getString();
                    final URI uri;
                    if (href.getString().startsWith("./") || href.getString().startsWith("../")) {
                        uri = baseUri.resolve(href.getString());
                    } else {
                        uri = new URI(href.getString());
                    }
                    return buildSource(uri, releaseId, theme);
                }
            } catch (URISyntaxException uriSyntaxException) {
                Logging.debug(uriSyntaxException);
            }
        }
        return null;
    }

    private MapWithAIInfo buildSource(URI uri, String releaseId, String theme) {
        final var info = new MapWithAIInfo(this.source);
        info.setUrl(uri.toString());
        info.setName(this.source.getName() + ": " + theme + " - " + releaseId);
        if ("addresses".equals(theme)) {
            info.setCategory(MapWithAICategory.ADDRESS);
        } else if ("buildings".equals(theme)) {
            info.setCategory(MapWithAICategory.BUILDING);
        } else {
            info.setCategory(MapWithAICategory.OTHER);
        }
        // Addresses and places are "interesting". Only removing "transportation" since that currently causes crashes.
        if ("transportation".equals(theme)) {
            return null;
        }
        final var categories = EnumSet.of(this.source.getCategory(),
                this.source.getAdditionalCategories().toArray(MapWithAICategory[]::new));
        categories.removeIf(MapWithAICategory.OTHER::equals);
        info.setAdditionalCategories(new ArrayList<>(categories));
        info.setId(info.getName());
        if (uri.getPath().endsWith(".pmtiles")) {
            info.setSourceType(MapWithAIType.PMTILES);
            readTileInformation(info, uri, releaseId);
        }
        return info;
    }

    /**
     * Read additional information (bounds, name, description) from the tiles
     *
     * @param info      The info to update
     * @param uri       The location of the tiles
     * @param releaseId The release id
     */
    void readTileInformation(MapWithAIInfo info, URI uri, String releaseId) {
        try {
            final var header = PMTiles.readHeader(uri);
            final var metadata = PMTiles.readMetadata(header);
            final var bounds = new Bounds(header.minLatitude(), header.minLongitude(), header.maxLatitude(),
                    header.maxLongitude());
            info.setBounds(new ImageryInfo.ImageryBounds(bounds.encodeAsString(","), ","));
            if (metadata.containsKey("name") && metadata.get("name")instanceof JsonString name) {
                info.setName(name.getString() + " - " + releaseId);
            }
            if (metadata.containsKey("description")
                    && metadata.get("description")instanceof JsonString description) {
                info.setDescription(description.getString());
            }
        } catch (IOException ioException) {
            Logging.error(ioException);
        }
    }
}
