// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.GBC;

/**
 * @author Taylor Smock
 *
 */
public class MapWithAILayer extends OsmDataLayer {
    private Integer maximumAddition = null;
    private String url = null;
    private Boolean switchLayers = null;
    private final Lock lock;

    /**
     * Create a new MapWithAI layer
     *
     * @param data           OSM data from MapWithAI
     * @param name           Layer name
     * @param associatedFile an associated file (can be null)
     */
    public MapWithAILayer(DataSet data, String name, File associatedFile) {
        super(data, name, associatedFile);
        data.setUploadPolicy(UploadPolicy.BLOCKED);
        data.setDownloadPolicy(DownloadPolicy.BLOCKED);
        lock = new MapLock();
    }

    // @Override TODO remove comment on 2020-01-01
    public String getChangesetSourceTag() {
        if (MapWithAIDataUtils.getAddedObjects() > 0) {
            return "MapWithAI";
        }
        return null;
    }

    public void setMaximumAddition(Integer max) {
        maximumAddition = max;
    }

    public Integer getMaximumAddition() {
        return maximumAddition;
    }

    public void setMapWithAIUrl(String url) {
        this.url = url;
    }

    public String getMapWithAIUrl() {
        return url;
    }

    public void setSwitchLayers(boolean selected) {
        switchLayers = selected;
    }

    public Boolean isSwitchLayers() {
        return switchLayers;
    }

    @Override
    public Object getInfoComponent() {
        final Object p = super.getInfoComponent();
        if (p instanceof JPanel) {
            final JPanel panel = (JPanel) p;
            if (maximumAddition != null) {
                panel.add(new JLabel(tr("Maximum Additions: {0}", maximumAddition), SwingConstants.HORIZONTAL),
                        GBC.eop().insets(15, 0, 0, 0));
            }
            if (url != null) {
                panel.add(new JLabel(tr("URL: {0}", url), SwingConstants.HORIZONTAL), GBC.eop().insets(15, 0, 0, 0));
            }
            if (switchLayers != null) {
                panel.add(new JLabel(tr("Switch Layers: {0}", switchLayers), SwingConstants.HORIZONTAL),
                        GBC.eop().insets(15, 0, 0, 0));
            }
        }
        return p;
    }

    @Override
    public Action[] getMenuEntries() {
        final List<Action> actions = Arrays.asList(super.getMenuEntries()).stream()
                .filter(action -> !(action instanceof LayerSaveAction) && !(action instanceof LayerSaveAsAction))
                .collect(Collectors.toList());
        return actions.toArray(new Action[0]);
    }

    public Lock getLock() {
        return lock;
    }

    private class MapLock extends ReentrantLock {
        private static final long serialVersionUID = 5441350396443132682L;
        private boolean dataSetLocked;

        public MapLock() {
            // Do nothing
        }

        @Override
        public void lock() {
            super.lock();
            dataSetLocked = getDataSet().isLocked();
            if (dataSetLocked) {
                getDataSet().unlock();
            }
        }

        @Override
        public void unlock() {
            super.unlock();
            if (dataSetLocked) {
                getDataSet().lock();
            }
        }
    }
}
