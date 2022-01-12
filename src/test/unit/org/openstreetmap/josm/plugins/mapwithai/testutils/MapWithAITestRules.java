// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Level;

import org.junit.runners.model.InitializationError;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.tools.Logging;

import mockit.integration.TestRunnerDecorator;

public class MapWithAITestRules extends JOSMTestRules {

    private boolean workerExceptions = true;
    private UncaughtExceptionHandler currentExceptionHandler;

    public MapWithAITestRules() {
        super();
    }

    public MapWithAITestRules noWorkerExceptions() {
        this.workerExceptions = false;
        return this;
    }

    /**
     * Set up before running a test
     *
     * @throws InitializationError          If an error occurred while creating the
     *                                      required environment.
     * @throws ReflectiveOperationException if a reflective access error occurs
     */
    @Override
    protected void before() throws InitializationError, ReflectiveOperationException {
        TestRunnerDecorator.cleanUpAllMocks();
        super.before();
        Logging.getLogger().setFilter(record -> record.getLevel().intValue() >= Level.WARNING.intValue()
                || record.getSourceClassName().startsWith("org.openstreetmap.josm.plugins.mapwithai"));

        if (workerExceptions) {
            currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                Logging.error(t.getClass().getSimpleName());
                Logging.error(e);
            });
        }
    }

    @Override
    protected void after() throws ReflectiveOperationException {
        super.after();

        if (workerExceptions) {
            Thread.setDefaultUncaughtExceptionHandler(currentExceptionHandler);
        }
        TestRunnerDecorator.cleanUpAllMocks();
    }
}
