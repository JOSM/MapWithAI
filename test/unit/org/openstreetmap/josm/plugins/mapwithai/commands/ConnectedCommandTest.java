// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class ConnectedCommandTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rule = new JOSMTestRules().projection();

    @Test
    void testVariousConditions() {
        DataSet ds = new DataSet();
        ConnectedCommand command = new ConnectedCommand(ds);
        Way way = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 0)));
        way.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way);

        Node toAdd = new Node(new LatLon(0.5, 0));
        ds.addPrimitive(toAdd);

        Collection<OsmPrimitive> temporaryList = Collections.singletonList(toAdd);
        assertThrows(NullPointerException.class, () -> command.getCommand(temporaryList));

        // SimplePrimitiveId doesn't like negative ids
        way.setOsmId(1, 1);
        way.firstNode().setOsmId(1, 1);
        way.lastNode().setOsmId(2, 1);

        toAdd.put(command.getKey(),
                "w" + way.getUniqueId() + ",n" + way.firstNode().getUniqueId() + ",n" + way.lastNode().getUniqueId());

        Command realCommand = command.getCommand(Collections.singletonList(toAdd));
        realCommand.executeCommand();
        assertFalse(toAdd.hasKey(command.getKey()));
        realCommand.undoCommand();
        assertTrue(toAdd.hasKey(command.getKey()));

        toAdd.setCoor(new LatLon(2, 0));

        realCommand = command.getCommand(Collections.singletonList(toAdd));
        realCommand.executeCommand();
        assertFalse(toAdd.hasKey(command.getKey()));
        realCommand.undoCommand();
        assertTrue(toAdd.hasKey(command.getKey()));
    }

}
