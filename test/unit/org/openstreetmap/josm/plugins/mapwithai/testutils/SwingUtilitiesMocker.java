// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

import javax.swing.SwingUtilities;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

public class SwingUtilitiesMocker extends MockUp<SwingUtilities> {
    public List<FutureTask<?>> futureTasks = new ArrayList<>();

    @Mock
    public void invokeLater(Invocation inv) {
        actualWork(inv);
    }

    @Mock
    public void invokeAndWait(Invocation inv) {
        actualWork(inv);
    }

    private void actualWork(Invocation inv) {
        Runnable run = (Runnable) inv.getInvokedArguments()[0];
        FutureTask<?> future = new FutureTask<>(run, true);
        futureTasks.add(future);
        inv.proceed(future);
    }
}
