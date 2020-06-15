// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class DuplicateCommandTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rule = new JOSMTestRules().projection();

    @Test
    void testVariousConditions() {
        DataSet ds = new DataSet();
        DuplicateCommand dupe = new DuplicateCommand(ds);

        Node dupe1 = new Node(new LatLon(0, 0));
        Node dupe2 = new Node(new LatLon(0, 0));
        ds.addPrimitive(dupe2);
        ds.addPrimitive(dupe1);

        assertNull(dupe.getCommand(Collections.singleton(dupe1)));

        // SimplePrimitiveId doesn't understand negative ids
        dupe2.setOsmId(2, 1);
        dupe1.put(dupe.getKey(), "n" + dupe2.getUniqueId());

        Command command = dupe.getCommand(Collections.singleton(dupe1));
        command.executeCommand();
        assertTrue(dupe1.isDeleted());
        assertFalse(dupe2.isDeleted());
        assertFalse(dupe2.hasKey(dupe.getKey()));
        command.undoCommand();
        assertFalse(dupe1.isDeleted());
        assertTrue(dupe1.hasKey(dupe.getKey()));
        assertFalse(dupe2.hasKey(dupe.getKey()));

        Command deleteDupe2 = DeleteCommand.delete(Collections.singleton(dupe2), true, true);
        deleteDupe2.executeCommand();
        command = dupe.getCommand(Collections.singleton(dupe1));
        command.executeCommand();
        assertFalse(dupe1.isDeleted());
        assertTrue(dupe2.isDeleted());
        assertFalse(dupe1.hasKey(dupe.getKey()));
        command.undoCommand();
        assertFalse(dupe1.isDeleted());
        assertTrue(dupe1.hasKey(dupe.getKey()));
        assertFalse(dupe2.hasKey(dupe.getKey()));
        deleteDupe2.undoCommand();

        dupe1.setCoor(new LatLon(1, 1));

        command = dupe.getCommand(Collections.singleton(dupe1));
        command.executeCommand();
        assertFalse(dupe1.isDeleted());
        assertFalse(dupe2.isDeleted());
        assertFalse(dupe1.hasKey(dupe.getKey()));
        assertFalse(dupe2.hasKey(dupe.getKey()));
        command.undoCommand();
        assertFalse(dupe1.isDeleted());
        assertTrue(dupe1.hasKey(dupe.getKey()));
        assertFalse(dupe2.hasKey(dupe.getKey()));
    }

}
