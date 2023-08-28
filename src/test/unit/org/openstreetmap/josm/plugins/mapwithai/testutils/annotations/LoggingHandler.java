// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils.annotations;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openstreetmap.josm.tools.Logging;

@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LoggingHandler.LogHandler.class)
public @interface LoggingHandler {
    class LogHandler implements AfterEachCallback, BeforeEachCallback {
        private static class TestLogHandler extends Handler {
            final Map<Level, List<LogRecord>> errorList = new HashMap<>();

            @Override
            public void publish(LogRecord record) {
                // Tests may have issues locating resources
                if (!record.getMessage().endsWith("Failed to locate image 'mapwithai'")) {
                    errorList.computeIfAbsent(record.getLevel(), level -> new ArrayList<>()).add(record);
                }
            }

            @Override
            public void flush() {
                // Do nothing
            }

            @Override
            public void close() {
                // Do nothing
            }
        }

        @Override
        public void afterEach(ExtensionContext context) {
            final Logger logger = Logging.getLogger();
            final Handler[] testHandlers = logger.getHandlers();
            final ExtensionContext.Store store = getStore(context);
            final Handler[] originalHandlers = store.get(logger, Handler[].class);
            for (Handler handler : testHandlers) {
                logger.removeHandler(handler);
            }
            for (Handler handler : originalHandlers) {
                logger.addHandler(handler);
            }
            final Map<Level, List<LogRecord>> recordedErrors = store.get(TestLogHandler.class,
                    TestLogHandler.class).errorList;
            final List<LogRecord> issues = new ArrayList<>();
            if (recordedErrors.containsKey(Level.SEVERE)) {
                issues.addAll(recordedErrors.get(Level.SEVERE));
            }
            if (recordedErrors.containsKey(Level.WARNING)) {
                issues.addAll(recordedErrors.get(Level.WARNING));
            }
            assertAll(Stream.concat(
                    issues.stream().filter(logRecord -> logRecord.getThrown() != null)
                            .map(logRecord -> fail(logRecord.getThrown())),
                    issues.stream().filter(logRecord -> logRecord.getThrown() == null)
                            .map(logRecord -> fail(logRecord.getMessage()))));
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            final Logger logger = Logging.getLogger();
            final Handler[] originalHandlers = logger.getHandlers();
            final ExtensionContext.Store store = getStore(context);
            store.put(logger, originalHandlers);
            for (Handler handler : originalHandlers) {
                logger.removeHandler(handler);
            }
            final TestLogHandler testLogHandler = new TestLogHandler();
            logger.addHandler(testLogHandler);
            store.put(TestLogHandler.class, testLogHandler);
        }

        private static ExtensionContext.Store getStore(ExtensionContext context) {
            return context.getStore(ExtensionContext.Namespace.create(Handler.class));
        }
    }
}
