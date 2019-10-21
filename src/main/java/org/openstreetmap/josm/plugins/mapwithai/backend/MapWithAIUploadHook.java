// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.Map;

import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Destroyable;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAIUploadHook implements UploadHook, Destroyable {
    private final String version;

    public MapWithAIUploadHook(PluginInformation info) {
        version = info.localversion;
        UploadAction.registerUploadHook(this);
    }

    @Override
    public void modifyChangesetTags(Map<String, String> tags) {
        final Long addedObjects = MapWithAIDataUtils.getAddedObjects();
        if (addedObjects != 0) {
            tags.put("mapwithai", addedObjects.toString());
            final StringBuilder sb = new StringBuilder();
            sb.append("version=").append(version);
            if (MapWithAIPreferenceHelper.getMaximumAddition() != MapWithAIPreferenceHelper
                    .getDefaultMaximumAddition()) {
                sb.append(";maxadd=").append(MapWithAIPreferenceHelper.getMaximumAddition());
            }
            if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
                sb.append(";task=").append(DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(","));
            }
            if (!MapWithAIPreferenceHelper.getMapWithAIUrl()
                    .equalsIgnoreCase(MapWithAIPreferenceHelper.DEFAULT_MAPWITHAI_API)) {
                sb.append(";url=").append(MapWithAIPreferenceHelper.getMapWithAIUrl());
            }
            tags.put("mapwithai:options", sb.toString());

        }
    }

    @Override
    public void destroy() {
        UploadAction.unregisterUploadHook(this);
    }
}
