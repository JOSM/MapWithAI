// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Tag("bleed")
@Test
/**
 * This is used for tests that bleed into other tests. This may cause
 * undesirable behavior, like the test failing. It may also cause desirable
 * behavior, like a test failing.
 *
 * @author Taylor Smock
 *
 */
public @interface BleedTest {

}
