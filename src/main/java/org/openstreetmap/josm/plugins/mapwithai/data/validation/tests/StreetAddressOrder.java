// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.IWaySegment;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.tests.SharpAngles;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;

public class StreetAddressOrder extends Test {
    private static final SharpAngles ANGLES_TEST = new SharpAngles();

    public StreetAddressOrder() {
        super(tr("Address order ({0})", MapWithAIPlugin.NAME), tr("Check that street address order makes sense"));
    }

    @Override
    public void visit(Way way) {
        if (way.isUsable() && way.hasTag("highway", StreetAddressTest.CLASSIFIED_HIGHWAYS) && way.hasTag("name")) {
            String name = way.get("name");
            List<IPrimitive> addresses = getNearbyAddresses(way).stream().filter(Objects::nonNull)
                    .filter(w -> w.hasTag("addr:housenumber")).filter(w -> name.equals(w.get("addr:street")))
                    .sorted(Comparator.comparing(p -> convertAddrHouseNumberToDouble(p.get("addr:housenumber"))))
                    .collect(Collectors.toList());
            List<IPrimitive> leftAddresses = getAddressesInDirection(true, addresses, way);
            List<IPrimitive> rightAddresses = getAddressesInDirection(false, addresses, way);
            Map<IPrimitive, List<IPrimitive>> potentialBadAddresses = new HashMap<>(checkOrdering(leftAddresses));
            potentialBadAddresses.putAll(checkOrdering(rightAddresses));
            potentialBadAddresses.forEach(this::createError);
        }
    }

    /**
     * Get nearby addresses to a way
     *
     * @param way The way to get nearby addresses from
     * @return The primitives that have appropriate addr tags near to the way
     */
    public static List<IPrimitive> getNearbyAddresses(Way way) {
        BBox bbox = StreetAddressTest.expandBBox(new BBox(way.getBBox()), StreetAddressTest.BBOX_EXPANSION);
        List<Node> addrNodes = way.getDataSet().searchNodes(bbox).stream()
                .filter(StreetAddressTest::hasStreetAddressTags).collect(Collectors.toList());
        List<Way> addrWays = way.getDataSet().searchWays(bbox).stream().filter(StreetAddressTest::hasStreetAddressTags)
                .collect(Collectors.toList());
        List<Relation> addrRelations = way.getDataSet().searchRelations(bbox).stream()
                .filter(StreetAddressTest::hasStreetAddressTags).collect(Collectors.toList());
        return Stream.of(addrNodes, addrWays, addrRelations).flatMap(List::stream)
                .filter(prim -> isNearestRoad(way, prim)).collect(Collectors.toList());
    }

    /**
     * Check if a way is the nearest road to a primitive
     *
     * @param way  The way to check
     * @param prim The primitive to get the distance from
     * @return {@code true} if the primitive is the nearest way
     */
    public static boolean isNearestRoad(Way way, OsmPrimitive prim) {
        BBox primBBox = StreetAddressTest.expandBBox(new BBox(prim.getBBox()), StreetAddressTest.BBOX_EXPANSION);
        List<Pair<Way, Double>> sorted = way.getDataSet().searchWays(primBBox).stream()
                .filter(StreetAddressTest::isHighway).map(iway -> StreetAddressTest.distanceToWay(iway, prim))
                .sorted(Comparator.comparing(p -> p.b)).collect(Collectors.toList());

        if (!sorted.isEmpty()) {
            double minDistance = sorted.get(0).b;
            List<Way> nearby = sorted.stream().filter(p -> p.b - minDistance < StreetAddressTest.BBOX_EXPANSION * 0.05)
                    .map(p -> p.a).collect(Collectors.toList());
            return nearby.contains(way);
        }
        return false;
    }

    /**
     * Convert a housenumber (addr:housenumber) to a double
     *
     * @param housenumber The housenumber to convert
     * @return The double representation, or {@link Double#NaN} if not convertible.
     */
    public static double convertAddrHouseNumberToDouble(String housenumber) {
        String[] parts = housenumber.split(" ", -1);
        double number = 0;
        for (String part : parts) {
            try {
                if (part.contains("/")) {
                    String[] fractional = part.split("/", -1);
                    double tmp = Double.parseDouble(fractional[0]);
                    for (int i = 1; i < fractional.length; i++) {
                        tmp = tmp / Double.parseDouble(fractional[i]);
                    }
                    number += tmp;
                } else {
                    number += Double.parseDouble(part);
                }
            } catch (NumberFormatException e) {
                Logging.debug("{0} found a malformed number {1}", MapWithAIPlugin.NAME, part);
                Logging.debug(e);
                number = Double.NaN;
            }
        }
        return number;
    }

    public void createError(IPrimitive potentialBadAddress, Collection<IPrimitive> surroundingAddresses) {
        if (potentialBadAddress instanceof OsmPrimitive) {
            errors.add(TestError.builder(this, Severity.OTHER, 58542100).primitives((OsmPrimitive) potentialBadAddress)
                    .highlight(surroundingAddresses.stream().filter(OsmPrimitive.class::isInstance)
                            .map(OsmPrimitive.class::cast).collect(Collectors.toSet()))
                    .message(tr("{0} (experimental)", MapWithAIPlugin.NAME), marktr("Potential bad address")).build());
        }
    }

    /**
     * Check the ordering of primitives by creating nodes at their centroids and
     * checking to see if a sharp angle is created.
     *
     * @param <T>        The type of the primitive
     * @param primitives The primitives to check the order of
     * @return Primitives that are out of order
     * @see SharpAngles
     */
    public static <T extends IPrimitive> Map<T, List<T>> checkOrdering(List<T> primitives) {
        if (primitives.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Node> centroids = primitives.stream().map(StreetAddressOrder::getCentroid).filter(Objects::nonNull)
                .collect(Collectors.toList());

        Way way = new Way();
        way.setNodes(centroids);
        double maxDistance = 100;
        Node previousCentroid = centroids.get(0);
        for (Node centroid : centroids) {
            if (previousCentroid.equals(centroid)) {
                continue;
            }
            double tDistance = Geometry.getDistance(centroid, previousCentroid);
            previousCentroid = centroid;
            if (tDistance > maxDistance) {
                maxDistance = tDistance;
            }
        }
        way.put("highway", "residential"); // Required for the SharpAngles test
        ANGLES_TEST.startTest(NullProgressMonitor.INSTANCE);
        ANGLES_TEST.setMaxLength(maxDistance);
        ANGLES_TEST.visit(way);
        ANGLES_TEST.endTest();
        List<Node> issueCentroids = ANGLES_TEST.getErrors().stream().flatMap(e -> e.getHighlighted().stream())
                .filter(Node.class::isInstance).map(Node.class::cast).collect(Collectors.toList());
        ANGLES_TEST.clear();
        way.setNodes(Collections.emptyList());
        List<T> badPrimitives = issueCentroids.stream().map(centroids::indexOf).map(primitives::get)
                .collect(Collectors.toList());
        return badPrimitives.stream().map(p -> new Pair<>(p, getNeighbors(p, primitives)))
                .collect(Collectors.toMap(p -> p.a, p -> p.b));
    }

    /**
     * Get the neighbors of a primitive from a list
     *
     * @param <T>              The type of the primitive
     * @param p                The primitive to get neighbors for
     * @param orderedNeighbors The ordered list of primitives of which p is part of
     * @return The primitive before/after p
     */
    public static <T extends IPrimitive> List<T> getNeighbors(T p, List<T> orderedNeighbors) {
        int index = orderedNeighbors.indexOf(p);
        List<T> neighbors = new ArrayList<>();
        if (index > 0) {
            neighbors.add(orderedNeighbors.get(index - 1));
        }
        if (index < orderedNeighbors.size() - 1) {
            neighbors.add(orderedNeighbors.get(index + 1));
        }
        return neighbors;
    }

    /**
     * Get addresses on different sides of the road
     *
     * @param <T>       The type of the primitive
     * @param left      If {@code true}, get addresses on the "left" side of the
     *                  road
     * @param addresses Addresses to filter for the side on the road
     * @param way       The road way
     * @return Addresses on the appropriate side of the road
     */
    public static <T extends IPrimitive> List<T> getAddressesInDirection(boolean left, Collection<T> addresses,
            IWay<?> way) {
        List<T> addressesToReturn = new ArrayList<>();
        for (T address : addresses) {
            if (address instanceof OsmPrimitive && way instanceof Way) {
                Node centroid = getCentroid(address);
                IWaySegment<?, ?> seg = Geometry.getClosestWaySegment((Way) way, (OsmPrimitive) address);
                if (seg.getFirstNode().getEastNorth() != null && seg.getSecondNode().getEastNorth() != null
                        && centroid != null && centroid.getEastNorth() != null) {
                    boolean right = Geometry.angleIsClockwise(seg.getFirstNode(), seg.getSecondNode(), centroid);
                    if (left != right) {
                        addressesToReturn.add(address);
                    }
                }
            }
        }
        return addressesToReturn;
    }

    /**
     * Get the centroid of a primitive
     *
     * @param primitive The primitive to get a centroid for
     * @return The node that represents the centroid, or {@code null} if no centroid
     *         can be determined
     */
    public static Node getCentroid(IPrimitive primitive) {
        if (primitive instanceof Node) {
            return (Node) primitive;
        } else if (primitive instanceof Way) {
            return new Node(Geometry.getCentroid(((Way) primitive).getNodes()));
        } else if (primitive instanceof Relation && "multipolygon".equals(primitive.get("type"))) {
            // This is not perfect by any stretch of the imagination
            List<Node> nodes = new ArrayList<>();
            for (RelationMember member : ((Relation) primitive).getMembers()) {
                if (member.hasRole("outer")) {
                    nodes.add(getCentroid(member.getMember()));
                }
            }
            if (!nodes.isEmpty()) {
                return new Node(Geometry.getCentroid(nodes));
            }
        }
        return null;
    }

}
