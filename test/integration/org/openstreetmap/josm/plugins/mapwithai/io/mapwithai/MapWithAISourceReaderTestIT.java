// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.plugins.mapwithai.backend.DataAvailability;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIType;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAISourceReaderTestIT {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rule = new JOSMTestRules().preferences().territories().projection();

    @Test
    public void testDefaultSourceIT() throws IOException {
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
