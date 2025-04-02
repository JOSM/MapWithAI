// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.OsmReader;
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
    private HttpClient client;
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
        if (!Utils.isStripEmpty(url) && !NetworkManager.isOffline(url)) {
            this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            try {
                final var form = new TreeMap<String, String>();
                if (osm != null) {
                    final var output = new StringWriter();
                    try (var writer = OsmWriterFactory.createOsmWriter(new PrintWriter(output), true, "0.6")) {
                        writer.write(osm);
                        form.put("openstreetmap", output.toString()); // APPLICATION_XML
                    }
                }
                // We need to reset the writers to avoid writing previous streams
                final var output = new StringWriter();
                try (var writer = OsmWriterFactory.createOsmWriter(new PrintWriter(output), true, "0.6")) {
                    writer.write(external);
                    form.put("external", output.toString());
                }

                final var boundary = UUID.randomUUID().toString();
                final var request = HttpRequest.newBuilder(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.ofString(formEncodeMap(boundary, form)))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "multipart/form-data;boundary=" + boundary).build();
                final var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    conflatedData = OsmReader.parseDataSet(response.body(), NullProgressMonitor.INSTANCE,
                            OsmReader.Options.SAVE_ORIGINAL_ID);
                } else {
                    conflatedData = null;
                }
                Logging.info(request.method() + ' ' + url + " -> " + request.uri().getScheme() + '/'
                        + response.version() + ' ' + response.statusCode());
            } catch (HttpTimeoutException httpTimeoutException) {
                final var oldThrowable = NetworkManager.addNetworkError(url, httpTimeoutException);
                if (oldThrowable != null) {
                    Logging.trace(oldThrowable);
                }
            } catch (IOException | UnsupportedOperationException | IllegalDataException e) {
                Logging.error(e);
            } catch (InterruptedException e) {
                Logging.trace(e);
                if (!this.isCancelled()) {
                    this.cancel(false);
                }
                Thread.currentThread().interrupt();
            }
        }
        this.done = true;
        synchronized (this) {
            this.notifyAll();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        this.done = true;
        this.cancelled = true;
        if (this.client != null) {
            this.client.executor().ifPresent(Object::notifyAll);
        }
        this.client = null;
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

    private static String formEncodeMap(String boundary, Map<String, String> form) {
        final var separator = "--" + boundary + "\r\nContent-Disposition: form-data; name=";
        final var builder = new StringBuilder();
        for (var entry : form.entrySet()) {
            builder.append(separator).append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append(";\r\nContent-Type: application/xml\r\n\r\n")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).append("\r\n");
        }
        builder.append("--").append(boundary).append("--");
        return builder.toString();
    }
}
