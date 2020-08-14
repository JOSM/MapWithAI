// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands.conflation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.mapwithai.commands.AbstractConflationCommand;

public class AbstractConflationCommandTest {
    /**
     * Non-regression test for
     * <a href="https://josm.openstreetmap.de/ticket/19495">#19495</a>
     */
    @Test
    public void testBadPrimitive() {
        assertDoesNotThrow(() -> AbstractConflationCommand.getPrimitives(new DataSet(), "Linie 401699530"));
    }
}
