// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtilsTest;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIDownloadSourceTypeTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rules = new JOSMTestRules().projection();

    /**
     * Check that we are appropriately checking that downloads are the correct size
     */
    @Test
    public void testMapWithAIDownloadDataSizeCheck() {
        MapWithAIDownloadSourceType type = new MapWithAIDownloadSourceType();
        assertFalse(type.isDownloadAreaTooLarge(MapWithAIDataUtilsTest.getTestBounds()),
                "The download area shouldn't be too large");
        assertTrue(type.isDownloadAreaTooLarge(new Bounds(0, 0, 0.0001, 10)), "The download area should be too large");
        assertFalse(type.isDownloadAreaTooLarge(MapWithAIDataUtilsTest.getTestBounds()),
                "The download area shouldn't be too large");
        assertTrue(type.isDownloadAreaTooLarge(new Bounds(0, 0, 10, 0.0001)), "The download area should be too large");
        assertFalse(type.isDownloadAreaTooLarge(MapWithAIDataUtilsTest.getTestBounds()),
                "The download area shouldn't be too large");
    }
}
