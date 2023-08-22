// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.Territories;

import com.google.common.collect.ImmutableMap;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

@BasicPreferences
@Territories
@Projection
class MapWithAISourceReaderTest {
    @Test
    void testParseSimple() throws IOException {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("nowhere", JsonValue.NULL);
        try (var reader = new MapWithAISourceReader("")) {
            List<MapWithAIInfo> infoList = reader.parseJson(builder.build());
            assertEquals(1, infoList.size());
            assertEquals("nowhere", infoList.get(0).getName());
        }
    }

    @Test
    void testParseComplex() throws IOException {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        JsonObjectBuilder co = Json.createObjectBuilder(
                ImmutableMap.of("url", "test", "license", "pd", "permission_url", "https://permission.url"));
        JsonObjectBuilder coCountries = Json.createObjectBuilder();
        JsonArrayBuilder coCountriesArray = Json.createArrayBuilder(Collections.singleton("addr:housenumber"));
        coCountries.add("US-CO", coCountriesArray.build());
        co.add("countries", coCountries.build());
        builder.add("Colorado", co);
        try (var reader = new MapWithAISourceReader("")) {
            List<MapWithAIInfo> infoList = reader.parseJson(builder.build());
            assertEquals(1, infoList.size());
            MapWithAIInfo info = infoList.stream().filter(i -> "Colorado".equals(i.getName())).findFirst().orElse(null);
            assertNotNull(info);
            assertEquals("Colorado", info.getName());
            assertEquals("test", info.getUrl());
        }
    }
}
