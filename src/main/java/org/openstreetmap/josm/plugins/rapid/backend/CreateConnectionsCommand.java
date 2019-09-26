// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.actions.MergeNodesAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

public class CreateConnectionsCommand extends Command {
    private final Collection<OsmPrimitive> primitives;
    public final static String DUPE_KEY = "dupe";
    public final static String CONN_KEY = "conn";
    private Command command = null;

    public CreateConnectionsCommand(DataSet data, Collection<OsmPrimitive> primitives) {
        super(data);
        this.primitives = primitives;
    }

    @Override
    public boolean executeCommand() {
        command = createConnections(getAffectedDataSet(), primitives);
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
     * Create connections based off of current RapiD syntax
     *
     * @param dataSet    The {@link DataSet} that should have the primitives we are
     *                   connecting to
     * @param collection The primitives with connection information (currently only
     *                   checks Nodes)
     * @return
     */
    public SequenceCommand createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
        Collection<Node> nodes = Utils.filteredCollection(collection, Node.class);
        List<Command> changedKeyList = new ArrayList<>();
        for (Node node : nodes) {
            if (node.hasKey(CONN_KEY)) {
                // Currently w<way id>,n<node1>,n<node2>
                OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get(CONN_KEY));
                for (int i = 0; i < primitiveConnections.length / 3; i++) {
                    if (primitiveConnections[i] instanceof Way && primitiveConnections[i + 1] instanceof Node
                            && primitiveConnections[i + 2] instanceof Node) {
                        changedKeyList.add(addNodesToWay(node, (Way) primitiveConnections[i],
                                (Node) primitiveConnections[i + 1], (Node) primitiveConnections[i + 2]));
                    } else {
                        Logging.error("{0}: {1}, {2}: {3}, {4}: {5}", i, primitiveConnections[i].getClass(), i + 1,
                                primitiveConnections[i + 1].getClass(), i + 2, primitiveConnections[i + 2].getClass());
                    }
                }
                Logging.debug("RapiD: Removing conn from {0} in {1}", node, dataSet.getName());
                changedKeyList.add(new ChangePropertyCommand(node, CONN_KEY, null));
            }
            if (node.hasKey(DUPE_KEY)) {
                OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get(DUPE_KEY));
                if (primitiveConnections.length != 1) {
                    Logging.error("RapiD: dupe connection connected to more than one node? (dupe={0})",
                            node.get(DUPE_KEY));
                }
                changedKeyList.add(replaceNode(node, (Node) primitiveConnections[0]));
            }
        }
        if (!changedKeyList.isEmpty())
            return new SequenceCommand(getDescriptionText(), changedKeyList);
        return null;
    }

    /**
     * Get the primitives from a dataset with specified ids
     *
     * @param dataSet The dataset holding the primitives (hopefully)
     * @param ids     The ids formated like n<NUMBER>,r<NUMBER>,w<NUMBER>
     * @return The primitives that the ids point to, if in the dataset.
     */
    private static OsmPrimitive[] getPrimitives(DataSet dataSet, String ids) {
        String[] connections = ids.split(",", -1);
        OsmPrimitive[] primitiveConnections = new OsmPrimitive[connections.length];
        for (int i = 0; i < connections.length; i++) {
            String member = connections[i];
            long id = Long.parseLong(member.substring(1));
            char firstChar = member.charAt(0);
            if (firstChar == 'w') {
                primitiveConnections[i] = dataSet.getPrimitiveById(id, OsmPrimitiveType.WAY);
            } else if (firstChar == 'n') {
                primitiveConnections[i] = dataSet.getPrimitiveById(id, OsmPrimitiveType.NODE);
            } else if (firstChar == 'r') {
                primitiveConnections[i] = dataSet.getPrimitiveById(id, OsmPrimitiveType.RELATION);
            }
        }
        return primitiveConnections;
    }

    /**
     * Add a node to a way
     *
     * @param toAddNode The node to add
     * @param way       The way to add the node to
     * @param first     The first node in a waysegment (the node is between this and
     *                  the second node)
     * @param second    The second node in a waysegemnt
     */
    public static Command addNodesToWay(Node toAddNode, Way way, Node first, Node second) {
        return new AddNodeToWayCommand(toAddNode, way, first, second);
    }

    public static Command replaceNode(Node original, Node newNode) {
        return MergeNodesAction.mergeNodes(Arrays.asList(original), newNode, newNode);
    }

    @Override
    public String getDescriptionText() {
        return tr("Create connections from {0} data", RapiDPlugin.NAME);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        // Don't touch the collectioins, since we haven't modified anything -- the
        // subcommands should do that.
    }
}
