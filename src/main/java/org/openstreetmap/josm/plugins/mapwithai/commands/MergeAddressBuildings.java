// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.command.ChangePropertyCommand;
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
    public static final String SOURCE = "source";

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
        final List<IPrimitive> toCheck = new ArrayList<>(affectedDataSet.searchNodes(object.getBBox()));
        toCheck.removeIf(IPrimitive::isDeleted);
        final Collection<IPrimitive> nodesInside = Geometry.filterInsideAnyPolygon(toCheck, object);

        final List<Node> nodesWithAddresses = nodesInside.stream().filter(Node.class::isInstance).map(Node.class::cast)
                .filter(node -> node.keySet().stream().anyMatch(str -> str.startsWith("addr:")))
                .collect(Collectors.toList());

        final List<Command> commandList = new ArrayList<>();
        if (nodesWithAddresses.size() == 1 && nodesWithAddresses.stream().allMatch(n -> n.getParentWays().isEmpty())) {
            String currentKey = null;
            Node node = nodesWithAddresses.get(0);
            List<String> sources = new ArrayList<>();
            try {
                // Remove the key to avoid the popup from utilsplugin2
                currentKey = object.get(KEY);
                sources.add(object.get(SOURCE));
                object.remove(KEY);
                object.remove(SOURCE);
                GuiHelper.runInEDTAndWait(
                        () -> commandList.add(ReplaceGeometryUtils.buildUpgradeNodeCommand(node, object)));
            } finally {
                if (currentKey != null) {
                    object.put(KEY, currentKey);
                }
                sources.add(node.get(SOURCE));
                sources.removeIf(Objects::isNull);
                sources = sources.stream().flatMap(source -> Stream.of(source.split(";", 0))).distinct()
                        .filter(Objects::nonNull).sorted().collect(Collectors.toList());
                if (!sources.isEmpty()) {
                    commandList.add(new ChangePropertyCommand(object, SOURCE, String.join(";", sources)));
                }
            }
        }
        return commandList;
    }

    @Override
    public boolean allowUndo() {
        return false;
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
