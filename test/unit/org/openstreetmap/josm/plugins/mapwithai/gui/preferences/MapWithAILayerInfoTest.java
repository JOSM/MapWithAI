// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences;

import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnableTest;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * This is currently (mostly) a utils class. It should be expanded to tests,
 * sometime.
 *
 * @author Taylor Smock
 *
 */
public class MapWithAILayerInfoTest {
    public static void setupMapWithAILayerInfo(WireMockServer wireMock) {
        synchronized (MapWithAILayerInfoTest.class) {
            resetMapWithAILayerInfo();
            MapWithAILayerInfo.instance.getLayers().stream()
                    .forEach(i -> i.setUrl(GetDataRunnableTest.getDefaultMapWithAIAPIForTest(wireMock, i.getUrl())));
            MapWithAILayerInfo.instance.save();
        }
    }

    public static void resetMapWithAILayerInfo() {
        synchronized (MapWithAILayerInfoTest.class) {
            MapWithAILayerInfo.instance.clear();
            MapWithAILayerInfo.instance.getDefaultLayers().stream().filter(MapWithAIInfo::isDefaultEntry)
                    .forEach(MapWithAILayerInfo.instance::add);
            MapWithAILayerInfo.instance.save();
        }
    }
}
