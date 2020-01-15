// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DuplicateCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ConnectingNodeInformationTestTest {
    /**
     * Setup test.
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
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
        way.firstNode().put(DuplicateCommand.DUPE_KEY, "n123");
        test.visit(ds.allPrimitives());
        assertEquals(1, test.getErrors().size());
        assertEquals(way.firstNode(), test.getErrors().get(0).getPrimitives().iterator().next());
        test.clear();

        way.firstNode().put(DuplicateCommand.DUPE_KEY, null);
        way.put(ConnectedCommand.CONN_KEY, "w1,w2,n123");
        test.visit(ds.allPrimitives());
        assertEquals(1, test.getErrors().size());
        assertEquals(way, test.getErrors().get(0).getPrimitives().iterator().next());
    }

}
