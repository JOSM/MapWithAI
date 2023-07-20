// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * Use boundaries dataset in this test. FIXME: Use the JOSM version
 *
 * @see JOSMTestRules#territories()
 * @author Taylor Smock
 * @since xxx
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@BasicPreferences // Needed for nodes
@Projection // Needed for getEastNorth
@ExtendWith(Territories.TerritoriesExtension.class)
public @interface Territories {
    /**
     * Initialization states. Please note that the highest initialization state
     * holds.
     *
     * @author Taylor Smock
     */
    enum Initialize {
        /** Don't initialize */
        NONE,
        /** Initialize only internal data */
        INTERNAL,
        /** Initialize internal and external data */
        ALL
    }

    /**
     * The way to initialize Territories
     *
     * @return The value to use
     */
    Initialize value() default Initialize.INTERNAL;

    /**
     * Initialize boundaries prior to use
     *
     * @author Taylor Smock
     *
     */
    class TerritoriesExtension implements BeforeAllCallback, AfterAllCallback {
        private static Initialize last = Initialize.NONE;

        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            synchronized (TerritoriesExtension.class) {
                // TODO
                // org.openstreetmap.josm.tools.Territories.uninitialize();
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            Optional<Territories> annotation = AnnotationSupport.findAnnotation(context.getElement(),
                    Territories.class);
            if (annotation.isPresent()) {
                Initialize current = annotation.get().value();
                if (current.ordinal() <= last.ordinal()) {
                    return;
                }
                last = current;
                // Avoid potential race conditions if tests are parallelized
                synchronized (TerritoriesExtension.class) {
                    if (current == Initialize.INTERNAL) {
                        org.openstreetmap.josm.tools.Territories.initializeInternalData();
                    } else if (current == Initialize.ALL) {
                        org.openstreetmap.josm.tools.Territories.initialize();
                    }
                }
            }
        }
    }
}
