// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 */
@BasicPreferences
class MergeDuplicateWaysActionTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules rules = new JOSMTestRules().projection().main();

    MergeDuplicateWaysAction action;

    @BeforeEach
    void setUp() {
        action = new MergeDuplicateWaysAction();
    }

    @Test
    void testActionPerformed() {
        action.actionPerformed(null);
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "Test Layer", null));
        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
        action.actionPerformed(null);
        UndoRedoHandler.getInstance().undo();

        Way way1 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 1)));
        Way way2 = TestUtils.newWay("", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 1)));
        Way way3 = TestUtils.newWay("", new Node(new LatLon(1, 1)), new Node(new LatLon(2, 1)));
        for (Way way : Arrays.asList(way1, way2, way3)) {
            way.getNodes().forEach(ds::addPrimitive);
            ds.addPrimitive(way);
        }

        runCommand(way1, way2);

        ds.setSelected(way1);
        runCommand(way1, way2);

        ds.addSelected(Collections.singleton(way2));
        runCommand(way1, way2);

        ds.addSelected(Collections.singleton(way3));
        action.actionPerformed(null);
        assertFalse(UndoRedoHandler.getInstance().hasUndoCommands());
    }

    private void runCommand(Way way1, Way way2) {
        action.actionPerformed(null);
        assertNotEquals(way1.isDeleted(), way2.isDeleted());
        UndoRedoHandler.getInstance().undo();
        assertEquals(way1.isDeleted(), way2.isDeleted());
        assertFalse(way1.isDeleted());
    }

    @Test
    void testUpdateEnabledState() {
        assertFalse(action.isEnabled());
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "Test Layer", null));
        assertTrue(action.isEnabled());
    }
}
