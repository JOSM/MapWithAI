// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.MergeSourceBuildingVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.rapid.RapiDPlugin;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;

public class RapiDAddCommand extends Command {
    DataSet editable;
    DataSet rapid;
    Collection<OsmPrimitive> primitives;
    AddPrimitivesCommand addPrimitivesCommand;
    Collection<OsmPrimitive> modifiedPrimitives;

    OsmDataLayer editLayer = null;

    public RapiDAddCommand(RapiDLayer rapidLayer, OsmDataLayer editLayer, Collection<OsmPrimitive> selection) {
        super(rapidLayer.getDataSet());
        this.rapid = rapidLayer.getDataSet();
        this.editable = editLayer.getDataSet();
        this.primitives = selection;
        this.editLayer = editLayer;
        modifiedPrimitives = null;

    }
    /**
     * Add primitives from RapiD to the OSM data layer
     *
     * @param rapid     The rapid dataset
     * @param editable  The OSM dataset
     * @param selection The primitives to add from RapiD
     */
    public RapiDAddCommand(DataSet rapid, DataSet editable, Collection<OsmPrimitive> selection) {
        super(rapid);
        this.rapid = rapid;
        this.editable = editable;
        this.primitives = selection;
        modifiedPrimitives = null;
    }

    @Override
    public boolean executeCommand() {
        if (rapid.equals(editable)) {
            Logging.error("{0}: DataSet rapid ({1}) should not be the same as DataSet editable ({2})", RapiDPlugin.NAME,
                    rapid, editable);
            throw new IllegalArgumentException();
        }
        primitives = new HashSet<>(primitives);
        RapiDDataUtils.addPrimitivesToCollection(/* collection= */ primitives, /* primitives= */ primitives);
        synchronized (this) {
            rapid.unlock();
            Collection<OsmPrimitive> newPrimitives = new TreeSet<>(moveCollection(rapid, editable, primitives));
            createConnections(editable, newPrimitives);
            RapiDDataUtils.removePrimitivesFromDataSet(primitives);
            rapid.lock();
        }
        if (editLayer != null && RapiDDataUtils.getSwitchLayers()) {
            MainApplication.getLayerManager().setActiveLayer(editLayer);
            editable.setSelected(
                    editable.getSelected().stream().filter(OsmPrimitive::isTagged).collect(Collectors.toSet()));
        }
        return true;
    }

    /**
     * Create connections based off of current RapiD syntax
     *
     * @param dataSet    The {@link DataSet} that should have the primitives we are
     *                   connecting to
     * @param collection The primitives with connection information (currently only
     *                   checks Nodes)
     */
    public static void createConnections(DataSet dataSet, Collection<OsmPrimitive> collection) {
        Collection<Node> nodes = Utils.filteredCollection(collection, Node.class);
        for (Node node : nodes) {
            if (node.hasKey("conn")) {
                // Currently w<way id>,n<node1>,n<node2>
                OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get("conn"));
                for (int i = 0; i < primitiveConnections.length / 3; i++) {
                    if (primitiveConnections[i] instanceof Way && primitiveConnections[i + 1] instanceof Node
                            && primitiveConnections[i + 2] instanceof Node) {
                        addNodesToWay(node, (Way) primitiveConnections[i], (Node) primitiveConnections[i + 1],
                                (Node) primitiveConnections[i + 2]);
                    } else {
                        Logging.error("{0}: {1}, {2}: {3}, {4}: {5}", i, primitiveConnections[i].getClass(), i + 1,
                                primitiveConnections[i + 1].getClass(), i + 2, primitiveConnections[i + 2].getClass());
                    }
                }
                Logging.debug("RapiD: Removing conn from {0} in {1}", node, dataSet.getName());
                node.remove("conn");
            }
            if (node.hasKey("dupe")) {
                OsmPrimitive[] primitiveConnections = getPrimitives(dataSet, node.get("dupe"));
                if (primitiveConnections.length != 1) {
                    Logging.error("RapiD: dupe connection connected to more than one node? (dupe={0})",
                            node.get("dupe"));
                }
                replaceNode(node, (Node) primitiveConnections[0]);
            }
        }
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
    public static void addNodesToWay(Node toAddNode, Way way, Node first, Node second) {
        int index = Math.max(way.getNodes().indexOf(first), way.getNodes().indexOf(second));
        way.addNode(index, toAddNode);
    }

    public static void replaceNode(Node original, Node newNode) {
        for (OsmPrimitive primitive : original.getReferrers()) {
            if (primitive instanceof Way) {
                Way way = (Way) primitive;
                List<Integer> indexes = new ArrayList<>();
                List<Node> nodes = way.getNodes();
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodes.get(i).equals(original)) {
                        indexes.add(i);
                    }
                }
                while (way.getNodes().contains(original)) {
                    way.removeNode(original);
                }
                for (int index : indexes) {
                    way.addNode(index, newNode);
                }
            } else if (primitive instanceof Relation) {
                List<Pair<Integer, RelationMember>> replaceMembers = new ArrayList<>();
                Relation relation = (Relation) primitive;
                List<RelationMember> relationMembers = relation.getMembers();
                for (int i = 0; i < relationMembers.size(); i++) {
                    RelationMember member = relationMembers.get(i);
                    if (member.getMember().equals(original)) {
                        replaceMembers.add(new Pair<>(i, new RelationMember(member.getRole(), newNode)));
                    }
                }
                relation.removeMembersFor(original);
                for (Pair<Integer, RelationMember> pair : replaceMembers) {
                    relation.addMember(pair.a, pair.b);
                }
            }
        }
        original.getDataSet().removePrimitive(original);
    }

    /**
     * Move primitives from one dataset to another
     *
     * @param to        The receiving dataset
     * @param from      The sending dataset
     * @param selection The primitives to move
     * @return true if the primitives have moved datasets
     */
    public Collection<? extends OsmPrimitive> moveCollection(DataSet from, DataSet to,
            Collection<OsmPrimitive> selection) {
        if (from == null || to.isLocked() || from.isLocked()) {
            Logging.error("RapiD: Cannot move primitives from {0} to {1}", from, to);
            return Collections.emptySet();
        }
        Collection<OsmPrimitive> originalSelection = from.getSelected();
        from.setSelected(selection);
        MergeSourceBuildingVisitor mergeBuilder = new MergeSourceBuildingVisitor(from);
        List<PrimitiveData> primitiveDataList = mergeBuilder.build().allPrimitives().stream().map(OsmPrimitive::save)
                .collect(Collectors.toList());
        from.setSelected(originalSelection);
        addPrimitivesCommand = new AddPrimitivesCommand(primitiveDataList, primitiveDataList, to);
        addPrimitivesCommand.executeCommand();
        return addPrimitivesCommand.getParticipatingPrimitives();
    }

    @Override
    public String getDescriptionText() {
        return tr("Add object from RapiD");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        // TODO Auto-generated method stub

    }
}
