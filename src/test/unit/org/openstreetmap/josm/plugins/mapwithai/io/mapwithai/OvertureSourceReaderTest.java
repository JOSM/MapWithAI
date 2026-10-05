// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import jakarta.json.Json;

/**
 * Test class for {@link OvertureSourceReader}
 */
@BasicPreferences
class OvertureSourceReaderTest {
    private static List<MapWithAIInfo> parse(String json) throws IOException {
        final var source = new MapWithAIInfo("Overture", "https://stac.overturemaps.org/catalog.json");
        source.setSourceType(MapWithAIType.OVERTURE);
        try (var reader = new OvertureSourceReader(source) {
            @Override
            void readTileInformation(MapWithAIInfo info, URI uri, String releaseId) {
                // Don't read the (remote) pmtiles headers in tests
            }
        };
                var sr = new StringReader(json);
                var parser = Json.createParser(sr)) {
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
        final var infos = parse("""
                {"type":"Catalog","id":"Overture Releases","stac_version":"1.1.0",
                "links":[{"rel":"child","href":"https://stac.overturemaps.org/2026-09-23.1/catalog.json","latest":true}],
                "latest":"2026-09-23.1"}""");
        assertEquals(5, infos.size());
        for (var info : infos) {
            assertEquals(MapWithAIType.PMTILES, info.getSourceType());
            assertTrue(info.getUrl().startsWith(
                    "https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com/tiles/2026-09-23.1/"),
                    info.getUrl());
            assertTrue(info.getUrl().endsWith(".pmtiles"), info.getUrl());
            assertTrue(info.hasValidUrl());
        }
        final var buildings = infos.stream().filter(i -> i.getUrl().endsWith("/buildings.pmtiles")).findFirst()
                .orElseThrow();
        assertEquals(MapWithAICategory.BUILDING, buildings.getCategory());
        // The id must not change between releases
        assertEquals("Overture: buildings", buildings.getId());
    }

    @Test
    void testBadCatalog() throws IOException {
        assertTrue(parse("{\"type\":\"Catalog\"}").isEmpty());
        assertTrue(parse("{\"latest\":\"../../something else\"}").isEmpty());
    }
}
