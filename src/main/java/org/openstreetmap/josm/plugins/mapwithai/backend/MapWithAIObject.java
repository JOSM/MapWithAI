// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.UndoRedoHandler.CommandQueueListener;
import org.openstreetmap.josm.gui.MapStatus;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.GBC;

/**
 * Show the number of MapWithAI objects that have been added since the last time
 * the command stack was cleared. The stack is usually cleared on upload.
 *
 * @author Taylor Smock
 *
 */
public class MapWithAIObject implements CommandQueueListener, Destroyable {
    private final JosmTextField mapWithAIObjects;
    private final List<MapStatus> statusLines;

    public MapWithAIObject() {
        mapWithAIObjects = new JosmTextField(null, null, "MapWithAI Objects Added: 1000".length() - 10, false);
        mapWithAIObjects.setBackground(MapStatus.PROP_BACKGROUND_COLOR.get());
        mapWithAIObjects.setEditable(false);
        statusLines = new ArrayList<>();
        UndoRedoHandler.getInstance().addCommandQueueListener(this);
        setText();
    }

    /**
     * Adds a new status line to the map status
     *
     * @param mapStatus The status bar to add a count to
     */
    public void addMapStatus(MapStatus mapStatus) {
        statusLines.add(mapStatus);
        mapStatus.add(mapWithAIObjects, GBC.std().insets(3, 0, 0, 0), mapStatus.getComponentCount() - 2);
    }

    /**
     * Removes a status line from the map status
     *
     * @param mapStatus The status bar to remove a count from
     */
    public void removeMapStatus(MapStatus mapStatus) {
        mapStatus.remove(mapWithAIObjects);
        statusLines.remove(mapStatus);
    }

    @Override
    public void commandChanged(int queueSize, int redoSize) {
        setText();
    }

    private void setText() {
        final long addedObjects = MapWithAIDataUtils.getAddedObjects();
        if (addedObjects == 0L) {
            mapWithAIObjects.setVisible(false);
            mapWithAIObjects.setVisible(true);
            mapWithAIObjects.setText(tr("{0} Objects Added: {1}", MapWithAIPlugin.NAME, addedObjects));
        } else {
            mapWithAIObjects.setVisible(true);
            mapWithAIObjects.setText(tr("{0} Objects Added: {1}", MapWithAIPlugin.NAME, addedObjects));
        }
        final int maxAdd = MapWithAIPreferenceHelper.getMaximumAddition();
        if (addedObjects == 0) {
            mapWithAIObjects.setBackground(MapStatus.PROP_BACKGROUND_COLOR.get());
        } else if (addedObjects < maxAdd) {
            mapWithAIObjects.setBackground(Color.GREEN);
        } else if (addedObjects < 10L * maxAdd) {
            mapWithAIObjects.setBackground(Color.YELLOW);
        } else {
            mapWithAIObjects.setBackground(Color.RED);
        }
    }

    @Override
    public void destroy() {
        statusLines.forEach(mapStatus -> mapStatus.remove(mapWithAIObjects));
        statusLines.clear();
        UndoRedoHandler.getInstance().removeCommandQueueListener(this);
    }
}
