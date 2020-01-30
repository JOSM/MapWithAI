// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnable;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;

public class MapWithAIAddCommand extends Command implements Runnable {
    DataSet editable;
    DataSet mapWithAI;
    Collection<OsmPrimitive> primitives;
    Command command;
    Lock lock;
    final Map<OsmPrimitive, String> sources;

    /**
     * Add primitives from MapWithAI to the OSM data layer
     *
     * @param mapWithAILayer The MapWithAI layer
     * @param editLayer      The OSM layer
     * @param selection      The primitives to add from MapWithAI
     */
    public MapWithAIAddCommand(MapWithAILayer mapWithAILayer, OsmDataLayer editLayer,
            Collection<OsmPrimitive> selection) {
        this(mapWithAILayer.getDataSet(), editLayer.getDataSet(), selection);
        lock = mapWithAILayer.getLock();
    }

    /**
     * Add primitives from MapWithAI to the OSM data layer
     *
     * @param mapWithAI The MapWithAI dataset
     * @param editable  The OSM dataset
     * @param selection The primitives to add from MapWithAI
     */
    public MapWithAIAddCommand(DataSet mapWithAI, DataSet editable, Collection<OsmPrimitive> selection) {
        super(editable);
        this.mapWithAI = mapWithAI;
        this.editable = editable;
        Collection<Way> nodeReferrers = selection.parallelStream().filter(Node.class::isInstance).map(Node.class::cast)
                .map(Node::getReferrers).flatMap(List::stream).filter(Way.class::isInstance).map(Way.class::cast)
                .collect(Collectors.toList());
        this.primitives = new HashSet<>(selection);
        this.primitives.addAll(nodeReferrers);
        sources = selection.parallelStream()
                .map(prim -> new Pair<OsmPrimitive, String>(prim, prim.get(GetDataRunnable.MAPWITHAI_SOURCE_TAG_KEY)))
                .filter(pair -> pair.b != null).collect(Collectors.toMap(pair -> pair.a, pair -> pair.b));
    }

    @Override
    public boolean executeCommand() {
        if (EventQueue.isDispatchThread()) {
            new Thread((Runnable) this::run, getClass().getName()).start();
        } else {
            run();
        }
        return true;
    }

    @Override
    public void run() {
        if (mapWithAI.equals(editable)) {
            Logging.error("{0}: DataSet mapWithAI ({1}) should not be the same as DataSet editable ({2})",
                    MapWithAIPlugin.NAME, mapWithAI, editable);
            throw new IllegalArgumentException();
        }
        synchronized (this) {
            try {
                if (lock != null) {
                    lock.lock();
                }
                if (command == null) {// needed for undo/redo (don't create a new command)
                    final List<OsmPrimitive> allPrimitives = new ArrayList<>();
                    MapWithAIDataUtils.addPrimitivesToCollection(allPrimitives, primitives);
                    Collection<PrimitiveData> primitiveData = new HashSet<>();
                    final Command movePrimitivesCommand = new MovePrimitiveDataSetCommand(editable, mapWithAI,
                            primitives, primitiveData);
                    final Command createConnectionsCommand = createConnections(editable, primitiveData);
                    command = new SequenceCommand(getDescriptionText(), movePrimitivesCommand,
                            createConnectionsCommand);
                }
                command.executeCommand();
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Create connections based off of current MapWithAI syntax
     *
     * @param dataSet    The {@link DataSet} that should have the primitives we are
     *                   connecting to
     * @param collection The primitives with connection information (currently only
     *                   checks Nodes)
     * @return A Command to create connections from the collection
     */
    public static Command createConnections(DataSet dataSet, Collection<PrimitiveData> collection) {
        return new CreateConnectionsCommand(dataSet, collection);
    }

    @Override
    public void undoCommand() {
        try {
            if (lock != null) {
                lock.lock();
            }
            synchronized (this) {
                if (command != null) {
                    command.undoCommand();
                }
            }
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    @Override
    public String getDescriptionText() {
        return tr("Add object from {0}", MapWithAIPlugin.NAME);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.addAll(primitives);
    }

    /**
     * @return The number of MapWithAI objects added in this command that are not
     *         deleted
     */
    public Long getAddedObjects() {
        Long returnLong;
        if (this.equals(UndoRedoHandler.getInstance().getLastCommand())) {
            returnLong = Long.valueOf(primitives.size());
        } else {
            returnLong = primitives.stream().map(editable::getPrimitiveById).filter(Objects::nonNull)
                    .filter(prim -> !prim.isDeleted()).count();
        }
        return returnLong;
    }

    public Collection<String> getSourceTags() {
        return sources.entrySet().parallelStream()
                .filter(entry -> editable.getPrimitiveById(entry.getKey()) != null
                        && !editable.getPrimitiveById(entry.getKey()).isDeleted())
                .map(Entry::getValue).filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object other) {
        boolean returnBoolean = false;
        if (other instanceof MapWithAIAddCommand && hashCode() == other.hashCode()) {
            returnBoolean = true;
        }
        return returnBoolean;
    }

    @Override
    public int hashCode() {
        return Objects.hash(editable, mapWithAI, primitives, lock);
    }
}
