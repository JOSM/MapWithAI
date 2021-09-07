// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.actions.AddMapWithAILayerAction;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.Logging;

/**
 * Tests for {@link MapWithAIMenu}
 *
 * @author Taylor Smock
 *
 */
@BasicPreferences
@Wiremock
@MapWithAISources
class MapWithAIMenuTest {
    @RegisterExtension
    static JOSMTestRules rule = new MapWithAITestRules().territories().projection().main();
    private static MapWithAIMenu menu;

    @BeforeAll
    static void setUp() {
        menu = new MapWithAIMenu();
    }

    @Test
    void testMapView() {
        menu.setSelected(true);
        assertEquals(1, getActiveActions(menu).size());
        menu.setSelected(false);
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(new DataSet(), "TEST", null));
        AutoScaleAction.zoomTo(Collections.singleton(TestUtils.newNode("")));
        menu.setSelected(true);
        assertEquals(1, getActiveActions(menu).size());
        menu.setSelected(false);

        AutoScaleAction.zoomTo(Collections.singleton(new Node(new LatLon(39, -108))));
        menu.setSelected(true);
        assertEquals(2, getActiveActions(menu).size());
        menu.setSelected(false);

    }

    private static List<AddMapWithAILayerAction> getActiveActions(MapWithAIMenu menu) {
        LinkedHashSet<AddMapWithAILayerAction> list = new LinkedHashSet<>();
        for (Field field : MapWithAIMenu.class.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object obj = field.get(menu);
                if (obj instanceof Collection) {
                    Collection<?> objCollection = (Collection<?>) obj;
                    objCollection.stream().filter(AddMapWithAILayerAction.class::isInstance)
                            .map(AddMapWithAILayerAction.class::cast).forEach(list::add);
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                Logging.error(e);
            }
        }
        return new ArrayList<>(list);
    }
}
