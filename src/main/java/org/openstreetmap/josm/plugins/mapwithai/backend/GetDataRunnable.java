// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressMonitor.CancelListener;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.plugins.mapwithai.MapWithAIPlugin;
import org.openstreetmap.josm.plugins.mapwithai.commands.MergeDuplicateWays;
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
    private static HttpClient client;
    private final transient DataSet dataSet;
    private final transient ProgressMonitor monitor;

    private static final Object LOCK = new Object();

    private static final int MAX_NUMBER_OF_BBOXES_TO_PROCESS = 1;
    private static final String SERVER_ID_KEY = "server_id";

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
        this.bbox = new ArrayList<>(bbox);
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
                            && n1.getKeys().equals(node.getKeys())
                            && n1.getCoor().greatCircleDistance(node.getCoor()) < MapWithAIPreferenceHelper
                            .getMaxNodeDistance())
                    .collect(Collectors.toList());
            final Command mergeCommand = MergeNodesAction.mergeNodes(nearbyNodes, n1);
            if (mergeCommand != null) {
                mergeCommand.executeCommand();
                nodes.removeAll(nearbyNodes);
            }
        }
    }

    /**
     * Actually get the data
     *
     * @param bbox    The bbox to get the data from
     * @param monitor Use to determine if the operation has been cancelled
     * @return A dataset with the data from the bbox
     */
    private static DataSet getDataReal(BBox bbox, ProgressMonitor monitor) {
        InputStream inputStream = null;
        final DataSet dataSet = new DataSet();
        String urlString = MapWithAIPreferenceHelper.getMapWithAIUrl();
        if (DetectTaskingManagerUtils.hasTaskingManagerLayer()) {
            urlString += "&crop_bbox={crop_bbox}";
        }

        dataSet.setUploadPolicy(UploadPolicy.DISCOURAGED);

        try {
            final URL url = new URL(urlString.replace("{bbox}", bbox.toStringCSV(",")).replace("{crop_bbox}",
                    DetectTaskingManagerUtils.getTaskingManagerBBox().toStringCSV(",")));
            client = HttpClient.create(url);
            final StringBuilder defaultUserAgent = new StringBuilder();
            defaultUserAgent.append(client.getHeaders().get("User-Agent"));
            if (defaultUserAgent.toString().trim().length() == 0) {
                defaultUserAgent.append("JOSM");
            }
            defaultUserAgent.append(tr("/ {0} {1}", MapWithAIPlugin.NAME, MapWithAIPlugin.getVersionInfo()));
            client.setHeader("User-Agent", defaultUserAgent.toString());
            if (!monitor.isCanceled()) {
                Logging.debug("{0}: Getting {1}", MapWithAIPlugin.NAME, client.getURL().toString());
                final Response response = client.connect();
                inputStream = response.getContent();
                final DataSet mergeData = OsmReaderCustom.parseDataSet(inputStream, null, true);
                dataSet.mergeFrom(mergeData);
                response.disconnect();
            }
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
            dataSet.setUploadPolicy(UploadPolicy.BLOCKED);
        }
        return dataSet;
    }

    @Override
    public void operationCanceled() {
        if (client != null) {
            client.disconnect();
        }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }

}
