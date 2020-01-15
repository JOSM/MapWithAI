// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.tools.Geometry;

public class MergeBuildingAddress extends AbstractConflationCommand {
    public static final String KEY = "addr:housenumber";

    public MergeBuildingAddress(DataSet data) {
        super(data);
    }

    @Override
    public String getDescriptionText() {
        return tr("Merge added addresses with existing buildings");
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Collections.singleton(Node.class);
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public Command getRealCommand() {
        List<Command> commands = new ArrayList<>();
        if (MapWithAIPreferenceHelper.isMergeBuildingAddress()) {
            possiblyAffectedPrimitives.stream().filter(Node.class::isInstance).map(Node.class::cast)
                    .filter(n -> n.hasKey(KEY))
                    .forEach(n -> commands.addAll(mergeBuildingAddress(getAffectedDataSet(), n)));
        }

        Command returnCommand = null;
        if (commands.size() == 1) {
            returnCommand = commands.get(0);
        } else if (!commands.isEmpty()) {
            returnCommand = new SequenceCommand(getDescriptionText(), commands);
        }
        return returnCommand;
    }

    private static Collection<Command> mergeBuildingAddress(DataSet affectedDataSet, Node node) {
        final List<OsmPrimitive> toCheck = new ArrayList<>();
        final BBox bbox = new BBox(node.getCoor().getX(), node.getCoor().getY(), 0.001);
        toCheck.addAll(affectedDataSet.searchWays(bbox));
        toCheck.addAll(affectedDataSet.searchRelations(bbox));
        toCheck.addAll(affectedDataSet.searchNodes(bbox));
        List<OsmPrimitive> possibleDuplicates = toCheck.parallelStream().filter(prim -> prim.hasTag(KEY))
                .filter(prim -> prim.get(KEY).equals(node.get(KEY))).filter(prim -> !prim.equals(node))
                .collect(Collectors.toList());
        for (String tag : Arrays.asList("addr:street", "addr:unit")) {
            if (node.hasTag(tag)) {
                possibleDuplicates = possibleDuplicates.parallelStream().filter(prim -> prim.hasTag(tag))
                        .filter(prim -> prim.get(tag).equals(node.get(tag))).collect(Collectors.toList());
            }
        }

        List<OsmPrimitive> buildings = toCheck.parallelStream().filter(prim -> prim.hasTag("building"))
                .filter(prim -> checkInside(node, prim)).collect(Collectors.toList());

        final List<Command> commandList = new ArrayList<>();
        if (possibleDuplicates.size() == 1) {
            commandList.add(new ChangePropertyCommand(possibleDuplicates, node.getKeys()));
            commandList.add(DeleteCommand.delete(Collections.singleton(node)));
        } else if (buildings.size() == 1 && getAddressPoints(buildings.get(0)).size() == 1) {
            commandList.add(new ChangePropertyCommand(buildings, node.getKeys()));
            commandList.add(DeleteCommand.delete(Collections.singleton(node)));
        }

        return commandList;
    }

    private static Collection<Node> getAddressPoints(OsmPrimitive prim) {
        if (prim instanceof Way && ((Way) prim).isClosed()) {
            return Geometry
                    .filterInsidePolygon(new ArrayList<>(prim.getDataSet().allNonDeletedPrimitives()), (Way) prim)
                    .parallelStream().filter(Node.class::isInstance).map(Node.class::cast).filter(n -> n.hasTag(KEY))
                    .collect(Collectors.toList());
        } else if (prim instanceof Relation) {
            return Geometry
                    .filterInsideMultipolygon(new ArrayList<>(prim.getDataSet().allNonDeletedPrimitives()),
                            (Relation) prim)
                    .parallelStream().filter(Node.class::isInstance).map(Node.class::cast).filter(n -> n.hasKey(KEY))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Check if the node is inside the other primitive
     *
     * @param node The node to check
     * @param prim The primitive that the node may be inside
     * @return true if the node is inside the primitive
     */
    private static boolean checkInside(Node node, OsmPrimitive prim) {
        if (prim instanceof Relation) {
            return !Geometry.filterInsideMultipolygon(Collections.singleton(node), (Relation) prim).isEmpty();
        } else if (prim instanceof Way) {
            return !Geometry.filterInsidePolygon(Arrays.asList(node), (Way) prim).isEmpty();
        }
        return false;
    }

    @Override
    public boolean allowUndo() {
        return true;
    }

}
