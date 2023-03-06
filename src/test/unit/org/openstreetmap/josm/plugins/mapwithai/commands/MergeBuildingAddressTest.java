// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void testNonRegression22789() {
        final Node addr = TestUtils.newNode("addr:city=Unincorporated\n" + "addr:housenumber=5144\n"
                + "addr:postcode=66617\n" + "addr:state=KS\n" + "addr:street=Northwest Rochester Road\n"
                + "current_id=-138648\n" + "source=esri_USDOT_Kansas\n" + "building=yes");
        addr.setCoor(new LatLon(39.1399708, -95.6723393));
        final Node oldAddr = TestUtils.newNode("addr:city=Topeka\n" + "addr:housenumber=5144\n"
                + "addr:postcode=66617\n" + "addr:street=Northwest Rochester Road\n" + "building=residential\n"
                + "lbcs:activity:code=1100\n" + "lbcs:activity:name=Household activities\n"
                + "lbcs:function:code=1101\n" + "lbcs:function:name=Single family residence (detached)\n"
                + "source=bing;http://gis.snco.us");
        oldAddr.setCoor(new LatLon(39.1399958, -95.6723318));
        oldAddr.setOsmId(2077593766, 6);
        final Way building = TestUtils.newWay("building=yes", new Node(new LatLon(39.1400507, -95.672136)),
                new Node(new LatLon(39.1398258, -95.6723906)), new Node(new LatLon(39.1399037, -95.6725051)),
                new Node(new LatLon(39.1401287, -95.6722505)));
        building.addNode(building.firstNode());
        building.setOsmId(198058477, 1);
        building.getNode(0).setOsmId(2082491004, 1);
        building.getNode(1).setOsmId(2082491000, 1);
        building.getNode(2).setOsmId(2082491002, 1);
        building.getNode(3).setOsmId(2082491005, 1);
        final DataSet ds = new DataSet();
        ds.addPrimitiveRecursive(building);
        ds.addPrimitive(oldAddr);
        ds.addPrimitive(addr);
        MergeBuildingAddress conflation = new MergeBuildingAddress(ds);
        assertNotNull(conflation.getCommand(Collections.singletonList(addr)));
        DeleteCommand.delete(Collections.singletonList(oldAddr), true, true).executeCommand();
        final Command mergeCommand = conflation.getCommand(Collections.singletonList(addr));
        assertNotNull(mergeCommand);
        mergeCommand.executeCommand();
        assertAll(() -> assertTrue(addr.isDeleted()), () -> assertTrue(oldAddr.isDeleted()),
                () -> assertEquals(5, building.getNodesCount()), () -> assertTrue(building.isClosed()),
                () -> assertEquals(2082491004, building.getNode(0).getOsmId()),
                () -> assertEquals(2082491000, building.getNode(1).getOsmId()),
                () -> assertEquals(2082491002, building.getNode(2).getOsmId()),
                () -> assertEquals(2082491005, building.getNode(3).getOsmId()),
                () -> assertEquals("5144", building.get("addr:housenumber")),
                () -> assertEquals("66617", building.get("addr:postcode")),
                () -> assertEquals("KS", building.get("addr:state")),
                () -> assertEquals("Northwest Rochester Road", building.get("addr:street")),
                () -> assertEquals("yes", building.get("building")));
    }
}
