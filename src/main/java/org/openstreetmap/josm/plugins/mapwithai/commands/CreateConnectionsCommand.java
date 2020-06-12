// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.AbstractConflationCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.AlreadyConflatedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DuplicateCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.MergeAddressBuildings;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.MergeBuildingAddress;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.cleanup.MissingConnectionTags;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.cleanup.OverNodedWays;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

public class CreateConnectionsCommand extends Command {
    private final Collection<PrimitiveData> primitives;
    private Command command;
    private Command undoCommands;
    private static final LinkedHashSet<Class<? extends AbstractConflationCommand>> CONFLATION_COMMANDS = new LinkedHashSet<>();
    static {
        CONFLATION_COMMANDS.add(MissingConnectionTags.class);
        CONFLATION_COMMANDS.add(ConnectedCommand.class);
        CONFLATION_COMMANDS.add(DuplicateCommand.class);
        CONFLATION_COMMANDS.add(MergeAddressBuildings.class);
        CONFLATION_COMMANDS.add(MergeBuildingAddress.class);
        CONFLATION_COMMANDS.add(OverNodedWays.class);
        CONFLATION_COMMANDS.add(AlreadyConflatedCommand.class);
    }

    public CreateConnectionsCommand(DataSet data, Collection<PrimitiveData> primitives) {
        super(data);
        this.primitives = primitives;
    }

    @Override
    public boolean executeCommand() {
        if (command == null) {
            List<Command> commands = createConnections(getAffectedDataSet(), primitives);
            command = commands.get(0);
            undoCommands = commands.get(1);
        }
        if (command != null) {
            command.executeCommand();
        }
        if (undoCommands != null && !UndoRedoHandler.getInstance().getUndoCommands().contains(undoCommands)) {
            GuiHelper.runInEDTAndWait(() -> UndoRedoHandler.getInstance().add(undoCommands));
        }
        return true;
    }

    @Override
    public void undoCommand() {
        if (command != null) {
            command.undoCommand();
        }
    }

    /**
     * Create connections based off of current MapWithAI syntax
     *
     * @param dataSet    The {@link DataSet} that should have the primitives we are
     *                   connecting to
     * @param collection The primitives with connection information (currently only
     *                   checks Nodes)
     * @return A list {@link Command} to create connections with (first is one that
     *         can be folded into other commands, second is one that should be
     *         undoable individually)
     */
    public static List<Command> createConnections(DataSet dataSet, Collection<PrimitiveData> collection) {
        final List<Command> permanent = new ArrayList<>();
        final List<Command> undoable = new ArrayList<>();
        List<Class<? extends AbstractConflationCommand>> runCommands = new ArrayList<>();
        for (final Class<? extends AbstractConflationCommand> abstractCommandClass : getConflationCommands()) {
            final AbstractConflationCommand abstractCommand;
            try {
                abstractCommand = abstractCommandClass.getConstructor(DataSet.class).newInstance(dataSet);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                Logging.debug(e);
                continue;
            }
            // If there are conflicting commands, don't add it.
            if (runCommands.parallelStream().anyMatch(c -> abstractCommand.conflictedCommands().contains(c))) {
                continue;
            }
            final Collection<OsmPrimitive> realPrimitives = collection.stream().map(dataSet::getPrimitiveById)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            final Collection<OsmPrimitive> tPrimitives = new TreeSet<>();
            abstractCommand.getInterestedTypes()
                    .forEach(clazz -> tPrimitives.addAll(Utils.filteredCollection(realPrimitives, clazz)));

            final Command actualCommand = abstractCommand.getCommand(
                    tPrimitives.stream().filter(prim -> prim.hasKey(abstractCommand.getKey()) && !prim.isDeleted())
                            .collect(Collectors.toList()));
            if (Objects.nonNull(actualCommand)) {
                if (abstractCommand.allowUndo()) {
                    undoable.add(actualCommand);
                } else {
                    permanent.add(actualCommand);
                }
                runCommands.add(abstractCommand.getClass());
            }
        }

        Command permanentCommand = null;
        if (permanent.size() == 1) {
            permanentCommand = permanent.get(0);
        } else if (!permanent.isEmpty()) {
            permanentCommand = new SequenceCommand(getRealDescriptionText(), permanent);
        }

        Command undoCommand = null;
        if (undoable.size() == 1) {
            undoCommand = undoable.get(0);
        } else if (!undoable.isEmpty()) {
            undoCommand = new SequenceCommand(getRealDescriptionText(), undoable);
        }

        return Arrays.asList(permanentCommand, undoCommand);
    }

    @Override
    public String getDescriptionText() {
        return getRealDescriptionText();
    }

    private static String getRealDescriptionText() {
        return tr("Create connections from {0} data", MapWithAIPlugin.NAME);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        command.fillModifiedData(modified, deleted, added);
    }

    /**
     * @param command A command to run when copying data from the MapWithAI layer
     */
    public static void addConflationCommand(Class<? extends AbstractConflationCommand> command) {
        CONFLATION_COMMANDS.add(command);
    }

    /**
     * @return A set of commands to run when copying data from the MapWithAI layer
     */
    public static Set<Class<? extends AbstractConflationCommand>> getConflationCommands() {
        return Collections.unmodifiableSet(CONFLATION_COMMANDS);
    }

    /**
     * @param command The command class to remove
     * @return {@code true} if the conflation command was removed and was present
     * @see List#remove
     */
    public static boolean removeConflationCommand(Class<? extends AbstractConflationCommand> command) {
        return CONFLATION_COMMANDS.remove(command);
    }
}
