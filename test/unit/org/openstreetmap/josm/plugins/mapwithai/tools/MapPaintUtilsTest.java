// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSetting;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;

public class MapPaintUtilsTest {
    @RegisterExtension
    MapWithAITestRules rule = (MapWithAITestRules) new MapWithAITestRules().wiremock().projection();

    @Test
    public void testAddPaintStyle() {
        MapPaintUtils.removeMapWithAIPaintStyles();
        Awaitility.await().atMost(Durations.TEN_SECONDS).until(() -> !MapPaintUtils.checkIfMapWithAIPaintStyleExists());
        List<StyleSource> paintStyles = MapPaintStyles.getStyles().getStyleSources();
        for (int i = 0; i < 10; i++) {
            MapPaintUtils.addMapWithAIPaintStyles();
            paintStyles = MapPaintStyles.getStyles().getStyleSources();
            assertEquals(1, paintStyles.stream().filter(s -> s.title.contains("MapWithAI")).count(),
                    "The paintstyle should have been added, but only one of it");
        }
    }

    @Test
    public void testStableSource() throws IOException {
        MapPaintUtils.removeMapWithAIPaintStyles();
        MapPaintUtils.addMapWithAIPaintStyles();
        MapCSSStyleSource mapcssSource = (MapCSSStyleSource) MapPaintUtils.getMapWithAIPaintStyle();
        try (CachedFile file = new CachedFile(mapcssSource.url)) {
            file.clear();
            MapPaintUtils.removeMapWithAIPaintStyles();
            MapPaintUtils.addMapWithAIPaintStyles();
            mapcssSource = (MapCSSStyleSource) MapPaintUtils.getMapWithAIPaintStyle();

            DataSet ds = new DataSet();
            MapPaintUtils.addSourcesToPaintStyle(ds);
            for (int i = 0; i < 10; i++) {
                ds.addPrimitive(TestUtils.newNode("source=digitalglobe"));
                ds.addPrimitive(TestUtils.newNode("source=TestSource"));
                MapPaintUtils.addSourcesToPaintStyle(ds);
                assertEquals(1, countLabels(mapcssSource, "digitalglobe"));
                assertEquals(1, countLabels(mapcssSource, "TestSource"));
            }
            Color color1digitalglobe = getColorStyleSetting(mapcssSource, "digitalglobe").getValue();
            Color color1TestSource = getColorStyleSetting(mapcssSource, "TestSource").getValue();
            file.clear();
            MapPaintUtils.removeMapWithAIPaintStyles();
            MapPaintUtils.addMapWithAIPaintStyles();
            mapcssSource = (MapCSSStyleSource) MapPaintUtils.getMapWithAIPaintStyle();
            MapPaintUtils.addSourcesToPaintStyle(ds);
            assertEquals(color1digitalglobe, getColorStyleSetting(mapcssSource, "digitalglobe").getValue());
            assertEquals(color1TestSource, getColorStyleSetting(mapcssSource, "TestSource").getValue());

        }
    }

    private static long countLabels(MapCSSStyleSource source, String label) {
        if (source == null || source.settings == null) {
            return -1;
        }
        return source.settings.stream().filter(StyleSetting.PropertyStyleSetting.class::isInstance)
                .map(StyleSetting.PropertyStyleSetting.class::cast)
                .filter(l -> l.label != null && l.label.contains(label)).count();
    }

    private static StyleSetting.ColorStyleSetting getColorStyleSetting(MapCSSStyleSource source, String label) {
        return source.settings.stream().filter(StyleSetting.ColorStyleSetting.class::isInstance)
                .map(StyleSetting.ColorStyleSetting.class::cast).filter(l -> l.label != null && l.label.contains(label))
                .findFirst().orElse(null);
    }

}
