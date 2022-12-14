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
import java.util.stream.Stream;

import org.openstreetmap.josm.data.coor.ILatLon;
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
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Pair;

/**
 * Check for addr:street and street name mismatches
 */
public class StreetAddressTest extends Test {
    /** Standard bbox expansion */
    public static final double BBOX_EXPANSION = 0.002;
    private static final String ADDR_STREET = "addr:street";
    private final Set<OsmPrimitive> namePrimitiveMap = new HashSet<>();
    /**
     * Classified highways. This uses a {@link Set} instead of a {@link List} since
     * the MapWithAI code doesn't care about order.
     *
     * Copied from {@link org.openstreetmap.josm.data.validation.tests.Highways}
     */
    public static final Set<String> CLASSIFIED_HIGHWAYS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList("motorway", "motorway_link", "trunk", "trunk_link", "primary",
                    "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
                    "residential", "living_street", "service", "road")));

    /**
     * Create a new test object
     */
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
        Map<String, List<OsmPrimitive>> values = namePrimitiveMap.stream()
                .collect(Collectors.groupingBy(p -> p.get(ADDR_STREET)));
        values.forEach(this::createError);
        namePrimitiveMap.clear();
    }

    /**
     * Create the error
     *
     * @param addrStreet The addr:street tag
     * @param primitives The bad primitives
     */
    public void createError(String addrStreet, List<OsmPrimitive> primitives) {
        errors.add(TestError.builder(this, Severity.WARNING, 2_136_232)
                .message(tr("{0} (experimental)", MapWithAIPlugin.NAME),
                        marktr("Addresses are not nearby a matching road ({0})"), addrStreet)
                .primitives(primitives).build());
    }

    private void realVisit(OsmPrimitive primitive) {
        if (primitive.isUsable() && hasStreetAddressTags(primitive) && !primitive.isOutsideDownloadArea()) {
            Collection<Way> surroundingWays = getSurroundingHighways(primitive);
            Collection<String> names = getWayNames(surroundingWays);
            if (!names.contains(primitive.get(ADDR_STREET))) {
                namePrimitiveMap.add(primitive);
            }
        }
    }

    private static Collection<String> getWayNames(Collection<Way> ways) {
        return ways.stream().flatMap(w -> w.getInterestingTags().entrySet().stream())
                .filter(e -> (e.getKey().contains("name") || e.getKey().contains("ref"))
                        && !e.getKey().contains("tiger"))
                .map(Map.Entry::getValue).flatMap(s -> Stream.of(s.split(";", -1))).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private static Collection<Way> getSurroundingHighways(OsmPrimitive address) {
        Objects.requireNonNull(address.getDataSet(), "Node must be part of a dataset");
        DataSet ds = address.getDataSet();
        BBox addrBox = expandBBox(new BBox(address.getBBox()), BBOX_EXPANSION);
        int expansions = 0;
        int maxExpansions = Config.getPref().getInt("mapwithai.validator.streetaddresstest.maxexpansions", 20);
        while (ds.searchWays(addrBox).stream().noneMatch(StreetAddressTest::isHighway) && expansions < maxExpansions) {
            expandBBox(addrBox, BBOX_EXPANSION);
            expansions++;
        }
        return ds.searchWays(addrBox).stream().filter(StreetAddressTest::isHighway).collect(Collectors.toSet());
    }

    /**
     * Get the distance to a way
     *
     * @param way  The way to get a distance to
     * @param prim The primitive to get a distance from (LatLon space)
     * @return A Pair&lt;Way, Double&gt; of the distance from the primitive to the
     *         way.
     */
    static Pair<Way, Double> distanceToWay(Way way, OsmPrimitive prim) {
        final Node[] nodes;
        if (prim instanceof Node) {
            nodes = new Node[] { (Node) prim };
        } else if (prim instanceof Way) {
            nodes = ((Way) prim).getNodes().toArray(new Node[0]);
        } else if (prim instanceof Relation) {
            nodes = ((Relation) prim).getMemberPrimitives().stream().filter(p -> p instanceof Node || p instanceof Way)
                    .flatMap(p -> p instanceof Node ? Stream.of((Node) p) : ((Way) p).getNodes().stream())
                    .toArray(Node[]::new);
        } else {
            throw new IllegalArgumentException("Unknown primitive type: " + prim.getClass());
        }
        double dist = Double.NaN;
        List<Node> wayNodes = way.getNodes();
        for (int i = 0; i < wayNodes.size() - 1; i++) {
            final Node a = wayNodes.get(i);
            final Node b = wayNodes.get(i + 1);
            for (Node node : nodes) {
                double tDist = getSegmentNodeDistSq(a, b, node);
                if (Double.isNaN(tDist) || (!Double.isNaN(tDist) && tDist < dist)) {
                    dist = tDist;
                }
            }
        }
        return new Pair<>(way, dist);
    }

    // ****** START COPY FROM GEOMETRY (EastNorth -> ILatLon, perf is more important
    // than accuracy, some modifications for zero memalloc) *******
    /**
     * Calculate closest distance between a line segment s1-s2 and a point p
     *
     * @param p1    start of segment
     * @param p2    end of segment
     * @param point the point
     * @return the square of the euclidean distance from p to the closest point on
     *         the segment
     */
    private static double getSegmentNodeDistSq(ILatLon p1, ILatLon p2, ILatLon point) {
        CheckParameterUtil.ensureParameterNotNull(p1, "p1");
        CheckParameterUtil.ensureParameterNotNull(p2, "p2");
        CheckParameterUtil.ensureParameterNotNull(point, "point");

        double ldx = p2.lon() - p1.lon();
        double ldy = p2.lat() - p1.lat();

        // segment zero length
        if (ldx == 0 && ldy == 0)
            return p1.distanceSq(point);

        double pdx = point.lon() - p1.lon();
        double pdy = point.lat() - p1.lat();

        double offset = (pdx * ldx + pdy * ldy) / (ldx * ldx + ldy * ldy);

        if (offset <= 0)
            return p1.distanceSq(point);
        else if (offset >= 1)
            return p2.distanceSq(point);
        // Math copied from ILatLon#interpolate to avoid memory allocation
        return point.distanceSq((1 - offset) * p1.lon() + offset * p2.lon(),
                (1 - offset) * p1.lat() + offset * p2.lat());
    }
    // ****** END COPY FROM GEOMETRY ********

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
