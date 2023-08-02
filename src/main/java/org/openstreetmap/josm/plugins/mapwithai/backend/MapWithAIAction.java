// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static java.util.function.Predicate.not;
import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.Action;
import javax.swing.JOptionPane;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.AbstractMergeAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Create or download MapWithAI data
 */
public class MapWithAIAction extends JosmAction {
    /** UID */
    @Serial
    private static final long serialVersionUID = 8886705479253246588L;
    private static final String DOWNLOAD_DATA = marktr("{0}: Download Data");
    private static final String SWITCH_LAYERS = marktr("{0}: Switch Layers");

    /**
     * Create the action
     */
    public MapWithAIAction() {
        super(tr(DOWNLOAD_DATA, MapWithAIPlugin.NAME), "mapwithai", tr("Get data from {0}", MapWithAIPlugin.NAME),
                Shortcut.registerShortcut("data:mapWithAI", tr("Data: {0}", MapWithAIPlugin.NAME), KeyEvent.VK_R,
                        Shortcut.CTRL),
                true, "mapwithai:downloadData", true);
        setHelpId(ht("Plugin/MapWithAI#BasicUsage"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (isEnabled()) {
            final boolean hasLayer = MapWithAIDataUtils.getLayer(false) != null;
            final var osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                    .filter(not(MapWithAILayer.class::isInstance)).filter(Layer::isVisible)
                    .collect(Collectors.toList());
            final var layer = getOsmLayer(osmLayers);
            if ((layer != null) && MapWithAIDataUtils.getMapWithAIData(MapWithAIDataUtils.getLayer(true), layer)) {
                final var notification = createMessageDialog();
                if (notification != null) {
                    notification.show();
                }
            } else if ((layer != null) && hasLayer) {
                toggleLayer(layer);
            }
        }
    }

    /**
     * Get the osm layer that the user wants to use to get data from (doesn't ask if
     * user only has one data layer)
     *
     * @param osmLayers The list of osm data layers
     * @return The layer that the user selects
     */
    protected static OsmDataLayer getOsmLayer(List<OsmDataLayer> osmLayers) {
        OsmDataLayer returnLayer = null;
        final var tLayers = new ArrayList<>(osmLayers);
        if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
            tLayers.removeIf(DetectTaskingManagerUtils.getTaskingManagerLayer()::equals);
        }
        if (tLayers.size() == 1) {
            returnLayer = osmLayers.get(0);
        } else if (!tLayers.isEmpty()) {
            returnLayer = AbstractMergeAction.askTargetLayer(osmLayers.toArray(new OsmDataLayer[0]),
                    tr("Please select the initial layer for boundaries"), tr("Select target layer for boundaries"),
                    tr("OK"), "download");
        }
        return returnLayer;
    }

    /**
     * Toggle the layer (the toLayer is the layer to switch to, if currently active
     * it will switch to the MapWithAI layer, if the MapWithAI layer is currently
     * active it will switch to the layer passed)
     *
     * @param toLayer The {@link Layer} to switch to
     */
    protected static void toggleLayer(Layer toLayer) {
        final var mapwithai = MapWithAIDataUtils.getLayer(false);
        final var currentLayer = MainApplication.getLayerManager().getActiveLayer();
        if (currentLayer != null) {
            if (currentLayer.equals(mapwithai) && (toLayer != null)) {
                MainApplication.getLayerManager().setActiveLayer(toLayer);
            } else if (currentLayer.equals(toLayer) && (mapwithai != null)) {
                MainApplication.getLayerManager().setActiveLayer(mapwithai);
            }
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
        if (this.isEnabled()) {
            if (MapWithAIDataUtils.getLayer(false) == null) {
                putValue(Action.NAME, tr(DOWNLOAD_DATA, MapWithAIPlugin.NAME));
            } else {
                putValue(Action.NAME, tr(SWITCH_LAYERS, MapWithAIPlugin.NAME));
            }
        }
    }

    /**
     * Create a message dialog to notify the user if data is available in their
     * downloaded region
     *
     * @return A Notification to show ({@link Notification#show})
     */
    public static Notification createMessageDialog() {
        final var layer = MapWithAIDataUtils.getLayer(false);
        final var notification = layer == null ? null : new Notification();
        if (notification != null) {
            final var bounds = new ArrayList<>(layer.getDataSet().getDataSourceBounds());
            if (bounds.isEmpty()) {
                MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                        .map(OsmDataLayer::getDataSet).filter(Objects::nonNull).map(DataSet::getDataSourceBounds)
                        .forEach(bounds::addAll);
            }
            final var message = new StringBuilder();
            message.append(MapWithAIPlugin.NAME).append(": ");
            DataAvailability.getInstance(); // force initialization, if it hasn't already occured
            final var availableTypes = new TreeMap<String, Boolean>();
            for (final var bound : bounds) {
                DataAvailability.getDataTypes(bound.getCenter())
                        .forEach((type, available) -> availableTypes.merge(type, available, Boolean::logicalOr));
            }
            if (availableTypes.isEmpty()) {
                message.append(tr("No data available"));
            } else {
                message.append("Data available: ").append(String.join(", ", availableTypes.keySet()));
            }

            notification.setContent(message.toString());
            notification.setDuration(Notification.TIME_DEFAULT);
            notification.setIcon(JOptionPane.INFORMATION_MESSAGE);
        }
        return notification;
    }
}
