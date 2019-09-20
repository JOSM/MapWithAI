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

	public RapiDAction() {
		super(RapiDPlugin.NAME, null, tr("Get data from RapiD"),
				Shortcut.registerShortcut("data:rapid", tr("Data: {0}", tr("RapiD")), KeyEvent.VK_D, Shortcut.SHIFT),
				true);
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		final RapiDLayer layer = getLayer();
		getRapiDData(layer);
	}

	public RapiDLayer getLayer() {
		final List<RapiDLayer> rapidLayers = MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class);
		RapiDLayer layer;
		synchronized (this) {
			if (rapidLayers.isEmpty()) {
				layer = new RapiDLayer(new DataSet(), RapiDPlugin.NAME, null);
				MainApplication.getLayerManager().addLayer(layer);
			} else {
				layer = rapidLayers.get(0);
			}
		}
		return layer;
	}

	public void getRapiDData(RapiDLayer layer) {
		final List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
		for (final OsmDataLayer osmLayer : osmLayers) {
			if (!osmLayer.isLocked()) {
				getRapiDData(layer, osmLayer);
			}
		}
	}

	public void getRapiDData(RapiDLayer layer, OsmDataLayer osmLayer) {
		final DataSet editSet = osmLayer.getDataSet();
		final List<Bounds> editSetBounds = editSet.getDataSourceBounds();
		final DataSet rapidSet = layer.getDataSet();
		final List<Bounds> rapidBounds = rapidSet.getDataSourceBounds();
		for (final Bounds bound : editSetBounds) {
			if (!rapidBounds.contains(bound)) {
				final DataSet newData = RapiDDataUtils.getData(bound.toBBox());
				synchronized (layer) {
					layer.unlock();
					layer.mergeFrom(newData);
					layer.lock();
				}
			}
		}
	}
}
