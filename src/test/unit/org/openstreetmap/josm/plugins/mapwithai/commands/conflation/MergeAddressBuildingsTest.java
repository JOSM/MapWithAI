// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands.conflation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.commands.MergeAddressBuildings;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Command
@BasicPreferences
class MergeAddressBuildingsTest {
    @RegisterExtension
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    JOSMTestRules test = new JOSMTestRules().projection().main();

    private MergeAddressBuildings command;
    private DataSet ds;

    @BeforeEach
    void setUp() {
        ds = new DataSet();
        command = new MergeAddressBuildings(ds);
    }

    @Test
    void testGetInterestedTypes() {
        Collection<Class<? extends OsmPrimitive>> primitiveTypes = command.getInterestedTypes();
        assertEquals(2, primitiveTypes.size());
        assertTrue(primitiveTypes.contains(Way.class));
        assertTrue(primitiveTypes.contains(Relation.class));
    }

    @Test
    void testGetKey() {
        assertEquals("building", command.getKey());
    }

    @Test
    void testGetRealCommand() {
        // Not yet closed building
        Way building = TestUtils.newWay("building=yes", new Node(new LatLon(0, 0)), new Node(new LatLon(1, 0)),
                new Node(new LatLon(1, 1)), new Node(new LatLon(0, 1)));
        Node address = TestUtils.newNode("addr:street=None addr:housenumber=2");
        building.getNodes().forEach(ds::addPrimitive);
        MainApplication.getLayerManager()
                .addLayer(new OsmDataLayer(ds, "required for ReplaceGeometry in utilsplugin2", null));
        ds.addPrimitive(building);
        ds.addPrimitive(address);

        command = new MergeAddressBuildings(ds);
        address.setCoor(new LatLon(0.5, 0.5));
        Command newCommand = command.getCommand(Arrays.asList(building));
        assertNull(newCommand);

        building.addNode(building.firstNode());
        newCommand = command.getCommand(Arrays.asList(building));

        newCommand.executeCommand();

        assertEquals(5, ds.allNonDeletedPrimitives().size());
        newCommand.undoCommand();
        assertEquals(6, ds.allNonDeletedPrimitives().size());

        address.setCoor(new LatLon(-1, -1));

        newCommand = command.getCommand(Arrays.asList(building));
        assertNull(newCommand);
        assertEquals(6, ds.allNonDeletedPrimitives().size());

        address.setCoor(new LatLon(.75, .75));

        Node address2 = new Node(new LatLon(0.25, 0.25));
        address.getKeys().forEach((key, value) -> address2.put(key, value));
        ds.addPrimitive(address2);

        newCommand = command.getCommand(Arrays.asList(building));
        assertNull(newCommand);
        assertEquals(7, ds.allNonDeletedPrimitives().size());
    }

    @Test
    void testGetDescriptionText() {
        assertEquals(tr("Merge added buildings with existing address nodes"), command.getDescriptionText());
    }

}
