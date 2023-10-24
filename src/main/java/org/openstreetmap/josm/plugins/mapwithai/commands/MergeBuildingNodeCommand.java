// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

import jakarta.annotation.Nullable;

/**
 * This is similar to the ReplaceGeometryUtils.buildUpgradeNodeCommand method
 *
 * @author Taylor Smock
 *
 */
public final class MergeBuildingNodeCommand {
    private MergeBuildingNodeCommand() {
        // Hide constructor
    }

    /**
     * Upgrade a node to a way or multipolygon
     *
     * @param subjectNode     node to be replaced
     * @param referenceObject object with greater spatial quality
     * @return The command that updates the node to a way/relation
     */
    @Nullable
    public static Command buildUpgradeNodeCommand(Node subjectNode, OsmPrimitive referenceObject) {
        boolean keepNode = !subjectNode.isNew();
        if (keepNode) {
            getNewOrNoTagNode(referenceObject);
        }
        return null;
    }

    private static Node getNewOrNoTagNode(OsmPrimitive referenceObject) {
        List<Node> nodes;
        if (referenceObject instanceof Way way) {
            nodes = way.getNodes();
        } else if (referenceObject instanceof Relation relation) {
            nodes = relation.getMemberPrimitives().stream().flatMap(o -> {
                if (o instanceof Way way) {
                    return way.getNodes().stream();
                } else if (o instanceof Node node) {
                    return Stream.of(node);
                }
                return null;
            }).filter(Objects::nonNull).toList();
        } else if (referenceObject instanceof Node node) {
            nodes = Collections.singletonList(node);
        } else {
            throw new IllegalArgumentException(tr("Unknown OsmPrimitive type"));
        }
        return nodes.stream().filter(OsmPrimitive::isNew).findAny()
                .orElse(nodes.stream().filter(p -> !p.isTagged()).findAny().orElse(nodes.get(0)));
    }
}
