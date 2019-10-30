// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import java.io.InputStream;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;

/**
 * TODO remove this class in January 2019 (if required patch is pulled)
 * Parser for the Osm API (XML output). Read from an input stream and construct a dataset out of it.
 *
 * For each xml element, there is a dedicated method.
 * The XMLStreamReader cursor points to the start of the element, when the method is
 * entered, and it must point to the end of the same element, when it is exited.
 */
public class OsmReaderCustom extends OsmReader {
    protected OsmReaderCustom(boolean convertUnknownToTags) {
        // Restricts visibility
        super();
        // TODO when we update to r15470 this.convertUnknownToTags =
        // convertUnknownToTags;
    }

    @Override
    protected OsmPrimitive buildPrimitive(PrimitiveData pd) {
        final Long serverId = pd.getUniqueId();
        final OsmPrimitive p = super.buildPrimitive(pd);
        p.put("server_id", Long.toString(serverId));
        return p;
    }

    /**
     * Parse the given input source and return the dataset.
     *
     * @param source the source input stream. Must not be null.
     * @param progressMonitor the progress monitor. If null, {@link NullProgressMonitor#INSTANCE} is assumed
     * @param convertUnknownToTags true if unknown xml attributes should be kept as tags
     *
     * @return the dataset with the parsed data
     * @throws IllegalDataException if an error was found while parsing the data from the source
     * @throws IllegalArgumentException if source is null
     * @since xxx
     */
    public static DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor,
            boolean convertUnknownToTags)
                    throws IllegalDataException {
        return new OsmReaderCustom(convertUnknownToTags).doParseDataSet(source, progressMonitor);
    }
}