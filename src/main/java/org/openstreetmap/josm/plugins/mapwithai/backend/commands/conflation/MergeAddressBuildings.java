// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryUtils;
import org.openstreetmap.josm.tools.Geometry;

/**
 * Merge buildings with pre-existing addresses
 *
 * @author Taylor Smock
 *
 */
public class MergeAddressBuildings extends AbstractConflationCommand {
    public static final String KEY = "building";

    public MergeAddressBuildings(DataSet data) {
        super(data);
    }

    @Override
    public String getDescriptionText() {
        return tr("Merge added buildings with existing address nodes");
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Arrays.asList(Way.class, Relation.class);
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public Command getRealCommand() {
        List<Command> commands = new ArrayList<>();
        if (MapWithAIPreferenceHelper.isMergeBuildingAddress()) {
            possiblyAffectedPrimitives.stream().filter(Way.class::isInstance).map(Way.class::cast)
                    .filter(way -> way.hasKey(KEY)).filter(Way::isClosed)
                    .forEach(way -> commands.addAll(mergeAddressBuilding(getAffectedDataSet(), way)));

            possiblyAffectedPrimitives.stream().filter(Relation.class::isInstance).map(Relation.class::cast)
                    .filter(rel -> rel.hasKey(KEY)).filter(Relation::isMultipolygon)
                    .forEach(rel -> commands.addAll(mergeAddressBuilding(getAffectedDataSet(), rel)));
        }

        Command returnCommand = null;
        if (commands.size() == 1) {
            returnCommand = commands.get(0);
        } else if (!commands.isEmpty()) {
            returnCommand = new SequenceCommand(getDescriptionText(), commands);
        }
        return returnCommand;
    }

    private static Collection<? extends Command> mergeAddressBuilding(DataSet affectedDataSet, OsmPrimitive object) {
        final List<IPrimitive> toCheck = new ArrayList<>();
        toCheck.addAll(affectedDataSet.searchNodes(object.getBBox()));
        final Collection<IPrimitive> nodesInside = Geometry.filterInsideAnyPolygon(toCheck, object);

        final List<Node> nodesWithAddresses = nodesInside.stream().filter(Node.class::isInstance).map(Node.class::cast)
                .filter(node -> node.hasKey("addr:housenumber", "addr:housename")).collect(Collectors.toList());

        final List<Command> commandList = new ArrayList<>();
        if (nodesWithAddresses.size() == 1
                && nodesWithAddresses.parallelStream().allMatch(n -> n.getParentWays().isEmpty())) {
            String currentKey = null;
            try {
                // Remove the key to avoid the popup from utilsplugin2
                currentKey = object.get(KEY);
                object.remove(KEY);
                GuiHelper.runInEDTAndWait(() -> commandList
                        .add(ReplaceGeometryUtils.buildUpgradeNodeCommand(nodesWithAddresses.get(0), object)));
            } finally {
                if (currentKey != null) {
                    object.put(KEY, currentKey);
                }
            }
        }
        return commandList;
    }

    @Override
    public boolean allowUndo() {
        return true;
    }

    @Override
    public boolean keyShouldNotExistInOSM() {
        return false;
    }

    @Override
    public Collection<Class<? extends AbstractConflationCommand>> conflictedCommands() {
        return Collections.singleton(MergeBuildingAddress.class);
    }
}
