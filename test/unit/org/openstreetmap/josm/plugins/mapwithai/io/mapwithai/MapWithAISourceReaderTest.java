// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@BasicPreferences
class MapWithAISourceReaderTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rule = new JOSMTestRules().territories().projection();

    @Test
    void testParseSimple() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("nowhere", JsonValue.NULL);
        List<MapWithAIInfo> infoList = new MapWithAISourceReader("").parseJson(builder.build());
        assertEquals(1, infoList.size());
        assertEquals("nowhere", infoList.get(0).getName());
    }

    @Test
    void testParseComplex() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        JsonObjectBuilder co = Json.createObjectBuilder(
                ImmutableMap.of("url", "test", "license", "pd", "permission_url", "https://permission.url"));
        JsonObjectBuilder coCountries = Json.createObjectBuilder();
        JsonArrayBuilder coCountriesArray = Json.createArrayBuilder(Collections.singleton("addr:housenumber"));
        coCountries.add("US-CO", coCountriesArray.build());
        co.add("countries", coCountries.build());
        builder.add("Colorado", co);
        List<MapWithAIInfo> infoList = new MapWithAISourceReader("").parseJson(builder.build());
        assertEquals(1, infoList.size());
        MapWithAIInfo info = infoList.stream().filter(i -> "Colorado".equals(i.getName())).findFirst().orElse(null);
        assertNotNull(info);
        assertEquals("Colorado", info.getName());
        assertEquals("test", info.getUrl());
    }
}
