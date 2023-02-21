// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.actions.MergeNodesAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Hash;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.IWaySegment;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Storage;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.TagMap;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MergeDuplicateWays;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAILayerInfo;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.PreConflatedDataUtils;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;

/**
 * Get data in a parallel manner
 *
 * @author Taylor Smock
 */
public class GetDataRunnable extends RecursiveTask<DataSet> {
    /**
     * This is functionally equivalent to
     * {@link org.openstreetmap.josm.data.validation.tests.DuplicateNode.NodeHash}
     */
    private static class ILatLonHash implements Hash<Object, Object> {
        private static final double PRECISION = DEGREE_BUFFER;

        /**
         * Returns the rounded coordinated according to {@link #PRECISION}
         *
         * @param coor The coordinate to round
         * @return The rounded coordinate
         * @see LatLon#roundToOsmPrecision
         */
        private static ILatLon roundCoord(ILatLon coor) {
            return new LatLon(Math.round(coor.lat() / PRECISION) * PRECISION,
                    Math.round(coor.lon() / PRECISION) * PRECISION);
        }

        @SuppressWarnings("unchecked")
        protected static ILatLon getLatLon(Object o) {
            if (o instanceof INode) {
                LatLon coor = ((INode) o).getCoor();
                if (coor == null)
                    return null;
                if (PRECISION == 0)
                    return coor.getRoundedToOsmPrecision();
                return roundCoord(coor);
            } else if (o instanceof List<?>) {
                LatLon coor = ((List<Node>) o).get(0).getCoor();
                if (coor == null)
                    return null;
                if (PRECISION == 0)
                    return coor.getRoundedToOsmPrecision();
                return roundCoord(coor);
            } else
                throw new AssertionError();
        }

        @Override
        public boolean equals(Object k, Object t) {
            ILatLon coorK = getLatLon(k);
            ILatLon coorT = getLatLon(t);
            return coorK == coorT || (coorK != null && coorT != null && coorK.equals(coorT));
        }

        @Override
        public int getHashCode(Object k) {
            ILatLon coorK = getLatLon(k);
            return coorK == null ? 0 : coorK.hashCode();
        }
    }

    /**
     * This checks that all visited objects are highways
     */
    private static final class HighwayVisitor implements PrimitiveVisitor {
        boolean onlyHighways = true;

        @Override
        public void visit(INode n) {
            onlyHighways = false;
        }

        @Override
        public void visit(IWay<?> w) {
            if (!w.isTagged() || !w.hasTag("highway")) {
                onlyHighways = false;
            }
        }

        @Override
        public void visit(IRelation<?> r) {
            onlyHighways = false;
        }
    }

    private static final long serialVersionUID = 258423685658089715L;
    private final transient List<Bounds> runnableBounds;
    private final transient DataSet dataSet;
    private final transient ProgressMonitor monitor;
    private static final float DEGREE_BUFFER = 0.001f;
    private static final int MAX_LATITUDE = 90;
    private static final int MAX_LONGITUDE = 180;

    private Integer maximumDimensions;
    private transient MapWithAIInfo info;

    private static final int MAX_NUMBER_OF_BBOXES_TO_PROCESS = 1;
    private static final String SERVER_ID_KEY = "current_id";

    /** An equals sign (=) used for tag splitting */
    private static final String EQUALS = "=";

    private static final double ARTIFACT_ANGLE = 0.1745; // 10 degrees in radians

    /**
     * The source tag to be used to populate source values. Not seen on objects
     * post-upload.
     */
    public static final String MAPWITHAI_SOURCE_TAG_KEY = "mapwithai:source";
    /**
     * The source tag to be added to all objects from the source. Seen on objects
     * post-upload.
     */
    public static final String SOURCE_TAG_KEY = "source";

    /**
     * Get data in the background
     *
     * @param bbox    The initial bbox to get data from (don't reduce beforehand --
     *                it will be reduced here)
     * @param dataSet The dataset to add the data to
     * @param monitor A monitor to keep track of progress
     */
    public GetDataRunnable(Bounds bbox, DataSet dataSet, ProgressMonitor monitor) {
        this(Collections.singletonList(bbox), dataSet, monitor);
    }

    /**
     * Get data in the background
     *
     * @param bbox    The initial bboxes to get data from (don't reduce beforehand
     *                -- it will be reduced here)
     * @param dataSet The dataset to add the data to
     * @param monitor A monitor to keep track of progress
     */
    public GetDataRunnable(List<Bounds> bbox, DataSet dataSet, ProgressMonitor monitor) {
        super();
        this.runnableBounds = bbox.stream().distinct().collect(Collectors.toList());
        this.dataSet = dataSet;
        this.monitor = Optional.ofNullable(monitor).orElse(NullProgressMonitor.INSTANCE);
    }

    /**
     * Set the maximum download bbox size. Must be called before execution.
     *
     * @param maximumDimensions The maximum bbox download size
     */
    public void setMaximumDimensions(int maximumDimensions) {
        this.maximumDimensions = maximumDimensions;
    }

    @Override
    public DataSet compute() {
        final List<Bounds> bounds = maximumDimensions == null ? MapWithAIDataUtils.reduceBoundSize(runnableBounds)
                : MapWithAIDataUtils.reduceBoundSize(runnableBounds, maximumDimensions);
        monitor.beginTask(tr("Downloading {0} data ({1} total downloads)", MapWithAIPlugin.NAME, bounds.size()),
                bounds.size() - 1);
        if (!monitor.isCanceled()) {
            if (bounds.size() == MAX_NUMBER_OF_BBOXES_TO_PROCESS) {
                final DataSet temporaryDataSet = getDataReal(bounds.get(0), monitor);
                synchronized (this.dataSet) {
                    dataSet.mergeFrom(temporaryDataSet);
                }
            } else {
                final Collection<GetDataRunnable> tasks = bounds.stream()
                        .map(bound -> new GetDataRunnable(bound, dataSet, monitor.createSubTaskMonitor(0, true)))
                        .collect(Collectors.toList());
                tasks.forEach(GetDataRunnable::fork);
                tasks.forEach(runnable -> {
                    runnable.join();
                    monitor.worked(1);
                });
            }
        }
        // This can technically be included in the above block, but it is here so that
        // cancellation is a little faster
        if (!monitor.isCanceled() && !bounds.isEmpty()) {
            cleanup(dataSet, bounds.get(0), info);
        }
        monitor.finishTask();
        return dataSet;
    }

    /**
     * Perform cleanups on a dataset (one dataset at a time)
     *
     * @param dataSet The dataset to cleanup
     * @param bounds  The newly added bounds to the dataset. May be {@code null}.
     * @param info    The information used to download the data
     */
    public static void cleanup(DataSet dataSet, Bounds bounds, MapWithAIInfo info) {
        realCleanup(dataSet, bounds, info);
    }

    private static synchronized void realCleanup(DataSet dataSet, Bounds bounds, MapWithAIInfo info) {
        final Bounds boundsToUse;
        if (bounds == null && !dataSet.getDataSourceBounds().isEmpty()) {
            boundsToUse = new Bounds(dataSet.getDataSourceBounds().get(0));
            dataSet.getDataSourceBounds().forEach(boundsToUse::extend);
        } else if (bounds == null) {
            boundsToUse = new Bounds(0, 0, 0, 0);
        } else {
            boundsToUse = new Bounds(bounds);
        }
        replaceTags(dataSet);
        removeCommonTags(dataSet);
        removeEmptyTags(dataSet, bounds);
        mergeNodes(dataSet);
        cleanupDataSet(dataSet);
        mergeWays(dataSet);
        PreConflatedDataUtils.removeConflatedData(dataSet, info);
        removeAlreadyAddedData(dataSet);
        List<Way> ways = dataSet.searchWays(boundsToUse.toBBox()).stream().filter(w -> w.hasKey("highway"))
                .collect(Collectors.toList());
        if (!ways.isEmpty()) {
            new MergeDuplicateWays(dataSet, ways).executeCommand();
        }
        (boundsToUse.isCollapsed() || boundsToUse.isOutOfTheWorld() ? dataSet.getWays()
                : dataSet.searchWays(boundsToUse.toBBox())).stream().filter(way -> !way.isDeleted())
                        .forEach(GetDataRunnable::cleanupArtifacts);
    }

    /**
     * Remove empty tags from primitives
     *
     * @param dataSet The dataset to remove tags from
     * @param bounds  The bounds to remove the empty tags from (performance)
     */
    public static void removeEmptyTags(DataSet dataSet, Bounds bounds) {
        Bounds boundsToUse;
        if (bounds == null && !dataSet.getDataSourceBounds().isEmpty()) {
            boundsToUse = new Bounds(dataSet.getDataSourceBounds().get(0));
            dataSet.getDataSourceBounds().forEach(boundsToUse::extend);
        } else if (bounds == null) {
            boundsToUse = new Bounds(-MAX_LATITUDE, -MAX_LONGITUDE, MAX_LATITUDE, MAX_LONGITUDE);
        } else {
            boundsToUse = new Bounds(bounds);
        }
        dataSet.searchPrimitives(boundsToUse.toBBox()).forEach(GetDataRunnable::realRemoveEmptyTags);
    }

    /**
     * Remove empty tags from primitives. We assume that the `getKey` implementation
     * returns a new map.
     *
     * @param prim The primitive to remove empty tags from.
     */
    private static void realRemoveEmptyTags(IPrimitive prim) {
        Map<String, String> keys = prim.getKeys();
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                // The OsmPrimitives all return a new map, so this is safe.
                prim.remove(entry.getKey());
            }
        }
    }

    /**
     * Remove ways that have already been added to an OSM layer
     *
     * @param dataSet The dataset with potential duplicate ways (it is modified)
     */
    public static void removeAlreadyAddedData(DataSet dataSet) {
        final List<DataSet> osmData = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                .map(OsmDataLayer::getDataSet).filter(ds -> !ds.equals(dataSet)).collect(Collectors.toList());
        for (Way way : dataSet.getWays()) {
            if (!way.isDeleted() && way.getOsmId() <= 0) {
                for (DataSet ds : osmData) {
                    if (checkIfPrimitiveDuplicatesPrimitiveInDataSet(way, ds)) {
                        final List<Node> nodes = way.getNodes();
                        Optional.ofNullable(DeleteCommand.delete(Collections.singleton(way), true, true))
                                .ifPresent(Command::executeCommand);
                        for (Node node : nodes) {
                            if (!node.isDeleted()
                                    && node.referrers(OsmPrimitive.class).allMatch(OsmPrimitive::isDeleted)) {
                                node.setDeleted(true);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    private static boolean checkIfPrimitiveDuplicatesPrimitiveInDataSet(OsmPrimitive primitive, DataSet ds) {
        final List<OsmPrimitive> possibleDuplicates = searchDataSet(ds, primitive);
        for (OsmPrimitive dupe : possibleDuplicates) {
            if (checkIfProbableDuplicate(dupe, primitive)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIfProbableDuplicate(OsmPrimitive one, OsmPrimitive two) {
        boolean equivalent = false;
        final TagMap oneMap = one.getKeys();
        final TagMap twoMap = two.getKeys();
        oneMap.remove(MAPWITHAI_SOURCE_TAG_KEY);
        twoMap.remove(MAPWITHAI_SOURCE_TAG_KEY);
        if (one.getClass().equals(two.getClass()) && oneMap.equals(twoMap)) {
            if (one instanceof Node) {
                final ILatLon coor1 = ((Node) one);
                final ILatLon coor2 = ((Node) two);
                if (one.hasSameInterestingTags(two) && coor1.isLatLonKnown() && coor2.isLatLonKnown()
                        && coor1.equalsEpsilon(coor2)) {
                    equivalent = true;
                }
            } else if (one instanceof Way) {
                equivalent = ((Way) one).getNodes().stream().filter(Objects::nonNull)
                        .allMatch(node1 -> ((Way) two).getNodes().stream().anyMatch(node1::equalsEpsilon));
            } else if (one instanceof Relation) {
                equivalent = ((Relation) one).getMembers().stream()
                        .allMatch(member1 -> ((Relation) two).getMembers().stream()
                                .anyMatch(member2 -> member1.getRole().equals(member2.getRole())
                                        && checkIfProbableDuplicate(member1.getMember(), member2.getMember())));
            }
        }
        return equivalent;
    }

    private static <T extends AbstractPrimitive> List<OsmPrimitive> searchDataSet(DataSet ds, T primitive) {
        if (primitive instanceof OsmPrimitive) {
            final BBox tBBox = new BBox();
            tBBox.addPrimitive((OsmPrimitive) primitive, DEGREE_BUFFER);
            if (primitive instanceof Node) {
                return Collections.unmodifiableList(ds.searchNodes(tBBox));
            } else if (primitive instanceof Way) {
                return Collections.unmodifiableList(ds.searchWays(tBBox));
            } else if (primitive instanceof Relation) {
                return Collections.unmodifiableList(ds.searchRelations(tBBox));
            }
        }
        return Collections.emptyList();
    }

    /**
     * Replace tags in a dataset with a set of replacement tags
     *
     * @param dataSet The dataset with primitives to change
     */
    public static void replaceTags(DataSet dataSet) {
        final Map<Tag, Tag> replaceTags = MapWithAIPreferenceHelper.getReplacementTags().entrySet().stream()
                .filter(entry -> entry.getKey().contains(EQUALS) && entry.getValue().contains(EQUALS))
                .map(entry -> new Pair<>(Tag.ofString(entry.getKey()), Tag.ofString(entry.getValue())))
                .collect(Collectors.toMap(pair -> pair.a, pair -> pair.b));
        MapWithAIPreferenceHelper.getReplacementTags().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(EQUALS) && Utils.isBlank(entry.getValue()))
                .map(entry -> new Tag(entry.getKey(), null)).forEach(tag -> replaceTags.put(tag, tag));
        replaceTags(dataSet, replaceTags);
    }

    /**
     * Replace tags in a dataset with a set of replacement tags
     *
     * @param dataSet     The dataset with primitives to change
     * @param replaceTags The tags to replace
     */
    public static void replaceTags(DataSet dataSet, Map<Tag, Tag> replaceTags) {
        replaceTags.forEach((orig, replace) -> dataSet.allNonDeletedPrimitives().stream()
                .filter(prim -> prim.hasTag(orig.getKey(), orig.getValue())
                        || (prim.hasKey(orig.getKey()) && Utils.isBlank(orig.getValue())))
                .forEach(prim -> prim.put(replace)));
    }

    /**
     * Replace tags in a dataset with a set of replacement tags
     *
     * @param dataSet     The dataset with primitives to change
     * @param replaceKeys The keys to replace (does not replace values)
     */
    public static void replaceKeys(DataSet dataSet, Map<String, String> replaceKeys) {
        replaceKeys.entrySet().stream().filter(e -> !e.getKey().equals(e.getValue())).forEach(
                e -> dataSet.allNonDeletedPrimitives().stream().filter(p -> p.hasKey(e.getKey())).forEach(p -> {
                    p.put(e.getValue(), p.get(e.getKey()));
                    p.remove(e.getKey());
                }));
    }

    private static void cleanupDataSet(DataSet dataSet) {
        Map<OsmPrimitive, String> origIds = dataSet.allPrimitives().stream()
                .filter(prim -> prim.hasKey(MergeDuplicateWays.ORIG_ID)).distinct()
                .collect(Collectors.toMap(prim -> prim, prim -> prim.get(MergeDuplicateWays.ORIG_ID)));
        final Map<OsmPrimitive, String> serverIds = dataSet.allPrimitives().stream()
                .filter(prim -> prim.hasKey(SERVER_ID_KEY)).distinct()
                .collect(Collectors.toMap(prim -> prim, prim -> prim.get(SERVER_ID_KEY)));

        final List<OsmPrimitive> toDelete = origIds.entrySet().stream()
                .filter(entry -> serverIds.containsValue(entry.getValue())).map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            new DeleteCommand(toDelete).executeCommand();
        }
        origIds = origIds.entrySet().stream().filter(entry -> !toDelete.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        serverIds.forEach((prim, str) -> prim.remove(SERVER_ID_KEY));
        origIds.forEach((prim, str) -> prim.remove(MergeDuplicateWays.ORIG_ID));
    }

    /**
     * Remove common tags from the dataset
     *
     * @param dataSet The dataset to remove tags from
     */
    public static void removeCommonTags(DataSet dataSet) {
        final Set<Node> emptyNodes = new HashSet<>();
        for (OsmPrimitive tagged : dataSet.allPrimitives()) {
            if (!tagged.hasKeys()) {
                continue;
            }
            if (tagged.hasKey(MergeDuplicateWays.ORIG_ID)) {
                tagged.remove(MergeDuplicateWays.ORIG_ID);
            }
            if (tagged instanceof Node) {
                tagged.remove(SERVER_ID_KEY);
                final Node node = (Node) tagged;
                if (!node.isDeleted() && !node.hasKeys() && node.getReferrers().isEmpty()) {
                    emptyNodes.add(node);
                }
            }
        }
        if (!emptyNodes.isEmpty()) {
            new DeleteCommand(emptyNodes).executeCommand();
        }
    }

    /**
     * Create an efficient collection ({@link Storage}) of {@link List} of
     * {@link Node}s and {@link Node} objects
     *
     * @param dataSet The dataset to get nodes from
     * @return The storage to use
     */
    private static Storage<Object> generateEfficientNodeSearchStorage(DataSet dataSet) {
        final Storage<Object> nodes = new Storage<>(new ILatLonHash());
        for (Node node : dataSet.getNodes()) {
            if (!node.isDeleted()) {
                final Object old = nodes.get(node);
                if (old == null) {
                    nodes.put(node);
                } else if (old instanceof Node) {
                    List<Node> list = new ArrayList<>(2);
                    list.add((Node) old);
                    list.add(node);
                    nodes.put(list);
                } else {
                    @SuppressWarnings("unchecked")
                    List<Node> list = (List<Node>) old;
                    list.add(node);
                }
            }
        }
        return nodes;
    }

    /**
     * Merge nodes that have the same tags and (almost) the same location
     *
     * @param dataSet The dataset to merge nodes in
     */
    private static void mergeNodes(DataSet dataSet) {
        final Storage<Object> nodes = generateEfficientNodeSearchStorage(dataSet);
        for (Object obj : nodes) {
            // We only care if there are multiple nodes at the location
            if (obj instanceof List<?>) {
                @SuppressWarnings("unchecked")
                final List<Node> iNodes = (List<Node>) obj;
                for (Node nearNode : iNodes) {
                    final List<Node> nearbyNodes = new ArrayList<>(iNodes);
                    nearbyNodes.removeIf(node -> !nearNode.hasSameInterestingTags(node) || !usableNode(nearNode, node));
                    final Command mergeCommand = MergeNodesAction.mergeNodes(nearbyNodes, nearNode);
                    if (mergeCommand != null) {
                        mergeCommand.executeCommand();
                    }
                }
            }
        }
    }

    private static boolean usableNode(Node nearNode, Node node) {
        return basicNodeChecks(nearNode, node) && onlyHasHighwayParents(node)
                && ((keyCheck(nearNode, node)
                        && distanceCheck(nearNode, node, MapWithAIPreferenceHelper.getMaxNodeDistance()))
                        || (nearNode.hasKeys() && node.hasKeys() && nearNode.getKeys().equals(node.getKeys())
                                && distanceCheck(nearNode, node, MapWithAIPreferenceHelper.getMaxNodeDistance() * 10)));
    }

    private static boolean distanceCheck(Node nearNode, Node node, Double distance) {
        return Geometry.getDistance(nearNode, node) < distance;
    }

    private static boolean keyCheck(INode nearNode, INode node) {
        return !nearNode.hasKeys() || !node.hasKeys() || nearNode.getKeys().equals(node.getKeys());
    }

    private static boolean onlyHasHighwayParents(Node node) {
        HighwayVisitor highwayVisitor = new HighwayVisitor();
        node.visitReferrers(highwayVisitor);
        return highwayVisitor.onlyHighways;
    }

    private static boolean basicNodeChecks(INode nearNode, INode node) {
        return node != null && nearNode != null && !node.isDeleted() && !nearNode.isDeleted() && !nearNode.equals(node)
                && node.isLatLonKnown() && nearNode.isLatLonKnown();
    }

    private static void mergeWays(DataSet dataSet) {
        for (final Way way1 : dataSet.getWays()) {
            if (way1.isDeleted()) {
                continue;
            }
            final BBox bbox = new BBox();
            bbox.addPrimitive(way1, DEGREE_BUFFER);
            for (Way nearbyWay : dataSet.searchWays(bbox)) {
                if (nearbyWay.getNodes().stream().filter(way1::containsNode).count() > 1) {
                    for (Map.Entry<IWaySegment<Node, Way>, List<IWaySegment<Node, Way>>> entry : checkWayDuplications(
                            way1, nearbyWay).entrySet()) {
                        GetDataRunnable.addMissingElement(entry);
                    }
                }
            }
        }
    }

    protected static void addMissingElement(Map.Entry<IWaySegment<Node, Way>, List<IWaySegment<Node, Way>>> entry) {
        final Way way = entry.getKey().getWay();
        final Way waySegmentWay;
        try {
            waySegmentWay = entry.getKey().toWay();
        } catch (ReflectiveOperationException e) {
            throw new JosmRuntimeException(e);
        }
        final Node toAdd = entry.getValue().stream().flatMap(seg -> Stream.of(seg.getFirstNode(), seg.getSecondNode()))
                .filter(node -> !waySegmentWay.containsNode(node)).findAny().orElse(null);
        if ((toAdd != null) && (convertToMeters(
                Geometry.getDistance(waySegmentWay, toAdd)) < (MapWithAIPreferenceHelper.getMaxNodeDistance() * 10))) {
            way.addNode(entry.getKey().getUpperIndex(), toAdd);
        }
        for (int i = 0; i < (way.getNodesCount() - 2); i++) {
            final Node node0 = way.getNode(i);
            final Node node3 = way.getNode(i + 2);
            if (node0.equals(node3)) {
                final List<Node> nodes = way.getNodes();
                nodes.remove(i + 2); // NOSONAR SonarLint doesn't like this (if it was i instead of i + 2, it would
                // be an issue)
                way.setNodes(nodes);
            }
        }
    }

    protected static double convertToMeters(double value) {
        return value * ProjectionRegistry.getProjection().getMetersPerUnit();
    }

    protected static void cleanupArtifacts(Way way) {
        for (int i = 0; i < (way.getNodesCount() - 2); i++) {
            final Node node0 = way.getNode(i);
            final Node node1 = way.getNode(i + 1);
            final Node node2 = way.getNode(i + 2);
            final double angle = Geometry.getCornerAngle(node0.getEastNorth(), node1.getEastNorth(),
                    node2.getEastNorth());
            if (angle < ARTIFACT_ANGLE) {
                final List<Node> nodes = way.getNodes();
                nodes.remove(i + 1); // NOSONAR not an issue since I'm adding it back
                nodes.add(i + 2, node1);
            }
        }
        if ((way.getNodesCount() == 2) && (way.getDataSet() != null)) {
            final BBox tBBox = new BBox();
            tBBox.addPrimitive(way, DEGREE_BUFFER);
            if (way.getDataSet().searchWays(tBBox).stream().filter(tWay -> !way.equals(tWay) && !tWay.isDeleted())
                    .anyMatch(tWay -> way.getNodes().stream().filter(
                            tNode -> Geometry.getDistance(tNode, tWay) < MapWithAIPreferenceHelper.getMaxNodeDistance())
                            .count() == way.getNodesCount())) {
                way.setDeleted(true);
            }
        }
    }

    /**
     * Check for nearly duplicate way sections
     *
     * @param way1 The way to map duplicate segments to
     * @param way2 The way that may have duplicate segments
     * @return A Map&lt;WaySegment to modify from way1, List&lt;WaySegments from
     *         way2&gt; to make the segment conform to &gt;
     */
    protected static Map<IWaySegment<Node, Way>, List<IWaySegment<Node, Way>>> checkWayDuplications(Way way1,
            Way way2) {
        final List<IWaySegment<Node, Way>> waySegments1 = way1.getNodePairs(false).stream()
                .map(pair -> IWaySegment.forNodePair(way1, pair.a, pair.b)).collect(Collectors.toList());
        final List<IWaySegment<Node, Way>> waySegments2 = way2.getNodePairs(false).stream()
                .map(pair -> IWaySegment.forNodePair(way2, pair.a, pair.b)).collect(Collectors.toList());
        final Map<IWaySegment<Node, Way>, List<IWaySegment<Node, Way>>> partials = new TreeMap<>();
        final BiPredicate<IWaySegment<Node, Way>, IWaySegment<Node, Way>> connected = (segment1,
                segment2) -> segment1.getFirstNode().equals(segment2.getFirstNode())
                        || segment1.getSecondNode().equals(segment2.getFirstNode())
                        || segment1.getFirstNode().equals(segment2.getSecondNode())
                        || segment1.getSecondNode().equals(segment2.getSecondNode());
        for (final IWaySegment<Node, Way> segment1 : waySegments1) {
            final List<IWaySegment<Node, Way>> replacements = waySegments2.stream()
                    .filter(seg2 -> connected.test(segment1, seg2)).filter(seg -> {
                        final Node node2 = segment1.getFirstNode().equals(seg.getFirstNode())
                                || segment1.getSecondNode().equals(seg.getFirstNode()) ? seg.getFirstNode()
                                        : seg.getSecondNode();
                        final Node node1 = node2.equals(seg.getFirstNode()) ? seg.getSecondNode() : seg.getFirstNode();
                        final Node node3 = segment1.getFirstNode().equals(node2) ? segment1.getSecondNode()
                                : segment1.getFirstNode();
                        return Math.abs(Geometry.getCornerAngle(node1.getEastNorth(), node2.getEastNorth(),
                                node3.getEastNorth())) < (Math.PI / 4);
                    }).collect(Collectors.toList());
            if ((replacements.size() != 2) || replacements.stream()
                    .anyMatch(seg -> Arrays.asList(segment1.getFirstNode(), segment1.getSecondNode())
                            .containsAll(Arrays.asList(seg.getFirstNode(), seg.getSecondNode())))) {
                continue;
            }
            partials.put(segment1, replacements);
        }
        return partials;
    }

    /**
     * Actually get the data
     *
     * @param bounds  The bounds to get the data from
     * @param monitor Use to determine if the operation has been cancelled
     * @return A dataset with the data from the bounds
     */
    private static DataSet getDataReal(Bounds bounds, ProgressMonitor monitor) {
        final DataSet dataSet = new DataSet();
        dataSet.setUploadPolicy(UploadPolicy.DISCOURAGED);

        final List<ForkJoinTask<DataSet>> tasks = new ArrayList<>();
        final ForkJoinPool pool = MapWithAIDataUtils.getForkJoinPool();
        for (MapWithAIInfo map : new ArrayList<>(MapWithAILayerInfo.getInstance().getLayers())) {
            tasks.add(pool.submit(
                    MapWithAIDataUtils.download(monitor, bounds, map, MapWithAIDataUtils.MAXIMUM_SIDE_DIMENSIONS)));
        }
        for (ForkJoinTask<DataSet> task : tasks) {
            dataSet.mergeFrom(task.join());
        }
        dataSet.setUploadPolicy(UploadPolicy.BLOCKED);
        return dataSet;
    }

    /**
     * Add source tags to primitives
     *
     * @param dataSet The dataset to add the mapwithai source tag to (not visible on
     *                object post-upload)
     * @param source  The source to associate with the data
     * @return The dataset for easy chaining
     */
    public static DataSet addMapWithAISourceTag(DataSet dataSet, String source) {
        return addTag(dataSet, MAPWITHAI_SOURCE_TAG_KEY, source);
    }

    /**
     * Add source tags to primitives
     *
     * @param dataSet The dataset to add the source tag to (visible on object
     *                post-upload)
     * @param source  The source to associate with the data
     * @return The dataset for easy chaining
     */
    public static DataSet addSourceTag(DataSet dataSet, String source) {
        return addTag(dataSet, SOURCE_TAG_KEY, source);
    }

    private static DataSet addTag(DataSet dataSet, String key, String value) {
        dataSet.getNodes().stream().filter(p -> checkIfMapWithAISourceShouldBeAdded(p, key))
                .filter(node -> node.getReferrers().isEmpty()).forEach(node -> node.put(key, value));
        dataSet.getWays().stream().filter(p -> checkIfMapWithAISourceShouldBeAdded(p, key))
                .forEach(way -> way.put(key, value));
        dataSet.getRelations().stream().filter(p -> checkIfMapWithAISourceShouldBeAdded(p, key))
                .forEach(rel -> rel.put(key, value));
        return dataSet;
    }

    private static boolean checkIfMapWithAISourceShouldBeAdded(OsmPrimitive prim, String key) {
        return !prim.isDeleted() && !prim.hasTag(key);
    }

    /**
     * Set the info that is being used to download data
     *
     * @param info The info being used
     */
    public void setMapWithAIInfo(MapWithAIInfo info) {
        this.info = info;
    }

}
