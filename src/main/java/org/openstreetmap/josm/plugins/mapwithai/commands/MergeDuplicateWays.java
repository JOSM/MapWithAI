// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.commands;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIPreferenceHelper;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;

/**
 * Merge duplicate ways
 */
public class MergeDuplicateWays extends Command {
    public static final String ORIG_ID = "orig_id";

    private final List<Way> ways;

    private final List<Command> commands;
    private Command command;

    private Bounds bound;

    /**
     * Merge duplicate ways
     *
     * @param data Merge all duplicate ways in the dataset
     */
    public MergeDuplicateWays(DataSet data) {
        this(data, null, null);
    }

    /**
     * Merge duplicate ways
     *
     * @param way1 The way to merge duplicates to
     */
    public MergeDuplicateWays(Way way1) {
        this(way1.getDataSet(), way1, null);
    }

    /**
     * Merge duplicate ways
     *
     * @param way1 The way to merge duplicates to
     * @param way2 The way to merge from
     */
    public MergeDuplicateWays(Way way1, Way way2) {
        this(way1.getDataSet(), way1, way2);
    }

    /**
     * Merge duplicate ways
     *
     * @param data The originating dataset
     * @param way1 The first way to check against
     * @param way2 The second way
     */
    public MergeDuplicateWays(DataSet data, Way way1, Way way2) {
        this(data, Stream.of(way1, way2).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * Merge duplicate ways
     *
     * @param data The originating dataset
     * @param ways The ways to merge. If empty, the entire dataset will be checked.
     */
    public MergeDuplicateWays(DataSet data, List<Way> ways) {
        super(data);
        this.ways = ways.stream().filter(MergeDuplicateWays::nonDeletedWay).collect(Collectors.toList());
        this.commands = new ArrayList<>();
    }

    private static boolean nonDeletedWay(Way way) {
        return !way.isDeleted() && way.getNodes().stream().noneMatch(OsmPrimitive::isDeleted);
    }

    @Override
    public boolean executeCommand() {
        if (commands.isEmpty() || (command == null)) {
            if (ways.isEmpty()) {
                filterDataSet(getAffectedDataSet(), commands, bound);
            } else if (ways.size() == 1) {
                checkForDuplicateWays(ways.get(0), commands);
            } else {
                Iterator<Way> it = ways.iterator();
                Way way1 = it.next();
                while (it.hasNext()) {
                    Way way2 = it.next();
                    final Command tCommand = checkForDuplicateWays(way1, way2);
                    if (tCommand != null) {
                        commands.add(tCommand);
                        tCommand.executeCommand();
                    }
                    way1 = way2;
                }
            }
            final List<Command> realCommands = commands.stream().filter(Objects::nonNull).distinct()
                    .collect(Collectors.toList());
            commands.clear();
            commands.addAll(realCommands);
            if (!commands.isEmpty() && (commands.size() != 1)) {
                command = new SequenceCommand(getDescriptionText(), commands);
            } else if (commands.size() == 1) {
                command = commands.get(0);
            }
        } else {
            command.executeCommand();
        }
        return true;
    }

    @Override
    public void undoCommand() {
        if (command != null) {
            command.undoCommand();
        }
    }

    /**
     * Set the bounds for the command. Must be called before execution.
     *
     * @param bound The boundary for the command
     */
    public void setBounds(Bounds bound) {
        this.bound = bound;
    }

    /**
     * Look for duplicates in the dataset
     *
     * @param dataSet  The dataset to look through
     * @param commands The command list to add to
     * @param bound    The bounds to look at, may be {@code null}
     */
    public static void filterDataSet(@Nonnull DataSet dataSet, @Nonnull List<Command> commands,
            @Nullable Bounds bound) {
        final List<Way> ways = (bound == null ? dataSet.getWays() : dataSet.searchWays(bound.toBBox())).stream()
                .filter(prim -> !prim.isIncomplete() && !prim.isDeleted())
                .collect(Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < ways.size(); i++) {
            final Way way1 = ways.get(i);
            final Collection<Way> nearbyWays = dataSet.searchWays(way1.getBBox()).stream()
                    .filter(MergeDuplicateWays::nonDeletedWay).collect(Collectors.toList());
            nearbyWays.remove(way1);
            for (final Way way2 : nearbyWays) {
                final Command command = checkForDuplicateWays(way1, way2);
                final Collection<OsmPrimitive> deletedWays = new ArrayList<>();
                if (command != null) {
                    commands.add(command);
                    command.executeCommand();
                    command.fillModifiedData(new ArrayList<>(), deletedWays, new ArrayList<>());
                    if (!deletedWays.contains(way1) && !deletedWays.contains(way2)) {
                        commands.add(command);
                    }
                    ways.remove(way2);
                }
            }
        }
    }

    /**
     * Check for ways that are (partial) duplicates, and if so merge them
     *
     * @param way      A way to check
     * @param commands A list of commands to add to
     */
    public static void checkForDuplicateWays(Way way, List<Command> commands) {
        final Collection<Way> nearbyWays = way.getDataSet().searchWays(way.getBBox()).stream()
                .filter(MergeDuplicateWays::nonDeletedWay).collect(Collectors.toList());
        nearbyWays.remove(way);
        for (final Way way2 : nearbyWays) {
            if (!way2.isDeleted()) {
                final Command tCommand = checkForDuplicateWays(way, way2);
                if (tCommand != null) {
                    commands.add(tCommand);
                    tCommand.executeCommand();
                }
            }
        }
    }

    /**
     * Check if ways are (partial) duplicates, and if so create a command to merge
     * them
     *
     * @param way1 A way to check
     * @param way2 A way to check
     * @return non-null command if they are duplicate ways
     */
    public static Command checkForDuplicateWays(Way way1, Way way2) {
        Command returnCommand = null;
        final Map<Pair<Integer, Node>, Map<Integer, Node>> duplicateNodes = getDuplicateNodes(way1, way2);
        final Set<Map.Entry<Pair<Integer, Node>, Map<Integer, Node>>> duplicateEntrySet = duplicateNodes.entrySet();
        final Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> compressed = duplicateNodes.entrySet().stream()
                .map(entry -> new Pair<>(entry.getKey(),
                        new Pair<>(entry.getValue().entrySet().iterator().next().getKey(),
                                entry.getValue().entrySet().iterator().next().getValue())))
                .sorted(Comparator.comparingInt(pair -> pair.a.a)).collect(Collectors.toCollection(LinkedHashSet::new));
        if (compressed.stream().anyMatch(entry -> entry.a.b.isDeleted() || entry.b.b.isDeleted())) {
            Logging.error("Bad node");
            Logging.error("{0}", way1);
            Logging.error("{0}", way2);
        }
        if ((compressed.size() > 1) && duplicateEntrySet.stream().noneMatch(entry -> entry.getValue().size() > 1)) {
            final List<Integer> initial = compressed.stream().map(entry -> entry.a.a).sorted()
                    .collect(Collectors.toList());
            final List<Integer> after = compressed.stream().map(entry -> entry.b.a).sorted()
                    .collect(Collectors.toList());
            if (sorted(initial) && sorted(after)) {
                returnCommand = mergeWays(way1, way2, compressed);
            }
        } else if (compressed.isEmpty() && way1.hasKey(ORIG_ID) && way1.get(ORIG_ID).equals(way2.get(ORIG_ID))) {
            returnCommand = mergeWays(way1, way2, compressed);
        }
        return returnCommand;
    }

    /**
     * Merge ways with multiple common nodes
     *
     * @param way1       The way to keep
     * @param way2       The way to remove while moving its nodes to way1
     * @param compressed The duplicate nodes
     * @return A command to merge ways, null if not possible
     */
    public static Command mergeWays(Way way1, Way way2,
            Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> compressed) {
        Command command = null;
        if ((compressed.size() > 1) || (way1.hasKey(ORIG_ID) && way1.get(ORIG_ID).equals(way2.get(ORIG_ID)))) {
            Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> realSet = new LinkedHashSet<>(compressed);
            final boolean sameDirection = checkDirection(realSet);
            final List<Node> way2Nodes = way2.getNodes();
            if (!sameDirection) {
                Collections.reverse(way2Nodes);
                realSet = realSet.stream().map(pair -> {
                    pair.b.a = way2Nodes.size() - pair.b.a - 1;
                    return pair;
                }).collect(Collectors.toSet());
            }
            final int last = realSet.stream().mapToInt(pair -> pair.b.a).max().orElse(way2Nodes.size());
            final int first = realSet.stream().mapToInt(pair -> pair.b.a).min().orElse(0);
            final List<Node> before = new ArrayList<>();
            final List<Node> after = new ArrayList<>();
            for (final Node node : way2Nodes) {
                final int position = way2Nodes.indexOf(node);
                if (position < first) {
                    before.add(node);
                } else if (position > last) {
                    after.add(node);
                }
            }
            Collections.reverse(before);
            final Way newWay = new Way(way1);
            List<Command> commands = new ArrayList<>();
            before.forEach(node -> newWay.addNode(0, node));
            after.forEach(newWay::addNode);
            if (newWay.getNodesCount() > 0) {
                final ChangeCommand changeCommand = new ChangeCommand(way1, newWay);
                commands.add(changeCommand);
                /*
                 * This must be executed, otherwise the delete command will believe that way2
                 * nodes don't belong to anything See Issue #46
                 */
                changeCommand.executeCommand();
                commands.add(DeleteCommand.delete(Collections.singleton(way2), true, true));
                /*
                 * Just to ensure that the dataset is consistent prior to the "real"
                 * executeCommand
                 */
                changeCommand.undoCommand();
            }
            if (commands.contains(null)) {
                commands = commands.stream().filter(Objects::nonNull).collect(Collectors.toList());
            }
            if (!commands.isEmpty()) {
                command = new SequenceCommand(tr("Merge ways"), commands);
            }
        }
        return command;
    }

    /**
     * Find a node's duplicate in a set of duplicates
     *
     * @param node       The node to find in the set
     * @param compressed The set of node duplicates
     * @return The node that the param {@code node} duplicates
     */
    public static Node nodeInCompressed(Node node, Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> compressed) {
        Node returnNode = node;
        for (final Pair<Pair<Integer, Node>, Pair<Integer, Node>> pair : compressed) {
            if (node.equals(pair.a.b)) {
                returnNode = pair.b.b;
            } else if (node.equals(pair.b.b)) {
                returnNode = pair.a.b;
            }
            if (!node.equals(returnNode)) {
                break;
            }
        }
        final Node tReturnNode = returnNode;
        node.getKeys().forEach(tReturnNode::put);
        return returnNode;
    }

    /**
     * Check if the node pairs increment in the same direction (only checks first
     * two pairs), ensure that they are sorted with {@link sorted}
     *
     * @param compressed The set of duplicate node/placement pairs
     * @return true if the node pairs increment in the same direction
     */
    public static boolean checkDirection(Set<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> compressed) {
        final Iterator<Pair<Pair<Integer, Node>, Pair<Integer, Node>>> iterator = compressed.iterator();
        boolean returnValue = false;
        if (compressed.size() > 1) {
            final Pair<Pair<Integer, Node>, Pair<Integer, Node>> first = iterator.next();
            final Pair<Pair<Integer, Node>, Pair<Integer, Node>> second = iterator.next();
            final boolean way1Forward = first.a.a < second.a.a;
            final boolean way2Forward = first.b.a < second.b.a;
            returnValue = way1Forward == way2Forward;
        }
        return returnValue;
    }

    /**
     * Check if a list is a consecutively increasing number list
     *
     * @param collection The list of integers
     * @return true if there are no gaps and it increases
     */
    public static boolean sorted(List<Integer> collection) {
        boolean returnValue = true;
        if (collection.size() > 1) {
            Integer last = collection.get(0);
            for (int i = 1; i < collection.size(); i++) {
                final Integer next = collection.get(i);
                if ((next - last) != 1) {
                    returnValue = false;
                    break;
                }
                last = next;
            }
        }
        return returnValue;
    }

    /**
     * Get duplicate nodes from two ways
     *
     * @param way1 An initial way with nodes
     * @param way2 A way that may have duplicate nodes with way1
     * @return A map of node -&gt; node(s) duplicates
     */
    public static Map<Pair<Integer, Node>, Map<Integer, Node>> getDuplicateNodes(Way way1, Way way2) {
        final Map<Pair<Integer, Node>, Map<Integer, Node>> duplicateNodes = new LinkedHashMap<>();
        for (int j = 0; j < way1.getNodesCount(); j++) {
            final Node origNode = way1.getNode(j);
            for (int k = 0; k < way2.getNodesCount(); k++) {
                final Node possDupeNode = way2.getNode(k);
                if (origNode.equals(possDupeNode) || (origNode
                        .greatCircleDistance(possDupeNode) < MapWithAIPreferenceHelper.getMaxNodeDistance())) {
                    final Pair<Integer, Node> origNodePair = new Pair<>(j, origNode);
                    final Map<Integer, Node> dupeNodeMap = duplicateNodes.getOrDefault(origNodePair, new HashMap<>());
                    dupeNodeMap.put(k, possDupeNode);
                    duplicateNodes.put(origNodePair, dupeNodeMap);
                }
            }
        }
        return duplicateNodes;
    }

    @Override
    public String getDescriptionText() {
        return tr("Merge ways");
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
            Collection<OsmPrimitive> added) {
        for (final Command currentCommand : commands) {
            currentCommand.fillModifiedData(modified, deleted, added);
        }
    }

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return commands.stream().flatMap(currentCommand -> currentCommand.getParticipatingPrimitives().stream())
                .collect(Collectors.toSet());
    }
}
