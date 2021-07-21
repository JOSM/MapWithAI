// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An annotation to use {@link MapWithAILayerInfo}
 *
 * @author Taylor Smock
 */
@ExtendWith(MapWithAISources.MapWithAILayerInfoExtension.class)
public @interface MapWithAISources {
    class MapWithAILayerInfoExtension implements AfterAllCallback, BeforeAllCallback {
        @Override
        public void afterAll(ExtensionContext context) {
            MapWithAILayerInfo.getInstance().clear();
        }

        @Override
        public void beforeAll(ExtensionContext context) {
            AtomicBoolean finished = new AtomicBoolean();
            MapWithAILayerInfo.getInstance().load(false, () -> finished.set(true));
            Awaitility.await().atMost(Durations.TEN_SECONDS).until(finished::get);
        }
    }
}
