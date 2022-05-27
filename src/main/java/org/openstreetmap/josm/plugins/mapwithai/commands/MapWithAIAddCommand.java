// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnable;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAILayer;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;

public class MapWithAIAddCommand extends Command implements Runnable {
    private final DataSet editable;
    private final DataSet mapWithAI;
    private final Collection<OsmPrimitive> primitives;
    Command command;
    private Lock lock;
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
        Collection<Way> nodeReferrers = Utils.filteredCollection(selection, Node.class).stream().map(Node::getReferrers)
                .flatMap(List::stream).filter(Way.class::isInstance).map(Way.class::cast).collect(Collectors.toList());
        this.primitives = new HashSet<>(selection);
        this.primitives.addAll(nodeReferrers);
        sources = selection.stream()
                .map(prim -> new Pair<>(prim,
                        prim.hasKey("source") ? prim.get("source")
                                : prim.get(GetDataRunnable.MAPWITHAI_SOURCE_TAG_KEY)))
                .filter(pair -> pair.b != null).collect(Collectors.toMap(pair -> pair.a, pair -> pair.b));
    }

    @Override
    public boolean executeCommand() {
        GuiHelper.runInEDTAndWait(this);
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
                    command = SequenceCommand.wrapIfNeeded(getDescriptionText(), movePrimitivesCommand,
                            createConnectionsCommand);
                }
                GuiHelper.runInEDTAndWait(command::executeCommand);
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
                    GuiHelper.runInEDTAndWait(command::undoCommand);
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

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return Stream
                .of(Optional.ofNullable(command).map(Command::getParticipatingPrimitives)
                        .orElseGet(Collections::emptySet), primitives)
                .flatMap(Collection::stream).collect(Collectors.toSet());
    }

    /**
     * Calculate the number of objects added in this command that are not deleted
     * (may not count significantly modified objects as well).
     *
     * @return The number of MapWithAI objects added in this command that are not
     *         deleted
     */
    public long getAddedObjects() {
        long returnLong;
        if (this.equals(UndoRedoHandler.getInstance().getLastCommand())) {
            returnLong = primitives.size();
        } else {
            returnLong = primitives.stream().map(editable::getPrimitiveById).filter(Objects::nonNull)
                    .filter(MapWithAIAddCommand::validPrimitive).count();
        }
        return returnLong;
    }

    public Collection<String> getSourceTags() {
        return sources.entrySet().stream().filter(entry -> validPrimitive(editable.getPrimitiveById(entry.getKey())))
                .map(Map.Entry::getValue).filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
    }

    private static boolean validPrimitive(OsmPrimitive prim) {
        return prim != null && (!prim.isDeleted() || prim instanceof Node);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof MapWithAIAddCommand) {
            MapWithAIAddCommand o = (MapWithAIAddCommand) other;
            return Objects.equals(this.editable, o.editable) && Objects.equals(this.mapWithAI, o.mapWithAI)
                    && Objects.equals(this.lock, o.lock) && o.primitives.containsAll(this.primitives)
                    && this.primitives.containsAll(o.primitives);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(editable, mapWithAI, primitives, lock);
    }
}
