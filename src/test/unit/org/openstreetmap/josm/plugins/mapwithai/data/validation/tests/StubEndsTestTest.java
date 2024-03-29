// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.testutils.annotations.Projection;

@Projection
class StubEndsTestTest {
    private Way nonStaticWay;
    private Way staticWay;
    private StubEndsTest tester;

    @BeforeEach
    void setUp() {
        staticWay = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
                new Node(new LatLon(0.01, 0.01)));
        DataSet ds = new DataSet();
        staticWay.getNodes().forEach(ds::addPrimitive);
        ds.addPrimitive(staticWay);

        nonStaticWay = TestUtils.newWay("highway=residential", new Node(new LatLon(0.010001, 0.010001)),
                staticWay.lastNode(), new Node(new LatLon(1, 2)));
        nonStaticWay.getNodes().stream().filter(node -> node.getDataSet() == null).forEach(ds::addPrimitive);
        ds.addPrimitive(nonStaticWay);

        tester = new StubEndsTest();
        tester.startTest(NullProgressMonitor.INSTANCE);
    }

    @AfterEach
    void tearDown() {
        tester.endTest();
    }

    @Test
    void testStartEnd() {
        tester.visit(staticWay);
        assertTrue(tester.getErrors().isEmpty());

        tester.visit(nonStaticWay);
        assertFalse(tester.getErrors().isEmpty());
        Node toDelete = nonStaticWay.getNode(0);
        tester.getErrors().get(0).getFix().executeCommand();
        assertTrue(toDelete.isDeleted());
        assertEquals(2, nonStaticWay.getNodesCount());
    }

    @Test
    void testEndEnd() {
        List<Node> nodes = nonStaticWay.getNodes();
        Collections.reverse(nodes);
        nonStaticWay.setNodes(nodes);

        tester.visit(staticWay);
        assertTrue(tester.getErrors().isEmpty());

        tester.visit(nonStaticWay);
        assertFalse(tester.getErrors().isEmpty());
        Node toDelete = nonStaticWay.lastNode();
        tester.getErrors().get(0).getFix().executeCommand();
        assertTrue(toDelete.isDeleted());
        assertEquals(2, nonStaticWay.getNodesCount());
    }
}
