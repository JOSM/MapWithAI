// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.PreConflatedDataUtils;

/**
 * All this currently does is remove
 * {@link PreConflatedDataUtils#CONFLATED_KEY}.
 *
 * @author Taylor Smock
 *
 */
public class AlreadyConflatedCommand extends AbstractConflationCommand {

    public AlreadyConflatedCommand(DataSet data) {
        super(data);
    }

    @Override
    public String getDescriptionText() {
        return tr("Remove key for already conflated data");
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Arrays.asList(Node.class, Way.class, Relation.class);
    }

    @Override
    public String getKey() {
        return PreConflatedDataUtils.getConflatedKey();
    }

    @Override
    public Command getRealCommand() {
        List<Command> commands = possiblyAffectedPrimitives.stream().filter(p -> p.hasTag(getKey()))
                .map(k -> new ChangePropertyCommand(k, getKey(), "")).collect(Collectors.toList());
        return commands.isEmpty() ? null : SequenceCommand.wrapIfNeeded(getDescriptionText(), commands);
    }

    @Override
    public boolean allowUndo() {
        return true;
    }

    @Override
    public boolean keyShouldNotExistInOSM() {
        return true;
    }

}
