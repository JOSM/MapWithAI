// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.plugins.mapwithai.backend.DataAvailability;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.NoExceptions;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

/**
 * Integration test for {@link MapWithAISourceReader}
 *
 * @author Taylor Smock
 */
@NoExceptions
@BasicPreferences
class MapWithAISourceReaderTestIT {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rule = new JOSMTestRules().territories().projection();

    @Test
    void testDefaultSourceIT() throws IOException {
        DataAvailability.setReleaseUrl(DataAvailability.DEFAULT_SERVER_URL);
        try (MapWithAISourceReader source = new MapWithAISourceReader(DataAvailability.getReleaseUrl())) {
            List<MapWithAIInfo> infoList = source.parse();
            assertFalse(infoList.isEmpty(), "There should be viable sources");
            for (MapWithAIType type : Arrays.asList(MapWithAIType.FACEBOOK, MapWithAIType.THIRD_PARTY)) {
                assertTrue(infoList.stream().filter(i -> type.equals(i.getSourceType())).count() > 0,
                        tr("Type {0} should have more than 0 sources", type.getTypeString()));
            }
        }
    }
}
