// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.text.JTextComponent;

import java.awt.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapStatus;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * @author Taylor Smock
 *
 */
@BasicPreferences
@Main
@Projection
class MapWithAIObjectTest {
    private MapWithAIObject mapWithAIObject;
    private MapStatus statusLine;
    private OsmDataLayer osmData;

    @BeforeEach
    void setUp() {
        JOSMFixture.initToolbar();
        mapWithAIObject = new MapWithAIObject();
        osmData = new OsmDataLayer(new DataSet(), "", null);
        // Required to have a non-null MainApplication.getMap()
        MainApplication.getLayerManager().addLayer(osmData);
        statusLine = MainApplication.getMap().statusLine;
    }

    /**
     * Test method for
     * {@link org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIObject#addMapStatus(org.openstreetmap.josm.gui.MapStatus)}.
     */
    @Test
    void testAddMapStatus() {
        int initialComponents = statusLine.getComponentCount();
        for (int i = 0; i < 10; i++) {
            mapWithAIObject.addMapStatus(statusLine);
            assertEquals(initialComponents + 1, statusLine.getComponentCount());
        }
    }

    /**
     * Test method for
     * {@link org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIObject#removeMapStatus(org.openstreetmap.josm.gui.MapStatus)}.
     */
    @Test
    void testRemoveMapStatus() {
        testAddMapStatus();
        int addedComponents = statusLine.getComponentCount();
        for (int i = 0; i < 10; i++) {
            mapWithAIObject.removeMapStatus(statusLine);
            assertEquals(addedComponents - 1, statusLine.getComponentCount());
        }
    }

    /**
     * Test method for
     * {@link org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIObject#commandChanged(int, int)}.
     */
    @Test
    void testCommandChanged() {
        List<Component> initialComponents = Arrays.asList(statusLine.getComponents());
        testAddMapStatus();
        JTextComponent text = Stream.of(statusLine.getComponents()).filter(JTextComponent.class::isInstance)
                .map(JTextComponent.class::cast).filter(i -> !initialComponents.contains(i)).findFirst().orElse(null);
        assertNotNull(text);
        assertTrue(text.getText().contains("0"));
        UndoRedoHandler.getInstance().addCommandQueueListener(mapWithAIObject);
        UndoRedoHandler.getInstance().add(
                new MapWithAIAddCommand(MapWithAIDataUtils.getLayer(true), osmData, Collections.emptySet()), false);
        assertTrue(text.getText().contains("0"));
        Node node1 = new Node(LatLon.ZERO);
        MapWithAIDataUtils.getLayer(true).getDataSet().addPrimitive(node1);
        UndoRedoHandler.getInstance().add(
                new MapWithAIAddCommand(MapWithAIDataUtils.getLayer(false), osmData, Collections.singleton(node1)),
                false);
        assertTrue(text.getText().contains("1"));
        UndoRedoHandler.getInstance().removeCommandQueueListener(mapWithAIObject);
    }

    /**
     * Test method for
     * {@link org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIObject#destroy()}.
     */
    @Test
    void testDestroy() {
        testAddMapStatus();
        int addedComponents = statusLine.getComponentCount();
        mapWithAIObject.destroy();
        assertEquals(addedComponents - 1, statusLine.getComponentCount());
    }

}
