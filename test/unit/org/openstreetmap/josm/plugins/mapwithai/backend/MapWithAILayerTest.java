// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAILayerTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences().main().projection();

    MapWithAILayer layer;

    @Before
    public void setUp() {
        layer = new MapWithAILayer(new DataSet(), "test", null);
    }

    @Test
    public void testGetSource() {
        Assert.assertNotNull(layer.getChangesetSourceTag());
        Assert.assertFalse(layer.getChangesetSourceTag().trim().isEmpty());
    }

    @Test
    public void testGetInfoComponent() {
        final Object tObject = layer.getInfoComponent();
        Assert.assertTrue(tObject instanceof JPanel);

        JPanel jPanel = (JPanel) tObject;
        final List<Component> startComponents = Arrays.asList(jPanel.getComponents());
        for (final Component comp : startComponents) {
            final JLabel label = (JLabel) comp;
            Assert.assertFalse(label.getText().contains("URL"));
            Assert.assertFalse(label.getText().contains("Maximum Additions"));
            Assert.assertFalse(label.getText().contains("Switch Layers"));
        }

        layer.setMapWithAIUrl("bad_url");
        layer.setMaximumAddition(0);
        layer.setSwitchLayers(false);

        jPanel = (JPanel) layer.getInfoComponent();
        final List<Component> currentComponents = Arrays.asList(jPanel.getComponents());

        for (final Component comp : currentComponents) {
            final JLabel label = (JLabel) comp;
            if (label.getText().contains("URL")) {
                Assert.assertEquals(tr("URL: {0}", "bad_url"), label.getText());
            } else if (label.getText().contains("Maximum Additions")) {
                Assert.assertEquals(tr("Maximum Additions: {0}", 0), label.getText());
            } else if (label.getText().contains("Switch Layers")) {
                Assert.assertEquals(tr("Switch Layers: {0}", false), label.getText());
            }
        }

    }
}
