// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Destroyable;

/**
 * Add information that is useful for QC/debugging to OSM changesets.
 *
 * @author Taylor Smock
 */
public class MapWithAIUploadHook implements UploadHook, Destroyable {
    private final String version;

    /**
     * Create the upload hook
     *
     * @param info The info to get version information from
     */
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
                sb.append(";task=")
                        .append(DetectTaskingManagerUtils.getTaskingManagerBounds().toBBox().toStringCSV(","));
            }
            if (!MapWithAIPreferenceHelper.getMapWithAIUrl().isEmpty()) {
                sb.append(";url_ids=")
                        .append(String.join(";url_ids=",
                                MapWithAIPreferenceHelper.getMapWithAIUrl().stream()
                                        .map(i -> i.getId() == null ? i.getUrl() : i.getId()).filter(Objects::nonNull)
                                        .collect(Collectors.joining(","))));
            }
            String mapwithaiOptions = sb.toString();
            if (mapwithaiOptions.length() > 255) {
                tags.put("mapwithai:options", mapwithaiOptions.substring(0, 255));
                int start = 255;
                int i = 1;
                while (start < mapwithaiOptions.length()) {
                    tags.put("mapwithai:options:" + i, mapwithaiOptions.substring(start,
                            start + 255 < mapwithaiOptions.length() ? start + 255 : mapwithaiOptions.length()));
                    start = start + 255;
                    i++;
                }
            } else {
                tags.put("mapwithai:options", mapwithaiOptions);
            }
        }
    }

    @Override
    public void destroy() {
        UploadAction.unregisterUploadHook(this);
    }
}
