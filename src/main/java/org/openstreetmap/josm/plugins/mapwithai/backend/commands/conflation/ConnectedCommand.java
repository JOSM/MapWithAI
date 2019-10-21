// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.commands.AddNodeToWayCommand;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;

public class ConnectedCommand extends AbstractConflationCommand {
    public static final String CONN_KEY = "conn";

    public ConnectedCommand(DataSet data) {
        super(data);
    }

    @Override
    public String getDescriptionText() {
        return tr("Connect nodes to ways");
    }

    /**
     * Add a node to a way
     *
     * @param toAddNode The node to add
     * @param way       The way to add the node to
     * @param first     The first node in a waysegment (the node is between this and
     *                  the second node)
     * @param second    The second node in a waysegment
     * @return Command to add a node to a way, or null if it won't be done
     */
    public static Command addNodesToWay(Node toAddNode, Way way, Node first, Node second) {
        Command tCommand = null;
        final Way tWay = new Way();
        tWay.addNode(first);
        tWay.addNode(second);
        final double distance = Geometry.getDistanceWayNode(tWay, toAddNode);
        if (distance < 5) {
            tCommand = new AddNodeToWayCommand(toAddNode, way, first, second);
        }
        return tCommand;
    }

    private static List<Command> connectedCommand(DataSet dataSet, Node node) {
        final List<Command> commands = new ArrayList<>();
        final OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get(CONN_KEY));
        for (int i = 0; i < primitiveConnections.length / 3; i++) {
            if (primitiveConnections[i] instanceof Way && primitiveConnections[i + 1] instanceof Node
                    && primitiveConnections[i + 2] instanceof Node) {
                final Command addNodesToWayCommand = addNodesToWay(node, (Way) primitiveConnections[i],
                        (Node) primitiveConnections[i + 1], (Node) primitiveConnections[i + 2]);
                if (addNodesToWayCommand != null) {
                    commands.add(addNodesToWayCommand);
                }
            } else {
                Logging.error("{0}: {1}, {2}: {3}, {4}: {5}", i, primitiveConnections[i].getClass(), i + 1,
                        primitiveConnections[i + 1].getClass(), i + 2, primitiveConnections[i + 2].getClass());
            }
        }
        commands.add(new ChangePropertyCommand(node, CONN_KEY, null));
        return commands;
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Arrays.asList(Node.class);
    }

    @Override
    public String getKey() {
        return CONN_KEY;
    }

    @Override
    public Command getRealCommand() {
        final List<Command> commands = new ArrayList<>();
        possiblyAffectedPrimitives.stream().filter(Node.class::isInstance).map(Node.class::cast)
        .forEach(node -> commands.addAll(connectedCommand(getAffectedDataSet(), node)));
        Command returnCommand = null;
        if (!commands.isEmpty()) {
            returnCommand = new SequenceCommand(getDescriptionText(), commands);
        }
        return returnCommand;
    }
}
