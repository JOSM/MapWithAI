// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmWriter;
import org.openstreetmap.josm.io.OsmWriterFactory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.tools.Logging;

/**
 * Conflate data with a third party server
 *
 * @author Taylor Smock
 */
public class DataConflationSender implements RunnableFuture<DataSet> {

    private static final int MAX_POLLS = 100;
    private DataSet external;
    private DataSet osm;
    private MapWithAICategory category;
    private DataSet conflatedData;
    private CloseableHttpClient client;
    private boolean done;
    private boolean cancelled;

    /**
     * Conflate external data
     *
     * @param category      The category to use to determine the conflation server
     * @param openstreetmap The OSM data (may be null -- try to avoid this)
     * @param external      The data to conflate (may not be null)
     */
    public DataConflationSender(MapWithAICategory category, DataSet openstreetmap, DataSet external) {
        Objects.requireNonNull(external, tr("We must have data to conflate"));
        Objects.requireNonNull(category, tr("We must have a category for the data"));
        this.osm = openstreetmap;
        this.external = external;
        this.category = category;
    }

    @Override
    public void run() {
        String url = MapWithAIConflationCategory.conflationUrlFor(category);
        this.client = HttpClients.createDefault();
        try (CloseableHttpClient client = this.client) {
            StringWriter output = new StringWriter();
            OsmWriter writer = OsmWriterFactory.createOsmWriter(new PrintWriter(output), true, "0.6");
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            if (osm != null) {
                writer.write(osm);
                multipartEntityBuilder.addTextBody("openstreetmap", output.toString(), ContentType.APPLICATION_XML);
            }
            // We need to reset the writers to avoid writing previous streams
            output = new StringWriter();
            writer = OsmWriterFactory.createOsmWriter(new PrintWriter(output), true, "0.6");
            writer.write(external);
            multipartEntityBuilder.addTextBody("external", output.toString(), ContentType.APPLICATION_XML);
            HttpEntity postData = multipartEntityBuilder.build();
            HttpUriRequest request = RequestBuilder.post(url).setEntity(postData).build();

            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                conflatedData = OsmReader.parseDataSet(response.getEntity().getContent(), NullProgressMonitor.INSTANCE,
                        OsmReader.Options.SAVE_ORIGINAL_ID);
            } else {
                conflatedData = null;
            }
            ProtocolVersion protocolVersion = response.getStatusLine().getProtocolVersion();
            Logging.info(new StringBuilder(request.getMethod()).append(' ').append(url).append(" -> ")
                    .append(protocolVersion.getProtocol()).append('/').append(protocolVersion.getMajor()).append('.')
                    .append(protocolVersion.getMinor()).append(' ').append(response.getStatusLine().getStatusCode())
                    .toString());
            this.done = true;
        } catch (IOException | UnsupportedOperationException | IllegalDataException e) {
            Logging.error(e);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            client.close();
        } catch (IOException e) {
            Logging.error(e);
            return false;
        }
        this.done = true;
        this.cancelled = true;
        return true;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    @Override
    public DataSet get() throws InterruptedException, ExecutionException {
        while (!isDone()) {
            Thread.sleep(100);
        }
        return this.conflatedData;
    }

    @Override
    public DataSet get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long realtime = unit.toMillis(timeout);
        long waitTime = realtime > MAX_POLLS ? realtime / MAX_POLLS : 1;
        long timeWaited = 0;
        while (!isDone()) {
            Thread.sleep(waitTime);
            timeWaited += waitTime;
        }
        if (!isDone() && timeWaited > realtime) {
            throw new TimeoutException();
        }
        return this.conflatedData;
    }
}
