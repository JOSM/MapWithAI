// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.io.mapwithai;

import java.io.InputStream;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.tools.Logging;

/**
 * Parser for the Osm API (XML output). Read from an input stream and construct
 * a dataset out of it.
 *
 * For each xml element, there is a dedicated method. The XMLStreamReader cursor
 * points to the start of the element, when the method is entered, and it must
 * point to the end of the same element, when it is exited.
 */
public class OsmReaderCustom extends OsmReader {
    protected OsmReaderCustom(boolean convertUnknownToTags) {
        // Restricts visibility
        super(Options.CONVERT_UNKNOWN_TO_TAGS);
    }

    // check every so often to see if I can keep original negative ids
    // See https://josm.openstreetmap.de/ticket/18258 (TODO) // NOSONAR
    @Override
    protected OsmPrimitive buildPrimitive(PrimitiveData pd) {
        final Long serverId = pd.getUniqueId();
        if (pd.getIdGenerator().currentUniqueId() < pd.getUniqueId()) {
            Logging.trace("Current id: {0} (wants {1})", pd.getIdGenerator().currentUniqueId(), pd.getUniqueId());
        }
        OsmPrimitive p;
        if (pd.getUniqueId() < pd.getIdGenerator().currentUniqueId()) {
            p = pd.getType().newInstance(pd.getUniqueId(), true);
        } else {
            p = pd.getType().newVersionedInstance(pd.getId(), pd.getVersion());
        }
        p.setVisible(pd.isVisible());
        p.load(pd);
        externalIdMap.put(pd.getPrimitiveId(), p);
        p.put("server_id", Long.toString(serverId));
        return p;
    }

    /**
     * Parse the given input source and return the dataset.
     *
     * @param source               the source input stream. Must not be null.
     * @param progressMonitor      the progress monitor. If null,
     *                             {@link NullProgressMonitor#INSTANCE} is assumed
     * @param convertUnknownToTags true if unknown xml attributes should be kept as
     *                             tags
     *
     * @return the dataset with the parsed data
     * @throws IllegalDataException     if an error was found while parsing the data
     *                                  from the source
     * @throws IllegalArgumentException if source is null
     * @since xxx
     */
    public static DataSet parseDataSet(InputStream source, ProgressMonitor progressMonitor,
            boolean convertUnknownToTags) throws IllegalDataException {
        return new OsmReaderCustom(convertUnknownToTags).doParseDataSet(source, progressMonitor);
    }
}
