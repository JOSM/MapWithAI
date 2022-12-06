// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;

/**
 * Connect a way to another way (in between nodes)
 */
public class ConnectedCommand extends AbstractConflationCommand {
    public static final String KEY = "conn";

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
     * @return Commands to add a node to a way, or null if it won't be done
     */
    public static List<Command> addNodesToWay(Node toAddNode, Way way, Node first, Node second) {
        List<Command> tCommands = new ArrayList<>();
        final Way tWay = new Way();
        final List<Way> ways = toAddNode.getReferrers().stream().filter(w -> !w.equals(way))
                .filter(Way.class::isInstance).map(Way.class::cast).collect(Collectors.toList());
        tWay.addNode(first);
        tWay.addNode(second);
        final double distance = Geometry.getDistanceWayNode(tWay, toAddNode);
        if (distance < 5) {
            if (!ways.isEmpty()) {
                int index = ways.get(0).getNodes().indexOf(toAddNode);
                final Node node4 = index == 0 ? ways.get(0).getNode(1) : ways.get(0).getNode(index - 1);
                final Node tNode = new Node(toAddNode);
                tNode.setCoor(ProjectionRegistry.getProjection().eastNorth2latlon(Geometry.getLineLineIntersection(
                        first.getEastNorth(), second.getEastNorth(), toAddNode.getEastNorth(), node4.getEastNorth())));
                tCommands.add(new ChangeCommand(toAddNode, tNode));
            }
            tCommands.add(new AddNodeToWayCommand(toAddNode, way, first, second));
        }
        return tCommands;
    }

    private static List<Command> connectedCommand(DataSet dataSet, Node node) {
        final List<Command> commands = new ArrayList<>();
        final OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get(KEY));
        for (int i = 0; i < primitiveConnections.length / 3; i++) {
            if (primitiveConnections[i] instanceof Way && primitiveConnections[i + 1] instanceof Node
                    && primitiveConnections[i + 2] instanceof Node) {
                final List<Command> addNodesToWayCommand = addNodesToWay(node, (Way) primitiveConnections[i],
                        (Node) primitiveConnections[i + 1], (Node) primitiveConnections[i + 2]);
                commands.addAll(addNodesToWayCommand);
            } else {
                Logging.error("MapWithAI: Cannot create connections ({0}: {1}, {2}: {3}, {4}: {5})", i,
                        primitiveConnections[i] == null ? null : primitiveConnections[i].getClass(), i + 1,
                        primitiveConnections[i + 1] == null ? null : primitiveConnections[i + 1].getClass(), i + 2,
                        primitiveConnections[i + 2] == null ? null : primitiveConnections[i + 2].getClass());
            }
        }
        commands.add(new ChangePropertyCommand(node, KEY, null));
        return commands;
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Collections.singletonList(Node.class);
    }

    @Override
    public String getKey() {
        return KEY;
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

    @Override
    public boolean allowUndo() {
        return false;
    }

    @Override
    public boolean keyShouldNotExistInOSM() {
        return true;
    }
}
