// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Tag("wounded")
@Test
/**
 * This is used for tests that have failed in the past due to another test
 * bleeding into it. The other test should be annotated with {@link BleedTest}.
 *
 * @author Taylor Smock
 *
 */

public @interface WoundedTest {

}
