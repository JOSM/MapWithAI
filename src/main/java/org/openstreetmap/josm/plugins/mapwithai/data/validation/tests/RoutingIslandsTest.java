// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.TagMap;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.tools.Access;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Pair;

/**
 * A test for routing islands
 *
 * @author Taylor Smock
 * @since xxx
 */
public class RoutingIslandsTest extends Test {

    private static final Map<Integer, Severity> SEVERITY_MAP = new HashMap<>();
    /** The code for the routing island validation test */
    public static final int ROUTING_ISLAND = 55000;
    /** The code for ways that are not connected to other ways, and are routable */
    public static final int LONELY_WAY = ROUTING_ISLAND + 1;
    static {
        SEVERITY_MAP.put(ROUTING_ISLAND, Severity.OTHER);
        SEVERITY_MAP.put(LONELY_WAY, Severity.ERROR);
    }

    private static final String HIGHWAY = "highway";
    private static final String WATERWAY = "waterway";
    private static final List<String> IGNORE_TAGS_HIGHWAY = Arrays.asList("services", "rest_area", "platform");
    private static final List<String> IGNORE_TAGS_WATERWAY = Arrays.asList("services", "rest_area", "dam");

    /**
     * This is mostly as a sanity check, and to avoid infinite recursion (shouldn't
     * happen, but still)
     */
    private static final int MAX_LOOPS = 1000;
    /** Highways to check for routing connectivity */
    private Set<Way> potentialHighways;
    /** Waterways to check for routing connectivity */
    private Set<Way> potentialWaterways;

    /**
     * Constructs a new {@code RightAngleBuildingTest} test.
     */
    public RoutingIslandsTest() {
        super(tr("Routing islands (MapWithAI)"), tr("Checks for roads that cannot be reached or left."));
        super.setPartialSelection(false);
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        potentialHighways = new HashSet<>();
        potentialWaterways = new HashSet<>();
    }

    @Override
    public void endTest() {
        Access.AccessTags.getByTransportType(Access.AccessTags.LAND_TRANSPORT_TYPE).forEach(mode -> {
            runTest(mode.getKey(), potentialHighways);
            progressMonitor.setCustomText(mode.getKey());
        });
        Access.AccessTags.getByTransportType(Access.AccessTags.WATER_TRANSPORT_TYPE).forEach(mode -> {
            progressMonitor.setCustomText(mode.getKey());
            runTest(mode.getKey(), potentialWaterways);
        });
        super.endTest();
    }

    @Override
    public void visit(Way way) {
        if (way.isUsable()
                && ((way.hasKey(HIGHWAY) && !IGNORE_TAGS_HIGHWAY.contains(way.get(HIGHWAY)))
                        || (way.hasKey(WATERWAY) && !IGNORE_TAGS_WATERWAY.contains(way.get(WATERWAY))))
                && way.getNodes().stream().anyMatch(node -> way.getDataSet().getDataSourceBounds().stream()
                        .anyMatch(source -> source.contains(node.getCoor())))) {
            if ((way.hasKey(HIGHWAY) || way.hasKey(WATERWAY)) && way.getNodes().stream()
                    .flatMap(node -> node.getReferrers().stream()).distinct().allMatch(way::equals)
                    && way.getNodes().stream().noneMatch(Node::isOutsideDownloadArea)) {
                errors.add(TestError.builder(this, SEVERITY_MAP.get(LONELY_WAY), LONELY_WAY).primitives(way)
                        .message(tr("MapWithAI (experimental)"), marktr("Routable way not connected to other ways"))
                        .build());
            } else if ((ValidatorPrefHelper.PREF_OTHER.get() || ValidatorPrefHelper.PREF_OTHER_UPLOAD.get()
                    || Severity.OTHER != SEVERITY_MAP.get(ROUTING_ISLAND)) && !isBeforeUpload) {
                if (way.hasKey(HIGHWAY) && !IGNORE_TAGS_HIGHWAY.contains(way.get(HIGHWAY))) {
                    potentialHighways.add(way);
                }
                if (way.hasKey(WATERWAY) && !IGNORE_TAGS_WATERWAY.contains(way.get(WATERWAY))) {
                    potentialWaterways.add(way);
                }
            }
        }
    }

    private void runTest(String currentTransportMode, Collection<Way> potentialWays) {
        Set<Way> incomingWays = new HashSet<>();
        Set<Way> outgoingWays = new HashSet<>();
        findConnectedWays(currentTransportMode, potentialWays, incomingWays, outgoingWays);
        Collection<Way> realPotentialWays = (incomingWays.isEmpty() || outgoingWays.isEmpty())
                ? expandNetwork(potentialWays)
                : potentialWays;

        if (incomingWays.isEmpty() || outgoingWays.isEmpty()) {
            findConnectedWays(currentTransportMode, realPotentialWays, incomingWays, outgoingWays);
        }
        runGenericTest(currentTransportMode, realPotentialWays, incomingWays, outgoingWays);

    }

    /**
     * Expand a network from an initial selection
     *
     * @param initial The initial collection of ways
     * @return An expanded collection of ways, which should be all connected ways
     *         that allow the current transport mode.
     */
    private static Collection<Way> expandNetwork(Collection<Way> initial) {
        Collection<Way> connected = initial.stream().flatMap(way -> way.getNodes().stream())
                .flatMap(node -> node.getReferrers().stream()).filter(Way.class::isInstance).map(Way.class::cast)
                .collect(Collectors.toSet());
        if (connected.containsAll(initial) && initial.containsAll(connected)) {
            return connected;
        }
        return expandNetwork(connected);
    }

    /**
     * This test is run when there are known incoming/outgoing ways
     *
     * @param currentTransportMode The current transport mode
     * @param potentialWays        The ways to check
     * @param incomingWays         The incoming ways
     * @param outgoingWays         The outgoing ways
     */
    private void runGenericTest(String currentTransportMode, Collection<Way> potentialWays,
            Collection<Way> incomingWays, Collection<Way> outgoingWays) {
        Set<Way> toIgnore = potentialWays.stream()
                .filter(way -> incomingWays.contains(way) || outgoingWays.contains(way))
                .filter(way -> !Access.getPositiveAccessValues().contains(
                        getDefaultAccessTags(way).getOrDefault(currentTransportMode, Access.AccessTags.NO.getKey())))
                .collect(Collectors.toSet());
        incomingWays.removeAll(toIgnore);
        outgoingWays.removeAll(toIgnore);

        checkForUnconnectedWays(incomingWays, outgoingWays, currentTransportMode);
        List<Pair<String, Set<Way>>> problematic = collectConnected(potentialWays.stream()
                .filter(way -> !incomingWays.contains(way) || !outgoingWays.contains(way))
                .filter(way -> Access.getPositiveAccessValues().contains(
                        getDefaultAccessTags(way).getOrDefault(currentTransportMode, Access.AccessTags.NO.getKey())))
                .collect(Collectors.toSet()))
                        .stream()
                        .map(way -> new Pair<>(
                                (incomingWays.containsAll(way) ? marktr("outgoing") : marktr("incoming")), way))
                        .collect(Collectors.toList());
        createErrors(problematic, currentTransportMode);
    }

    /**
     * Find ways that may be connected to the wider network
     *
     * @param currentTransportMode The current mode of transport
     * @param potentialWays        The ways to check for connections
     * @param incomingWays         A collection that will have incoming ways after
     *                             this method is called
     * @param outgoingWays         A collection that will have outgoing ways after
     *                             this method is called
     */
    private static void findConnectedWays(String currentTransportMode, Collection<Way> potentialWays,
            Collection<Way> incomingWays, Collection<Way> outgoingWays) {
        potentialWays.stream().filter(Way::isUsable).filter(Way::isOutsideDownloadArea).forEach(way -> {
            Node firstNode = firstNode(way, currentTransportMode);
            Node lastNode = lastNode(way, currentTransportMode);
            Integer isOneway = isOneway(way, currentTransportMode);
            if (firstNode != null && firstNode.isOutsideDownloadArea()) {
                incomingWays.add(way);
            }
            if (lastNode != null && lastNode.isOutsideDownloadArea()) {
                outgoingWays.add(way);
            }
            if (isOneway != null && isOneway == 0 && firstNode != null && lastNode != null
                    && (firstNode.isOutsideDownloadArea() || lastNode.isOutsideDownloadArea())) {
                incomingWays.add(way);
                outgoingWays.add(way);
            }
        });
    }

    /**
     * Take a collection of ways and modify it so that it is a list of connected
     * ways
     *
     * @param ways A collection of ways that may or may not be connected
     * @return a list of sets of ways that are connected
     */
    private static List<Set<Way>> collectConnected(Collection<Way> ways) {
        ArrayList<Set<Way>> collected = new ArrayList<>();
        ArrayList<Way> listOfWays = new ArrayList<>(ways);
        final int maxLoop = Config.getPref().getInt("validator.routingislands.maxrecursion", MAX_LOOPS);
        for (int i = 0; i < listOfWays.size(); i++) {
            Way initial = listOfWays.get(i);
            Set<Way> connected = new HashSet<>();
            connected.add(initial);
            int loopCounter = 0;
            while (!getConnected(connected) && loopCounter < maxLoop) {
                loopCounter++;
            }
            if (listOfWays.removeAll(connected)) {
                /*
                 * Not an issue -- this ensures that everything is accounted for, only triggers
                 * when ways removed
                 */
                i--; // NOSONAR NOPMD
            }
            collected.add(connected);
        }
        return collected;
    }

    private static boolean getConnected(Collection<Way> ways) {
        TagMap defaultAccess = getDefaultAccessTags(ways.iterator().next());
        return ways.addAll(ways.stream().flatMap(way -> way.getNodes().stream())
                .flatMap(node -> node.getReferrers().stream()).filter(Way.class::isInstance).map(Way.class::cast)
                .filter(way -> getDefaultAccessTags(way).equals(defaultAccess)).collect(Collectors.toSet()));
    }

    /**
     * Create errors for a problematic way
     *
     * @param problematic The set of problematic ways (Pairs are
     *                    &lt;incoming/outgoing, Set&lt;Connected ways with same
     *                    issue&gt;&gt;)
     * @param mode        The transport mode
     */
    private void createErrors(List<Pair<String, Set<Way>>> problematic, String mode) {
        for (Pair<String, Set<Way>> ways : problematic) {
            errors.add(
                    TestError.builder(this, SEVERITY_MAP.getOrDefault(ROUTING_ISLAND, Severity.OTHER), ROUTING_ISLAND)
                            .message(tr("MapWithAI (experimental)"), marktr("Routing island"), "{1}: {0}", tr(ways.a),
                                    mode == null ? marktr("default") : mode)
                            .primitives(ways.b).build());
        }
    }

    /**
     * Check for unconnected ways
     *
     * @param incoming             The current incoming ways (will be modified)
     * @param outgoing             The current outgoing ways (will be modified)
     * @param currentTransportMode The transport mode we are investigating (may be
     *                             {@code null})
     */
    public static void checkForUnconnectedWays(Collection<Way> incoming, Collection<Way> outgoing,
            String currentTransportMode) {
        int loopCount = 0;
        int maxLoops = Config.getPref().getInt("validator.routingislands.maxrecursion", MAX_LOOPS);
        do {
            loopCount++;
        } while (loopCount <= maxLoops && getWaysFor(incoming, currentTransportMode,
                (way, oldWay) -> oldWay.containsNode(firstNode(way, currentTransportMode))
                        && checkAccessibility(oldWay, way, currentTransportMode)));
        loopCount = 0;
        do {
            loopCount++;
        } while (loopCount <= maxLoops && getWaysFor(outgoing, currentTransportMode,
                (way, oldWay) -> oldWay.containsNode(lastNode(way, currentTransportMode))
                        && checkAccessibility(oldWay, way, currentTransportMode)));
    }

    private static boolean getWaysFor(Collection<Way> directional, String currentTransportMode,
            BiPredicate<Way, Way> predicate) {
        Set<Way> toAdd = new HashSet<>();
        for (Way way : directional) {
            for (Node node : way.getNodes()) {
                Set<Way> referrers = node.getReferrers(true).stream().filter(Way.class::isInstance).map(Way.class::cast)
                        .filter(tWay -> !directional.contains(tWay)).collect(Collectors.toSet());
                for (Way tWay : referrers) {
                    Integer oneWay = isOneway(tWay, currentTransportMode);
                    if ((oneWay != null && oneWay == 0) || predicate.test(tWay, way) || tWay.isClosed()) {
                        toAdd.add(tWay);
                    }
                }
            }
        }
        return directional.addAll(toAdd);
    }

    /**
     * Check if I can get to way to from way from (currently doesn't work with via
     * ways)
     *
     * @param from                 The from way
     * @param to                   The to way
     * @param currentTransportMode The specific transport mode to check
     * @return {@code true} if the to way can be accessed from the from way TODO
     *         clean up and work with via ways
     */
    public static boolean checkAccessibility(Way from, Way to, String currentTransportMode) {
        boolean isAccessible = true;

        List<Relation> relations = from.getReferrers().stream().distinct().filter(Relation.class::isInstance)
                .map(Relation.class::cast).filter(relation -> "restriction".equals(relation.get("type")))
                .collect(Collectors.toList());
        for (Relation relation : relations) {
            if (((relation.hasKey("except") && relation.get("except").contains(currentTransportMode))
                    || (currentTransportMode == null || currentTransportMode.trim().isEmpty()))
                    && relation.getMembersFor(Collections.singleton(from)).stream()
                            .anyMatch(member -> "from".equals(member.getRole()))
                    && relation.getMembersFor(Collections.singleton(to)).stream()
                            .anyMatch(member -> "to".equals(member.getRole()))) {
                isAccessible = false;
            }
        }
        return isAccessible;
    }

    /**
     * Check if a node connects to the outside world
     *
     * @param node The node to check
     * @return true if outside download area, connects to an aeroport, or a water
     *         transport
     */
    public static Boolean outsideConnections(Node node) {
        return node.isOutsideDownloadArea() || node.hasTag("amenity", "parking_entrance", "parking", "parking_space",
                "motorcycle_parking", "ferry_terminal");
    }

    /**
     * Check if a way is oneway for a specific transport type
     *
     * @param way           The way to look at
     * @param transportType The specific transport type
     * @return See {@link Way#isOneway} (but may additionally return {@code null} if
     *         the transport type cannot route down that way)
     */
    @Nullable
    public static Integer isOneway(@Nonnull Way way, @Nullable String transportType) {
        if (transportType == null || transportType.trim().isEmpty()) {
            return way.isOneway();
        }
        String forward = transportType.concat(":forward");
        String backward = transportType.concat(":backward");
        boolean possibleForward = "yes".equals(way.get(forward)) || (!way.hasKey(forward) && way.isOneway() != -1);
        boolean possibleBackward = "yes".equals(way.get(backward)) || (!way.hasKey(backward) && way.isOneway() != 1);
        if (transportType.equals(Access.AccessTags.FOOT.getKey()) && !"footway".equals(way.get(HIGHWAY))
                && !way.hasTag("foot:forward") && !way.hasTag("foot:backward")) {
            /*
             * Foot is almost never oneway, especially on generic road types. There are some
             * cases on mountain paths.
             */
            return 0;
        }
        if (possibleForward && !possibleBackward) {
            return 1;
        } else if (!possibleForward && possibleBackward) {
            return -1;
        } else if (!possibleBackward) {
            return null;
        }
        return 0;
    }

    /**
     * Get the first node of a way respecting the oneway for a transport type
     *
     * @param way           The way to get the node from
     * @param transportType The transport type
     * @return The first node for the specified transport type, or null if it is not
     *         routable
     */
    public static Node firstNode(Way way, String transportType) {
        Integer oneway = isOneway(way, transportType);
        Node node = Integer.valueOf(-1).equals(oneway) ? way.lastNode() : way.firstNode();

        Map<String, String> accessValues = getDefaultAccessTags(way);
        boolean accessible = Access.getPositiveAccessValues()
                .contains(accessValues.getOrDefault(transportType, Access.AccessTags.NO.getKey()));
        return (transportType == null || accessible) ? node : null;
    }

    /**
     * Get the last node of a way respecting the oneway for a transport type
     *
     * @param way           The way to get the node from
     * @param transportType The transport type
     * @return The last node for the specified transport type, or the last node of
     *         the way, or null if it is not routable
     */
    public static Node lastNode(Way way, String transportType) {
        Integer oneway = isOneway(way, transportType);
        Node node = Integer.valueOf(-1).equals(oneway) ? way.firstNode() : way.lastNode();
        Map<String, String> accessValues = getDefaultAccessTags(way);
        boolean accessible = Access.getPositiveAccessValues()
                .contains(accessValues.getOrDefault(transportType, Access.AccessTags.NO.getKey()));
        return (transportType == null || accessible) ? node : null;
    }

    /**
     * Get the default access tags for a primitive
     *
     * @param primitive The primitive to get access tags for
     * @return The map of access tags to access
     */
    public static TagMap getDefaultAccessTags(OsmPrimitive primitive) {
        TagMap access = new TagMap();
        final TagMap tags;
        if (primitive.hasKey(HIGHWAY)) {
            tags = getDefaultHighwayAccessTags(primitive.getKeys());
        } else if (primitive.hasKey(WATERWAY)) {
            tags = getDefaultWaterwayAccessTags(primitive.getKeys());
        } else {
            tags = new TagMap();
        }
        tags.putAll(Access.expandAccessValues(tags));

        for (String direction : Arrays.asList("", "forward:", "backward:")) {
            Access.getTransportModes().stream().map(direction::concat).filter(tags::containsKey)
                    .forEach(mode -> access.put(mode, tags.get(direction.concat(mode))));
        }
        return access;
    }

    private static TagMap getDefaultWaterwayAccessTags(TagMap tags) {
        if ("river".equals(tags.get(WATERWAY))) {
            tags.putIfAbsent("boat", Access.AccessTags.YES.getKey());
        }
        return tags;
    }

    private static TagMap getDefaultHighwayAccessTags(TagMap tags) {
        String highway = tags.get(HIGHWAY);

        if (tags.containsKey("sidewalk") && !tags.get("sidewalk").equals(Access.AccessTags.NO.getKey())) {
            tags.putIfAbsent(Access.AccessTags.FOOT.getKey(), Access.AccessTags.YES.getKey());
        }

        if (tags.keySet().stream()
                .anyMatch(str -> str.contains("cycleway") && !Access.AccessTags.NO.getKey().equals(tags.get(str)))) {
            tags.putIfAbsent(Access.AccessTags.BICYCLE.getKey(), Access.AccessTags.YES.getKey());
        }

        if ("residential".equals(highway)) {
            tags.putIfAbsent(Access.AccessTags.VEHICLE.getKey(), Access.AccessTags.YES.getKey());
            tags.putIfAbsent(Access.AccessTags.FOOT.getKey(), Access.AccessTags.YES.getKey());
            tags.putIfAbsent(Access.AccessTags.BICYCLE.getKey(), Access.AccessTags.YES.getKey());
        } else if (Arrays.asList("service", "unclassified", "tertiary", "tertiary_link").contains(highway)) {
            tags.putIfAbsent(Access.AccessTags.VEHICLE.getKey(), Access.AccessTags.YES.getKey());
        } else if (Arrays.asList("secondary", "secondary_link").contains(highway)) {
            tags.putIfAbsent(Access.AccessTags.VEHICLE.getKey(), Access.AccessTags.YES.getKey());
        } else if (Arrays.asList("primary", "primary_link").contains(highway)) {
            tags.putIfAbsent(Access.AccessTags.VEHICLE.getKey(), Access.AccessTags.YES.getKey());
            tags.putIfAbsent(Access.AccessTags.HGV.getKey(), Access.AccessTags.YES.getKey());
        } else if (Arrays.asList("motorway", "trunk", "motorway_link", "trunk_link").contains(highway)) {
            tags.putIfAbsent(Access.AccessTags.VEHICLE.getKey(), Access.AccessTags.YES.getKey());
            tags.putIfAbsent(Access.AccessTags.BICYCLE.getKey(), Access.AccessTags.NO.getKey());
            tags.putIfAbsent(Access.AccessTags.FOOT.getKey(), Access.AccessTags.NO.getKey());
        } else if ("steps".equals(highway)) {
            tags.putIfAbsent(Access.AccessTags.ACCESS_KEY.getKey(), Access.AccessTags.NO.getKey());
            tags.putIfAbsent(Access.AccessTags.FOOT.getKey(), Access.AccessTags.YES.getKey());
        } else if ("path".equals(highway)) {
            tags.putIfAbsent(Access.AccessTags.MOTOR_VEHICLE.getKey(), Access.AccessTags.NO.getKey());
            tags.putIfAbsent(Access.AccessTags.EMERGENCY.getKey(), Access.AccessTags.DESTINATION.getKey());
        } else if ("footway".equals(highway)) {
            tags.putIfAbsent(Access.AccessTags.FOOT.getKey(), Access.AccessTags.DESIGNATED.getKey());
        } else if ("bus_guideway".equals(highway)) {
            tags.putIfAbsent(Access.AccessTags.ACCESS_KEY.getKey(), Access.AccessTags.NO.getKey());
            tags.putIfAbsent(Access.AccessTags.BUS.getKey(), Access.AccessTags.DESIGNATED.getKey());
        } else if ("road".equals(highway)) { // Don't expect these to be routable
            tags.putIfAbsent(Access.AccessTags.ACCESS_KEY.getKey(), Access.AccessTags.NO.getKey());
        } else {
            tags.putIfAbsent(Access.AccessTags.ACCESS_KEY.getKey(), Access.AccessTags.YES.getKey());
        }
        return tags;
    }

    /**
     * Get the error level for a test
     *
     * @param test The integer value of the test error
     * @return The severity for the test
     */
    public static Severity getErrorLevel(int test) {
        return SEVERITY_MAP.get(test);
    }

    /**
     * Set the error level for a test
     *
     * @param test     The integer value of the test error
     * @param severity The new severity for the test
     */
    public static void setErrorLevel(int test, Severity severity) {
        SEVERITY_MAP.put(test, severity);
    }
}
