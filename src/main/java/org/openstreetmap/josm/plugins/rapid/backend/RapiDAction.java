package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Shortcut;

public class RapiDAction extends JosmAction {
    /** UID */
    private static final long serialVersionUID = 8886705479253246588L;

    private static final Object layerLock = new Object();

    public RapiDAction() {
        super(RapiDPlugin.NAME, null, tr("Get data from RapiD"),
                Shortcut.registerShortcut("data:rapid", tr("Data: {0}", tr("RapiD")), KeyEvent.VK_R, Shortcut.SHIFT),
                true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        final RapiDLayer layer = getLayer(true);
        getRapiDData(layer);
    }

    /**
     * Get the first {@link RapiDLayer} that we can find.
     *
     * @param create true if we want to create a new layer
     * @return A RapiDLayer, or a new RapiDLayer if none exist. May return
     *         {@code null} if {@code create} is {@code false}.
     */
    public static RapiDLayer getLayer(boolean create) {
        final List<RapiDLayer> rapidLayers = MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class);
        RapiDLayer layer;
        synchronized (layerLock) {
            if (rapidLayers.isEmpty() && create) {
                layer = new RapiDLayer(new DataSet(), RapiDPlugin.NAME, null);
                MainApplication.getLayerManager().addLayer(layer);
            } else if (!rapidLayers.isEmpty()) {
                layer = rapidLayers.get(0);
            } else {
                layer = null;
            }
        }
        return layer;
    }

    /**
     * Get data for a {@link RapiDLayer}
     *
     * @param layer The {@link RapiDLayer} to add data to
     */
    public static void getRapiDData(RapiDLayer layer) {
        final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
        for (final OsmDataLayer osmLayer : osmLayers) {
            if (!osmLayer.isLocked()) {
                getRapiDData(layer, osmLayer);
            }
        }
    }

    /**
     * Get the data for RapiD
     *
     * @param layer    A pre-existing {@link RapiDLayer}
     * @param osmLayer The osm datalayer with a set of bounds
     */
    public static void getRapiDData(RapiDLayer layer, OsmDataLayer osmLayer) {
        final DataSet editSet = osmLayer.getDataSet();
        final List<Bounds> editSetBounds = editSet.getDataSourceBounds();
        final DataSet rapidSet = layer.getDataSet();
        final List<Bounds> rapidBounds = rapidSet.getDataSourceBounds();
        for (final Bounds bound : editSetBounds) {
            // TODO remove bounds that are already downloaded
            if (rapidBounds.parallelStream().filter(bound::equals).count() == 0) {
                final DataSet newData = RapiDDataUtils.getData(bound.toBBox());
                /* Microsoft buildings don't have a source, so we add one */
                RapiDDataUtils.addSourceTags(newData, "building", "Microsoft");
                synchronized (layerLock) {
                    layer.unlock();
                    layer.mergeFrom(newData);
                    layer.lock();
                }
            }
        }
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null);
    }
}
