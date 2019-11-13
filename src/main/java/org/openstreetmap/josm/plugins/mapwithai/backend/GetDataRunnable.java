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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import org.openstreetmap.josm.actions.MergeNodesAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor.CancelListener;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
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
        if (!monitor.isCanceled()) {
            synchronized (LOCK) {
                /* Microsoft buildings don't have a source, so we add one */
                MapWithAIDataUtils.addSourceTags(dataSet, "building", "microsoft");
                replaceTags(dataSet);
                removeCommonTags(dataSet);
                mergeNodes(dataSet);
                cleanupDataSet(dataSet);
                mergeWays(dataSet);
                dataSet.getWays().parallelStream().filter(way -> !way.isDeleted())
                .forEach(GetDataRunnable::cleanupArtifacts);
                for (int i = 0; i < 5; i++) {
                    new MergeDuplicateWays(dataSet).executeCommand();
                }
            }
        }
        monitor.finishTask();
        return dataSet;
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
            final List<Node> nearbyNodes = dataSet.searchNodes(bbox).parallelStream()
                    .filter(node -> !node.isDeleted() && !node.equals(n1)
                            && ((n1.getKeys().equals(node.getKeys()) || n1.getKeys().isEmpty()
                                    || node.getKeys().isEmpty())
                                    && n1.getCoor().greatCircleDistance(node.getCoor()) < MapWithAIPreferenceHelper
                                    .getMaxNodeDistance()
                                    || !n1.getKeys().isEmpty() && n1.getKeys().equals(node.getKeys())
                                    && n1.getCoor().greatCircleDistance(
                                            node.getCoor()) < MapWithAIPreferenceHelper.getMaxNodeDistance() * 10))
                    .collect(Collectors.toList());
            final Command mergeCommand = MergeNodesAction.mergeNodes(nearbyNodes, n1);
            if (mergeCommand != null) {
                mergeCommand.executeCommand();
                nodes.removeAll(nearbyNodes);
            }
        }
    }

    private static void mergeWays(DataSet dataSet) {
        final List<Way> ways = dataSet.getWays().parallelStream().filter(way -> !way.isDeleted()).collect(Collectors.toList());
        for (int i = 0; i < ways.size(); i++) {
            final Way way1 = ways.get(i);
            final BBox bbox = new BBox();
            bbox.addPrimitive(way1, 0.001);
            final List<Way> nearbyWays = dataSet.searchWays(bbox).parallelStream().filter(way -> way.getNodes().parallelStream().filter(node -> way1.getNodes().contains(node)).count() > 1).collect(Collectors.toList());
            way1.getNodePairs(false);
            nearbyWays.parallelStream().flatMap(way2 -> checkWayDuplications(way1, way2).entrySet().parallelStream())
            .forEach(GetDataRunnable::addMissingElement);
        }
    }

    protected static void addMissingElement(Map.Entry<WaySegment, List<WaySegment>> entry) {
        Way way = entry.getKey().way;
        Way waySegmentWay = entry.getKey().toWay();
        Node toAdd = entry.getValue().parallelStream()
                .flatMap(seg -> Arrays.asList(seg.getFirstNode(), seg.getSecondNode()).parallelStream())
                .filter(node -> !waySegmentWay.containsNode(node)).findAny().orElse(null);
        if (toAdd != null
                && Geometry.getDistance(waySegmentWay, toAdd) < MapWithAIPreferenceHelper.getMaxNodeDistance() * 10) {
            way.addNode(entry.getKey().lowerIndex + 1, toAdd);
        }
        for (int i = 0; i < way.getNodesCount() - 2; i++) {
            Node node0 = way.getNode(i);
            Node node3 = way.getNode(i + 2);
            if (node0.equals(node3)) {
                List<Node> nodes = way.getNodes();
                nodes.remove(i + 2); // SonarLint doesn't like this (if it was i instead of i + 2, it would be an
                // issue)
                way.setNodes(nodes);
            }
        }
    }

    protected static void cleanupArtifacts(Way way) {
        for (int i = 0; i < way.getNodesCount() - 2; i++) {
            Node node0 = way.getNode(i);
            Node node1 = way.getNode(i + 1);
            Node node2 = way.getNode(i + 2);
            double angle = Geometry.getCornerAngle(node0.getEastNorth(), node1.getEastNorth(), node2.getEastNorth());
            if (angle < ARTIFACT_ANGLE) {
                List<Node> nodes = way.getNodes();
                nodes.remove(i + 1); // not an issue since I'm adding it back
                nodes.add(i + 2, node1);
            }
        }
        if (way.getNodesCount() == 2 && way.getDataSet() != null) {
            BBox tBBox = new BBox();
            tBBox.addPrimitive(way, 0.001);
            if (way.getDataSet().searchWays(tBBox).parallelStream()
                    .filter(tWay -> !way.equals(tWay) && !tWay.isDeleted()).anyMatch(
                            tWay -> Geometry.getDistance(way, tWay) < MapWithAIPreferenceHelper.getMaxNodeDistance())) {
                way.setDeleted(true);
            }
        }
    }
    protected static Map<WaySegment, List<WaySegment>> checkWayDuplications(Way way1, Way way2) {
        List<WaySegment> waySegments1 = way1.getNodePairs(false).stream()
                .map(pair -> WaySegment.forNodePair(way1, pair.a, pair.b)).collect(Collectors.toList());
        List<WaySegment> waySegments2 = way2.getNodePairs(false).stream()
                .map(pair -> WaySegment.forNodePair(way2, pair.a, pair.b)).collect(Collectors.toList());
        Map<WaySegment, List<WaySegment>> partials = new TreeMap<>();
        for (WaySegment segment1 : waySegments1) {
            boolean same = false;
            boolean first = false;
            boolean second = false;
            List<WaySegment> replacements = new ArrayList<>();
            for (WaySegment segment2 : waySegments2) {
                same = segment1.isSimilar(segment2);
                if (same) {
                    break;
                }
                if (Math.max(Geometry.getDistance(way1, segment2.getFirstNode()), Geometry.getDistance(way1,
                        segment2.getSecondNode())) < MapWithAIPreferenceHelper.getMaxNodeDistance() * 10) {
                    if (!first && (segment1.getFirstNode().equals(segment2.getFirstNode())
                            || segment1.getFirstNode().equals(segment2.getSecondNode()))) {
                        replacements.add(segment2);
                        first = true;
                    } else if (!second && (segment1.getSecondNode().equals(segment2.getFirstNode())
                            || segment1.getSecondNode().equals(segment2.getSecondNode()))) {
                        replacements.add(segment2);
                        second = true;
                    }
                }
            }
            if (same) {
                continue;
            }
            if (replacements.size() == 2) {
                partials.put(segment1, replacements);
            }
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
        List<Map<String, String>> urlMaps = MapWithAIPreferenceHelper.getMapWithAIUrl().stream()
                .map(map -> new TreeMap<>(map)).collect(Collectors.toList());
        if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
            urlMaps.forEach(map -> map.put("url", map.get("url").concat("&crop_bbox={crop_bbox}")));
        }

        dataSet.setUploadPolicy(UploadPolicy.DISCOURAGED);

        clients = new ArrayList<>();
        urlMaps.forEach(map -> {
            try {
                clients.add(HttpClient.create(new URL(map.get("url").replace("{bbox}", bbox.toStringCSV(","))
                        .replace("{crop_bbox}", DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(",")))));
            } catch (MalformedURLException e1) {
                Logging.debug(e1);
            }
        });
        clients.forEach(client -> clientCall(client, dataSet, monitor));
        dataSet.setUploadPolicy(UploadPolicy.BLOCKED);
        return dataSet;
    }

    private static void clientCall(HttpClient client, DataSet dataSet, ProgressMonitor monitor) {
        final StringBuilder defaultUserAgent = new StringBuilder();
        client.setReadTimeout(DEFAULT_TIMEOUT);
        defaultUserAgent.append(client.getHeaders().get("User-Agent"));
        if (defaultUserAgent.toString().trim().length() == 0) {
            defaultUserAgent.append("JOSM");
        }
        defaultUserAgent.append(tr("/ {0} {1}", MapWithAIPlugin.NAME, MapWithAIPlugin.getVersionInfo()));
        client.setHeader("User-Agent", defaultUserAgent.toString());
        if (!monitor.isCanceled()) {
            InputStream inputStream = null;
            try {
                Logging.debug("{0}: Getting {1}", MapWithAIPlugin.NAME, client.getURL().toString());
                final Response response = client.connect();
                inputStream = response.getContent();
                final DataSet mergeData = OsmReaderCustom.parseDataSet(inputStream, null, true);
                dataSet.mergeFrom(mergeData);
                response.disconnect();
            } catch (SocketException e) {
                if (!monitor.isCanceled()) {
                    Logging.debug(e);
                }
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
