// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Shortcut;

public class MapWithAIAction extends JosmAction {
    /** UID */
    private static final long serialVersionUID = 8886705479253246588L;

    public MapWithAIAction() {
        super(tr("{0}: Download data", MapWithAIPlugin.NAME), null, tr("Get data from {0}", MapWithAIPlugin.NAME),
                Shortcut.registerShortcut("data:mapWithAI", tr("Data: {0}", MapWithAIPlugin.NAME), KeyEvent.VK_R,
                        Shortcut.SHIFT),
                true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        MapWithAIDataUtils.getMapWithAIData(MapWithAIDataUtils.getLayer(true));
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
    }
}
