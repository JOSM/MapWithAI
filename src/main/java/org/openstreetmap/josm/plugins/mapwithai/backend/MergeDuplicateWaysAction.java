// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
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
import org.openstreetmap.josm.tools.Shortcut;

/**
 * An action that attempts to merge duplicate ways
 *
 * @author Taylor Smock
 */
public class MergeDuplicateWaysAction extends JosmAction {
    private static final long serialVersionUID = 8971004636405132635L;
    private static final String DESCRIPTION = "Attempt to merge potential duplicate ways";
    /**
     * If there are 2 ways, we directly compare them to see if they are duplicates
     */
    private static final int COMPARE_WAYS_NUMBER = 2;

    public MergeDuplicateWaysAction() {
        super(tr("{0}: ".concat(DESCRIPTION), MapWithAIPlugin.NAME), "mapwithai", tr(DESCRIPTION),
                Shortcut.registerShortcut("data:attemptmergeway", tr(DESCRIPTION), KeyEvent.VK_EXCLAMATION_MARK,
                        Shortcut.ALT_CTRL_SHIFT),
                true, "mapwithai:attemptmergeway", true);
        setHelpId(ht("Plugin/MapWithAI"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (MainApplication.getLayerManager().getActiveDataSet() != null) {
            final List<Way> ways = new ArrayList<>(
                    MainApplication.getLayerManager().getActiveDataSet().getSelectedWays());
            Command command = null;
            int i = 0;
            do {
                if (ways.size() == COMPARE_WAYS_NUMBER) {
                    command = new MergeDuplicateWays(ways.get(0), ways.get(1));
                } else if (ways.size() == 1) {
                    command = new MergeDuplicateWays(ways.get(0));
                } else if (ways.isEmpty()) {
                    command = new MergeDuplicateWays(MainApplication.getLayerManager().getActiveDataSet());
                }
                if (command != null) {
                    UndoRedoHandler.getInstance().add(command);
                    i++;
                }
            } while ((command != null) && (i < 1));
        }
    }

    @Override
    public void updateEnabledState() {
        setEnabled(MainApplication.getLayerManager().getActiveDataSet() != null);
    }
}
