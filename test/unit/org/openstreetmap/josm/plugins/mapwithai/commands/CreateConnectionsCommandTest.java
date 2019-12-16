// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
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
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DuplicateCommand;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import com.github.tomakehurst.wiremock.WireMockServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for {@link CreateConnections}
 *
 * @author Taylor Smock
 */
public class CreateConnectionsCommandTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        Config.getPref().put("osm-server.url", wireMock.baseUrl());
    }

    @After
    public void tearDown() {
        wireMock.stop();
    }

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

        assertFalse(dataSet.isModified(), "DataSet shouldn't be modified yet");
        createConnections.undoCommand();
        assertFalse(dataSet.isModified(), "DataSet shouldn't be modified yet");

        node3.put(ConnectedCommand.CONN_KEY,
                "w" + way.getUniqueId() + ",n" + node1.getUniqueId() + ",n" + node2.getUniqueId());
        createConnections = new CreateConnectionsCommand(dataSet, Collections.singleton(node3));
        createConnections.executeCommand();
        assertTrue(dataSet.isModified(), "DataSet should be modified");
        assertEquals(3, way.getNodesCount(), "The way should have three nodes");
        assertFalse(node3.hasKey(ConnectedCommand.CONN_KEY), "There should be no conn key");
        createConnections.fillModifiedData(modified, deleted, added);
        assertEquals(3, modified.size(),
                "There should be three modifications (the connecting way, the node, and the node again for key removal)");
        assertTrue(deleted.isEmpty(), "Nothing has been deleted");
        assertTrue(added.isEmpty(), "Nothing has been added");
        createConnections.undoCommand();
        assertFalse(dataSet.isModified(), "DataSet is no longer modified");
        assertEquals(2, way.getNodesCount(), "The way should have two nodes again");
        assertTrue(node3.hasKey(ConnectedCommand.CONN_KEY), "The conn key should exist again");

        dupe.put(DuplicateCommand.DUPE_KEY, "n" + node1.getUniqueId());
        createConnections = new CreateConnectionsCommand(dataSet, Collections.singleton(dupe));
        createConnections.executeCommand();
        assertTrue(dataSet.isModified(), "The DataSet should be modified");
        assertEquals(2, way.getNodesCount(), "The way should have two nodes");
        assertFalse(node1.hasKey(DuplicateCommand.DUPE_KEY), "There should no longer be a dupe key");
        modified.clear();
        createConnections.fillModifiedData(modified, deleted, added);
        assertEquals(2, modified.size(), "We removed a node and modified a way");
        assertTrue(deleted.isEmpty(), "Nothing was truly deleted (the dupe node doesn't count)");
        assertTrue(added.isEmpty(), "Nothing was added");
        createConnections.undoCommand();
        assertFalse(node1.hasKey(DuplicateCommand.DUPE_KEY), "The original node should not have the dupe key");
        assertTrue(dupe.hasKey(DuplicateCommand.DUPE_KEY), "The dupe node should have the dupe key");
        assertFalse(dataSet.isModified(), "The DataSet is no longer modified");
        assertEquals(2, way.getNodesCount(), "The way still has two nodes");
    }

    /**
     * Test method for
     * {@link CreateConnectionsCommand#addNodesToWay(Node, Node, Node, Node)}.
     */
    @Test
    public void testAddNodesToWay() {
        final Node wayNode1 = new Node(new LatLon(0, 0));
        final Node wayNode2 = new Node(new LatLon(1, 0));
        final Node toAddNode = new Node(new LatLon(0.5, 0));
        final Way way = TestUtils.newWay("highway=residential", wayNode1, wayNode2);
        new DataSet(wayNode1, wayNode2, toAddNode, way);
        Command addNodeToWayCommand = ConnectedCommand.addNodesToWay(toAddNode, way, wayNode1, wayNode2);
        assertEquals(2, way.getNodesCount(), "The way should still have 2 nodes");
        addNodeToWayCommand.executeCommand();
        assertEquals(3, way.getNodesCount(), "The way should now have 3 nodes");
        addNodeToWayCommand.undoCommand();
        assertEquals(2, way.getNodesCount(), "The way should be back to having 2 nodes");

        wayNode2.setCoor(new LatLon(1, 0.1));
        addNodeToWayCommand = ConnectedCommand.addNodesToWay(toAddNode, way, wayNode1, wayNode2);
        assertNull(addNodeToWayCommand, "There shouldn't be a command to for a node that is a fair distance away");

        wayNode2.setCoor(new LatLon(1, 0.01));
        addNodeToWayCommand = ConnectedCommand.addNodesToWay(toAddNode, way, wayNode1, wayNode2);
        assertNull(addNodeToWayCommand, "There shouldn't be a command to for a node that is a fair distance away");

        wayNode2.setCoor(new LatLon(1, 0.00008));
        addNodeToWayCommand = ConnectedCommand.addNodesToWay(toAddNode, way, wayNode1, wayNode2);
        addNodeToWayCommand.executeCommand();
        assertEquals(3, way.getNodesCount(), "The way should have 3 nodes now");
        addNodeToWayCommand.undoCommand();
        assertEquals(2, way.getNodesCount(), "The way should now have 2 nodes");
    }

    /**
     * Test method for {@link CreateConnectionsCommand#replaceNode(Node, Node)}.
     */
    @Test
    public void testReplaceNode() {
        final Node node1 = new Node(new LatLon(0, 0));
        final Node node2 = new Node(new LatLon(0, 0));
        new DataSet(node1, node2);
        final Command replaceNodeCommand = DuplicateCommand.replaceNode(node1, node2);
        replaceNodeCommand.executeCommand();
        assertTrue(node1.isDeleted(), "The node should not exist anymore");
        replaceNodeCommand.undoCommand();
        assertFalse(node1.isDeleted(), "The node should exist again");

        node2.setCoor(new LatLon(0.1, 0.1));
        assertNull(DuplicateCommand.replaceNode(node1, node2),
                "There should not be a command for nodes with a large distance");
    }

    /**
     * Test if we get missing primitives
     */
    @Test
    public void testGetMissingPrimitives() {
        final Node node1 = new Node(new LatLon(39.0674124, -108.5592645));
        final DataSet dataSet = new DataSet(node1);
        node1.put(DuplicateCommand.DUPE_KEY, "n6146500887");
        Command replaceNodeCommand = CreateConnectionsCommand.createConnections(dataSet, Collections.singleton(node1));

        replaceNodeCommand.executeCommand();
        assertEquals(1, dataSet.allNonDeletedPrimitives().size(), "There should be one primitive left");
        assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE),
                "The OSM primitive should be the remaining primitive");

        replaceNodeCommand.undoCommand();
        assertEquals(2, dataSet.allNonDeletedPrimitives().size(), "We don't roll back downloaded data");
        assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE),
                "We don't roll back downloaded data");

        node1.setCoor(new LatLon(39.067399, -108.5608433));
        node1.put(DuplicateCommand.DUPE_KEY, "n6151680832");
        final OsmDataLayer layer = new OsmDataLayer(dataSet, "temp layer", null);
        MainApplication.getLayerManager().addLayer(layer);

        replaceNodeCommand = CreateConnectionsCommand.createConnections(dataSet, Collections.singleton(node1));
        replaceNodeCommand.executeCommand();
        assertEquals(2, dataSet.allNonDeletedPrimitives().size(), "The dupe node no longer matches with the OSM node");
        assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE), "The OSM node should still exist");

        replaceNodeCommand.undoCommand();
        assertEquals(3, dataSet.allNonDeletedPrimitives().size(), "We don't roll back downloaded data");
        assertNotNull(dataSet.getPrimitiveById(6146500887L, OsmPrimitiveType.NODE),
                "We don't roll back downloaded data");

    }

    /**
     * Test method for {@link CreateConnectionsCommand#getDescriptionText()}.
     */
    @Test
    public void testGetDescriptionText() {
        final String text = new CreateConnectionsCommand(new DataSet(), Collections.emptyList()).getDescriptionText();
        assertNotNull(text, "There should be a description for the command");
        assertFalse(text.isEmpty(), "The description should not be an empty string");
    }
}
