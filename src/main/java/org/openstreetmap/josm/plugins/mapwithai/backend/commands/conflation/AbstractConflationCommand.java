// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.tools.Pair;

/**
 * This is an abstract class for conflation commands
 *
 * @author Taylor Smock
 *
 */
public abstract class AbstractConflationCommand extends Command {
    protected Collection<OsmPrimitive> possiblyAffectedPrimitives;

    public AbstractConflationCommand(DataSet data) {
        super(data);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        // Do nothing -- the sequence commands should take care of it.
    }

    /**
     * Only return Node/Way/Relation here. It can be any combination.
     *
     * @return The types of primitive that the command is interested in
     */
    public abstract Collection<Class<? extends OsmPrimitive>> getInterestedTypes();

    /**
     * Return the key that the command uses to perform conflation. For example,
     * `conn` or `dupe`.
     *
     * @return The key that the command is interested in
     */
    public abstract String getKey();

    /**
     * Get the actual command to run. This should not be normally overriden by
     * subclasses. Override {@link AbstractConflationCommand#getRealCommand}
     * instead.
     *
     * @param primitives The primitives to run the command on
     * @return The command that will be run (may be {@code null})
     */
    public Command getCommand(Collection<OsmPrimitive> primitives) {
        possiblyAffectedPrimitives = primitives.stream().distinct().collect(Collectors.toList());
        return getRealCommand();
    }

    /**
     * A command that performs the conflation steps.
     *
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
        Objects.requireNonNull(dataSet, tr("DataSet cannot be null"));
        Objects.requireNonNull(ids, tr("The ids string cannot be null"));
        final Map<Integer, Pair<Long, OsmPrimitiveType>> missingPrimitives = new TreeMap<>();
        final String[] connections = ids.split(",", -1);
        final OsmPrimitive[] primitiveConnections = new OsmPrimitive[connections.length];
        for (int i = 0; i < connections.length; i++) {
            final String member = connections[i];
            SimplePrimitiveId primitiveId = SimplePrimitiveId.fromString(member);
            primitiveConnections[i] = dataSet.getPrimitiveById(primitiveId);
            if (primitiveConnections[i] == null) {
                missingPrimitives.put(i, new Pair<>(primitiveId.getUniqueId(), primitiveId.getType()));
            }
        }
        obtainMissingPrimitives(dataSet, primitiveConnections, missingPrimitives);
        return primitiveConnections;
    }

    private static void obtainMissingPrimitives(DataSet dataSet, OsmPrimitive[] primitiveConnections,
            Map<Integer, Pair<Long, OsmPrimitiveType>> missingPrimitives) {
        if (!missingPrimitives.isEmpty()) {
            final Map<PrimitiveId, Integer> ids = missingPrimitives.entrySet().stream().collect(Collectors
                    .toMap(entry -> new SimplePrimitiveId(entry.getValue().a, entry.getValue().b), Map.Entry::getKey));
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

            final ProgressMonitor monitor;
            if (GraphicsEnvironment.isHeadless()) {
                monitor = NullProgressMonitor.INSTANCE;
            } else {
                monitor = new PleaseWaitProgressMonitor(tr("Downloading additional OsmPrimitives"));
            }
            final DownloadPrimitivesTask downloadPrimitivesTask = new DownloadPrimitivesTask(layer, toFetch, true,
                    monitor);
            downloadPrimitivesTask.run();
            for (final Map.Entry<PrimitiveId, Integer> entry : ids.entrySet()) {
                final int index = entry.getValue().intValue();
                final OsmPrimitive primitive = dataSet.getPrimitiveById(entry.getKey());
                primitiveConnections[index] = primitive;
            }

            if (generatedLayerName.equals(layer.getName())) {
                layer.destroy();
            }
        }
    }

    /**
     * Use this to ensure that something that cannot be undone without errors isn't
     * undone.
     *
     * @return true if the command should show as a separate command in the
     *         undo/redo lists
     */
    public abstract boolean allowUndo();

    /**
     * `conn` and `dupe` should not exist in OSM, but `addr:street` should. This is
     * used in a validation test.
     *
     * @return {@code true} if the key should not exist in OpenStreetMap
     */
    public abstract boolean keyShouldNotExistInOSM();

    /**
     * If another command conflicts with this command, it should be returned here.
     * For example, if one command adds an addr node to a building, and another
     * command does the reverse, they conflict.
     *
     * @return Conflation commands that conflict with this conflation command
     */
    public Collection<Class<? extends AbstractConflationCommand>> conflictedCommands() {
        return Collections.emptyList();
    }
}
