package org.openstreetmap.josm.plugins.rapid.backend;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;

public class RapiDAction extends AbstractAction {
	/** UID */
	private static final long serialVersionUID = 8886705479253246588L;

	public RapiDAction() {
		super(RapiDPlugin.NAME);
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
