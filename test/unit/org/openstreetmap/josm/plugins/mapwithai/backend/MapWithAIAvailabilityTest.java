// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;

import org.awaitility.Durations;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Territories;

import com.github.tomakehurst.wiremock.WireMockServer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIAvailabilityTest {
    private DataAvailability instance;

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().projection();

    WireMockServer wireMock = new WireMockServer(options().usingFilesUnderDirectory("test/resources/wiremock"));

    @Before
    public void setUp() {
        wireMock.start();
        MapWithAIAvailability.setReleaseUrl(
                wireMock.baseUrl() + "/facebookmicrosites/Open-Mapping-At-Facebook/master/data/rapid_releases.geojson");
        Territories.initialize();
        instance = DataAvailability.getInstance();
        LatLon temp = new LatLon(40, -100);
        await().atMost(Durations.TEN_SECONDS).until(() -> Territories.isIso3166Code("US", temp));
    }

    @After
    public void tearDown() {
        wireMock.stop();
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
        Assert.assertTrue(DataAvailability.getDataTypes(new LatLon(0, 0)).isEmpty());
        Assert.assertTrue(DataAvailability.getDataTypes(new LatLon(40, -100)).getOrDefault("highway", false));
        Assert.assertTrue(DataAvailability.getDataTypes(new LatLon(40, -100)).getOrDefault("building", false));
        Assert.assertFalse(
                DataAvailability.getDataTypes(new LatLon(45.424722, -75.695)).getOrDefault("highway", false));
        Assert.assertTrue(
                DataAvailability.getDataTypes(new LatLon(45.424722, -75.695)).getOrDefault("building", false));
        Assert.assertTrue(
                DataAvailability.getDataTypes(new LatLon(19.433333, -99.133333)).getOrDefault("highway", false));
        Assert.assertFalse(
                DataAvailability.getDataTypes(new LatLon(19.433333, -99.133333)).getOrDefault("building", false));
    }
}
