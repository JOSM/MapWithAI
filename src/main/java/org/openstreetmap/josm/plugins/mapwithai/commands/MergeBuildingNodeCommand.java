// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

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
        if (referenceObject instanceof Way) {
            nodes = ((Way) referenceObject).getNodes();
        } else if (referenceObject instanceof Relation) {
            nodes = ((Relation) referenceObject).getMemberPrimitives().stream().flatMap(o -> {
                if (o instanceof Way) {
                    return ((Way) o).getNodes().stream();
                } else if (o instanceof Node) {
                    return Stream.of((Node) o);
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
        } else if (referenceObject instanceof Node) {
            nodes = Collections.singletonList((Node) referenceObject);
        } else {
            throw new IllegalArgumentException(tr("Unknown OsmPrimitive type"));
        }
        return nodes.stream().filter(OsmPrimitive::isNew).findAny()
                .orElse(nodes.stream().filter(p -> !p.isTagged()).findAny().orElse(nodes.get(0)));
    }
}
