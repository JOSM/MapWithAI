// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.spi.preferences.Config;

public class StubEndsTest extends Test {
    private static final String HIGHWAY = "highway";
    private static final List<String> BAD_HIGHWAYS = Arrays.asList("services", "rest_area");
    private double max_length = Config.getPref().getDouble(MapWithAIPlugin.NAME + ".stubendlength", 5);

    public StubEndsTest() {
        super(tr("Stub Ends ({0})", MapWithAIPlugin.NAME), tr("Look for short ends on ways"));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        max_length = Config.getPref().getDouble(MapWithAIPlugin.NAME + ".stubendlength", 10);
    }

    @Override
    public void visit(Way way) {
        if (way.hasTag(HIGHWAY) && !BAD_HIGHWAYS.contains(way.get(HIGHWAY)) && !way.isClosed()) {
            checkEnds(way);
        }
    }

    private void checkEnds(Way way) {
        List<Node> nodesToFirstConnection = new ArrayList<>();
        double distanceToFirstConnection = distanceToFirstConnection(way, nodesToFirstConnection);
        if (distanceToFirstConnection < max_length && !nodesToFirstConnection.isEmpty()) {
            errors.add(createError(way, nodesToFirstConnection, distanceToFirstConnection));
        }

        List<Node> nodesToLastConnection = new ArrayList<>();
        double distanceToLastConnection = distanceToLastConnection(way, nodesToLastConnection);
        if (distanceToLastConnection < max_length && !nodesToLastConnection.isEmpty()) {
            errors.add(createError(way, nodesToLastConnection, distanceToLastConnection));
        }
    }

    private TestError createError(Way way, List<Node> nodes, double distance) {
        TestError.Builder error = TestError.builder(this, Severity.ERROR, 333300239)
                .message(tr("{0} (experimental)", MapWithAIPlugin.NAME), marktr("Stub end ({0}m)"), distance)
                .primitives(way).highlight(nodes);
        if (way.isNew()) {
            Way tWay = new Way(way);
            List<Node> tNodes = tWay.getNodes();
            boolean reversed = false;
            if (tWay.lastNode().equals(nodes.get(0))) {
                reversed = true;
                Collections.reverse(tNodes);
            }
            for (Node node : nodes) {
                if (tNodes.get(0).equals(node)) {
                    tNodes.remove(0);
                }
            }
            if (reversed) {
                Collections.reverse(tNodes);
            }
            List<Node> nodesToDelete = nodes.stream().filter(node -> !node.hasKeys())
                    .filter(node -> node.getReferrers().size() == 1).collect(Collectors.toList());
            tWay.setNodes(tNodes);
            nodesToDelete.removeAll(tNodes);
            error.fix(() -> new SequenceCommand(tr("Remove stub ends"), new ChangeCommand(way, tWay),
                    DeleteCommand.delete(nodesToDelete)));
        }
        return error.build();
    }

    private static double distanceToFirstConnection(Way way, List<Node> nodesToFirstConnection) {
        return distanceToConnection(way, nodesToFirstConnection, way.getNodes());
    }

    private static double distanceToLastConnection(Way way, List<Node> nodesToLastConnection) {
        List<Node> nodes = way.getNodes();
        Collections.reverse(nodes);
        return distanceToConnection(way, nodesToLastConnection, nodes);
    }

    private static double distanceToConnection(Way way, List<Node> nodesToConnection, List<Node> nodeOrder) {
        double distance = 0;
        Node previous = nodeOrder.get(0);
        if (previous.hasTag("noexit")) {
            return Double.NaN;
        }
        // isOutsideDownloadArea returns false if new or undeleted as well
        if (!previous.isOutsideDownloadArea()) {
            for (Node node : nodeOrder) {
                List<Way> connectingWays = previous.getReferrers().parallelStream().filter(Way.class::isInstance)
                        .map(Way.class::cast).filter(tWay -> !tWay.equals(way))
                        .filter(tWay -> tWay.hasTag(HIGHWAY) && !BAD_HIGHWAYS.contains(tWay.get(HIGHWAY)))
                        .collect(Collectors.toList());
                if (!node.equals(previous) && connectingWays.isEmpty()) {
                    nodesToConnection.add(previous);
                    distance += node.getCoor().greatCircleDistance(previous.getCoor());
                    previous = node;
                }
                if (!connectingWays.isEmpty()) {
                    break;
                }
            }
        }
        return distance;
    }
}
