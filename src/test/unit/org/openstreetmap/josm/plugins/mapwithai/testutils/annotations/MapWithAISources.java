// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;

/**
 * An annotation to use {@link MapWithAILayerInfo}
 *
 * @author Taylor Smock
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Wiremock
@Territories
@ExtendWith(Wiremock.MapWithAILayerInfoExtension.class)
public @interface MapWithAISources {
}
