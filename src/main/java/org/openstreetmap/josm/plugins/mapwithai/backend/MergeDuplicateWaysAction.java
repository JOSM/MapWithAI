// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MergeDuplicateWays;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * @author Taylor Smock
 */
public class MergeDuplicateWaysAction extends JosmAction {
    private static final long serialVersionUID = 8971004636405132635L;
    private static final String DESCRIPTION = "Attempt to merge potential duplicate ways";

    public MergeDuplicateWaysAction() {
        super(tr("{0}: ".concat(DESCRIPTION), MapWithAIPlugin.NAME), null, tr(DESCRIPTION),
                Shortcut.registerShortcut("data:attemptmergeway",
                        tr(DESCRIPTION), KeyEvent.VK_EXCLAMATION_MARK,
                        Shortcut.ALT_CTRL_SHIFT),
                true);

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final List<Way> ways = new ArrayList<>(MainApplication.getLayerManager().getActiveDataSet().getSelectedWays());
        Command command = null;
        int i = 0;
        do {
            if (ways.size() == 2) {
                command = new MergeDuplicateWays(ways.get(0), ways.get(1));
            } else if (ways.size() == 1) {
                command = new MergeDuplicateWays(ways.get(0));
            } else if (ways.isEmpty()) {
                command = new MergeDuplicateWays(MainApplication.getLayerManager().getActiveDataSet());
            }
            if (command != null) {
                UndoRedoHandler.getInstance().add(command);
                i++;
                Logging.error(Integer.toString(i));
            }
        } while (command != null && i < 10);
    }

}
