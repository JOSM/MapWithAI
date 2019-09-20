// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import javax.swing.JMenu;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDAction;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDMoveAction;

public final class RapiDPlugin extends Plugin {
	/** The name of the plugin */
	public static final String NAME = "RapiD";

	public RapiDPlugin(PluginInformation info) {
		super(info);

		JMenu dataMenu = MainApplication.getMenu().dataMenu;
		MainMenu.add(dataMenu, new RapiDAction(), false);
		MainMenu.add(dataMenu, new RapiDMoveAction(), false);
	}
}
