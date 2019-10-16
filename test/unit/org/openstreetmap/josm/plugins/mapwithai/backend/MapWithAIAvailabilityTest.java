// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Territories;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIAvailabilityTest {
    private MapWithAIAvailability instance;

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        MapWithAIAvailability.setReleaseUrl(wireMockRule.baseUrl()
                + "/facebookmicrosites/Open-Mapping-At-Facebook/master/data/rapid_realeases.geojson");
        Territories.initialize();
        instance = MapWithAIAvailability.getInstance();
        LatLon temp = new LatLon(40, -100);
        await().atMost(10, TimeUnit.SECONDS).until(() -> Territories.isIso3166Code("US", temp));
    }

    @Test
    public void testHasDataBBox() {
        Assert.assertFalse(instance.hasData(new BBox(0, 0, 0.1, 0.1)));
        Assert.assertTrue(instance.hasData(new BBox(-99.9, 39.9, 100.1, 40.1)));
    }

    @Test
    public void testHasDataLatLon() {
        Assert.assertFalse(instance.hasData(new LatLon(0, 0)));
        Assert.assertTrue(instance.hasData(new LatLon(40, -100)));
        Assert.assertTrue(instance.hasData(new LatLon(45.424722, -75.695)));
        Assert.assertTrue(instance.hasData(new LatLon(19.433333, -99.133333)));
    }

    @Test
    public void testgetDataLatLon() {
        Assert.assertTrue(instance.getDataTypes(new LatLon(0, 0)).isEmpty());
        Assert.assertTrue(instance.getDataTypes(new LatLon(40, -100)).get("roads"));
        Assert.assertTrue(instance.getDataTypes(new LatLon(40, -100)).get("buildings"));
        Assert.assertFalse(instance.getDataTypes(new LatLon(45.424722, -75.695)).get("roads"));
        Assert.assertTrue(instance.getDataTypes(new LatLon(45.424722, -75.695)).get("buildings"));
        Assert.assertTrue(instance.getDataTypes(new LatLon(19.433333, -99.133333)).get("roads"));
        Assert.assertFalse(instance.getDataTypes(new LatLon(19.433333, -99.133333)).get("buildings"));
    }
}
