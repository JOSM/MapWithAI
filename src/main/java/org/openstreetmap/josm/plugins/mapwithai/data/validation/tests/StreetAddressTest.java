// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
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
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Pair;

public class StreetAddressTest extends Test {
    /** Standard bbox expansion */
    public static final double BBOX_EXPANSION = 0.001;
    private static final String ADDR_STREET = "addr:street";
    private final Set<OsmPrimitive> namePrimitiveMap = new HashSet<>();
    /**
     * Classified highways in order of importance
     *
     * Copied from {@link org.openstreetmap.josm.data.validation.tests.Highways}
     */
    public static final List<String> CLASSIFIED_HIGHWAYS = Collections.unmodifiableList(Arrays.asList("motorway",
            "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link", "unclassified", "residential", "living_street", "service", "road"));

    public StreetAddressTest() {
        super(tr("Mismatched street/street addresses ({0})", MapWithAIPlugin.NAME),
                tr("Check for addr:street/street name mismatches"));
    }

    @Override
    public void visit(Relation relation) {
        realVisit(relation);
    }

    @Override
    public void visit(Way way) {
        realVisit(way);
    }

    @Override
    public void visit(Node node) {
        realVisit(node);
    }

    @Override
    public void endTest() {
        Map<String, List<OsmPrimitive>> values = namePrimitiveMap.parallelStream()
                .collect(Collectors.groupingBy(p -> p.get(ADDR_STREET)));
        values.forEach(this::createError);
        namePrimitiveMap.clear();
    }

    public void createError(String addrStreet, List<OsmPrimitive> primitives) {
        errors.add(TestError.builder(this, Severity.WARNING, 2136232)
                .message(tr("{0} (experimental)", MapWithAIPlugin.NAME),
                        marktr("Addresses are not nearby a matching road ({0})"), addrStreet)
                .primitives(primitives).build());
    }

    public void realVisit(OsmPrimitive primitive) {
        if (primitive.isUsable() && hasStreetAddressTags(primitive)) {
            Collection<Way> surroundingWays = getSurroundingHighways(primitive);
            Collection<String> names = getWayNames(surroundingWays);
            if (!names.contains(primitive.get(ADDR_STREET))) {
                namePrimitiveMap.add(primitive);
            }
        }
    }

    public static Collection<String> getWayNames(Collection<Way> ways) {
        return ways.parallelStream().flatMap(w -> w.getInterestingTags().entrySet().parallelStream())
                .filter(e -> e.getKey().contains("name") && !e.getKey().contains("tiger")).map(Map.Entry::getValue)
                .collect(Collectors.toSet());
    }

    public static Collection<Way> getSurroundingHighways(OsmPrimitive address) {
        Objects.requireNonNull(address.getDataSet(), "Node must be part of a dataset");
        DataSet ds = address.getDataSet();
        BBox addrBox = expandBBox(address.getBBox(), BBOX_EXPANSION);
        int expansions = 0;
        int maxExpansions = Config.getPref().getInt("mapwithai.validator.streetaddresstest.maxexpansions", 20);
        while (ds.searchWays(addrBox).parallelStream().filter(StreetAddressTest::isHighway).count() == 0
                && expansions < maxExpansions) {
            expandBBox(addrBox, BBOX_EXPANSION);
            expansions++;
        }
        return ds.searchWays(addrBox).parallelStream().filter(StreetAddressTest::isHighway).collect(Collectors.toSet());
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
