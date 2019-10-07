// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.MergeNodesAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.DownloadPrimitivesTask;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;

public class CreateConnectionsCommand extends Command {
    private final Collection<OsmPrimitive> primitives;
    public static final String DUPE_KEY = "dupe";
    public static final String CONN_KEY = "conn";
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
     * @return A {@link SequenceCommand} to create connections with
     */
    public static SequenceCommand createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
        final Collection<Node> nodes = Utils.filteredCollection(collection, Node.class);
        final List<Command> changedKeyList = new ArrayList<>();
        for (final Node node : nodes) {
            if (node.hasKey(CONN_KEY)) {
                changedKeyList.addAll(connectedCommand(dataSet, node));
            }
            if (node.hasKey(DUPE_KEY)) {
                changedKeyList.addAll(duplicateNode(dataSet, node));
            }
        }
        if (!changedKeyList.isEmpty()) {
            return new SequenceCommand(getRealDescriptionText(), changedKeyList);
        }
        return null;
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

    private static List<Command> duplicateNode(DataSet dataSet, Node node) {
        final OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get(DUPE_KEY));
        if (primitiveConnections.length != 1) {
            Logging.error("{0}: {3} connection connected to more than one node? ({3}={1})", RapiDPlugin.NAME,
                    node.get(DUPE_KEY), DUPE_KEY);
        }

        List<Command> commands = new ArrayList<>();
        if (primitiveConnections[0] instanceof Node) {
            Node replaceNode = (Node) primitiveConnections[0];
            Command tCommand = replaceNode(node, replaceNode);
            if (tCommand != null) {
                commands.add(tCommand);
                if (replaceNode.hasKey(DUPE_KEY)) {
                    String key = replaceNode.get(DUPE_KEY);
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
     * Get the primitives from a dataset with specified ids
     *
     * @param dataSet The dataset holding the primitives (hopefully)
     * @param ids     The ids formated like n<NUMBER>,r<NUMBER>,w<NUMBER>
     * @return The primitives that the ids point to, if in the dataset.
     */
    private static OsmPrimitive[] getPrimitives(DataSet dataSet, String ids) {
        final Map<Integer, Pair<Long, OsmPrimitiveType>> missingPrimitives = new TreeMap<>();
        final String[] connections = ids.split(",", -1);
        final OsmPrimitive[] primitiveConnections = new OsmPrimitive[connections.length];
        for (int i = 0; i < connections.length; i++) {
            final String member = connections[i];
            final long id = Long.parseLong(member.substring(1));
            final char firstChar = member.charAt(0);
            OsmPrimitiveType type = null;
            if (firstChar == 'w') {
                type = OsmPrimitiveType.WAY;
            } else if (firstChar == 'n') {
                type = OsmPrimitiveType.NODE;
            } else if (firstChar == 'r') {
                type = OsmPrimitiveType.RELATION;
            } else {
                throw new IllegalArgumentException(
                        tr("{0}: We don't know how to handle {1} types", RapiDPlugin.NAME, firstChar));
            }
            primitiveConnections[i] = dataSet.getPrimitiveById(id, type);
            if (primitiveConnections[i] == null) {
                missingPrimitives.put(i, new Pair<>(id, type));
            }
        }
        obtainMissingPrimitives(dataSet, primitiveConnections, missingPrimitives);
        return primitiveConnections;
    }

    private static void obtainMissingPrimitives(DataSet dataSet, OsmPrimitive[] primitiveConnections,
            Map<Integer, Pair<Long, OsmPrimitiveType>> missingPrimitives) {
        final Map<PrimitiveId, Integer> ids = missingPrimitives.entrySet().stream().collect(Collectors
                .toMap(entry -> new SimplePrimitiveId(entry.getValue().a, entry.getValue().b), Entry::getKey));
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

        final PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor(
                tr("Downloading additional OsmPrimitives"));
        final DownloadPrimitivesTask downloadPrimitivesTask = new DownloadPrimitivesTask(layer, toFetch, true, monitor);
        downloadPrimitivesTask.run();
        for (final Entry<PrimitiveId, Integer> entry : ids.entrySet()) {
            final int index = entry.getValue().intValue();
            final OsmPrimitive primitive = dataSet.getPrimitiveById(entry.getKey());
            primitiveConnections[index] = primitive;
        }

        if (generatedLayerName.equals(layer.getName())) {
            layer.destroy();
        }
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
        return getRealDescriptionText();
    }

    private static String getRealDescriptionText() {
        return tr("Create connections from {0} data", RapiDPlugin.NAME);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        command.fillModifiedData(modified, deleted, added);
    }
}
