// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.AbstractConflationCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.ConnectedCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DuplicateCommand;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.MergeAddressBuildings;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

public class CreateConnectionsCommand extends Command {
    private final Collection<OsmPrimitive> primitives;
    private Command command = null;
    private static final List<Class<? extends AbstractConflationCommand>> CONFLATION_COMMANDS = new ArrayList<>();
    static {
        CONFLATION_COMMANDS.add(ConnectedCommand.class);
        CONFLATION_COMMANDS.add(DuplicateCommand.class);
        CONFLATION_COMMANDS.add(MergeAddressBuildings.class);
    }

    public CreateConnectionsCommand(DataSet data, Collection<OsmPrimitive> primitives) {
        super(data);
        this.primitives = primitives;
    }

    @Override
    public boolean executeCommand() {
        if (command == null) {
            command = createConnections(getAffectedDataSet(), primitives);
        }
        if (command != null) {
            command.executeCommand();
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
     * @return A {@link SequenceCommand} to create connections with
     */
    public static SequenceCommand createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
        final List<Command> changedKeyList = new ArrayList<>();
        SequenceCommand returnSequence = null;
        final Collection<OsmPrimitive> realPrimitives = collection.stream().map(dataSet::getPrimitiveById)
                .filter(Objects::nonNull).collect(Collectors.toList());
        for (final Class<? extends AbstractConflationCommand> abstractCommandClass : getConflationCommands()) {
            final AbstractConflationCommand abstractCommand;
            try {
                abstractCommand = abstractCommandClass.getConstructor(DataSet.class).newInstance(dataSet);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                Logging.debug(e);
                continue;
            }
            final Collection<OsmPrimitive> tPrimitives = new TreeSet<>();
            abstractCommand.getInterestedTypes()
            .forEach(clazz -> tPrimitives.addAll(Utils.filteredCollection(realPrimitives, clazz)));

            final Command actualCommand = abstractCommand.getCommand(tPrimitives.stream()
                    .filter(prim -> prim.hasKey(abstractCommand.getKey())).collect(Collectors.toList()));
            if (Objects.nonNull(actualCommand)) {
                changedKeyList.add(actualCommand);
            }
        }
        if (!changedKeyList.isEmpty()) {
            returnSequence = new SequenceCommand(getRealDescriptionText(), changedKeyList);
        }
        return returnSequence;
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
    public static List<Class<? extends AbstractConflationCommand>> getConflationCommands() {
        return Collections.unmodifiableList(CONFLATION_COMMANDS);
    }
}
