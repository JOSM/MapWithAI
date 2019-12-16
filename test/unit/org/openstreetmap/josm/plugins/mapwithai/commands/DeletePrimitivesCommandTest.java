// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class DeletePrimitivesCommandTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    @Test
    public void testDeletePrimitives() {
        final DataSet ds = new DataSet();
        final Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(-0.1, 0.1)));
        way1.getNodes().forEach(node -> ds.addPrimitive(node));
        ds.addPrimitive(way1);

        DeletePrimitivesCommand delete = new DeletePrimitivesCommand(ds, Collections.singleton(way1));
        delete.executeCommand();
        assertTrue(way1.isDeleted(), "The way should be deleted");
        assertEquals(0, ds.allNonDeletedPrimitives().size(), "There should be no non-deleted primitives");

        delete.undoCommand();

        assertTrue(ds.containsWay(way1), "The DataSet should contain way1");
        assertEquals(3, ds.allNonDeletedPrimitives().size(), "There should be three non-deleted primitives");

        final Node tNode = new Node(new LatLon(0.1, 0.1));
        ds.addPrimitive(tNode);

        delete.executeCommand();
        assertTrue(way1.isDeleted(), "The way should be deleted");
        assertEquals(1, ds.allNonDeletedPrimitives().size(), "Non-relevant objects should not be affected");

        delete.undoCommand();
        assertEquals(4, ds.allNonDeletedPrimitives().size(), "The DataSet should be as it was");

        way1.firstNode().put("highway", "stop");

        delete.executeCommand();
        assertTrue(way1.isDeleted(), "The way should be deleted");
        assertEquals(2, ds.allNonDeletedPrimitives().size(), "Objects with their own keys should remain");

        delete.undoCommand();
        assertTrue(way1.firstNode().hasKey("highway"), "Objects should not lose their keys");

        delete = new DeletePrimitivesCommand(ds, Collections.singleton(way1), true);

        delete.executeCommand();
        assertTrue(way1.isDeleted(), "The way should be deleted");
        assertEquals(1, ds.allNonDeletedPrimitives().size(),
                "All nodes in the way not in another way should be removed");

        delete.undoCommand();
        assertEquals(4, ds.allNonDeletedPrimitives().size(), "The DataSet should be as it was");

        assertTrue(way1.firstNode().hasKey("highway"), "Objects should not lose their keys");
    }

}
