// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Integration test for {@link MapWithAISourceReader}
 *
 * @author Taylor Smock
 */
@NoExceptions
@BasicPreferences
@Wiremock
class MapWithAISourceReaderTestIT {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rule = new JOSMTestRules().territories().projection();

    @Test
    @Wiremock(false)
    void testDefaultSourceIT() throws IOException {
        try (MapWithAISourceReader source = new MapWithAISourceReader(
                MapWithAIConfig.getUrls().getMapWithAISourcesJson())) {
            List<MapWithAIInfo> infoList = source.parse().orElse(Collections.emptyList());
            assertFalse(infoList.isEmpty(), "There should be viable sources");
            for (MapWithAIType type : Arrays.asList(MapWithAIType.FACEBOOK, MapWithAIType.THIRD_PARTY)) {
                assertTrue(infoList.stream().anyMatch(i -> type.equals(i.getSourceType())),
                        tr("Type {0} should have more than 0 sources", type.getTypeString()));
            }
        }
    }
}
