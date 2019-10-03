// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.rapid.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.Collection;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

public class AddNodeToWayCommand extends Command {
    private final Node toAddNode;
    private final Way way;
    private final Node firstNode;
    private final Node secondNode;

    /**
     * Add a node to a way in an undoable manner
     *
     * @param toAddNode  The node to add
     * @param way        The way to add the node to
     * @param firstNode  The node that comes before the node to add
     * @param secondNode The node that comes after the node to add
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
        final int index = Math.max(getWay().getNodes().indexOf(getFirstNode()),
                getWay().getNodes().indexOf(getSecondNode()));
        getWay().addNode(index, getToAddNode());
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
