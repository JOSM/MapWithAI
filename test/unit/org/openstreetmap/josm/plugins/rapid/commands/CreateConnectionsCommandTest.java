// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * Tests for {@link CreateConnections}
 *
 * @author Taylor Smock
 */
public class CreateConnectionsCommandTest {
    @Rule
    public JOSMTestRules test = new JOSMTestRules().projection();

    /**
     * Test method for
     * {@link CreateConnectionsCommand#createConnections(DataSet, Collection)}.
     */
    @Test
    public void testCreateConnections() {
        final Node node1 = new Node(new LatLon(0, 0));
        final Node node2 = new Node(new LatLon(1, 0));
        final Node node3 = new Node(new LatLon(0.5, 0));
        final Node dupe = new Node(new LatLon(0, 0));
        final Way way = TestUtils.newWay("highway=residential", node1, node2);
        final Collection<OsmPrimitive> added = new ArrayList<>();
        final Collection<OsmPrimitive> modified = new ArrayList<>();
        final Collection<OsmPrimitive> deleted = new ArrayList<>();
        final DataSet dataSet = new DataSet(node1, node2, node3, dupe, way);

        CreateConnectionsCommand createConnections = new CreateConnectionsCommand(dataSet,
                Collections.singleton(node3));
        createConnections.executeCommand();

        Assert.assertFalse(dataSet.isModified());
        createConnections.undoCommand();
        Assert.assertFalse(dataSet.isModified());

        node3.put(CreateConnectionsCommand.CONN_KEY,
                "w" + way.getUniqueId() + ",n" + node1.getUniqueId() + ",n" + node2.getUniqueId());
        createConnections = new CreateConnectionsCommand(dataSet, Collections.singleton(node3));
        createConnections.executeCommand();
        Assert.assertTrue(dataSet.isModified());
        Assert.assertEquals(3, way.getNodesCount());
        Assert.assertFalse(node3.hasKey(CreateConnectionsCommand.CONN_KEY));
        createConnections.fillModifiedData(modified, deleted, added);
        Assert.assertEquals(3, modified.size()); // 3 since we remove the key from the node
        Assert.assertTrue(deleted.isEmpty());
        Assert.assertTrue(added.isEmpty());
        createConnections.undoCommand();
        Assert.assertFalse(dataSet.isModified());
        Assert.assertEquals(2, way.getNodesCount());
        Assert.assertTrue(node3.hasKey(CreateConnectionsCommand.CONN_KEY));

        dupe.put(CreateConnectionsCommand.DUPE_KEY, "n" + node1.getUniqueId());
        createConnections = new CreateConnectionsCommand(dataSet, Collections.singleton(dupe));
        createConnections.executeCommand();
        Assert.assertTrue(dataSet.isModified());
        Assert.assertEquals(2, way.getNodesCount());
        Assert.assertFalse(node1.hasKey(CreateConnectionsCommand.DUPE_KEY));
        modified.clear();
        createConnections.fillModifiedData(modified, deleted, added);
        Assert.assertEquals(2, modified.size());
        Assert.assertTrue(deleted.isEmpty());
        Assert.assertTrue(added.isEmpty());
        createConnections.undoCommand();
        Assert.assertFalse(node1.hasKey(CreateConnectionsCommand.DUPE_KEY));
        Assert.assertTrue(dupe.hasKey(CreateConnectionsCommand.DUPE_KEY));
        Assert.assertFalse(dataSet.isModified());
        Assert.assertEquals(2, way.getNodesCount());
    }

    /**
     * Test method for
     * {@link CreateConnectionsCommand#addNodesToWay(Node, Node, Node, Node)}.
     */
    @Test
    public void testAddNodesToWay() {
        final Node node1 = new Node(new LatLon(0, 0));
        final Node node2 = new Node(new LatLon(1, 0));
        final Node node3 = new Node(new LatLon(0.5, 0));
        final Way way = TestUtils.newWay("highway=residential", node1, node2);
        new DataSet(node1, node2, node3, way);
        Command addNodeToWayCommand = CreateConnectionsCommand.addNodesToWay(node3, way, node1, node2);
        Assert.assertEquals(2, way.getNodesCount());
        addNodeToWayCommand.executeCommand();
        Assert.assertEquals(3, way.getNodesCount());
        addNodeToWayCommand.undoCommand();
        Assert.assertEquals(2, way.getNodesCount());

        node2.setCoor(new LatLon(1, 0.1));
        addNodeToWayCommand = CreateConnectionsCommand.addNodesToWay(node3, way, node1, node2);
        Assert.assertNull(addNodeToWayCommand);

        node2.setCoor(new LatLon(1, 0.01));
        addNodeToWayCommand = CreateConnectionsCommand.addNodesToWay(node3, way, node1, node2);
        Assert.assertNull(addNodeToWayCommand);

        node2.setCoor(new LatLon(1, 0.00008));
        addNodeToWayCommand = CreateConnectionsCommand.addNodesToWay(node3, way, node1, node2);
        addNodeToWayCommand.executeCommand();
        Assert.assertEquals(3, way.getNodesCount());
        addNodeToWayCommand.undoCommand();
        Assert.assertEquals(2, way.getNodesCount());
    }

    /**
     * Test method for {@link CreateConnectionsCommand#replaceNode(Node, Node)}.
     */
    @Test
    public void testReplaceNode() {
        final Node node1 = new Node(new LatLon(0, 0));
        final Node node2 = new Node(new LatLon(0, 0));
        new DataSet(node1, node2);
        final Command replaceNodeCommand = CreateConnectionsCommand.replaceNode(node1, node2);
        replaceNodeCommand.executeCommand();
        Assert.assertTrue(node1.isDeleted());
        replaceNodeCommand.undoCommand();
        Assert.assertFalse(node1.isDeleted());

        node2.setCoor(new LatLon(0.1, 0.1));
        Assert.assertNull(CreateConnectionsCommand.replaceNode(node1, node2));
    }

    /**
     * Test if we get missing primitives
     */
    @Test
    public void testGetMissingPrimitives() {
        final Node node1 = new Node(new LatLon(39.0674124, -108.5592645));
        final DataSet dataSet = new DataSet(node1);
        node1.put(CreateConnectionsCommand.DUPE_KEY, "n6146500887");
        Command replaceNodeCommand = CreateConnectionsCommand.createConnections(dataSet,
                Collections.singleton(node1));

        replaceNodeCommand.executeCommand();
        Assert.assertEquals(1, dataSet.allNonDeletedPrimitives().size());
        Assert.assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE));

        replaceNodeCommand.undoCommand();
        Assert.assertEquals(2, dataSet.allNonDeletedPrimitives().size()); // We don't roll back downloaded data
        Assert.assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE));

        node1.setCoor(new LatLon(39.067399, -108.5608433));
        node1.put(CreateConnectionsCommand.DUPE_KEY, "n6151680832");
        OsmDataLayer layer = new OsmDataLayer(dataSet, "temp layer", null);
        MainApplication.getLayerManager().addLayer(layer);

        replaceNodeCommand = CreateConnectionsCommand.createConnections(dataSet, Collections.singleton(node1));
        replaceNodeCommand.executeCommand();
        Assert.assertEquals(2, dataSet.allNonDeletedPrimitives().size());
        Assert.assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE));

        replaceNodeCommand.undoCommand();
        Assert.assertEquals(3, dataSet.allNonDeletedPrimitives().size()); // We don't roll back downloaded data
        Assert.assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE));

    }

    /**
     * Test method for {@link CreateConnectionsCommand#getDescriptionText()}.
     */
    @Test
    public void testGetDescriptionText() {
        final String text = new CreateConnectionsCommand(new DataSet(), null).getDescriptionText();
        Assert.assertNotNull(text);
        Assert.assertFalse(text.isEmpty());
    }

}
