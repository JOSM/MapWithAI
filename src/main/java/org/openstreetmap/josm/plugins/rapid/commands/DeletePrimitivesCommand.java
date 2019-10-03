package org.openstreetmap.josm.plugins.rapid.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Logging;

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
        for (final OsmPrimitive primitive : selection) {
            final DataSet dataSet = primitive.getDataSet();

            if (from.equals(dataSet)) {
                try {
                    SwingUtilities.invokeAndWait(() -> dataSet.removePrimitive(primitive));
                } catch (final InvocationTargetException e) {
                    Logging.debug(e);
                } catch (final InterruptedException e) {
                    Logging.debug(e);
                    Thread.currentThread().interrupt();
                }
                removed.add(primitive);
            }
            if (primitive instanceof Way) {
                final List<OsmPrimitive> nodes = ((Way) primitive).getNodes().stream()
                        .filter(node -> (!node.hasKeys() || deleteChildren) && !selection.contains(node))
                        .collect(Collectors.toList());
                final DeletePrimitivesCommand delNodes = new DeletePrimitivesCommand(from, nodes);
                delNodes.executeCommand();
                commands.add(delNodes);
            }
        }
        return true;
    }

    @Override
    public void undoCommand() {
        for (final Command command : commands) {
            command.undoCommand();
        }
        for (final OsmPrimitive primitive : removed) {
            final DataSet removedPrimitiveDataSet = primitive.getDataSet();
            if (removedPrimitiveDataSet != null) {
                removedPrimitiveDataSet.removePrimitive(primitive);
            }
            if (primitive instanceof Way) {
                for (final Node node : ((Way) primitive).getNodes()) {
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
        for (final Command command : commands) {
            command.fillModifiedData(modified, deleted, added);
        }
        deleted.addAll(removed);
    }

}
