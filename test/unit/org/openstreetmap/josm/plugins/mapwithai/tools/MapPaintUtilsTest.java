// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;

public class MapPaintUtilsTest {
    @RegisterExtension
    JOSMTestRules rule = new MapWithAITestRules().wiremock();

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
        MapCSSStyleSource mapcssSource = new MapCSSStyleSource(MapPaintUtils.getMapWithAIPaintStyle());
        mapcssSource.loadStyleSource();
        try (CachedFile file = new CachedFile(mapcssSource.url)) {
            file.clear();
            MapPaintUtils.addSourcesToPaintStyle(new DataSet());
        }
    }

}
