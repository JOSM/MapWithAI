// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Test class for {@link OvertureSourceReader}
 */
@BasicPreferences
class OvertureSourceReaderTest {
    private static final String STAC = "https://stac.overturemaps.org/";
    private static final String ROOT = """
            {"type":"Catalog","id":"Overture Releases","stac_version":"1.1.0","latest":"2026-09-23.1",
            "links":[{"rel":"child","href":"https://stac.overturemaps.org/2026-08-19.0/catalog.json","title":"2026-08-19.0 Overture Release"},
            {"rel":"child","href":"https://stac.overturemaps.org/2026-09-23.1/catalog.json","title":"2026-09-23.1 Overture Release","latest":true},
            {"rel":"self","href":"https://stac.overturemaps.org/catalog.json"}]}""";
    private static final String RELEASE = """
            {"type":"Catalog","id":"2026-09-23.1","stac_version":"1.1.0",
            "links":[{"rel":"root","href":"https://stac.overturemaps.org/catalog.json"},
            {"rel":"child","href":"https://stac.overturemaps.org/2026-09-23.1/addresses/catalog.json","title":"addresses"},
            {"rel":"child","href":"https://stac.overturemaps.org/2026-09-23.1/buildings/catalog.json","title":"buildings"},
            {"rel":"child","href":"https://stac.overturemaps.org/2026-09-23.1/divisions/catalog.json","title":"divisions"},
            {"rel":"child","href":"https://stac.overturemaps.org/2026-09-23.1/transportation/catalog.json","title":"transportation"}]}""";

    private static String theme(String theme, boolean tiles) {
        return "{\"type\":\"Catalog\",\"id\":\"" + theme + "\",\"links\":[{\"rel\":\"root\",\"href\":\"" + STAC
                + "catalog.json\"}" + (tiles ? ",{\"rel\":\"pmtiles\",\"href\":\"https://tiles.overturemaps.org/2026-09-23.1/"
                        + theme + ".pmtiles\",\"type\":\"application/vnd.pmtiles\"}" : "")
                + "]}";
    }

    private static final Map<String, String> CATALOGS = Map.of(STAC + "2026-09-23.1/catalog.json", RELEASE,
            STAC + "2026-09-23.1/addresses/catalog.json", theme("addresses", true),
            STAC + "2026-09-23.1/buildings/catalog.json", theme("buildings", true),
            STAC + "2026-09-23.1/divisions/catalog.json", theme("divisions", false),
            STAC + "2026-09-23.1/transportation/catalog.json", theme("transportation", true));

    private static List<MapWithAIInfo> parse(String json) throws IOException {
        final var source = new MapWithAIInfo("Overture", STAC + "catalog.json");
        source.setSourceType(MapWithAIType.OVERTURE);
        try (var reader = new OvertureSourceReader(source) {
            @Override
            protected JsonObject readObject(String url) throws IOException {
                // Serve the canned catalogs instead of going to the network
                if (!CATALOGS.containsKey(url)) {
                    throw new IOException("Unexpected url: " + url);
                }
                try (var parser = Json.createParser(new StringReader(CATALOGS.get(url)))) {
                    parser.next();
                    return parser.getObject();
                }
            }

            @Override
            void readTileInformation(MapWithAIInfo info, URI uri, String releaseId) {
                // Don't read the (remote) pmtiles headers in tests
            }
        }; var sr = new StringReader(json); var parser = Json.createParser(sr)) {
            parser.next();
            return reader.parseJson(parser);
        }
    }

    /**
     * Non-regression test for #24875: the old pmtiles catalog no longer exists. Use
     * the STAC catalog instead.
     */
    @Test
    void testStacCatalog() throws IOException {
        final var infos = parse(ROOT);
        // divisions has no tiles, transportation is skipped
        assertEquals(2, infos.size(), infos.toString());
        for (var info : infos) {
            assertEquals(MapWithAIType.PMTILES, info.getSourceType());
            assertTrue(info.hasValidUrl());
            assertTrue(info.getName().contains("2026-09-23.1"), info.getName());
        }
        final var buildings = infos.stream().filter(i -> i.getUrl().endsWith("/buildings.pmtiles")).findFirst()
                .orElseThrow();
        assertEquals("https://tiles.overturemaps.org/2026-09-23.1/buildings.pmtiles", buildings.getUrl());
        assertEquals(MapWithAICategory.BUILDING, buildings.getCategory());
        // The id must not change between releases
        assertEquals("Overture: buildings", buildings.getId());
        final var addresses = infos.stream().filter(i -> i.getUrl().endsWith("/addresses.pmtiles")).findFirst()
                .orElseThrow();
        assertEquals(MapWithAICategory.ADDRESS, addresses.getCategory());
    }

    /**
     * The {@code latest} field is optional in STAC; fall back to the link marked as
     * latest.
     */
    @Test
    void testStacCatalogWithoutLatest() throws IOException {
        assertEquals(2, parse(ROOT.replace("\"latest\":\"2026-09-23.1\",", "")).size());
    }

    @Test
    void testBadCatalog() throws IOException {
        assertTrue(parse("{\"type\":\"Catalog\"}").isEmpty());
        assertTrue(parse("{\"type\":\"Catalog\",\"links\":[]}").isEmpty());
        assertTrue(parse("{\"type\":\"Catalog\",\"latest\":\"2000-01-01.0\",\"links\":[{\"rel\":\"child\","
                + "\"href\":\"https://stac.overturemaps.org/2000-01-01.0/catalog.json\"}]}").isEmpty());
    }
}
