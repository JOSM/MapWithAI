// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIActionTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().main().projection();

    private MapWithAIAction action;

    @Before
    public void setUp() {
        action = new MapWithAIAction();
    }

    @Test
    public void testEnabled() {
        Assert.assertFalse(action.isEnabled());
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "temporary", null));
        Assert.assertTrue(action.isEnabled());
    }

    @Test
    public void testDownload() {
        Assert.assertTrue(MainApplication.getLayerManager().getLayers().isEmpty());
        action.actionPerformed(null);
        Assert.assertTrue(MainApplication.getLayerManager().getLayers().isEmpty());

        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "temporary", null));
        action.actionPerformed(null);
        Awaitility.await().timeout(8, TimeUnit.SECONDS)
                .until(() -> 1 == MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).size());
        Assert.assertEquals(1, MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class).size());
    }
}
