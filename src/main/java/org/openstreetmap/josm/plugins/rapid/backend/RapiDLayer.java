// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import java.io.File;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * @author Taylor Smock
 *
 */
public class RapiDLayer extends OsmDataLayer {

    /**
     * Create a new RapiD layer
     * @param data OSM data from rapid
     * @param name Layer name
     * @param associatedFile an associated file (can be null)
     */
    public RapiDLayer(DataSet data, String name, File associatedFile) {
        super(data, name, associatedFile);
        this.lock();
        this.setUploadDiscouraged(true);
    }

	// @Override only JOSM > 15323
	public String getChangesetSourceTag() {
		return "MapWithAI";
	}
}
