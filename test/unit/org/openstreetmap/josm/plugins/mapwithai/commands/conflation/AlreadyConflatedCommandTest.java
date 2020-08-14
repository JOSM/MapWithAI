// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands.conflation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.commands.AlreadyConflatedCommand;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.PreConflatedDataUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class AlreadyConflatedCommandTest {
    @Rule
    public JOSMTestRules rule = new JOSMTestRules().projection();
    private AlreadyConflatedCommand alreadyConflatedCommand;

    @Before
    public void setUp() {
        DataSet ds = new DataSet();
        alreadyConflatedCommand = new AlreadyConflatedCommand(ds);
    }

    @Test
    public void testGetInterestedTypes() {
        assertTrue(alreadyConflatedCommand.getInterestedTypes().contains(Relation.class));
        assertTrue(alreadyConflatedCommand.getInterestedTypes().contains(Way.class));
        assertTrue(alreadyConflatedCommand.getInterestedTypes().contains(Node.class));
    }

    @Test
    public void testGetKey() {
        assertEquals(PreConflatedDataUtils.getConflatedKey(), alreadyConflatedCommand.getKey());
    }

    @Test
    public void testGetRealCommand() {
        DataSet ds = alreadyConflatedCommand.getAffectedDataSet();
        String key = PreConflatedDataUtils.getConflatedKey();
        ds.addPrimitive(TestUtils.newNode(key + "=w1000"));
        ds.addPrimitive(TestUtils.newNode("addr:street=42"));
        Way way1 = TestUtils.newWay(key + "=n1", new Node(LatLon.ZERO), new Node(LatLon.NORTH_POLE));
        Way way2 = TestUtils.newWay("highway=residential", new Node(LatLon.ZERO), new Node(LatLon.SOUTH_POLE));
        Relation relation1 = TestUtils.newRelation(key + "=true", new RelationMember("", new Node(LatLon.ZERO)));
        Relation relation2 = TestUtils.newRelation("type=restriction", new RelationMember("", new Node(LatLon.ZERO)));
        for (Way w : Arrays.asList(way1, way2)) {
            w.getNodes().forEach(ds::addPrimitive);
            ds.addPrimitive(w);
        }
        for (Relation r : Arrays.asList(relation1, relation2)) {
            r.getMemberPrimitives().forEach(ds::addPrimitive);
            ds.addPrimitive(r);
        }

        Command command = alreadyConflatedCommand.getCommand(ds.allPrimitives());
        for (int i = 0; i < 10; i++) {
            assertEquals(3, ds.allPrimitives().stream().filter(p -> p.hasKey(key)).count());
            command.executeCommand();
            assertEquals(0, ds.allPrimitives().stream().filter(p -> p.hasKey(key)).count());

            command.undoCommand();
            assertEquals(3, ds.allPrimitives().stream().filter(p -> p.hasKey(key)).count());
        }
    }

    @Test
    public void testAllowUndo() {
        assertTrue(alreadyConflatedCommand.allowUndo());
    }

    @Test
    public void testKeyShouldNotExistInOSM() {
        assertTrue(alreadyConflatedCommand.keyShouldNotExistInOSM());
    }

    @Test
    public void testGetDescriptionText() {
        assertNotNull(alreadyConflatedCommand.getDescriptionText());
        assertFalse(alreadyConflatedCommand.getDescriptionText().trim().isEmpty());
    }

}
