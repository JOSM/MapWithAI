// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.MergeNodesAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Logging;

public class DuplicateCommand extends AbstractConflationCommand {
    public static final String DUPE_KEY = "dupe";

    public DuplicateCommand(DataSet data) {
        super(data);
    }

    private static List<Command> duplicateNode(DataSet dataSet, Node node) {
        final OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get(DUPE_KEY));
        if (primitiveConnections.length != 1) {
            Logging.error("{0}: {3} connection connected to more than one node? ({3}={1})", MapWithAIPlugin.NAME,
                    node.get(DUPE_KEY), DUPE_KEY);
        }

        final List<Command> commands = new ArrayList<>();
        if (primitiveConnections[0] instanceof Node) {
            final Node replaceNode = (Node) primitiveConnections[0];
            final Command tCommand = replaceNode(node, replaceNode);
            if (tCommand != null) {
                commands.add(tCommand);
                if (replaceNode.hasKey(DUPE_KEY)) {
                    final String key = replaceNode.get(DUPE_KEY);
                    commands.add(new ChangePropertyCommand(replaceNode, DUPE_KEY, key));
                } else {
                    replaceNode.put(DUPE_KEY, "empty_value"); // This is needed to actually have a command.
                    commands.add(new ChangePropertyCommand(replaceNode, DUPE_KEY, null));
                    replaceNode.remove(DUPE_KEY);
                }
            }
        }
        return commands;
    }

    /**
     * Replace nodes that are in the same location
     *
     * @param original The original node (the one to replace)
     * @param newNode  The node that is replacing the original node
     * @return A command that replaces the node, or null if they are not at the same
     *         location.
     */
    public static Command replaceNode(Node original, Node newNode) {
        Command tCommand = null;
        if (original.getCoor().equalsEpsilon(newNode.getCoor())) {
            tCommand = MergeNodesAction.mergeNodes(Arrays.asList(original), newNode, newNode);
        }
        return tCommand;
    }

    @Override
    public String getDescriptionText() {
        return tr("Remove duplicated nodes");
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Arrays.asList(Node.class);
    }

    @Override
    public String getKey() {
        return DUPE_KEY;
    }

    @Override
    public Command getRealCommand() {
        final List<Command> commands = new ArrayList<>();
        for (Node tNode : possiblyAffectedPrimitives.stream().filter(Node.class::isInstance).map(Node.class::cast)
                .distinct().filter(node -> node.hasKey(DUPE_KEY)).collect(Collectors.toList())) {
            List<Command> tCommands = duplicateNode(getAffectedDataSet(), tNode);
            // We have to execute the command to avoid duplicating the command later. Undo
            // occurs later, so that the state doesn't actually change.
            tCommands.forEach(Command::executeCommand);
            commands.addAll(tCommands);
        }
        Collections.reverse(commands);
        commands.forEach(Command::undoCommand);
        Collections.reverse(commands);
        Command returnCommand = null;
        if (!commands.isEmpty()) {
            returnCommand = new SequenceCommand(getDescriptionText(), commands);
        }
        return returnCommand;
    }
}
