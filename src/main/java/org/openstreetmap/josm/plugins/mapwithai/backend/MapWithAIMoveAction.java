// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JOptionPane;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MapWithAIAddCommand;
import org.openstreetmap.josm.plugins.mapwithai.tools.BlacklistUtils;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Move data between the MapWithAI layer and an OSM data layer
 */
public class MapWithAIMoveAction extends JosmAction {
    /** UID for abstract action */
    private static final long serialVersionUID = 319374598;

    /** The maximum number of objects is this times the maximum add */
    public static final long MAX_ADD_MULTIPLIER = 10;

    /**
     * Create a new action
     */
    public MapWithAIMoveAction() {
        super(tr("{0}: Add selected data", MapWithAIPlugin.NAME), "mapwithai",
                tr("Add data from {0}", MapWithAIPlugin.NAME), obtainShortcut(), true, "mapwithai:movedata", true);
        setHelpId(ht("Plugin/MapWithAI#BasicUsage"));
    }

    /**
     * @return The default shortcut, if available, or an alternate shortcut that
     *         makes sense otherwise
     */
    private static Shortcut obtainShortcut() {
        int key = KeyEvent.VK_A;
        int modifier = Shortcut.SHIFT;
        final String shortText = "data:mapwithaiadd";
        final Optional<Shortcut> shortCut = Shortcut.findShortcut(key, InputEvent.SHIFT_DOWN_MASK); // Shortcut.SHIFT
        // maps to
        // KeyEvent.SHIFT_DOWN_MASK
        if (shortCut.isPresent() && !shortText.equals(shortCut.get().getShortText())) {
            key = KeyEvent.VK_C;
            modifier = Shortcut.ALT;
        }
        return Shortcut.registerShortcut(shortText, tr("{0}: {1}", MapWithAIPlugin.NAME, tr("Add selected data")), key,
                modifier);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (BlacklistUtils.isBlacklisted()) {
            MapWithAILayer.createBadDataNotification();
            return;
        }
        for (final MapWithAILayer mapWithAI : MainApplication.getLayerManager().getLayersOfType(MapWithAILayer.class)) {
            final DataSet ds = mapWithAI.getDataSet();
            final int maxAddition = MapWithAIPreferenceHelper.getMaximumAddition();
            final List<Node> nodes = ds.getSelectedNodes().stream().filter(node -> !node.getReferrers().isEmpty())
                    .collect(Collectors.toList());
            ds.clearSelection(nodes);
            nodes.stream().map(Node::getReferrers).forEach(ds::addSelected);
            final Collection<OsmPrimitive> selected = limitCollection(ds, maxAddition);
            final OsmDataLayer editLayer = getOsmDataLayer();
            if (editLayer != null && !selected.isEmpty()
                    && (MapWithAIDataUtils.getAddedObjects() < maxAddition * MAX_ADD_MULTIPLIER
                            || (maxAddition == 0 && ExpertToggleAction.isExpert()))) {
                final MapWithAIAddCommand command = new MapWithAIAddCommand(mapWithAI, editLayer, selected);
                GuiHelper.runInEDTAndWait(() -> UndoRedoHandler.getInstance().add(command));
                if (MapWithAIPreferenceHelper.isSwitchLayers()) {
                    MainApplication.getLayerManager().setActiveLayer(editLayer);
                }
            } else if (MapWithAIDataUtils.getAddedObjects() >= maxAddition * MAX_ADD_MULTIPLIER) {
                createTooManyAdditionsNotification(maxAddition);
            }
        }
    }

    private static void createTooManyAdditionsNotification(int maxAddition) {
        Notification tooMany = new Notification();
        tooMany.setIcon(JOptionPane.WARNING_MESSAGE);
        tooMany.setDuration(Notification.TIME_DEFAULT);
        tooMany.setContent(
                tr("There is a soft cap of {0} objects before uploading. Please verify everything before uploading.",
                        maxAddition * MAX_ADD_MULTIPLIER));
        tooMany.show();
    }

    private static Collection<OsmPrimitive> limitCollection(DataSet ds, int maxSize) {
        return (maxSize > 0 || !ExpertToggleAction.isExpert())
                ? ds.getSelected().stream().limit(maxSize).collect(Collectors.toList())
                : ds.getSelected();
    }

    /**
     * Get the OSM Data Layer to add MapWithAI data to
     *
     * @return An OSM data layer that data can be added to
     */
    public static OsmDataLayer getOsmDataLayer() {
        return MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                .filter(OsmDataLayer::isVisible).filter(OsmDataLayer::isUploadable)
                .filter(osmLayer -> !osmLayer.isLocked() && osmLayer.getClass().equals(OsmDataLayer.class)).findFirst()
                .orElse(null);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(checkIfActionEnabled());
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        if ((selection == null) || selection.isEmpty()) {
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
            returnValue = (selection != null) && !selection.isEmpty();
        }
        return returnValue;
    }
}
