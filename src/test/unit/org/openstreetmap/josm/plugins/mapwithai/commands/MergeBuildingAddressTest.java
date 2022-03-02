// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class MergeBuildingAddressTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rule = new JOSMTestRules().projection();

    @Test
    void testSingleAddress() {
        DataSet ds = new DataSet();
        Node addr = new Node(new LatLon(0, 0));
        ds.addPrimitive(addr);
        addr.put("addr:housenumber", "1");
        MergeBuildingAddress conflation = new MergeBuildingAddress(ds);
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        assertEquals(1, conflation.getParticipatingPrimitives().size());
        final double square = 0.0001;
        Way way = TestUtils.newWay("", new Node(new LatLon(-square, -square)), new Node(new LatLon(-square, square)),
                new Node(new LatLon(square, square)), new Node(new LatLon(square, -square)));
        way.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way);
        way.addNode(way.firstNode());
        assertNull(conflation.getCommand(Collections.singletonList(addr)));

        way.put("building", "yes");
        Command command = conflation.getCommand(Collections.singletonList(addr));
        command.executeCommand();
        assertEquals("1", way.get("addr:housenumber"));
        assertEquals(1, conflation.getParticipatingPrimitives().size());
        command.undoCommand();

        way.remove("building");
        Relation multipolygon = new Relation();
        multipolygon.addMember(new RelationMember("outer", way));
        multipolygon.put("type", "multipolygon");
        ds.addPrimitive(multipolygon);
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        multipolygon.put("building", "yes");
        command = conflation.getCommand(Collections.singletonList(addr));
        command.executeCommand();
        assertEquals("1", multipolygon.get("addr:housenumber"));
        assertEquals(1, conflation.getParticipatingPrimitives().size());
        command.undoCommand();
    }

    @Test
    void testMultiAddress() {
        DataSet ds = new DataSet();
        Node addr = new Node(new LatLon(0, 0));
        Node addr2 = new Node(new LatLon(0.00005, 0.00005));
        ds.addPrimitive(addr);
        ds.addPrimitive(addr2);
        addr.put("addr:housenumber", "1");
        addr2.put("addr:housenumber", "2");
        addr.put("addr:street", "Test");
        addr2.put("addr:street", "Test");
        MergeBuildingAddress conflation = new MergeBuildingAddress(ds);
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        assertEquals(1, conflation.getParticipatingPrimitives().size());
        final double square = 0.0001;
        Way way = TestUtils.newWay("", new Node(new LatLon(-square, -square)), new Node(new LatLon(-square, square)),
                new Node(new LatLon(square, square)), new Node(new LatLon(square, -square)));
        way.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(way);
        way.addNode(way.firstNode());
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        assertEquals(1, conflation.getParticipatingPrimitives().size());

        way.put("building", "yes");
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        assertEquals(1, conflation.getParticipatingPrimitives().size());

        way.remove("building");
        Relation multipolygon = new Relation();
        multipolygon.addMember(new RelationMember("outer", way));
        multipolygon.put("type", "multipolygon");
        ds.addPrimitive(multipolygon);
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        assertEquals(1, conflation.getParticipatingPrimitives().size());
        multipolygon.put("building", "yes");
        assertNull(conflation.getCommand(Collections.singletonList(addr)));
        assertEquals(1, conflation.getParticipatingPrimitives().size());
    }
}
