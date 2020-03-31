// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Dimension;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.imagery.AddImageryPanel.ContentValidationListener;
import org.openstreetmap.josm.gui.util.WindowGeometry;

/**
 * Dialog shown to add a new source from preferences.
 *
 */
public class AddMapWithAIDialog extends ExtendedDialog implements ContentValidationListener {
    private static final long serialVersionUID = 7513676077181970148L;

    /**
     * Constructs a new AddMapWithAIDialog.
     *
     * @param parent The parent element that will be used for position and maximum
     *               size
     * @param panel  The content that will be displayed in the message dialog
     */
    public AddMapWithAIDialog(Component parent, AddMapWithAIPanel panel) {
        super(parent, tr("Add MapWithAI URL"), tr("OK"), tr("Cancel"));
        setButtonIcons("ok", "cancel");
        setCancelButton(2);
        configureContextsensitiveHelp("/Preferences/MapWithAI", true /* show help button */);
        setContent(panel, false);
        setMinimumSize(new Dimension(300, 400));
        panel.addContentValidationListener(this);
        setRememberWindowGeometry(panel.getClass().getName() + ".geometry",
                WindowGeometry.centerInWindow(MainApplication.getMainFrame(), new Dimension(400, 600)));
    }

    @Override
    public void setupDialog() {
        super.setupDialog();
        contentChanged(false);
    }

    @Override
    public void contentChanged(boolean isValid) {
        buttons.get(0).setEnabled(isValid);
    }
}
