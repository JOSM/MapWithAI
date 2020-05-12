// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.testutils;

import java.awt.Component;

import org.openstreetmap.josm.gui.PleaseWaitDialog;

import mockit.Mock;
import mockit.MockUp;

public class PleaseWaitDialogMocker extends MockUp<PleaseWaitDialog> {
    @Mock
    protected void adjustLayout() {
        // We don't want to adjust the layout...
    }

    @Mock
    public void setLocationRelativeTo(Component c) {
        // Do nothing
    }

    @Mock
    public void setVisible(boolean visible) {
        // Do nothing
    }

    @Mock
    public void dispose() {
        // Do nothing...
    }
}
