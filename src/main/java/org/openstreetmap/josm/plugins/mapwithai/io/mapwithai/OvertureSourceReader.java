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
    public List<MapWithAIInfo> parseJson(JsonObject jsonObject) {
        if (jsonObject.containsKey("releases")) {
            final var info = new ArrayList<MapWithAIInfo>(6 * 4);
            final var releases = jsonObject.get("releases");
            final var baseUri = URI.create(this.source.getUrl()).resolve("./"); // safe since we created an URI from the source to get to this point
            if (releases instanceof JsonArray rArray) {
                for (JsonValue value : rArray) {
                    if (value instanceof JsonObject release && release.containsKey("release_id")
                            && release.containsKey("files")) {
                        final var id = release.get("release_id");
                        final var files = release.get("files");
                        if (id instanceof JsonString sId && files instanceof JsonArray fArray) {
                            final String releaseId = sId.getString();
                            for (JsonValue file : fArray) {
                                final var newInfo = parseFile(baseUri, releaseId, file);
                                if (newInfo != null) {
                                    info.add(newInfo);
                                }
                            }
                        }
                    }
                }
            }
            info.trimToSize();
            return info;
        }
        return Collections.emptyList();
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
            // Set additional information
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
        return info;
    }
}
