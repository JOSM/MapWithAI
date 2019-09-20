// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDAddCommand;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDAddComandTest {
	@Rule
	public JOSMTestRules test = new JOSMTestRules();

	@Test
	public void testMoveCollection() {
		DataSet ds1 = new DataSet();
		DataSet ds2 = new DataSet();
		Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)),
				new Node(new LatLon(0, 0.1)));
		for (Node node : way1.getNodes()) {
			ds1.addPrimitive(node);
		}
		ds1.addPrimitive(way1);
		ds1.lock();
		RapiDAddCommand command = new RapiDAddCommand(ds1, ds2, Collections.singleton(way1));
		command.executeCommand();
		Assert.assertTrue(ds2.containsWay(way1));
		Assert.assertTrue(ds2.containsNode(way1.firstNode()));
		Assert.assertTrue(ds2.containsNode(way1.lastNode()));
		// Assert.assertFalse(ds1.containsWay(way1)); // Only if we delete ways from
		// rapid dataset
	}

	@Test
	public void testAddPrimitivesToCollection() {
		Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
		Collection<OsmPrimitive> collection = new TreeSet<>();
		Assert.assertEquals(0, collection.size());
		RapiDAddCommand.addPrimitivesToCollection(collection, Collections.singletonList(way1));
		Assert.assertEquals(3, collection.size());
	}

	@Test
	public void testCreateConnections() {
		DataSet ds1 = new DataSet();
		Way way1 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0)), new Node(new LatLon(0, 0.1)));
		Way way2 = TestUtils.newWay("highway=residential", new Node(new LatLon(0, 0.05)),
				new Node(new LatLon(0.05, 0.2)));
		way2.firstNode().put("conn",
				"w".concat(Long.toString(way1.getUniqueId())).concat(",n")
				.concat(Long.toString(way1.firstNode().getUniqueId())).concat(",n")
				.concat(Long.toString(way1.lastNode().getUniqueId())));
		way1.getNodes().forEach(node -> ds1.addPrimitive(node));
		way2.getNodes().forEach(node -> ds1.addPrimitive(node));
		ds1.addPrimitive(way2);
		ds1.addPrimitive(way1);
		RapiDAddCommand.createConnections(ds1, Collections.singletonList(way2.firstNode()));
		Assert.assertEquals(3, way1.getNodesCount());
		Assert.assertFalse(way1.isFirstLastNode(way2.firstNode()));
	}
}
