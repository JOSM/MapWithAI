// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.IWaySegment;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;

/**
 * Add a node to a way
 */
public class AddNodeToWayCommand extends Command {
    private final Node toAddNode;
    private final Way way;
    private final Node firstNode;
    private final Node secondNode;
    private Command changeCommand;

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
            // IWaySegment#forNodePair throws an IllegalArgumentException when the node pair
            // doesn't exist as a segment in the way.
            IWaySegment.forNodePair(getWay(), getFirstNode(), getSecondNode());
            index = Math.max(getWay().getNodes().indexOf(getFirstNode()), getWay().getNodes().indexOf(getSecondNode()));
        } catch (IllegalArgumentException e) {
            Logging.trace(e);
            // OK, someone has added a node between the two nodes since calculation
            Way tWay = new Way();
            tWay.setNodes(Arrays.asList(getFirstNode(), getSecondNode()));
            List<Node> relevantNodes = getWay().getNodes().stream()
                    .filter(node -> Geometry.getDistance(tWay, node) < MapWithAIPreferenceHelper.getMaxNodeDistance())
                    .collect(Collectors.toList());
            for (int i = 0; i < relevantNodes.size() - 1; i++) {
                Way tWay2 = new Way();
                tWay2.setNodes(Arrays.asList(relevantNodes.get(i), relevantNodes.get(i + 1)));
                if (Geometry.getDistance(tWay2, getToAddNode()) < MapWithAIPreferenceHelper.getMaxNodeDistance()) {
                    index = Math.max(way.getNodes().indexOf(tWay2.firstNode()),
                            way.getNodes().indexOf(tWay2.lastNode()));
                }
            }
        }
        if (index != Integer.MIN_VALUE && changeCommand == null) {
            Way tWay = new Way(getWay());
            tWay.addNode(index, getToAddNode());
            changeCommand = new ChangeCommand(getWay(), tWay);
        }
        if (changeCommand != null) {
            changeCommand.executeCommand();
        }
        return true;
    }

    @Override
    public void undoCommand() {
        if (changeCommand != null) {
            changeCommand.undoCommand();
        }
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

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return changeCommand.getParticipatingPrimitives();
    }

    /**
     * Get the node that will be added to a way
     *
     * @return {@link Node} to add to {@link Way}
     */
    public Node getToAddNode() {
        return toAddNode;
    }

    /**
     * Get the way that we are modifying
     *
     * @return {@link Way} that we are adding a {@link Node} to
     */
    public Way getWay() {
        return way;
    }

    /**
     * Get the node that we are adding our node after
     *
     * @return {@link Node} that we are adding another {@link Node} after.
     */
    public Node getFirstNode() {
        return firstNode;
    }

    /**
     * Get the node that we are adding our node before
     *
     * @return {@link Node} that we are adding another {@link Node} before.
     */
    public Node getSecondNode() {
        return secondNode;
    }
}
