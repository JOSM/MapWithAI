// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.visitor.MergeSourceBuildingVisitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnable;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.bugreport.ReportedException;

/**
 * Move primitives between datasets (*not* a copy)
 *
 * @author Taylor Smock
 */
public class MovePrimitiveDataSetCommand extends Command {
    private Command command;

    /**
     * Move primitives from one dataset to another
     *
     * @param to         The destination dataset
     * @param from       The originating dataset
     * @param primitives The primitives to move
     */
    public MovePrimitiveDataSetCommand(DataSet to, DataSet from, Collection<OsmPrimitive> primitives) {
        super(to);
        if (from == null || to.isLocked() || from.isLocked() || to.equals(from)) {
            Logging.error("{0}: Cannot move primitives from {1} to {2}", MapWithAIPlugin.NAME, from, to);
        } else {
            command = moveCollection(from, to, primitives);
        }
    }

    /**
     * Move primitives from one dataset to another
     *
     * @param to            The destination dataset
     * @param from          The originating dataset
     * @param primitives    The primitives to move
     * @param primitiveData A collection to be add the primitive data to (important
     *                      if any positive ids are available)
     */
    public MovePrimitiveDataSetCommand(DataSet to, DataSet from, Collection<OsmPrimitive> primitives,
            Collection<PrimitiveData> primitiveData) {
        super(to);
        if (from == null || to.isLocked() || from.isLocked() || to.equals(from)) {
            Logging.error("{0}: Cannot move primitives from {1} to {2}", MapWithAIPlugin.NAME, from, to);
        } else {
            command = moveCollection(from, to, primitives, primitiveData);
        }
    }

    @Override
    public boolean executeCommand() {
        if (command != null) {
            // DeleteCommand is used when the MapWithAI layer has been deleted.
            if (command instanceof DeleteCommand) {
                command.undoCommand();
            } else {
                command.getAffectedDataSet().update(command::executeCommand);
            }
        }
        return true;
    }

    /**
     * Move primitives from one dataset to another
     *
     * @param to        The receiving dataset
     * @param from      The sending dataset
     * @param selection The primitives to move
     * @return The command that does the actual move
     */
    public static Command moveCollection(DataSet from, DataSet to, Collection<OsmPrimitive> selection) {
        return moveCollection(from, to, selection, new HashSet<>());
    }

    /**
     * Move primitives from one dataset to another
     *
     * @param to            The receiving dataset
     * @param from          The sending dataset
     * @param selection     The primitives to move
     * @param primitiveData A collection to be add the primitive data to (important
     *                      if any positive ids are available)
     * @return The command that does the actual move
     */
    public static Command moveCollection(DataSet from, DataSet to, Collection<OsmPrimitive> selection,
            Collection<PrimitiveData> primitiveData) {
        final var commands = new ArrayList<Command>();

        final var selected = from.getAllSelected();
        GuiHelper.runInEDTAndWait(() -> from.setSelected(selection));
        final var builder = new MergeSourceBuildingVisitor(from);
        final var hull = builder.build();
        GuiHelper.runInEDTAndWait(() -> from.setSelected(selected));

        final var primitiveAddData = hull.allPrimitives().stream().map(OsmPrimitive::save).toList();
        primitiveAddData.stream().map(data -> {
            if (data.getUniqueId() > 0) {
                // Don't do this with conn data?
                data.clearOsmMetadata();
            }
            return data;
        }).forEach(data -> data.remove(GetDataRunnable.MAPWITHAI_SOURCE_TAG_KEY));
        primitiveData.addAll(primitiveAddData);

        commands.add(new AddPrimitivesCommand(primitiveAddData,
                selection.stream().map(OsmPrimitive::save).collect(Collectors.toList()), to));
        final var removeKeyCommand = new ArrayList<Command>();
        final var fullSelection = new HashSet<OsmPrimitive>();
        MapWithAIDataUtils.addPrimitivesToCollection(fullSelection, selection);
        if (!fullSelection.isEmpty()) {
            CreateConnectionsCommand.getConflationCommands().forEach(clazz -> {
                try {
                    removeKeyCommand.add(new ChangePropertyCommand(fullSelection,
                            clazz.getConstructor(DataSet.class).newInstance(from).getKey(), null));
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    Logging.error(e);
                }
            });
        }
        Command delete;
        if (!removeKeyCommand.isEmpty()) {
            final var sequence = new SequenceCommand("Temporary Command", removeKeyCommand);
            sequence.executeCommand(); // This *must* be executed for the delete command to get everything.
            delete = DeleteCommand.delete(selection, true, true);
            sequence.undoCommand();
        } else {
            delete = DeleteCommand.delete(selection, true, true);
        }
        commands.add(delete);
        commands.removeIf(Objects::isNull);

        if (!commands.isEmpty()) {
            return SequenceCommand.wrapIfNeeded(trn("Move {0} OSM Primitive between data sets",
                    "Move {0} OSM Primitives between data sets", selection.size(), selection.size()), commands);
        }
        return null;
    }

    @Override
    public void undoCommand() {
        if (command != null) {
            try {
                if (command instanceof DeleteCommand) {
                    command.executeCommand();
                } else {
                    command.undoCommand();
                }
            } catch (ReportedException | AssertionError e) {
                if (!e.getMessage().contains("Primitive is of wrong data set for this command")) {
                    throw e;
                }
                command = DeleteCommand.delete(command.getParticipatingPrimitives());
                command.executeCommand();
            }
        }
    }

    @Override
    public String getDescriptionText() {
        return tr("Move OsmPrimitives between layers");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        command.fillModifiedData(modified, deleted, added);
    }

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return Optional.ofNullable(command).map(Command::getParticipatingPrimitives).orElseGet(Collections::emptySet);
    }
}
