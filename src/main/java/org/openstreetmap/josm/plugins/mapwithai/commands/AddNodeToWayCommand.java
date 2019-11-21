// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.tools.Geometry;

public class AddNodeToWayCommand extends Command {
    private final Node toAddNode;
    private final Way way;
    private final Node firstNode;
    private final Node secondNode;

    /**
     * Add a node to a way in an undoable manner
     *
     * @param toAddNode The node to add
     * @param way       The way to add the node to
     * @param first     The node that comes before the node to add
     * @param second    The node that comes after the node to add
     */
    public AddNodeToWayCommand(Node toAddNode, Way way, Node first, Node second) {
        super(way.getDataSet());
        this.toAddNode = toAddNode;
        this.way = way;
        this.firstNode = first;
        this.secondNode = second;
    }

    @Override
    public boolean executeCommand() {
        int index = Integer.MIN_VALUE;
        try {
            WaySegment.forNodePair(getWay(), getFirstNode(), getSecondNode());
            index = Math.max(getWay().getNodes().indexOf(getFirstNode()), getWay().getNodes().indexOf(getSecondNode()));
        } catch (IllegalArgumentException e) {
            // OK, someone has added a node between the two nodes since calculation
            Way tWay = new Way();
            tWay.setNodes(Arrays.asList(getFirstNode(), getSecondNode()));
            List<Node> relevantNodes = new ArrayList<>(getWay().getNodes().stream()
                    .filter(node -> Geometry.getDistance(tWay, node) < MapWithAIPreferenceHelper.getMaxNodeDistance())
                    .collect(Collectors.toList()));
            for (int i = 0; i < relevantNodes.size() - 1; i++) {
                Way tWay2 = new Way();
                tWay2.setNodes(Arrays.asList(relevantNodes.get(i), relevantNodes.get(i + 1)));
                if (Geometry.getDistance(tWay2, getToAddNode()) < MapWithAIPreferenceHelper.getMaxNodeDistance()) {
                    index = Math.max(way.getNodes().indexOf(tWay2.firstNode()),
                            way.getNodes().indexOf(tWay2.lastNode()));
                }
            }
        }
        if (index != Integer.MIN_VALUE) {
            getWay().addNode(index, getToAddNode());
        }
        return true;
    }

    @Override
    public void undoCommand() {
        getWay().removeNode(getToAddNode());
    }

    @Override
    public String getDescriptionText() {
        return tr("Add node to way");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.addAll(Arrays.asList(getToAddNode(), getWay()));
    }

    /**
     * @return {@link Node} to add to {@link Way}
     */
    public Node getToAddNode() {
        return toAddNode;
    }

    /**
     * @return {@link Way} that we are adding a {@link Node} to
     */
    public Way getWay() {
        return way;
    }

    /**
     * @return {@link Node} that we are adding another {@link Node} after.
     */
    public Node getFirstNode() {
        return firstNode;
    }

    /**
     * @return {@link Node} that we are adding another {@link Node} before.
     */
    public Node getSecondNode() {
        return secondNode;
    }
}
