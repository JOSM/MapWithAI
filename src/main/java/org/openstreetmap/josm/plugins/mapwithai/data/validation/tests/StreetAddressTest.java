// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.validation.tests;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.util.ValUtil;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
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
    private final Map<Point2D, Set<String>> nameMap = new HashMap<>();
    private final Map<Point2D, List<OsmPrimitive>> primitiveCellMap = new HashMap<>();
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
        if (node.isLatLonKnown()) {
            realVisit(node);
        }
    }

    @Override
    public void endTest() {
        for (Map.Entry<Point2D, List<OsmPrimitive>> entry : this.primitiveCellMap.entrySet()) {
            Collection<String> names = getSurroundingHighwayNames(entry.getKey());
            for (OsmPrimitive primitive : entry.getValue()) {
                if (!primitive.isOutsideDownloadArea()) {
                    if ((this.partialSelection || this.isBeforeUpload) && !names.contains(primitive.get(ADDR_STREET))
                            && primitive.getDataSet() != null) {
                        BBox bbox = new BBox(primitive.getBBox());
                        bbox.addPrimitive(primitive, 0.01);
                        for (Way way : primitive.getDataSet().searchWays(bbox)) {
                            if (isHighway(way)) {
                                this.visit(way);
                            }
                        }
                        names = getSurroundingHighwayNames(entry.getKey());
                    }
                    if (!names.contains(primitive.get(ADDR_STREET))) {
                        namePrimitiveMap.add(primitive);
                    }
                    if (this.isCanceled()) {
                        break;
                    }
                }
            }
            if (this.isCanceled()) {
                break;
            }
        }

        Map<String, List<OsmPrimitive>> values = namePrimitiveMap.stream()
                .collect(Collectors.groupingBy(p -> p.get(ADDR_STREET)));
        values.forEach(this::createError);
        namePrimitiveMap.clear();
        this.nameMap.clear();
        this.primitiveCellMap.clear();
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
        if (primitive.isUsable()) {
            final double gridDetail = OsmValidator.getGridDetail() / 100;
            if (isHighway(primitive)) {
                Collection<String> names = getWayNames((Way) primitive);
                if (names.isEmpty()) {
                    return;
                }
                List<Node> nodes = ((Way) primitive).getNodes();
                for (int i = 0; i < nodes.size() - 1; i++) {
                    // Populate the name map
                    INode n1 = nodes.get(i);
                    INode n2 = nodes.get(i + 1);
                    for (Point2D cell : ValUtil.getSegmentCells(n1, n2, gridDetail)) {
                        this.nameMap.computeIfAbsent(cell, k -> new HashSet<>()).addAll(names);
                    }
                }
            } else if (hasStreetAddressTags(primitive) && !primitive.isOutsideDownloadArea()) {
                final EastNorth en;
                if (primitive instanceof Node) {
                    en = ((Node) primitive).getEastNorth();
                } else {
                    en = primitive.getBBox().getCenter().getEastNorth(ProjectionRegistry.getProjection());
                }
                long x = (long) Math.floor(en.getX() * gridDetail);
                long y = (long) Math.floor(en.getY() * gridDetail);
                Point2D point = new Point2D.Double(x, y);
                primitiveCellMap.computeIfAbsent(point, p -> new ArrayList<>()).add(primitive);
            }
        }
    }

    private static Collection<String> getWayNames(IPrimitive way) {
        return way.getInterestingTags().entrySet().stream()
                .filter(e -> (e.getKey().contains("name") || e.getKey().contains("ref"))
                        && !e.getKey().contains("tiger"))
                .map(Map.Entry::getValue).flatMap(s -> Stream.of(s.split(";", -1))).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private Collection<String> getSurroundingHighwayNames(Point2D point2D) {
        if (this.nameMap.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> surroundingWays = new HashSet<>();
        int surrounding = 2;
        while (surroundingWays.isEmpty()) {
            for (int x = -surrounding; x <= surrounding; x++) {
                for (int y = -surrounding; y <= surrounding; y++) {
                    Point2D key = new Point2D.Double((long) Math.floor(point2D.getX() + x),
                            (long) Math.floor(point2D.getY() + y));
                    if (this.nameMap.containsKey(key)) {
                        surroundingWays.addAll(this.nameMap.get(key));
                    }
                }
            }
            if (surroundingWays.isEmpty()) {
                surrounding++;
            }
        }
        return surroundingWays;
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
        final ILatLon[] nodes;
        if (prim instanceof Node) {
            nodes = new ILatLon[] { (Node) prim };
        } else if (prim instanceof Way) {
            nodes = ((Way) prim).getNodes().toArray(new ILatLon[0]);
        } else if (prim instanceof Relation) {
            nodes = ((Relation) prim).getMemberPrimitives().stream()
                    .filter(p -> p instanceof ILatLon || p instanceof Way)
                    .flatMap(p -> p instanceof ILatLon ? Stream.of((ILatLon) p) : ((Way) p).getNodes().stream())
                    .toArray(ILatLon[]::new);
        } else {
            throw new IllegalArgumentException("Unknown primitive type: " + prim.getClass());
        }
        double dist = Double.NaN;
        List<? extends INode> wayNodes = way.getNodes();
        for (int i = 0; i < wayNodes.size() - 1; i++) {
            final ILatLon a = wayNodes.get(i);
            final ILatLon b = wayNodes.get(i + 1);
            for (ILatLon node : nodes) {
                double tDist = getSegmentNodeDistSq(a, b, node);
                if (Double.isNaN(dist) || (!Double.isNaN(tDist) && tDist < dist)) {
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
