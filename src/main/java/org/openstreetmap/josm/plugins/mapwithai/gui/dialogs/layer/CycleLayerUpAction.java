// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.dialogs.layer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Allow users to cycle between adjacent layers easily
 *
 * @author Taylor Smock
 * @since xxx
 */
public class CycleLayerUpAction extends JosmAction {
    private static final long serialVersionUID = -4041662823217465004L;
    protected final static int KEYUP = KeyEvent.VK_OPEN_BRACKET;
    protected final static int MODIFIER = Shortcut.SHIFT;
    private static Shortcut cycleUp = Shortcut.registerShortcut("core:cyclelayerup", tr("Cycle layers up"), KEYUP,
            MODIFIER);

    /**
     * Create a CycleLayerDownAction that cycles through layers that are in the
     * model
     */
    public CycleLayerUpAction() {
        super(tr("Cycle layer up"), "dialogs/next", tr("Cycle up through layers"), cycleUp, true, "cycle-layer", false);
        new ImageProvider("dialogs", "next").getResource().attachImageIcon(this, true);
        putValue(SHORT_DESCRIPTION, tr("Cycle through visible layers."));
        putValue(NAME, tr("Cycle layers"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MainLayerManager manager = MainApplication.getLayerManager();
        List<Layer> managerLayers = manager.getLayers().stream().filter(layer -> !(layer instanceof ImageryLayer))
                .collect(Collectors.toList());
        if (managerLayers.isEmpty()) {
            return;
        }
        int index = managerLayers.indexOf(manager.getActiveLayer());
        int sublist = index < managerLayers.size() ? index + 1 : index;
        if (index >= managerLayers.size() - 1) {
            index = 0;
            sublist = 0;
        }
        List<Layer> layers = managerLayers.subList(sublist, managerLayers.size());
        Layer layer = layers.stream().filter(Layer::isVisible).filter(tlayer -> !(tlayer instanceof ImageryLayer))
                .findFirst().orElse(manager.getActiveLayer());

        manager.setActiveLayer(layer);
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}