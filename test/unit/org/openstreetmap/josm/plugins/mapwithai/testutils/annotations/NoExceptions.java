// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.gui.MainApplication;

/**
 * Ensure that no exceptions occur in different threads
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD })
@ExtendWith(NoExceptions.NoExceptionsExtension.class)
public @interface NoExceptions {
    class NoExceptionsExtension implements AfterAllCallback, BeforeAllCallback {
        @Override
        public void afterAll(ExtensionContext context) {
            final AtomicBoolean atomicBoolean = new AtomicBoolean();
            MainApplication.worker.submit(() -> atomicBoolean.set(true));
            Awaitility.await().atMost(Durations.TEN_SECONDS).until(atomicBoolean::get);
            final ExtensionContext.Store store = context
                    .getStore(ExtensionContext.Namespace.create(NoExceptions.class));
            Thread.setDefaultUncaughtExceptionHandler(
                    store.get(Thread.UncaughtExceptionHandler.class, Thread.UncaughtExceptionHandler.class));
            final NoExceptionsUncaughtExceptionHandler handler = store.get(NoExceptions.class,
                    NoExceptionsUncaughtExceptionHandler.class);
            if (!handler.exceptionCollection.isEmpty()) {
                assertAll(handler.exceptionCollection.stream().map(Assertions::fail));
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            final ExtensionContext.Store store = context
                    .getStore(ExtensionContext.Namespace.create(NoExceptions.class));
            store.put(Thread.UncaughtExceptionHandler.class, Thread.getDefaultUncaughtExceptionHandler());
            final NoExceptionsUncaughtExceptionHandler handler = new NoExceptionsUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(handler);
            store.put(NoExceptions.class, handler);
        }

        static class NoExceptionsUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
            final List<Throwable> exceptionCollection = new ArrayList<>();

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                this.exceptionCollection.add(e);
            }
        }
    }
}
