// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.DownloadPrimitivesTask;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Pair;

public abstract class AbstractConflationCommand extends Command {
    Collection<OsmPrimitive> possiblyAffectedPrimitives;

    public AbstractConflationCommand(DataSet data) {
        super(data);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        // Do nothing -- the sequence commands should take care of it.
    }

    /**
     * @return The types of primitive that the command is interested in
     */
    public abstract Collection<Class<? extends OsmPrimitive>> getInterestedTypes();

    /**
     * @return The key that the command is interested in
     */
    public abstract String getKey();

    /**
     * @param primitives The primitives to run the command on
     * @return The command that will be run (may be {@code null})
     */
    public Command getCommand(List<OsmPrimitive> primitives) {
        possiblyAffectedPrimitives = primitives.stream().distinct().collect(Collectors.toList());
        return getRealCommand();
    }

    /**
     * @return The command to do whatever is required for the result
     */
    public abstract Command getRealCommand();

    /**
     * Get the primitives from a dataset with specified ids
     *
     * @param dataSet The dataset holding the primitives (hopefully)
     * @param ids     The ids formated like
     *                n&lt;NUMBER&gt;,r&lt;NUMBER&gt;,w&lt;NUMBER&gt;
     * @return The primitives that the ids point to, if in the dataset.
     */
    public static OsmPrimitive[] getPrimitives(DataSet dataSet, String ids) {
        final Map<Integer, Pair<Long, OsmPrimitiveType>> missingPrimitives = new TreeMap<>();
        final String[] connections = ids.split(",", -1);
        final OsmPrimitive[] primitiveConnections = new OsmPrimitive[connections.length];
        for (int i = 0; i < connections.length; i++) {
            final String member = connections[i];
            final char firstChar = member.charAt(0);
            OsmPrimitiveType type = null;
            if (firstChar == 'w') {
                type = OsmPrimitiveType.WAY;
            } else if (firstChar == 'n') {
                type = OsmPrimitiveType.NODE;
            } else if (firstChar == 'r') {
                type = OsmPrimitiveType.RELATION;
            } else {
                throw new IllegalArgumentException(
                        tr("{0}: We don't know how to handle {1} types", MapWithAIPlugin.NAME, firstChar));
            }
            final long id = Long.parseLong(member.substring(1));
            primitiveConnections[i] = dataSet.getPrimitiveById(id, type);
            if (primitiveConnections[i] == null) {
                missingPrimitives.put(i, new Pair<>(id, type));
            }
        }
        obtainMissingPrimitives(dataSet, primitiveConnections, missingPrimitives);
        return primitiveConnections;
    }

    private static void obtainMissingPrimitives(DataSet dataSet, OsmPrimitive[] primitiveConnections,
            Map<Integer, Pair<Long, OsmPrimitiveType>> missingPrimitives) {
        if (!missingPrimitives.isEmpty()) {
            final Map<PrimitiveId, Integer> ids = missingPrimitives.entrySet().stream().collect(Collectors
                    .toMap(entry -> new SimplePrimitiveId(entry.getValue().a, entry.getValue().b), Entry::getKey));
            final List<PrimitiveId> toFetch = new ArrayList<>(ids.keySet());
            final Optional<OsmDataLayer> optionalLayer = MainApplication.getLayerManager()
                    .getLayersOfType(OsmDataLayer.class).parallelStream()
                    .filter(layer -> layer.getDataSet().equals(dataSet)).findFirst();

            OsmDataLayer layer;
            final String generatedLayerName = "EvKlVarShAiAllsM generated layer";
            if (optionalLayer.isPresent()) {
                layer = optionalLayer.get();
            } else {
                layer = new OsmDataLayer(dataSet, generatedLayerName, null);
            }

            final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor(
                    tr("Downloading additional OsmPrimitives"));
            final DownloadPrimitivesTask downloadPrimitivesTask = new DownloadPrimitivesTask(layer, toFetch, true,
                    monitor);
            downloadPrimitivesTask.run();
            for (final Entry<PrimitiveId, Integer> entry : ids.entrySet()) {
                final int index = entry.getValue().intValue();
                final OsmPrimitive primitive = dataSet.getPrimitiveById(entry.getKey());
                primitiveConnections[index] = primitive;
            }

            if (generatedLayerName.equals(layer.getName())) {
                layer.destroy();
            }
        }
    }
}
