// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.bbox.BBoxChooser;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.download.DownloadSelection;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai.MapWithAIProvidersPanel;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.GBC;

/**
 * Add options for MapWithAI downloads to the main JOSM download window
 *
 * @author Taylor Smock
 */
public class MapWithAIDownloadOptions extends JPanel implements DownloadSelection, Destroyable, PropertyChangeListener {
    private final JPanel optionPanel;
    private DownloadDialog iGui;
    private final MapWithAIProvidersPanel mapwithaiProvidersPanel;

    /**
     * Create a new options panel
     */
    public MapWithAIDownloadOptions() {
        optionPanel = new JPanel(new GridBagLayout());
        JPanel infoHeader = new JPanel();
        infoHeader.add(new JLabel("Browse and activate extra data sets to facilitate your mapping needs."));
        optionPanel.add(infoHeader, GBC.eol().fill(GridBagConstraints.HORIZONTAL).anchor(GridBagConstraints.NORTH));
        mapwithaiProvidersPanel = new MapWithAIProvidersPanel(this);
        optionPanel.add(mapwithaiProvidersPanel,
                GBC.eol().fill(GridBagConstraints.BOTH).anchor(GridBagConstraints.CENTER));
        mapwithaiProvidersPanel.defaultMap.addPropertyChangeListener(this);
    }

    @Override
    public void addGui(DownloadDialog gui) {
        iGui = gui;
        iGui.addDownloadAreaSelector(optionPanel, tr("Browse MapWithAI Data Sources"));
        iGui.addDownloadAreaListener(this);
    }

    @Override
    public void setDownloadArea(Bounds area) {
        // This is (currently) never called.
        // See https://josm.openstreetmap.de/ticket/19310
        mapwithaiProvidersPanel.setCurrentBounds(area);
    }

    @Override
    public void destroy() {
        if (this.iGui != null) {
            for (JComponent component : getJComponents(this.iGui.getComponents())) {
                removeFromComponent(component);
            }
            this.iGui.removeDownloadAreaListener(this);
            this.iGui = null;
        }
    }

    private static JComponent[] getJComponents(Component[] components) {
        return Stream.of(components).filter(JComponent.class::isInstance).map(JComponent.class::cast)
                .toArray(JComponent[]::new);
    }

    private boolean removeFromComponent(JComponent component) {
        for (JComponent newComponent : getJComponents(component.getComponents())) {
            if (optionPanel.equals(newComponent)) {
                component.remove(optionPanel);
                return true;
            } else if (removeFromComponent(newComponent)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(BBoxChooser.BBOX_PROP) && iGui != null) {
            mapwithaiProvidersPanel.fireAreaListeners();
            iGui.boundingBoxChanged((Bounds) evt.getNewValue(), this);
        }
    }
}
