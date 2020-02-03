// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Pair;

public class StreetAddressTest extends Test {
    private static final double BBOX_EXPANSION = 0.001;
    private static final String ADDR_STREET = "addr:street";
    /**
     * Classified highways in order of importance
     *
     * Copied from {@link org.openstreetmap.josm.data.validation.tests.Highways}
     */
    public static final List<String> CLASSIFIED_HIGHWAYS = Collections.unmodifiableList(
            Arrays.asList("motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary",
                    "secondary_link", "tertiary", "tertiary_link", "unclassified", "residential", "living_street"));

    public StreetAddressTest() {
        super(tr("Mismatched street/street addresses ({0})", MapWithAIPlugin.NAME),
                tr("Check for addr:street/street name mismatches"));
    }

    @Override
    public void visit(Way way) {
        if (way.isUsable() && isHighway(way)) {
            List<IPrimitive> addresses = getNearbyAddresses(way);
            Map<String, Integer> addressOccurance = getAddressOccurance(addresses);
            createError(way, addressOccurance, addresses);
        }
    }

    public void createError(Way way, Map<String, Integer> occurances, List<IPrimitive> addresses) {
        String name = way.get("name");
        Collection<String> likelyNames = getLikelyNames(occurances);
        TestError.Builder error = null;
        if (name == null) {
            error = TestError.builder(this, Severity.WARNING, 65446500);
            error.message(tr("{0} (experimental)", MapWithAIPlugin.NAME),
                    marktr("Street with no name with {0} tags nearby, name possibly {1}"), ADDR_STREET, likelyNames)
                    .highlight(getAddressPOI(likelyNames, addresses));
        } else if (!likelyNames.contains(name)) {
            error = TestError.builder(this, Severity.WARNING, 65446501);
            error.message(tr("{0} (experimental)", MapWithAIPlugin.NAME),
                    marktr("Street name does not match most likely name, name possibly {0}"), likelyNames)
                    .highlight(getAddressPOI(likelyNames, addresses));
        }
        if (error != null && !likelyNames.isEmpty()) {
            error.primitives(way);
            errors.add(error.build());
        }
    }

    /**
     * Get a list of likely names from a map of occurrences
     *
     * @param occurances The map of Name to occurrences
     * @return The string(s) with the most occurrences
     */
    public static List<String> getLikelyNames(Map<String, Integer> occurances) {
        List<String> likelyNames = new ArrayList<>();
        Integer max = 0;
        for (Entry<String, Integer> entry : occurances.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                continue;
            }
            if (entry.getValue() > max) {
                max = entry.getValue();
                likelyNames.clear();
                likelyNames.add(entry.getKey());
            } else if (max.equals(entry.getValue())) {
                likelyNames.add(entry.getKey());
            }
        }
        return likelyNames;
    }

    /**
     * Get address points relevant to a set of names
     *
     * @param names     The street names of interest
     * @param addresses Potential address points
     * @return POI's for the street names
     */
    public static List<OsmPrimitive> getAddressPOI(Collection<String> names, Collection<IPrimitive> addresses) {
        return addresses.stream().filter(OsmPrimitive.class::isInstance).map(OsmPrimitive.class::cast)
                .filter(p -> names.contains(p.get(ADDR_STREET))).collect(Collectors.toList());
    }

    /**
     * Count the street address occurances
     *
     * @param addressPOI The list to count
     * @return A map of street names with a count
     */
    public static Map<String, Integer> getAddressOccurance(Collection<IPrimitive> addressPOI) {
        Map<String, Integer> count = new HashMap<>();
        for (IPrimitive prim : addressPOI) {
            if (prim.hasTag(ADDR_STREET)) {
                int current = count.getOrDefault(prim.get(ADDR_STREET), 0);
                count.put(prim.get(ADDR_STREET), ++current);
            }
        }
        return count;
    }

    /**
     * Get nearby addresses to a way
     *
     * @param way The way to get nearby addresses from
     * @return The primitives that have appropriate addr tags near to the way
     */
    public static List<IPrimitive> getNearbyAddresses(Way way) {
        BBox bbox = expandBBox(way.getBBox(), BBOX_EXPANSION);
        List<Node> addrNodes = way.getDataSet().searchNodes(bbox).parallelStream()
                .filter(StreetAddressTest::hasStreetAddressTags).collect(Collectors.toList());
        List<Way> addrWays = way.getDataSet().searchWays(bbox).parallelStream()
                .filter(StreetAddressTest::hasStreetAddressTags).collect(Collectors.toList());
        List<Relation> addrRelations = way.getDataSet().searchRelations(bbox).parallelStream()
                .filter(StreetAddressTest::hasStreetAddressTags).collect(Collectors.toList());
        return Stream.of(addrNodes, addrWays, addrRelations).flatMap(List::parallelStream)
                .filter(prim -> StreetAddressTest.isNearestRoad(way, prim)).collect(Collectors.toList());
    }

    /**
     * Check if a way is the nearest road to a primitive
     *
     * @param way  The way to check
     * @param prim The primitive to get the distance from
     * @return {@code true} if the primitive is the nearest way
     */
    public static boolean isNearestRoad(Way way, OsmPrimitive prim) {
        BBox primBBox = expandBBox(prim.getBBox(), BBOX_EXPANSION);
        List<Pair<Way, Double>> sorted = way.getDataSet().searchWays(primBBox).parallelStream()
                .filter(StreetAddressTest::isHighway).map(iway -> distanceToWay(iway, prim))
                .sorted(Comparator.comparing(p -> p.b)).collect(Collectors.toList());

        if (!sorted.isEmpty()) {
            double minDistance = sorted.get(0).b;
            List<Way> nearby = sorted.stream().filter(p -> p.b - minDistance < BBOX_EXPANSION * 0.05).map(p -> p.a)
                    .collect(Collectors.toList());
            return nearby.contains(way);
        }
        return false;
    }

    /**
     * Get the distance to a way
     *
     * @param way  The way to get a distance to
     * @param prim The primitive to get a distance from
     * @return A Pair&lt;Way, Double&gt; of the distance from the primitive to the
     *         way
     */
    public static Pair<Way, Double> distanceToWay(Way way, OsmPrimitive prim) {
        return new Pair<>(way, Geometry.getDistance(way, prim));
    }

    /**
     * Check if the primitive has an appropriate highway tag
     *
     * @param prim The primitive to check
     * @return {@code true} if it has a highway tag that is classified
     */
    public static boolean isHighway(IPrimitive prim) {
        return prim instanceof IWay && prim.hasTag("highway", CLASSIFIED_HIGHWAYS);
    }

    /**
     * Check if the primitive has appropriate address tags
     *
     * @param prim The primitive to check
     * @return {@code true} if it has addr:street tags (may change)
     */
    public static boolean hasStreetAddressTags(IPrimitive prim) {
        return prim.hasTag(ADDR_STREET);
    }

    /**
     * Expand a bbox by a set amount
     *
     * @param bbox   The bbox to expand
     * @param degree The amount to expand the bbox by
     * @return The bbox, for easy chaining
     */
    public static BBox expandBBox(BBox bbox, double degree) {
        bbox.add(bbox.getBottomRightLon() + degree, bbox.getBottomRightLat() - degree);
        bbox.add(bbox.getTopLeftLon() - degree, bbox.getTopLeftLat() + degree);
        return bbox;
    }
}
