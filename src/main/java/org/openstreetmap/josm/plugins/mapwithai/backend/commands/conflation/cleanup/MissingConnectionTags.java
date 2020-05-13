// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.cleanup;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.tests.CrossingWays;
import org.openstreetmap.josm.data.validation.tests.DuplicateNode;
import org.openstreetmap.josm.data.validation.tests.UnconnectedWays;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.AbstractConflationCommand;
import org.openstreetmap.josm.plugins.mapwithai.commands.CreateConnectionsCommand;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * This checks for missing dupe tags, and asks the user if it is a duplicate
 *
 * @author Taylor Smock
 */
public class MissingConnectionTags extends AbstractConflationCommand {
    private double precision;
    private static final String HIGHWAY = "highway";

    public MissingConnectionTags(DataSet data) {
        super(data);
    }

    @Override
    public String getDescriptionText() {
        return tr("Deduplicate nodes and connect ways");
    }

    @Override
    public Collection<Class<? extends OsmPrimitive>> getInterestedTypes() {
        return Collections.singleton(Way.class);
    }

    @Override
    public String getKey() {
        // For now, we assume that only highways are at issue.
        return HIGHWAY;
    }

    @Override
    public Command getRealCommand() {
        precision = Config.getPref().getDouble("validator.duplicatenodes.precision", 0.);
        // precision is in meters
        precision = precision == 0 ? 1 : precision;
        Layer current = MainApplication.getLayerManager().getActiveLayer();
        Collection<Way> ways = Utils.filteredCollection(possiblyAffectedPrimitives, Way.class);
        if (!ways.isEmpty()) {
            DataSet ds = this.getAffectedDataSet();
            Layer toSwitch = MainApplication.getLayerManager().getLayersOfType(AbstractOsmDataLayer.class).stream()
                    .filter(d -> ds.equals(d.getDataSet())).findAny().orElse(null);
            if (toSwitch != null) {
                MainApplication.getLayerManager().setActiveLayer(toSwitch);
            }
        }
        String prefKey = "mapwithai.conflation.missingconflationtags";

        Collection<OsmPrimitive> selection = getAffectedDataSet().getAllSelected();
        ConditionalOptionPaneUtil.startBulkOperation(prefKey);
        Collection<Command> commands = new ArrayList<>();
        fixErrors(prefKey, commands, findDuplicateNodes(possiblyAffectedPrimitives));
        fixErrors(prefKey, commands, findCrossingWaysAtNodes(possiblyAffectedPrimitives));
        fixErrors(prefKey, commands, findUnconnectedWays(possiblyAffectedPrimitives));
        ConditionalOptionPaneUtil.endBulkOperation(prefKey);
        if (current != null) {
            MainApplication.getLayerManager().setActiveLayer(current);
        }
        GuiHelper.runInEDT(() -> getAffectedDataSet().setSelected(selection));
        commands.forEach(Command::undoCommand);
        return commands.isEmpty() ? null : new SequenceCommand(tr("Perform missing conflation steps"), commands);
    }

    private void fixErrors(String prefKey, Collection<Command> commands, Collection<TestError> issues) {
        for (TestError issue : issues) {
            issue.getHighlighted();
            if (!issue.isFixable() || issue.getPrimitives().parallelStream().anyMatch(IPrimitive::isDeleted)) {
                continue;
            }
            GuiHelper.runInEDT(() -> getAffectedDataSet().setSelected(issue.getPrimitives()));
            Collection<? extends OsmPrimitive> primitives = issue.getPrimitives();
            if (primitives.stream().noneMatch(Node.class::isInstance)) {
                AutoScaleAction.zoomTo(issue.getPrimitives());
            } else {
                AutoScaleAction.zoomTo(issue.getPrimitives().stream().filter(Node.class::isInstance)
                        .map(Node.class::cast).collect(Collectors.toList()));
            }
            String message = issue.getFix().getDescriptionText();
            boolean fixIt = ConditionalOptionPaneUtil.showConfirmationDialog(prefKey, MainApplication.getMainFrame(),
                    message, tr("Possible missing conflation key"), JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_OPTION);
            if (fixIt) {
                Command command = issue.getFix();
                command.executeCommand();
                commands.add(command);
            }
        }
    }

    /**
     * Find nodes that may be missing a dupe tag
     *
     * @param possiblyAffectedPrimitives The primitives that may be affected
     * @return The issues
     */
    protected static Collection<TestError> findDuplicateNodes(Collection<OsmPrimitive> possiblyAffectedPrimitives) {
        Collection<TestError> issues = new ArrayList<>();
        DuplicateNode duplicateNodeTest = new DuplicateNode();
        for (Way way : Utils.filteredCollection(possiblyAffectedPrimitives, Way.class)) {
            for (Node node : way.getNodes()) {
                duplicateNodeTest.startTest(NullProgressMonitor.INSTANCE);
                BBox searchBBox = node.getBBox();
                searchBBox.addPrimitive(node, 0.001);
                way.getDataSet().searchNodes(searchBBox).stream().filter(MissingConnectionTags::noConflationKey)
                        .forEach(duplicateNodeTest::visit);
                duplicateNodeTest.endTest();
                issues.addAll(duplicateNodeTest.getErrors().stream().distinct().collect(Collectors.toList()));
                duplicateNodeTest.clear();
            }
        }
        return issues;
    }

    /**
     * Find nodes that may be missing a conn tag
     *
     * @param possiblyAffectedPrimitives The primitives that may be affected
     * @return The issues found
     */
    protected Collection<TestError> findCrossingWaysAtNodes(Collection<OsmPrimitive> possiblyAffectedPrimitives) {
        Collection<TestError> issues = new ArrayList<>();
        CrossingWays.Ways crossingWays = new CrossingWays.Ways();
        for (Way way : Utils.filteredCollection(possiblyAffectedPrimitives, Way.class)) {
            Collection<OsmPrimitive> seenFix = new HashSet<>();
            crossingWays.startTest(NullProgressMonitor.INSTANCE);
            way.getDataSet().searchWays(way.getBBox()).forEach(crossingWays::visit);
            crossingWays.endTest();
            for (TestError error : crossingWays.getErrors()) {
                if (seenFix.containsAll(error.getPrimitives())
                        || error.getPrimitives().stream().filter(Way.class::isInstance).map(Way.class::cast)
                                .filter(w -> w.hasKey(HIGHWAY)).count() == 0L) {
                    continue;
                }
                TestError.Builder fixError = TestError.builder(error.getTester(), error.getSeverity(), error.getCode())
                        .primitives(error.getPrimitives()).fix(createIntersectionCommandSupplier(error, way))
                        .message(error.getMessage());
                seenFix.addAll(error.getPrimitives());
                issues.add(fixError.build());
            }
            crossingWays.clear();
        }
        return issues;
    }

    private Supplier<Command> createIntersectionCommandSupplier(TestError error, Way way) {
        Collection<Node> nodes = Geometry.addIntersections(error.getPrimitives().stream().filter(Way.class::isInstance)
                .map(Way.class::cast).filter(w -> w.hasKey(HIGHWAY)).collect(Collectors.toList()), false,
                new ArrayList<>());
        if (nodes.stream().anyMatch(n -> way.getNodes().stream().filter(MissingConnectionTags::noConflationKey)
                .anyMatch(wn -> n.getCoor().greatCircleDistance(wn.getCoor()) < precision))) {
            return () -> createIntersectionCommand(way,
                    way.getNodes().stream().filter(MissingConnectionTags::noConflationKey)
                            .filter(n1 -> nodes.stream().anyMatch(n2 -> Geometry.getDistance(n1, n2) < precision))
                            .collect(Collectors.toList()),
                    precision);
        }
        return null;
    }

    private static Command createIntersectionCommand(Way way, Collection<Node> intersectionNodes, double precision) {
        Collection<Command> commands = new ArrayList<>();
        if (intersectionNodes.stream().anyMatch(way::containsNode)) {
            Collection<Way> searchWays = way.getDataSet().searchWays(way.getBBox()).parallelStream()
                    .collect(Collectors.toList());
            searchWays.remove(way);
            for (Way potential : searchWays) {
                for (Node node : way.getNodes()) {
                    Command command = createAddNodeCommand(potential, node, precision);
                    if (command != null) {
                        commands.add(command);
                    }
                }
            }
        }
        if (commands.size() == 1) {
            return commands.iterator().next();
        } else if (!commands.isEmpty()) {
            return new SequenceCommand(tr("Create intersections"), commands);
        }
        return null;
    }

    private static Command createAddNodeCommand(Way way, Node node, double precision) {
        if (Geometry.getDistance(node, way) < precision) {
            WaySegment seg = Geometry.getClosestWaySegment(way, node);
            int index1 = way.getNodes().indexOf(seg.getFirstNode());
            int index2 = way.getNodes().indexOf(seg.getSecondNode());
            if (index2 - index1 == 1) {
                Way newWay = new Way(way);
                newWay.addNode(index2, node);
                return new ChangeCommand(way, newWay);
            }
        }
        return null;
    }

    protected static Collection<TestError> findUnconnectedWays(Collection<OsmPrimitive> possiblyAffectedPrimitives) {
        UnconnectedWays.UnconnectedHighways unconnectedWays = new UnconnectedWays.UnconnectedHighways();
        unconnectedWays.startTest(NullProgressMonitor.INSTANCE);
        unconnectedWays.visit(possiblyAffectedPrimitives);
        unconnectedWays.endTest();
        double p = Config.getPref().getDouble(
                ValidatorPrefHelper.PREFIX + "." + UnconnectedWays.class.getSimpleName() + ".node_way_distance", 10.0);
        // precision is in meters
        double precision = p == 0 ? 1 : p;

        Collection<TestError> issues = new ArrayList<>();
        for (TestError issue : unconnectedWays.getErrors()) {
            if (issue.isFixable()) {
                issues.add(issue);
                continue;
            }
            Way way = Utils.filteredCollection(new ArrayList<>(issue.getPrimitives()), Way.class).stream().findAny()
                    .orElse(null);
            Node node = Utils.filteredCollection(new ArrayList<>(issue.getPrimitives()), Node.class).stream().findAny()
                    .orElse(null);
            if (way != null && node != null) {
                TestError.Builder issueBuilder = TestError
                        .builder(issue.getTester(), issue.getSeverity(), issue.getCode()).message(issue.getMessage())
                        .primitives(issue.getPrimitives());
                issueBuilder.fix(() -> createAddNodeCommand(way, node, precision));
                issues.add(issueBuilder.build());
            }
        }
        return issues;
    }

    private static boolean noConflationKey(OsmPrimitive prim) {
        return CreateConnectionsCommand.getConflationCommands().stream().map(c -> {
            try {
                return c.getConstructor(DataSet.class).newInstance(prim.getDataSet());
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                Logging.error(e);
            }
            return null;
        }).filter(Objects::nonNull).noneMatch(c -> prim.hasKey(c.getKey()));
    }

    @Override
    public boolean allowUndo() {
        return false;
    }

    @Override
    public boolean keyShouldNotExistInOSM() {
        return false;
    }

}
