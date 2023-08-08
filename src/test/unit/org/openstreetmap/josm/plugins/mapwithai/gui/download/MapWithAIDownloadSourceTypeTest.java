// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JCheckBox;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtilsTest;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.Territories;

@BasicPreferences
@Projection
@Territories
@Wiremock
class MapWithAIDownloadSourceTypeTest {
    /**
     * Check that we are appropriately checking that downloads are the correct size
     */
    @Test
    void testMapWithAIDownloadDataSizeCheck() {
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

    /**
     * Test that the listener works properly
     */
    @Test
    void testMapWithAIDownloadSourceTypeListener() {
        MapWithAIDownloadSourceType type = new MapWithAIDownloadSourceType();
        JCheckBox checkbox = type.getCheckBox();
        assertNotNull(checkbox);
        assertSame(checkbox, type.getCheckBox());

        AtomicBoolean listener = new AtomicBoolean();
        type.getCheckBox(l -> listener.set(!listener.get()));
        assertFalse(listener.get());
        checkbox.doClick();
        assertTrue(listener.get());
        checkbox.doClick();
        assertFalse(listener.get());
    }
}
