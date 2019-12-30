package org.openstreetmap.josm.plugins.mapwithai.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.MergeAddressBuildings;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MergeAddressBuildingsTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().projection().main();

    private MergeAddressBuildings command;
    private DataSet ds;

    @Before
    public void setUp() {
        ds = new DataSet();
        command = new MergeAddressBuildings(ds);
    }

    @Test
    public void testGetInterestedTypes() {
        Collection<Class<? extends OsmPrimitive>> primitiveTypes = command.getInterestedTypes();
        Assert.assertEquals(2, primitiveTypes.size());
        Assert.assertTrue(primitiveTypes.contains(Way.class));
        Assert.assertTrue(primitiveTypes.contains(Relation.class));
    }

    @Test
    public void testGetKey() {
        Assert.assertEquals("building", command.getKey());
    }

    @Test
    public void testGetRealCommand() {
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
        Assert.assertNull(newCommand);

        building.addNode(building.firstNode());
        newCommand = command.getCommand(Arrays.asList(building));

        newCommand.executeCommand();

        Assert.assertEquals(5, ds.allNonDeletedPrimitives().size());
        newCommand.undoCommand();
        Assert.assertEquals(6, ds.allNonDeletedPrimitives().size());

        address.setCoor(new LatLon(-1, -1));

        newCommand = command.getCommand(Arrays.asList(building));
        Assert.assertNull(newCommand);
        Assert.assertEquals(6, ds.allNonDeletedPrimitives().size());

        address.setCoor(new LatLon(.75, .75));

        Node address2 = new Node(new LatLon(0.25, 0.25));
        address.getKeys().forEach((key, value) -> address2.put(key, value));
        ds.addPrimitive(address2);

        newCommand = command.getCommand(Arrays.asList(building));
        Assert.assertNull(newCommand);
        Assert.assertEquals(7, ds.allNonDeletedPrimitives().size());
    }

    @Test
    public void testGetDescriptionText() {
        Assert.assertEquals(tr("Merge added buildings with existing address nodes"), command.getDescriptionText());
    }

}
