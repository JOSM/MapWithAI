// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands.conflation.cleanup;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.commands.cleanup.MissingConnectionTags;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MissingConnectionTagsMocker;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mockit.integration.TestRunnerDecorator;

public class MissingConnectionTagsTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules josmTestRules = new JOSMTestRules().projection().main();
    private DataSet ds;
    private MissingConnectionTags missing;

    @Before
    public void setUp() {
        TestRunnerDecorator.cleanUpAllMocks();
        ds = new DataSet();
        // Required to avoid an NPE in AutoScaleAction
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(ds, "Test Layer", null));
        missing = new MissingConnectionTags(ds);
    }

    @After
    public void tearDown() {
        TestRunnerDecorator.cleanUpAllMocks();
    }

    @Test
    public void testUndoable() {
        /*
         * If we allow undo, issues occur due to dataset issues. If removing this test,
         * add an broader test to check for what happens when the move command is used
         * to move data, and then data is modified with this command, and then undo/redo
         * happens.
         */
        assertFalse(missing.allowUndo());
    }

    @Test
    public void testDupeNode() {
        new WindowMocker();
        Map<String, Object> actions = new HashMap<>();
        actions.put("Set dupe=node 1 for node 'node'", JOptionPane.YES_OPTION);
        new MissingConnectionTagsMocker(actions);
        Node node11 = new Node(LatLon.ZERO);
        Node node21 = new Node(LatLon.ZERO);
        Node node12 = new Node(LatLon.NORTH_POLE);
        Node node22 = new Node(LatLon.SOUTH_POLE);
        Way way1 = TestUtils.newWay("highway=residential", node11, node12);
        Way way2 = TestUtils.newWay("highway=residential", node21, node22);
        for (Way way : Arrays.asList(way1, way2)) {
            way.getNodes().forEach(ds::addPrimitive);
            ds.addPrimitive(way);
        }
        way1.firstNode().setOsmId(1, 1);
        assertFalse(way2.firstNode().hasKey("dupe"));
        Command command = missing.getCommand(Collections.singleton(way2));
        // The dupe key has to appear after making the command, since the command
        // was immediately run.
        assertTrue(way2.firstNode().hasKey("dupe"));
        // The change command will always replace its "undo" after every execution
        command.undoCommand();
        assertFalse(way2.firstNode().hasKey("dupe"));
        for (int i = 0; i < 10; i++) {
            command.executeCommand();
            assertTrue(way2.firstNode().hasKey("dupe"));
            assertEquals(way1.firstNode().getOsmPrimitiveId(),
                    SimplePrimitiveId.fromString(way2.firstNode().get("dupe")));
            command.undoCommand();
            assertFalse(way2.firstNode().hasKey("dupe"));
        }
    }

}
