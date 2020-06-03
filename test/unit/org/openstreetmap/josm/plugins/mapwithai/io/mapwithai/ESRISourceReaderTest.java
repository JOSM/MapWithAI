// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;

public class ESRISourceReaderTest {
    @Rule
    public MapWithAITestRules rule = (MapWithAITestRules) new MapWithAITestRules().wiremock().projection();

    @Test
    public void testAddEsriLayer() throws IOException {
        // TODO wiremock
        MapWithAIInfo info = new MapWithAIInfo("TEST", "test_url", "bdf6c800b3ae453b9db239e03d7c1727");
        info.setSourceType(MapWithAIType.ESRI);
        String tUrl = rule.getWireMock().baseUrl() + "/sharing/rest";
        for (String url : Arrays.asList(tUrl, tUrl + "/")) {
            info.setUrl(url);
            try (ESRISourceReader reader = new ESRISourceReader(info)) {
                Collection<MapWithAIInfo> layers = reader.parse();
                assertFalse(layers.isEmpty());
                assertTrue(layers.stream().noneMatch(i -> info.getUrl().equals(i.getUrl())));
                assertTrue(layers.stream().allMatch(i -> MapWithAIType.ESRI_FEATURE_SERVER.equals(i.getSourceType())));
            }
        }
    }
}
