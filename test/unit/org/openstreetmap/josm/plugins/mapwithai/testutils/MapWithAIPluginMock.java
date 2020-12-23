// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;

import mockit.Mock;
import mockit.MockUp;

public class MapWithAIPluginMock extends MockUp<MapWithAIPlugin> {
    @Mock
    public static String getVersionInfo() {
        return "1.0";
    }
}
