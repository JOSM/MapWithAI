// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.openstreetmap.josm.actions.MergeNodesAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.TagMap;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor.CancelListener;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation.DataUrl;
import org.openstreetmap.josm.plugins.mapwithai.commands.MergeDuplicateWays;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.HttpClient.Response;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;

/**
 * Get data in a parallel manner
 *
 * @author Taylor Smock
 */
public class GetDataRunnable extends RecursiveTask<DataSet> implements CancelListener {
    private static final long serialVersionUID = 258423685658089715L;
    private final List<BBox> bbox;
    private static List<HttpClient> clients;
    private final transient DataSet dataSet;
    private final transient ProgressMonitor monitor;

    private static final Object LOCK = new Object();

    private static final int MAX_NUMBER_OF_BBOXES_TO_PROCESS = 1;
    private static final String SERVER_ID_KEY = "server_id";
    private static final int DEFAULT_TIMEOUT = 50_000; // 50 seconds

    private static final double ARTIFACT_ANGLE = 0.1745; // 10 degrees in radians

    public static final String MAPWITHAI_SOURCE_TAG_KEY = "mapwithai:source";

    /**
     * @param bbox    The initial bbox to get data from (don't reduce beforehand --
     *                it will be reduced here)
     * @param dataSet The dataset to add the data to
     * @param monitor A monitor to keep track of progress
     */
    public GetDataRunnable(BBox bbox, DataSet dataSet, ProgressMonitor monitor) {
        this(Arrays.asList(bbox), dataSet, monitor);
    }

    /**
     * @param bbox    The initial bboxes to get data from (don't reduce beforehand
     *                -- it will be reduced here)
     * @param dataSet The dataset to add the data to
     * @param monitor A monitor to keep track of progress
     */
    public GetDataRunnable(List<BBox> bbox, DataSet dataSet, ProgressMonitor monitor) {
        super();
        this.bbox = bbox.stream().distinct().collect(Collectors.toList());
        this.dataSet = dataSet;
        this.monitor = Optional.ofNullable(monitor).orElse(NullProgressMonitor.INSTANCE);
        this.monitor.addCancelListener(this);
    }

    @Override
    public DataSet compute() {
        final List<BBox> bboxes = MapWithAIDataUtils.reduceBBoxSize(bbox);
        monitor.beginTask(tr("Downloading {0} data ({1} total downloads)", MapWithAIPlugin.NAME, bboxes.size()),
                bboxes.size() - 1);
        if (!monitor.isCanceled()) {
            if (bboxes.size() == MAX_NUMBER_OF_BBOXES_TO_PROCESS) {
                final DataSet temporaryDataSet = getDataReal(bboxes.get(0), monitor);
                synchronized (GetDataRunnable.class) {
                    dataSet.mergeFrom(temporaryDataSet);
                }
            } else {
                final Collection<GetDataRunnable> tasks = bboxes.parallelStream()
                        .map(tBbox -> new GetDataRunnable(tBbox, dataSet, monitor.createSubTaskMonitor(0, true)))
                        .collect(Collectors.toList());
                tasks.forEach(GetDataRunnable::fork);
                tasks.parallelStream().forEach(runnable -> {
                    runnable.join();
                    monitor.worked(1);
                });
            }
        }
        // This can technically be included in the above block, but it is here so that
        // cancellation is a little faster
        if (!monitor.isCanceled() && !bboxes.isEmpty()) {
            cleanup(dataSet, new Bounds(bboxes.get(0).getBottomRight(), bboxes.get(0).getTopLeft()));
        }
        monitor.finishTask();
        return dataSet;
    }

    /**
     * Perform cleanups on a dataset (one dataset at a time)
     *
     * @param dataSet The dataset to cleanup
     * @param bounds
     */
    public static void cleanup(DataSet dataSet, Bounds bounds) {
        synchronized (LOCK) {
            removeRedundantSource(dataSet);
            replaceTags(dataSet);
            removeCommonTags(dataSet);
            mergeNodes(dataSet);
            cleanupDataSet(dataSet);
            mergeWays(dataSet);
            removeAlreadyAddedData(dataSet);
            new MergeDuplicateWays(dataSet).executeCommand();
            (bounds == null ? dataSet.getWays() : dataSet.searchWays(bounds.toBBox())).parallelStream()
                    .filter(way -> !way.isDeleted()).forEach(GetDataRunnable::cleanupArtifacts);
        }
    }

    /**
     * Remove redudant sources from objects (if source on way and source on node,
     * and node doesn't have any other tags, then node doesn't need the source)
     *
     * @param dataSet The dataset with potential duplicate source tags
     */
    public static void removeRedundantSource(DataSet dataSet) {
        dataSet.getNodes().parallelStream().filter(node -> !node.getReferrers().isEmpty())
                .filter(node -> node.getKeys().entrySet().parallelStream().map(Entry::getKey)
                        .allMatch(key -> key.contains("source"))
                        && node.getKeys().entrySet().parallelStream()
                                .allMatch(entry -> node.getReferrers().parallelStream()
                                        .anyMatch(parent -> parent.hasTag(entry.getKey(), entry.getValue()))))
                .forEach(node -> node.getKeys().entrySet().parallelStream().map(Entry::getKey)
                        .filter(key -> key.contains("source")).forEach(node::remove));
    }

    /**
     * Remove ways that have already been added to an OSM layer
     *
     * @param dataSet The dataset with potential duplicate ways (it is modified)
     */
    public static void removeAlreadyAddedData(DataSet dataSet) {
        final List<DataSet> osmData = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class)
                .parallelStream().map(OsmDataLayer::getDataSet).filter(ds -> !ds.equals(dataSet))
                .collect(Collectors.toList());
        dataSet.getWays().parallelStream().filter(way -> !way.isDeleted())
                .filter(way -> osmData.stream().anyMatch(ds -> checkIfPrimitiveDuplicatesPrimitiveInDataSet(way, ds)))
                .forEach(way -> {
                    final List<Node> nodes = way.getNodes();
                    DeleteCommand.delete(Collections.singleton(way), true, true).executeCommand();
                    nodes.parallelStream()
                            .filter(node -> !node.isDeleted()
                                    && node.getReferrers().parallelStream().allMatch(OsmPrimitive::isDeleted))
                            .forEach(node -> node.setDeleted(true));
                });
    }

    private static boolean checkIfPrimitiveDuplicatesPrimitiveInDataSet(OsmPrimitive primitive, DataSet ds) {
        final List<OsmPrimitive> possibleDuplicates = searchDataSet(ds, primitive);
        return possibleDuplicates.parallelStream().filter(prim -> !prim.isDeleted())
                .anyMatch(prim -> checkIfProbableDuplicate(prim, primitive));
    }

    private static boolean checkIfProbableDuplicate(OsmPrimitive one, OsmPrimitive two) {
        boolean equivalent = false;
        final TagMap oneMap = one.getKeys();
        final TagMap twoMap = two.getKeys();
        oneMap.remove(MAPWITHAI_SOURCE_TAG_KEY);
        twoMap.remove(MAPWITHAI_SOURCE_TAG_KEY);
        if (one.getClass().equals(two.getClass()) && oneMap.equals(twoMap)) {
            if (one instanceof Node) {
                if (one.hasSameInterestingTags(two) && ((Node) one).getCoor().equalsEpsilon(((Node) two).getCoor())) {
                    equivalent = true;
                }
            } else if (one instanceof Way) {
                equivalent = ((Way) one).getNodes().parallelStream().allMatch(node1 -> ((Way) two).getNodes()
                        .parallelStream().anyMatch(node2 -> node1.getCoor().equalsEpsilon(node2.getCoor())));
            } else if (one instanceof Relation) {
                equivalent = ((Relation) one).getMembers().parallelStream()
                        .allMatch(member1 -> ((Relation) two).getMembers().parallelStream()
                                .anyMatch(member2 -> member1.getRole().equals(member2.getRole())
                                        && checkIfProbableDuplicate(member1.getMember(), member2.getMember())));
            }
        }
        return equivalent;
    }

    private static <T extends AbstractPrimitive> List<OsmPrimitive> searchDataSet(DataSet ds, T primitive) {
        List<OsmPrimitive> returnList = Collections.emptyList();
        if (primitive instanceof OsmPrimitive) {
            final BBox tBBox = new BBox();
            tBBox.addPrimitive((OsmPrimitive) primitive, 0.001);
            if (primitive instanceof Node) {
                returnList = new ArrayList<>(ds.searchNodes(tBBox));
            } else if (primitive instanceof Way) {
                returnList = new ArrayList<>(ds.searchWays(tBBox));
            } else if (primitive instanceof Relation) {
                returnList = new ArrayList<>(ds.searchRelations(tBBox));
            }
        }
        return returnList;
    }

    /**
     * Replace tags in a dataset with a set of replacement tags
     *
     * @param dataSet The dataset with primitives to change
     */
    public static void replaceTags(DataSet dataSet) {
        final Map<Tag, Tag> replaceTags = MapWithAIPreferenceHelper.getReplacementTags().entrySet().parallelStream()
                .map(entry -> new Pair<>(Tag.ofString(entry.getKey()), Tag.ofString(entry.getValue())))
                .collect(Collectors.toMap(pair -> pair.a, pair -> pair.b));
        replaceTags.forEach((orig, replace) -> dataSet.allNonDeletedPrimitives().parallelStream()
                .filter(prim -> prim.hasTag(orig.getKey(), orig.getValue())).forEach(prim -> prim.put(replace)));
    }

    private static void cleanupDataSet(DataSet dataSet) {
        Map<OsmPrimitive, String> origIds = dataSet.allPrimitives().parallelStream()
                .filter(prim -> prim.hasKey(MergeDuplicateWays.ORIG_ID)).distinct()
                .collect(Collectors.toMap(prim -> prim, prim -> prim.get(MergeDuplicateWays.ORIG_ID)));
        final Map<OsmPrimitive, String> serverIds = dataSet.allPrimitives().parallelStream()
                .filter(prim -> prim.hasKey(SERVER_ID_KEY)).distinct()
                .collect(Collectors.toMap(prim -> prim, prim -> prim.get(SERVER_ID_KEY)));

        final List<OsmPrimitive> toDelete = origIds.entrySet().parallelStream()
                .filter(entry -> serverIds.containsValue(entry.getValue())).map(Entry::getKey)
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            new DeleteCommand(toDelete).executeCommand();
        }
        origIds = origIds.entrySet().parallelStream().filter(entry -> !toDelete.contains(entry.getKey()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        serverIds.forEach((prim, str) -> prim.remove(SERVER_ID_KEY));
        origIds.forEach((prim, str) -> prim.remove(MergeDuplicateWays.ORIG_ID));
    }

    /**
     * Remove common tags from the dataset
     *
     * @param dataSet The dataset to remove tags from
     */
    public static void removeCommonTags(DataSet dataSet) {
        dataSet.allPrimitives().parallelStream().filter(prim -> prim.hasKey(MergeDuplicateWays.ORIG_ID))
                .forEach(prim -> prim.remove(MergeDuplicateWays.ORIG_ID));
        dataSet.getNodes().parallelStream().forEach(node -> node.remove(SERVER_ID_KEY));
        final List<Node> emptyNodes = dataSet.getNodes().parallelStream().distinct().filter(node -> !node.isDeleted())
                .filter(node -> node.getReferrers().isEmpty() && !node.hasKeys()).collect(Collectors.toList());
        if (!emptyNodes.isEmpty()) {
            new DeleteCommand(emptyNodes).executeCommand();
        }
    }

    private static void mergeNodes(DataSet dataSet) {
        final List<Node> nodes = dataSet.getNodes().parallelStream().filter(node -> !node.isDeleted())
                .collect(Collectors.toList());
        for (int i = 0; i < nodes.size(); i++) {
            final Node n1 = nodes.get(i);
            final BBox bbox = new BBox();
            bbox.addPrimitive(n1, 0.001);
            final List<Node> nearbyNodes = dataSet.searchNodes(bbox).parallelStream().filter(node -> !node.isDeleted()
                    && !node.equals(n1) && node.getReferrers().parallelStream().allMatch(prim -> prim.hasKey("highway"))
                    && (((n1.getKeys().equals(node.getKeys()) || n1.getKeys().isEmpty() || node.getKeys().isEmpty())
                            && (n1.getCoor().greatCircleDistance(node.getCoor()) < MapWithAIPreferenceHelper
                                    .getMaxNodeDistance()))
                            || (!n1.getKeys().isEmpty() && n1.getKeys().equals(node.getKeys())
                                    && (n1.getCoor().greatCircleDistance(
                                            node.getCoor()) < (MapWithAIPreferenceHelper.getMaxNodeDistance() * 10)))))
                    .collect(Collectors.toList());
            final Command mergeCommand = MergeNodesAction.mergeNodes(nearbyNodes, n1);
            if (mergeCommand != null) {
                mergeCommand.executeCommand();
                nodes.removeAll(nearbyNodes);
            }
        }
    }

    private static void mergeWays(DataSet dataSet) {
        final List<Way> ways = dataSet.getWays().parallelStream().filter(way -> !way.isDeleted())
                .collect(Collectors.toList());
        for (int i = 0; i < ways.size(); i++) {
            final Way way1 = ways.get(i);
            final BBox bbox = new BBox();
            bbox.addPrimitive(way1, 0.001);
            final List<Way> nearbyWays = dataSet.searchWays(bbox).parallelStream().filter(
                    way -> way.getNodes().parallelStream().filter(node -> way1.getNodes().contains(node)).count() > 1)
                    .collect(Collectors.toList());
            way1.getNodePairs(false);
            nearbyWays.parallelStream().flatMap(way2 -> checkWayDuplications(way1, way2).entrySet().parallelStream())
                    .forEach(GetDataRunnable::addMissingElement);
        }
    }

    protected static void addMissingElement(Map.Entry<WaySegment, List<WaySegment>> entry) {
        final Way way = entry.getKey().way;
        final Way waySegmentWay = entry.getKey().toWay();
        final Node toAdd = entry.getValue().parallelStream()
                .flatMap(seg -> Arrays.asList(seg.getFirstNode(), seg.getSecondNode()).parallelStream())
                .filter(node -> !waySegmentWay.containsNode(node)).findAny().orElse(null);
        if ((toAdd != null) && (convertToMeters(
                Geometry.getDistance(waySegmentWay, toAdd)) < (MapWithAIPreferenceHelper.getMaxNodeDistance() * 10))) {
            way.addNode(entry.getKey().lowerIndex + 1, toAdd);
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
            tBBox.addPrimitive(way, 0.001);
            if (way.getDataSet().searchWays(tBBox).parallelStream()
                    .filter(tWay -> !way.equals(tWay) && !tWay.isDeleted())
                    .anyMatch(tWay -> way.getNodes().parallelStream().filter(
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
    protected static Map<WaySegment, List<WaySegment>> checkWayDuplications(Way way1, Way way2) {
        final List<WaySegment> waySegments1 = way1.getNodePairs(false).stream()
                .map(pair -> WaySegment.forNodePair(way1, pair.a, pair.b)).collect(Collectors.toList());
        final List<WaySegment> waySegments2 = way2.getNodePairs(false).stream()
                .map(pair -> WaySegment.forNodePair(way2, pair.a, pair.b)).collect(Collectors.toList());
        final Map<WaySegment, List<WaySegment>> partials = new TreeMap<>();
        for (final WaySegment segment1 : waySegments1) {
            final Way waySegment1 = segment1.toWay();
            final List<WaySegment> replacements = waySegments2.parallelStream()
                    .filter(seg2 -> waySegment1.isFirstLastNode(seg2.getFirstNode())
                            || waySegment1.isFirstLastNode(seg2.getSecondNode()))
                    .filter(seg -> {
                        final Node node2 = waySegment1.isFirstLastNode(seg.getFirstNode()) ? seg.getFirstNode()
                                : seg.getSecondNode();
                        final Node node1 = node2.equals(seg.getFirstNode()) ? seg.getSecondNode() : seg.getFirstNode();
                        final Node node3 = waySegment1.getNode(0).equals(node2) ? waySegment1.getNode(1)
                                : waySegment1.getNode(0);
                        return Math.abs(Geometry.getCornerAngle(node1.getEastNorth(), node2.getEastNorth(),
                                node3.getEastNorth())) < (Math.PI / 4);
                    }).collect(Collectors.toList());
            if ((replacements.size() != 2) || replacements.parallelStream()
                    .anyMatch(seg -> waySegment1.getNodes().containsAll(seg.toWay().getNodes()))) {
                continue;
            }
            partials.put(segment1, replacements);
        }
        return partials;
    }

    /**
     * Actually get the data
     *
     * @param bbox    The bbox to get the data from
     * @param monitor Use to determine if the operation has been cancelled
     * @return A dataset with the data from the bbox
     */
    private static DataSet getDataReal(BBox bbox, ProgressMonitor monitor) {
        final DataSet dataSet = new DataSet();
        final List<Map<String, String>> urlMaps = MapWithAIPreferenceHelper.getMapWithAIUrl().stream().map(TreeMap::new)
                .collect(Collectors.toList());
        if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
            urlMaps.forEach(map -> map.put("url", map.get("url").concat("&crop_bbox={crop_bbox}")));
        }

        urlMaps.parallelStream().filter(map -> map.containsKey("parameters")).forEach(DataUrl::addUrlParameters);

        dataSet.setUploadPolicy(UploadPolicy.DISCOURAGED);

        clients = new ArrayList<>();
        urlMaps.parallelStream().forEach(map -> {
            try {
                final HttpClient client = HttpClient
                        .create(new URL(map.get("url").replace("{bbox}", bbox.toStringCSV(",")).replace("{crop_bbox}",
                                DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(","))));
                clients.add(client);
                clientCall(client, dataSet, map.getOrDefault("source", MapWithAIPlugin.NAME), monitor);
            } catch (final MalformedURLException e1) {
                Logging.debug(e1);
            }
        });
        dataSet.setUploadPolicy(UploadPolicy.BLOCKED);
        return dataSet;
    }

    /**
     * Add information to the user agent and then perform the actual internet call
     *
     * @param client  The HttpClient
     * @param dataSet The dataset to add data to
     * @param source  The source of the data (added as a tag to "whole" objects)
     * @param monitor The monitor (so we know when a cancellation has occurred)
     */
    private static void clientCall(HttpClient client, DataSet dataSet, String source, ProgressMonitor monitor) {
        final StringBuilder defaultUserAgent = new StringBuilder();
        client.setReadTimeout(DEFAULT_TIMEOUT);
        defaultUserAgent.append(client.getHeaders().get("User-Agent"));
        if (defaultUserAgent.toString().trim().length() == 0) {
            defaultUserAgent.append("JOSM");
        }
        defaultUserAgent.append(tr("/ {0} {1}", MapWithAIPlugin.NAME, MapWithAIPlugin.getVersionInfo()));
        client.setHeader("User-Agent", defaultUserAgent.toString());
        if (!monitor.isCanceled()) {
            clientCallInternet(client, dataSet, source, monitor);
        }
    }

    /**
     * Add perform an internet request to add data to a dataset
     *
     * @param client  The HttpClient
     * @param dataSet The dataset to add data to
     * @param source  The source of the data (added as a tag to "whole" objects)
     * @param monitor The monitor (so we know when a cancellation has occurred)
     */
    private static void clientCallInternet(HttpClient client, DataSet dataSet, String source, ProgressMonitor monitor) {
        InputStream inputStream = null;
        try {
            Logging.debug("{0}: Getting {1}", MapWithAIPlugin.NAME, client.getURL().toString());
            final Response response = client.connect();
            inputStream = response.getContent();
            final DataSet mergeData = OsmReaderCustom.parseDataSet(inputStream, null, true);
            addMapWithAISourceTag(mergeData, source);
            dataSet.mergeFrom(mergeData);
            response.disconnect();
        } catch (final SocketException e) {
            if (!monitor.isCanceled()) {
                Logging.debug(e);
            }
        } catch (final SSLException e) {
            Logging.debug(e);
            new Notification(tr("{0}: Bad SSL Certificate: {1}", MapWithAIPlugin.NAME, client.getURL()))
                    .setDuration(Notification.TIME_DEFAULT).show();
        } catch (UnsupportedOperationException | IllegalDataException | IOException e) {
            Logging.debug(e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException e) {
                    Logging.debug(e);
                }
            }
        }

    }

    /**
     * @param dataSet The dataset to add the mapwithai source tag to
     * @param source  The source to associate with the data
     * @return The dataset for easy chaining
     */
    public static DataSet addMapWithAISourceTag(DataSet dataSet, String source) {
        dataSet.getNodes().parallelStream().filter(node -> !node.isDeleted() && node.getReferrers().isEmpty())
                .forEach(node -> node.put(MAPWITHAI_SOURCE_TAG_KEY, source));
        dataSet.getWays().parallelStream().filter(way -> !way.isDeleted())
                .forEach(way -> way.put(MAPWITHAI_SOURCE_TAG_KEY, source));
        dataSet.getRelations().parallelStream().filter(rel -> !rel.isDeleted())
                .forEach(rel -> rel.put(MAPWITHAI_SOURCE_TAG_KEY, source));
        return dataSet;
    }

    @Override
    public void operationCanceled() {
        if (clients != null) {
            clients.parallelStream().filter(Objects::nonNull).forEach(HttpClient::disconnect);
        }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }

}
