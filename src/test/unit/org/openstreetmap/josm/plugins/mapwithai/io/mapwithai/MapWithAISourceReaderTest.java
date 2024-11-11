// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.StringReader;
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
import jakarta.json.stream.JsonParser;

@BasicPreferences
@Territories
@Projection
class MapWithAISourceReaderTest {
    @Test
    void testParseSimple() throws IOException {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("nowhere", JsonValue.NULL);
        final String json = builder.build().toString();
        try (var reader = new MapWithAISourceReader("");
                StringReader sr = new java.io.StringReader(json);
                JsonParser parser = Json.createParser(sr)) {
            parser.next();
            List<MapWithAIInfo> infoList = reader.parseJson(parser);
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
        String json = builder.add("Colorado", co).build().toString();
        try (var reader = new MapWithAISourceReader("");
                StringReader sr = new StringReader(json);
                JsonParser parser = Json.createParser(sr)) {
            parser.next();
            List<MapWithAIInfo> infoList = reader.parseJson(parser);
            assertEquals(1, infoList.size());
            MapWithAIInfo info = infoList.stream().filter(i -> "Colorado".equals(i.getName())).findFirst().orElse(null);
            assertNotNull(info);
            assertEquals("Colorado", info.getName());
            assertEquals("test", info.getUrl());
        }
    }
}
