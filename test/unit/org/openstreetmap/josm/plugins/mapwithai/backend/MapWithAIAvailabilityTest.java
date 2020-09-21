// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.awaitility.Durations;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Territories;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIAvailabilityTest {
    private DataAvailability instance;

    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new MapWithAITestRules().sources().wiremock().projection().territories();

    @Before
    public void setUp() {
        instance = DataAvailability.getInstance();
        LatLon temp = new LatLon(40, -100);
        await().atMost(Durations.TEN_SECONDS).until(() -> Territories.isIso3166Code("US", temp));
    }

    @Test
    public void testHasDataBBox() {
        assertFalse(instance.hasData(new Bounds(0, 0, 0.1, 0.1)), "There shouldn't be data in the ocean");
        assertTrue(instance.hasData(new Bounds(39.9, -99.9, 40.1, 100.1)), "There should be data in the US");
    }

    @Test
    public void testHasDataLatLon() {
        assertFalse(instance.hasData(new LatLon(0, 0)), "There should not be data in the ocean");
        assertTrue(instance.hasData(new LatLon(40, -100)), "There should be data in the US");
        assertTrue(instance.hasData(new LatLon(45.424722, -75.695)), "There should be data in Canada");
        assertTrue(instance.hasData(new LatLon(19.433333, -99.133333)), "There should be data in Mexico");
    }

    @Test
    public void testgetDataLatLon() {
        assertTrue(DataAvailability.getDataTypes(new LatLon(0, 0)).isEmpty(), "There should not be data in the ocean");
        assertTrue(DataAvailability.getDataTypes(new LatLon(40, -100)).getOrDefault("highway", false),
                "The US should have highway data");
        assertTrue(DataAvailability.getDataTypes(new LatLon(40, -100)).getOrDefault("building", false),
                "The US should have building data");
        assertFalse(DataAvailability.getDataTypes(new LatLon(39.1, 125.7)).getOrDefault("highway", false),
                "North Korea should not have highway data");
        assertFalse(DataAvailability.getDataTypes(new LatLon(39.1, 125.7)).getOrDefault("building", false),
                "North Korea does not have building data");
        assertTrue(DataAvailability.getDataTypes(new LatLon(19.433333, -99.133333)).getOrDefault("highway", false),
                "Mexico has highway data");
        assertFalse(DataAvailability.getDataTypes(new LatLon(19.433333, -99.133333)).getOrDefault("building", false),
                "Mexico does not yet have building data");
    }

    @Test
    public void testNoURLs() {
        new ArrayList<>(MapWithAILayerInfo.getInstance().getLayers())
                .forEach(i -> MapWithAILayerInfo.getInstance().remove(i));
        DataAvailability.getInstance();
        testgetDataLatLon();
        MapWithAILayerInfo.getInstance().getLayers().forEach(i -> MapWithAILayerInfo.getInstance().remove(i));
        DataAvailability.getInstance();
        testHasDataLatLon();
        MapWithAILayerInfo.getInstance().getLayers().forEach(i -> MapWithAILayerInfo.getInstance().remove(i));
        DataAvailability.getInstance();
        testHasDataBBox();
    }

    @Test
    public void testGetPrivacyUrls() {
        assertFalse(DataAvailability.getPrivacyPolicy().isEmpty());
    }

    @Test
    public void testGetTOSUrls() {
        assertFalse(DataAvailability.getTermsOfUse().isEmpty());
    }

    @Test
    public void testDefaultUrlImplementations() {
        DataAvailability instance = DataAvailability.getInstance();
        assertNull(instance.getUrl());
        assertEquals("", instance.getPrivacyPolicyUrl());
        assertEquals("", instance.getTermsOfUseUrl());
    }
}
