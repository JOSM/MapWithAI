// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.Notification;
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
        if (isEnabled()) {
            MapWithAIDataUtils.getMapWithAIData(MapWithAIDataUtils.getLayer(true));
            createMessageDialog();
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
    }

    public void createMessageDialog() {
        final MapWithAILayer layer = MapWithAIDataUtils.getLayer(false);
        if (layer != null) {
            final Notification notification = new Notification();
            final List<Bounds> bounds = layer.getDataSet().getDataSourceBounds();
            final StringBuilder message = new StringBuilder();
            message.append(MapWithAIPlugin.NAME);
            message.append(": ");
            final MapWithAIAvailability availability = MapWithAIAvailability.getInstance();
            final Map<String, Boolean> availableTypes = new TreeMap<>();
            for (final Bounds bound : bounds) {
                availability.getDataTypes(bound.getCenter())
                .forEach((type, available) -> availableTypes.merge(type, available, Boolean::logicalOr));
            }
            final List<String> types = availableTypes.entrySet().stream().filter(Entry::getValue)
                    .map(entry -> MapWithAIAvailability.getPossibleDataTypesAndMessages().get(entry.getKey()))
                    .collect(Collectors.toList());
            if (types.isEmpty()) {
                message.append(tr("No data available"));
            } else {
                message.append("Data available: ");
                message.append(String.join(", ", types));
            }

            notification.setContent(message.toString());
            notification.setDuration(Notification.TIME_LONG);
            notification.setIcon(JOptionPane.INFORMATION_MESSAGE);
            notification.show();
        }
    }
}
