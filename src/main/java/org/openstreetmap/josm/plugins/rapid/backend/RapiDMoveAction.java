package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Shortcut;

public class RapiDMoveAction extends JosmAction {
	/** UID for abstract action */
	private static final long serialVersionUID = 319374598;

	public RapiDMoveAction() {
		super(tr("Add from ".concat(RapiDPlugin.NAME)), null, tr("Add data from RapiD"), Shortcut.registerShortcut(
				"data:rapidadd", tr("Rapid: {0}", tr("Add selected data")), KeyEvent.VK_A, Shortcut.SHIFT), true);
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
