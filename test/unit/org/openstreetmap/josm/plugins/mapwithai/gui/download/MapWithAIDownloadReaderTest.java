// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.download.DownloadSettings;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtilsTest;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.mapwithai.gui.download.MapWithAIDownloadReader.MapWithAIDownloadData;
import org.openstreetmap.josm.plugins.mapwithai.testutils.MapWithAITestRules;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;
import org.openstreetmap.josm.tools.ImageProvider;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class MapWithAIDownloadReaderTest {
    @Rule
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules rules = new MapWithAITestRules().wiremock().projection().territories();

    @Test
    public void testGetLabel() {
        assertEquals(tr("Download from {0} API", MapWithAIPlugin.NAME), new MapWithAIDownloadReader().getLabel());
    }

    @Test
    public void testIsExpert() {
        assertFalse(new MapWithAIDownloadReader().onlyExpert());
    }

    @Test
    public void testDoDownload() {
        new WindowMocker();
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        // TODO revert commit that adds these lines as soon as MapWithAI fixes timeout
        // issue see
        // https://mapwith.ai/maps/ml_roads?conflate_with_osm=true&theme=ml_road_vector&collaborator=josm&token=ASb3N5o9HbX8QWn8G_NtHIRQaYv3nuG2r7_f3vnGld3KhZNCxg57IsaQyssIaEw5rfRNsPpMwg4TsnrSJtIJms5m&hash=ASawRla3rBcwEjY4HIY&bbox=-108.4522247,39.0239848,-108.3368683,39.1066201&result_type=road_building_vector_xml
        DownloadSettings settings = new DownloadSettings(new Bounds(39.095376, -108.4495519, 39.0987811, -108.4422314),
                false, false);
        MapWithAIDownloadReader.MapWithAIDownloadData data = new MapWithAIDownloadReader.MapWithAIDownloadData(
                MapWithAIPreferenceHelper.getMapWithAIUrl(), errors -> {
                });
        reader.doDownload(data, settings);
        MapWithAIDataUtils.getForkJoinPool().awaitQuiescence(1000, TimeUnit.SECONDS);
        assertNotNull(MapWithAIDataUtils.getLayer(false));
        assertFalse(MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().isEmpty());
        assertTrue(settings.getDownloadBounds().get().toBBox().bboxIsFunctionallyEqual(
                MapWithAIDataUtils.getLayer(false).getDataSet().getDataSourceBounds().get(0).toBBox(), 0.0001));
    }

    @Test
    public void testMapWithAIDownloadDataGetData() {
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        MapWithAIDownloadReader.MapWithAIDownloadPanel panel = new MapWithAIDownloadReader.MapWithAIDownloadPanel(
                reader);
        MapWithAIDownloadData data = panel.getData();
        assertFalse(data.getUrls().isEmpty());
    }

    /**
     * This rememberSettings method is blank, so just make certain nothing gets
     * thrown
     */
    @Test
    public void testMapWithAIDownloadDataRememberSettings() {
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        MapWithAIDownloadReader.MapWithAIDownloadPanel panel = new MapWithAIDownloadReader.MapWithAIDownloadPanel(
                reader);
        assertDoesNotThrow(() -> panel.rememberSettings());
    }

    /**
     * This restoreSettings method is blank, so just make certain nothing gets
     * thrown
     */
    @Test
    public void testMapWithAIDownloadDataRestoreSettings() {
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        MapWithAIDownloadReader.MapWithAIDownloadPanel panel = new MapWithAIDownloadReader.MapWithAIDownloadPanel(
                reader);
        assertDoesNotThrow(() -> panel.restoreSettings());
    }

    @Test
    public void testMapWithAIDownloadDataSizeCheck()
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        MapWithAIDownloadReader.MapWithAIDownloadPanel panel = new MapWithAIDownloadReader.MapWithAIDownloadPanel(
                reader);
        Field sizeCheckField = MapWithAIDownloadReader.MapWithAIDownloadPanel.class.getDeclaredField("sizeCheck");
        sizeCheckField.setAccessible(true);
        JLabel sizeCheck = (JLabel) sizeCheckField.get(panel);
        assertTrue(sizeCheck.getText().isEmpty());
        panel.boundingBoxChanged(null);
        assertEquals(tr("No area selected yet"), sizeCheck.getText());
        panel.boundingBoxChanged(MapWithAIDataUtilsTest.getTestBounds());
        assertEquals(tr("Download area ok, size probably acceptable to server"), sizeCheck.getText());
        panel.boundingBoxChanged(new Bounds(0, 0, 0.0001, 10));
        assertEquals(tr("Download area too large; will probably be rejected by server"), sizeCheck.getText());
        panel.boundingBoxChanged(MapWithAIDataUtilsTest.getTestBounds());
        assertEquals(tr("Download area ok, size probably acceptable to server"), sizeCheck.getText());
        panel.boundingBoxChanged(new Bounds(0, 0, 10, 0.0001));
        assertEquals(tr("Download area too large; will probably be rejected by server"), sizeCheck.getText());
        panel.boundingBoxChanged(MapWithAIDataUtilsTest.getTestBounds());
        assertEquals(tr("Download area ok, size probably acceptable to server"), sizeCheck.getText());
    }

    @Test
    public void testMapWithAIDownloadDataGetSimpleName() {
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        MapWithAIDownloadReader.MapWithAIDownloadPanel panel = new MapWithAIDownloadReader.MapWithAIDownloadPanel(
                reader);
        assertFalse(panel.getSimpleName().isEmpty());
    }

    @Test
    public void testMapWithAIDownloadDataGetIcon() {
        // Eclipse doesn't have dialogs/mapwithai when running tests
        try {
            ImageProvider.get("dialogs", "mapwithai");
        } catch (Exception e) {
            Assume.assumeNoException(e);
        }
        MapWithAIDownloadReader reader = new MapWithAIDownloadReader();
        MapWithAIDownloadReader.MapWithAIDownloadPanel panel = new MapWithAIDownloadReader.MapWithAIDownloadPanel(
                reader);
        assertNotNull(panel.getIcon());
    }
}
