// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.Map;

import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIUploadHook implements UploadHook {
    private final String version;

    public MapWithAIUploadHook(PluginInformation info) {
        version = info.localversion;
    }

    @Override
    public void modifyChangesetTags(Map<String, String> tags) {
        final Integer addedObjects = getAddedObjects();
        if (addedObjects != 0) {
            tags.put("mapwithai", addedObjects.toString());
            final StringBuilder sb = new StringBuilder();
            sb.append("version=").append(version);
            if (MapWithAIPreferenceHelper.getMaximumAddition() != MapWithAIPreferenceHelper
                    .getDefaultMaximumAddition()) {
                sb.append(";maxadd=").append(MapWithAIPreferenceHelper.getMaximumAddition());
            }
            if (!MapWithAIPreferenceHelper.getMapWithAIUrl()
                    .equalsIgnoreCase(MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)) {
                sb.append(";url=").append(MapWithAIPreferenceHelper.getMapWithAIUrl());
            }
            if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
                sb.append(";task=").append(DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(","));
            }
            tags.put("mapwithai:options", sb.toString());

        }
    }

    private static int getAddedObjects() {
        return UndoRedoHandler.getInstance().getUndoCommands().parallelStream()
                .filter(object -> object instanceof MapWithAIAddCommand).map(object -> (MapWithAIAddCommand) object)
                .mapToInt(MapWithAIAddCommand::getAddedObjects).sum();
    }
}
