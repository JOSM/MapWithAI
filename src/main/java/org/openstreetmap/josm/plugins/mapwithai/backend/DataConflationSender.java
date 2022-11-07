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

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.StatusLine;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmWriter;
import org.openstreetmap.josm.io.OsmWriterFactory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAICategory;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIConflationCategory;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * Conflate data with a third party server
 *
 * @author Taylor Smock
 */
public class DataConflationSender implements RunnableFuture<DataSet> {

    private static final int MAX_POLLS = 100;
    private final DataSet external;
    private final DataSet osm;
    private final MapWithAICategory category;
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
        if (!Utils.isBlank(url)) {
            this.client = HttpClients.createDefault();
            try (CloseableHttpClient currentClient = this.client) {
                MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                if (osm != null) {
                    StringWriter output = new StringWriter();
                    try (OsmWriter writer = OsmWriterFactory.createOsmWriter(new PrintWriter(output), true, "0.6")) {
                        writer.write(osm);
                        multipartEntityBuilder.addTextBody("openstreetmap", output.toString(),
                                ContentType.APPLICATION_XML);
                    }
                }
                // We need to reset the writers to avoid writing previous streams
                StringWriter output = new StringWriter();
                try (OsmWriter writer = OsmWriterFactory.createOsmWriter(new PrintWriter(output), true, "0.6")) {
                    writer.write(external);
                    multipartEntityBuilder.addTextBody("external", output.toString(), ContentType.APPLICATION_XML);
                }
                HttpEntity postData = multipartEntityBuilder.build();
                HttpUriRequest request = new HttpPost(url);
                request.setEntity(postData);

                CloseableHttpResponse response = currentClient.execute(request);
                StatusLine statusLine = new StatusLine(response);
                if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                    conflatedData = OsmReader.parseDataSet(response.getEntity().getContent(),
                            NullProgressMonitor.INSTANCE, OsmReader.Options.SAVE_ORIGINAL_ID);
                } else {
                    conflatedData = null;
                }
                ProtocolVersion protocolVersion = statusLine.getProtocolVersion();
                Logging.info(request.getMethod() + ' ' + url + " -> " + protocolVersion.getProtocol() + '/'
                        + protocolVersion.getMajor() + '.' + protocolVersion.getMinor() + ' '
                        + statusLine.getStatusCode());
            } catch (IOException | UnsupportedOperationException | IllegalDataException e) {
                Logging.error(e);
            }
        }
        this.done = true;
        synchronized (this) {
            this.notifyAll();
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
        synchronized (this) {
            this.notifyAll();
        }
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
        synchronized (this) {
            while (!isDone()) {
                this.wait(100);
            }
        }
        return this.conflatedData;
    }

    @Override
    public DataSet get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long realtime = unit.toMillis(timeout);
        long waitTime = realtime > MAX_POLLS ? realtime / MAX_POLLS : 1;
        long timeWaited = 0;
        synchronized (this) {
            while (!isDone()) {
                this.wait(waitTime);
                timeWaited += waitTime;
            }
        }
        if (!isDone() && timeWaited > realtime) {
            throw new TimeoutException();
        }
        return this.conflatedData;
    }
}
