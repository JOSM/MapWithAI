// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.visitor.MergeSourceBuildingVisitor;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.GetDataRunnable;
import org.openstreetmap.josm.tools.Logging;

/**
 * Move primitives between datasets (*not* a copy)
 *
 * @author Taylor Smock
 */
public class MovePrimitiveDataSetCommand extends Command {
    private SequenceCommand command;

    public MovePrimitiveDataSetCommand(DataSet to, DataSet from, Collection<OsmPrimitive> primitives) {
        super(to);
        if (from == null || to.isLocked() || from.isLocked() || to.equals(from)) {
            Logging.error("{0}: Cannot move primitives from {1} to {2}", MapWithAIPlugin.NAME, from, to);
        } else {
            command = moveCollection(from, to, primitives);
        }
    }

    @Override
    public boolean executeCommand() {
        if (command != null) {
            command.executeCommand();
        }
        return true;
    }

    /**
     * Move primitives from one dataset to another
     *
     * @param to        The receiving dataset
     * @param from      The sending dataset
     * @param selection The primitives to move
     * @return The command that does the actual move
     */
    public static SequenceCommand moveCollection(DataSet from, DataSet to, Collection<OsmPrimitive> selection) {
        final List<Command> commands = new ArrayList<>();

        final Collection<OsmPrimitive> selected = from.getAllSelected();
        from.setSelected(selection);
        final MergeSourceBuildingVisitor builder = new MergeSourceBuildingVisitor(from);
        final DataSet hull = builder.build();
        from.setSelected(selected);

        final List<PrimitiveData> primitiveAddData = hull.allPrimitives().stream().map(OsmPrimitive::save)
                .collect(Collectors.toList());
        primitiveAddData.parallelStream().forEach(data -> data.remove(GetDataRunnable.MAPWITHAI_SOURCE_TAG_KEY));

        commands.add(new AddPrimitivesCommand(primitiveAddData,
                selection.stream().map(OsmPrimitive::save).collect(Collectors.toList()), to));
        commands.add(new DeletePrimitivesCommand(from, selection, true));

        return new SequenceCommand(trn("Move {0} OSM Primitive between data sets",
                "Move {0} OSM Primitives between data sets", selection.size(), selection.size()), commands);
    }

    @Override
    public void undoCommand() {
        if (command != null) {
            command.undoCommand();
        }
    }

    @Override
    public String getDescriptionText() {
        return tr("Move OsmPrimitives between layers");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        command.fillModifiedData(modified, deleted, added);
    }
}
