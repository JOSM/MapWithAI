// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDDataUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class RapiDDataUtilsTest {
	@Rule
	public JOSMTestRules test = new JOSMTestRules();

	/**
	 * This gets data from RapiD. This test may fail if someone adds the data to OSM.
	 */
	@Test
	public void testGetData() {
		BBox testBBox = new BBox();
		testBBox.add(new LatLon(39.0666521, -108.5707187));
		testBBox.add(new LatLon(39.0664016, -108.5702225));
		DataSet ds = new DataSet(RapiDDataUtils.getData(testBBox));
		Assert.assertEquals(1, ds.getWays().size());
	}
}
