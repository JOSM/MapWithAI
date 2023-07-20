// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.widgets.JosmTextArea;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.MapWithAISources;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Territories;
import org.openstreetmap.josm.plugins.mapwithai.testutils.annotations.Wiremock;
import org.openstreetmap.josm.testutils.annotations.AssertionsInEDT;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.mockers.WindowMocker;

/**
 * Test class for {@link AddMapWithAIPanel}
 *
 * @author Taylor Smock
 */
@AssertionsInEDT
@MapWithAISources
@Projection
@Territories
@Wiremock
class AddMapWithAIPanelTest {
    @Test
    void testAddMapWithAIPanel() throws ReflectiveOperationException {
        new WindowMocker();
        MapWithAIInfo oldInfo = new MapWithAIInfo();
        AddMapWithAIPanel panel = new AddMapWithAIPanel(oldInfo);
        Field rawUrlField = AddMapWithAIPanel.class.getDeclaredField("rawUrl");
        Field nameField = AddMapWithAIPanel.class.getDeclaredField("name");
        Field infoField = AddMapWithAIPanel.class.getDeclaredField("info");
        Field parametersTableField = AddMapWithAIPanel.class.getDeclaredField("parametersTable");
        for (Field f : Arrays.asList(rawUrlField, nameField, infoField, parametersTableField)) {
            f.setAccessible(true);
        }
        JosmTextArea rawUrl = (JosmTextArea) rawUrlField.get(panel);
        JosmTextField name = (JosmTextField) nameField.get(panel);
        MapWithAIInfo info = (MapWithAIInfo) infoField.get(panel);

        assertSame(oldInfo, info);
        assertTrue(rawUrl.getText().isEmpty());
        assertTrue(name.getText().isEmpty());
        assertTrue(panel.getCommonParameters().isEmpty());

        assertFalse(panel.isSourceValid());

        rawUrl.setText("https://import.data");
        assertFalse(panel.isSourceValid());
        name.setText("Import Data");
        assertTrue(panel.isSourceValid());

        assertSame(oldInfo, panel.getSourceInfo());
        assertEquals("https://import.data", oldInfo.getUrl());
        assertEquals("Import Data", oldInfo.getName());

        rawUrl.setText("");
        assertFalse(panel.isSourceValid());
    }
}
