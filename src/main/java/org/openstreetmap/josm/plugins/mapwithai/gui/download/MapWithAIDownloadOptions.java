// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.gui.download;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.bbox.JosmMapViewer;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.download.DownloadSelection;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.gui.preferences.mapwithai.MapWithAIProvidersPanel;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.GBC;

public class MapWithAIDownloadOptions extends JPanel implements DownloadSelection, Destroyable {
    private final JPanel optionPanel;
    private DownloadDialog iGui;
    private JosmMapViewer defaultMap;
    private MapWithAIProvidersPanel mapwithaiProvidersPanel;

    public MapWithAIDownloadOptions() {
        optionPanel = new JPanel(new GridBagLayout());
        JPanel favorites = new JPanel();
        favorites.add(new JLabel("TODO: Favorites go here!")); // TODO
        optionPanel.add(favorites, GBC.eol().fill(GBC.HORIZONTAL).anchor(GBC.NORTH));
        mapwithaiProvidersPanel = new MapWithAIProvidersPanel(this, MapWithAILayerInfo.getInstance());
        optionPanel.add(mapwithaiProvidersPanel, GBC.eol().fill(GBC.HORIZONTAL).anchor(GBC.CENTER));
    }

    @Override
    public void addGui(DownloadDialog gui) {
        iGui = gui;
        iGui.addDownloadAreaSelector(optionPanel, tr("Browse Data Sources"));
    }

    @Override
    public void setDownloadArea(Bounds area) {
        // TODO
    }

    @Override
    public void destroy() {
        if (this.iGui != null) {
            this.iGui.remove(this);
        }
    }

}
