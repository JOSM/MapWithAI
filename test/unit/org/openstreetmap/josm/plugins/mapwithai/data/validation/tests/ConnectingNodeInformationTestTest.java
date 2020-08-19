// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.commands.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.commands.DuplicateCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ConnectingNodeInformationTestTest {
    /**
     * Setup test.
     */
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rule = new JOSMTestRules().projection();

    @Test
    public void testGetBadData() {
        DataSet ds = new DataSet();
        Way way = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 1)));
        way.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way);
        ds.addPrimitive(new Relation());
        ConnectingNodeInformationTest test = new ConnectingNodeInformationTest();
        test.startTest(null);
        test.visit(ds.allPrimitives());
        assertTrue(test.getErrors().isEmpty());
        way.firstNode().put(DuplicateCommand.KEY, "n123");
        test.visit(ds.allPrimitives());
        assertEquals(1, test.getErrors().size());
        assertEquals(way.firstNode(), test.getErrors().get(0).getPrimitives().iterator().next());
        test.getErrors().get(0).getFix().executeCommand();
        assertFalse(way.firstNode().hasKey(DuplicateCommand.KEY));
        test.clear();

        way.firstNode().put(DuplicateCommand.KEY, null);
        way.put(ConnectedCommand.KEY, "w1,w2,n123");
        test.visit(ds.allPrimitives());
        assertEquals(1, test.getErrors().size());
        assertEquals(way, test.getErrors().get(0).getPrimitives().iterator().next());
        test.getErrors().get(0).getFix().executeCommand();
        assertFalse(way.hasKey(ConnectedCommand.KEY));
        assertTrue(way.hasKeys());
    }

}
