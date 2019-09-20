package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractAction;

import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;

public class RapiDMoveAction extends AbstractAction {
	/** UID for abstract action */
	private static final long serialVersionUID = 319374598;

	public RapiDMoveAction() {
		super(tr("Add from ".concat(RapiDPlugin.NAME)));
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		for (RapiDLayer layer : MainApplication.getLayerManager().getLayersOfType(RapiDLayer.class)) {
			List<OsmDataLayer> osmLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
			DataSet editData = null;
			DataSet rapid = layer.getDataSet();
			Collection<OsmPrimitive> selected = rapid.getSelected();
			for (OsmDataLayer osmLayer : osmLayers) {
				if (!osmLayer.isLocked() && osmLayer.isVisible() && osmLayer.isUploadable()
						&& osmLayer.getClass().equals(OsmDataLayer.class)) {
					editData = osmLayer.getDataSet();
					break;
				}
			}
			if (editData != null) {
				RapiDAddCommand command = new RapiDAddCommand(rapid, editData, selected);
				UndoRedoHandler.getInstance().add(command);
			}
		}
	}
}
