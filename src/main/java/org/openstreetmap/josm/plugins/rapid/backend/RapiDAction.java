// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Shortcut;

public class RapiDAction extends JosmAction {
    /** UID */
    private static final long serialVersionUID = 8886705479253246588L;

    public RapiDAction() {
        super(tr("{0}: Download data", RapiDPlugin.NAME), null, tr("Get data from RapiD"),
                Shortcut.registerShortcut("data:rapid", tr("Data: {0}", tr("RapiD")), KeyEvent.VK_R, Shortcut.SHIFT),
                true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        final RapiDLayer layer = RapiDDataUtils.getLayer(true);
        RapiDDataUtils.getRapiDData(layer);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
    }
}
