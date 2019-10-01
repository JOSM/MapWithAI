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
    private final Node first;
    private final Node second;

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
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean executeCommand() {
        int index = Math.max(way.getNodes().indexOf(first), way.getNodes().indexOf(second));
        way.addNode(index, toAddNode);
        return true;
    }

    @Override
    public void undoCommand() {
        way.removeNode(toAddNode);
    }

    @Override
    public String getDescriptionText() {
        return tr("Add node to way");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        modified.addAll(Arrays.asList(toAddNode, way));
    }
}
