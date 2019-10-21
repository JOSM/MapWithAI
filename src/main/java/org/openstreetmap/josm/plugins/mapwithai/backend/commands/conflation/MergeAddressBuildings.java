package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryUtils;
import org.openstreetmap.josm.tools.Geometry;

public class MergeAddressBuildings extends AbstractConflationCommand {
    private static final String BUILDING_KEY = "building";

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
        return BUILDING_KEY;
    }

    @Override
    public Command getRealCommand() {
        List<Command> commands = new ArrayList<>();
        if (MapWithAIPreferenceHelper.isMergeBuildingAddress()) {
            possiblyAffectedPrimitives.stream().filter(Way.class::isInstance).map(Way.class::cast)
            .filter(way -> way.hasKey(BUILDING_KEY)).filter(Way::isClosed)
            .forEach(way -> commands.addAll(mergeAddressBuilding(getAffectedDataSet(), way)));

            possiblyAffectedPrimitives.stream().filter(Relation.class::isInstance).map(Relation.class::cast)
            .filter(rel -> rel.hasKey(BUILDING_KEY)).filter(Relation::isMultipolygon)
            .forEach(rel -> commands.addAll(mergeAddressBuilding(getAffectedDataSet(), rel)));
        }

        Command returnCommand = null;
        if (!commands.isEmpty()) {
            returnCommand = new SequenceCommand(getDescriptionText(), commands);
        }
        return returnCommand;
    }

    private static Collection<? extends Command> mergeAddressBuilding(DataSet affectedDataSet, Relation rel) {
        final List<IPrimitive> toCheck = new ArrayList<>();
        toCheck.addAll(affectedDataSet.searchNodes(rel.getBBox()));
        final Collection<IPrimitive> nodesInside = Geometry.filterInsideMultipolygon(toCheck, rel);
        final List<Node> nodesWithAddresses = nodesInside.stream().filter(Node.class::isInstance).map(Node.class::cast)
                .filter(node -> node.hasKey("addr:housenumber", "addr:housename")).collect(Collectors.toList());

        final List<Command> commandList = new ArrayList<>();
        if (nodesWithAddresses.size() == 1) {
            commandList.add(ReplaceGeometryUtils.buildUpgradeNodeCommand(nodesWithAddresses.get(0), rel));
        }
        return commandList;
    }

    private Collection<? extends Command> mergeAddressBuilding(DataSet affectedDataSet, Way way) {
        final List<IPrimitive> toCheck = new ArrayList<>();
        toCheck.addAll(affectedDataSet.searchNodes(way.getBBox()));
        final Collection<IPrimitive> nodesInside = Geometry.filterInsidePolygon(toCheck, way);
        final List<Node> nodesWithAddresses = nodesInside.stream().filter(Node.class::isInstance).map(Node.class::cast)
                .filter(node -> node.hasKey("addr:housenumber", "addr:housename")).collect(Collectors.toList());

        final List<Command> commandList = new ArrayList<>();
        if (nodesWithAddresses.size() == 1) {
            commandList.add(ReplaceGeometryUtils.buildUpgradeNodeCommand(nodesWithAddresses.get(0), way));
        }
        return commandList;
    }
}
