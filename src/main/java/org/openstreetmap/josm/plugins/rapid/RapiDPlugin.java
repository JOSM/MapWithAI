// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.rapid.backend.RapiDAction;

public final class RapiDPlugin extends Plugin {
	/** The name of the plugin */
	public static final String NAME = "RapiD";

	public RapiDPlugin(PluginInformation info) {
		super(info);

		RapiDAction action = new RapiDAction();
		MainApplication.getMenu().dataMenu.add(action);
		MainApplication.getMenu().fileMenu.add(action);
	}
}
