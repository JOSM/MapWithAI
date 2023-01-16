// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test class for {@link MergeBuildingAddress}
 */
class MergeBuildingAddressTest {
    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    static JOSMTestRules rule = new JOSMTestRules().projection();

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

    /**
     * Adding two address nodes with the same tags shouldn't result in <i>both</i>
     * objects being deleted. The example from Slack had the same address for two
     * different buildings. There should probably be an `addr:unit` tag, but that
     * can be added later, by something like StreetComplete.
     */
    @Test
    void testMultiSameAddress() {
        DataSet ds = new DataSet();
        Node addr = new Node(new LatLon(0, 0));
        Node addr2 = new Node(new LatLon(0.00005, 0.00005));
        ds.addPrimitive(addr);
        ds.addPrimitive(addr2);
        addr.put("addr:housenumber", "1");
        addr2.put("addr:housenumber", "1");
        addr.put("addr:street", "Test");
        addr2.put("addr:street", "Test");
        Way building1 = TestUtils.newWay("building=yes", new Node(new LatLon(0.00001, 0.00001)),
                new Node(new LatLon(0.00001, -0.00001)), new Node(new LatLon(-0.00001, -0.00001)),
                new Node(new LatLon(-0.00001, 0.00001)));
        Way building2 = TestUtils.newWay("building=yes", new Node(new LatLon(0.00006, 0.00006)),
                new Node(new LatLon(0.00006, 0.00004)), new Node(new LatLon(0.00004, 0.00004)),
                new Node(new LatLon(0.00004, 0.00006)));
        ds.addPrimitiveRecursive(building1);
        ds.addPrimitiveRecursive(building2);
        building1.addNode(building1.firstNode());
        building2.addNode(building2.firstNode());
        MergeBuildingAddress conflation = new MergeBuildingAddress(ds);
        Command command = conflation.getCommand(Arrays.asList(addr, addr2));
        assertNotNull(command);
        command.executeCommand();
        assertEquals(3, building1.getInterestingTags().size());
        assertEquals(3, building2.getInterestingTags().size());
        assertEquals(building1.getInterestingTags(), building2.getInterestingTags());
        assertEquals("Test", building1.get("addr:street"));
        assertEquals("1", building1.get("addr:housenumber"));
    }

    @Test
    void testDeletedBuilding() {
        DataSet ds = new DataSet();
        Node addr = new Node(new LatLon(0, 0));
        addr.put("addr:street", "Test");
        addr.put("addr:housenumber", "1");
        Way building1 = TestUtils.newWay("building=yes", new Node(new LatLon(0.00001, 0.00001)),
                new Node(new LatLon(0.00001, -0.00001)), new Node(new LatLon(-0.00001, -0.00001)),
                new Node(new LatLon(-0.00001, 0.00001)));
        ds.addPrimitive(addr);
        ds.addPrimitiveRecursive(building1);
        building1.addNode(building1.firstNode());
        MergeBuildingAddress conflation = new MergeBuildingAddress(ds);
        DeleteCommand.delete(Collections.singletonList(building1)).executeCommand();
        Command command = conflation.getCommand(Collections.singletonList(addr));
        assertNull(command);
    }
}
