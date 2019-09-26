package org.openstreetmap.josm.plugins.rapid.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

public class DeletePrimitivesCommand extends Command {
    private final Collection<OsmPrimitive> selection;
    private final DataSet from;
    private final Collection<OsmPrimitive> removed;
    private final Collection<Command> commands;
    private final boolean deleteChildren;

    /**
     * Delete primitives from a DataSet
     *
     * @param from      The {@link DataSet} to remove primitives from
     * @param selection The primitives to remove
     */
    public DeletePrimitivesCommand(DataSet from, Collection<OsmPrimitive> selection) {
        this(from, selection, false);
    }

    /**
     * Delete primitives from a DataSet
     *
     * @param from      The {@link DataSet} to remove primitives from
     * @param selection The primitives to remove
     * @param removeAll {@code true} if all children should be removed (for ways).
     */
    public DeletePrimitivesCommand(DataSet from, Collection<OsmPrimitive> selection, boolean removeAll) {
        super(from);
        this.from = from;
        this.selection = selection;
        commands = new ArrayList<>();
        removed = new ArrayList<>();
        this.deleteChildren = removeAll;
    }

    @Override
    public boolean executeCommand() {
        for (OsmPrimitive primitive : selection) {
            DataSet dataSet = primitive.getDataSet();

            if (from.equals(dataSet)) {
                dataSet.removePrimitive(primitive);
                removed.add(primitive);
            }
            if (primitive instanceof Way) {
                List<OsmPrimitive> nodes = ((Way) primitive).getNodes().stream()
                        .filter(node -> (!node.hasKeys() || deleteChildren) && !selection.contains(node))
                        .collect(Collectors.toList());
                DeletePrimitivesCommand delNodes = new DeletePrimitivesCommand(from, nodes);
                delNodes.executeCommand();
                commands.add(delNodes);
            }
        }
        return true;
    }

    @Override
    public void undoCommand() {
        for (Command command : commands) {
            command.undoCommand();
        }
        for (OsmPrimitive primitive : removed) {
            DataSet removedPrimitiveDataSet = primitive.getDataSet();
            if (removedPrimitiveDataSet != null) {
                removedPrimitiveDataSet.removePrimitive(primitive);
            }
            if (primitive instanceof Way) {
                for (Node node : ((Way) primitive).getNodes()) {
                    if (node.getDataSet() == null) {
                        from.addPrimitive(node);
                    }
                }
            }
            from.addPrimitive(primitive);
        }
    }

    @Override
    public String getDescriptionText() {
        return tr("Remove primitives from data set");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        deleted.addAll(removed);
    }

}
