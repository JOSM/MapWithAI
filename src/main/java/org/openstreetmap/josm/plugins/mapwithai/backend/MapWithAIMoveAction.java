package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.tools.Shortcut;

public class MapWithAIMoveAction extends JosmAction {
    /** UID for abstract action */
    private static final long serialVersionUID = 319374598;

    public MapWithAIMoveAction() {
        super(tr("{0}: Add selected data", MapWithAIPlugin.NAME), null, tr("Add data from {0}", MapWithAIPlugin.NAME),
                Shortcut.registerShortcut(
                        "data:mapwithaiadd", tr("{0}: {1}", MapWithAIPlugin.NAME, tr("Add selected data")),
                        KeyEvent.VK_A,
                        Shortcut.SHIFT),
                true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        for (final MapWithAILayer mapWithAI : MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class)) {
            final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
            OsmDataLayer editLayer = null;
            final int maxAddition = MapWithAIDataUtils.getMaximumAddition();
            Collection<OsmPrimitive> selected;
            if (maxAddition > 0) {
                selected = mapWithAI.getDataSet().getSelected().stream().limit(maxAddition).collect(Collectors.toList());
            } else {
                selected = mapWithAI.getDataSet().getSelected();
            }
            for (final OsmDataLayer osmLayer : osmLayers) {
                if (!osmLayer.isLocked() && osmLayer.isVisible() && osmLayer.isUploadable()
                        && osmLayer.getClass().equals(OsmDataLayer.class)) {
                    editLayer = osmLayer;
                    break;
                }
            }
            if (editLayer != null) {
                final MapWithAIAddCommand command = new MapWithAIAddCommand(mapWithAI, editLayer, selected);
                UndoRedoHandler.getInstance().add(command);
                if (MapWithAIDataUtils.isSwitchLayers()) {
                    MainApplication.getLayerManager().setActiveLayer(editLayer);
                }
            }
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(checkIfActionEnabled());
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        if (selection == null || selection.isEmpty()) {
            setEnabled(false);
        } else {
            setEnabled(checkIfActionEnabled());
        }
    }

    private boolean checkIfActionEnabled() {
        boolean returnValue = false;
        final Layer active = getLayerManager().getActiveLayer();
        if (active instanceof MapWithAILayer) {
            final MapWithAILayer mapWithAILayer = (MapWithAILayer) active;
            final Collection<OsmPrimitive> selection = mapWithAILayer.getDataSet().getAllSelected();
            returnValue = selection != null && !selection.isEmpty();
        }
        return returnValue;
    }
}
