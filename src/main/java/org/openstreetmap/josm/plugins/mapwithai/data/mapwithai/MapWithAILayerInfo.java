// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.openstreetmap.gui.jmapviewer.tilesources.TileSourceInfo;
import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.StructUtils;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.CachingProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.imagery.ImageryReader;
import org.openstreetmap.josm.plugins.mapwithai.backend.MapWithAIDataUtils;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAIPreferenceEntry;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.ESRISourceReader;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.MapWithAISourceReader;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.OvertureSourceReader;
import org.openstreetmap.josm.plugins.mapwithai.spi.preferences.MapWithAIConfig;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import jakarta.annotation.Nonnull;

/**
 * Manages the list of imagery entries that are shown in the imagery menu.
 */
public class MapWithAILayerInfo {
    /**
     * A boolean preference used to determine if preview datasets should be shown
     */
    public static final CachingProperty<Boolean> SHOW_PREVIEW = new BooleanProperty("mapwithai.sources.preview", false)
            .cached();

    /** Finish listeners */
    private ListenerList<FinishListener> finishListenerListenerList = ListenerList.create();
    /** List of all usable layers */
    private final List<MapWithAIInfo> layers = Collections.synchronizedList(new ArrayList<>());
    /** List of layer ids of all usable layers */
    private final Map<String, MapWithAIInfo> layerIds = new HashMap<>();
    private final ListenerList<LayerChangeListener> listeners = ListenerList.create();
    /** List of all available default layers */
    static final List<MapWithAIInfo> defaultLayers = Collections.synchronizedList(new ArrayList<>());
    /** List of all available default layers (including mirrors) */
    static final List<MapWithAIInfo> allDefaultLayers = Collections.synchronizedList(new ArrayList<>());
    /** List of all layer ids of available default layers (including mirrors) */
    static final Map<String, MapWithAIInfo> defaultLayerIds = Collections.synchronizedMap(new HashMap<>());

    /** The prefix for configuration of the MapWithAI sources */
    public static final String CONFIG_PREFIX = "mapwithai.sources.";

    /** Unique instance -- MUST be after DEFAULT_LAYER_SITES */
    private static MapWithAILayerInfo instance;

    public static MapWithAILayerInfo getInstance() {
        if (instance != null) {
            return instance;
        }
        final var finished = new AtomicBoolean();
        synchronized (MapWithAILayerInfo.class) {
            if (instance == null) {
                instance = new MapWithAILayerInfo(() -> {
                    synchronized (MapWithAILayerInfo.class) {
                        finished.set(true);
                        MapWithAILayerInfo.class.notifyAll();
                    }
                });
            } else {
                finished.set(true);
            }
        }
        // Avoid a deadlock in the EDT.
        if (!finished.get() && !SwingUtilities.isEventDispatchThread()) {
            var count = 0;
            synchronized (MapWithAILayerInfo.class) {
                while (!finished.get() && count < 120) {
                    count++;
                    try {
                        MapWithAILayerInfo.class.wait(1000);
                    } catch (InterruptedException e) {
                        Logging.error(e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Returns the list of source layers sites.
     *
     * @return the list of source layers sites
     * @since 7434
     */
    public static Collection<String> getImageryLayersSites() {
        return Config.getPref().getList(CONFIG_PREFIX + "layers.sites",
                Collections.singletonList(MapWithAIConfig.getUrls().getMapWithAISourcesJson()));
    }

    /**
     * Set the source sites
     *
     * @param sites The sites to set
     * @return See
     *         {@link org.openstreetmap.josm.spi.preferences.IPreferences#putList}
     */
    public static boolean setImageryLayersSites(Collection<String> sites) {
        if (sites == null || sites.isEmpty()) {
            return Config.getPref().put(CONFIG_PREFIX + "layers.sites", null);
        } else {
            return Config.getPref().putList(CONFIG_PREFIX + "layers.sites", new ArrayList<>(sites));
        }
    }

    private MapWithAILayerInfo(FinishListener listener) {
        load(false, listener);
    }

    /**
     * Constructs a new {@code ImageryLayerInfo} from an existing one.
     *
     * @param info info to copy
     */
    public MapWithAILayerInfo(MapWithAILayerInfo info) {
        layers.addAll(info.layers);
    }

    /**
     * Clear the lists of layers.
     */
    public void clear() {
        layers.clear();
        layerIds.clear();
    }

    /**
     * Loads the custom as well as default imagery entries.
     *
     * @param fastFail whether opening HTTP connections should fail fast, see
     *                 {@link ImageryReader#setFastFail(boolean)}
     * @param listener A listener to call when loading default entries is finished
     */
    public void load(boolean fastFail, FinishListener listener) {
        clear();
        final var entries = StructUtils.getListOfStructs(Config.getPref(), CONFIG_PREFIX + "entries", null,
                MapWithAIPreferenceEntry.class);
        if (entries != null) {
            for (MapWithAIPreferenceEntry prefEntry : entries) {
                try {
                    final var i = new MapWithAIInfo(prefEntry);
                    add(i);
                } catch (IllegalArgumentException e) {
                    Logging.warn("Unable to load imagery preference entry:" + e);
                }
            }
            // Remove a remote control commands in layers
            layers.removeIf(i -> i.getUrl().contains("localhost:8111"));
            Collections.sort(layers);
        }
        // Ensure that the cache is initialized prior to running in the fork join pool
        // on webstart
        if (System.getSecurityManager() != null) {
            Logging.trace("MapWithAI loaded: {0}", ESRISourceReader.SOURCE_CACHE.getClass());
        }
        loadDefaults(false, MapWithAIDataUtils.getForkJoinPool(), fastFail, listener);
    }

    /**
     * Loads the available imagery entries.
     * <p>
     * The data is downloaded from the JOSM website (or loaded from cache). Entries
     * marked as "default" are added to the user selection, if not already present.
     *
     * @param clearCache if true, clear the cache and start a fresh download.
     * @param worker     executor service which will perform the loading. If null,
     *                   it should be performed using a {@link PleaseWaitRunnable}
     *                   in the background
     * @param fastFail   whether opening HTTP connections should fail fast, see
     *                   {@link ImageryReader#setFastFail(boolean)}
     * @param listener   A listener to call when the everything is done
     * @since 12634
     */
    public void loadDefaults(boolean clearCache, ForkJoinPool worker, boolean fastFail, FinishListener listener) {
        final var loader = new DefaultEntryLoader(clearCache, fastFail);
        if (this.finishListenerListenerList == null) {
            this.finishListenerListenerList = ListenerList.create();
        }
        boolean running = this.finishListenerListenerList.hasListeners();
        if (listener != null) {
            this.finishListenerListenerList.addListener(listener);
        }
        if (running) {
            return;
        }
        if (worker == null) {
            final var pleaseWaitRunnable = new PleaseWaitRunnable(tr("Update default entries")) {
                @Override
                protected void cancel() {
                    loader.canceled = true;
                }

                @Override
                protected void realRun() {
                    loader.compute();
                }

                @Override
                protected void finish() {
                    loader.finish();
                }
            };
            pleaseWaitRunnable.run();
        } else {
            worker.execute(loader);
        }
    }

    /**
     * Add a listener for when the data finishes updating
     *
     * @param finishListener The listener
     */
    public void addFinishListener(final FinishListener finishListener) {
        if (this.finishListenerListenerList == null) {
            finishListener.onFinish();
        } else {
            this.finishListenerListenerList.addListener(finishListener);
        }
    }

    /**
     * Loader/updater of the available imagery entries
     */
    class DefaultEntryLoader extends RecursiveTask<List<MapWithAIInfo>> {

        @Serial
        private static final long serialVersionUID = 12550342142551680L;
        private final boolean clearCache;
        private final boolean fastFail;
        private final List<MapWithAIInfo> newLayers = new ArrayList<>();
        private MapWithAISourceReader reader;
        private boolean canceled;
        private boolean loadError;

        DefaultEntryLoader(boolean clearCache, boolean fastFail) {
            this.clearCache = clearCache;
            this.fastFail = fastFail;
        }

        protected void cancel() {
            canceled = true;
            Utils.close(reader);
        }

        @Override
        public List<MapWithAIInfo> compute() {
            if (this.clearCache) {
                ESRISourceReader.SOURCE_CACHE.clear();
            }
            // This is literally to avoid allocations on startup
            final Preferences preferences;
            if (Config.getPref()instanceof Preferences pref) {
                preferences = pref;
            } else {
                preferences = null;
            }
            try {
                if (preferences != null) {
                    preferences.enableSaveOnPut(false);
                }
                for (String source : getImageryLayersSites()) {
                    if (canceled) {
                        return this.newLayers;
                    }
                    loadSource(source);
                }
            } finally {
                if (preferences != null) {
                    // saveOnPut is pretty much always true
                    preferences.enableSaveOnPut(true);
                    MainApplication.worker.execute(() -> {
                        try {
                            preferences.save();
                        } catch (IOException e) {
                            // This is highly unlikely to happen
                            Logging.error(e);
                        }
                    });
                }
            }
            GuiHelper.runInEDTAndWait(this::finish);
            return this.newLayers;
        }

        protected void loadSource(String source) {
            boolean online = !NetworkManager.isOffline(source);
            if (clearCache && online) {
                CachedFile.cleanup(source);
            }
            try {
                reader = new MapWithAISourceReader(source);
                this.reader.setClearCache(this.clearCache);
                reader.setFastFail(fastFail);
                final var result = reader.parse().orElse(Collections.emptyList());
                // This is called here to "pre-cache" the layer information, to avoid blocking
                // the EDT
                this.updateEsriLayers(result);
                this.updateOvertureLayers(result);
                newLayers.addAll(result);
            } catch (IOException ex) {
                loadError = true;
                Logging.log(Logging.LEVEL_ERROR, ex);
            }
        }

        /**
         * Update the esri layer information
         *
         * @param layers The layers to update
         */
        private void updateEsriLayers(@Nonnull final Collection<MapWithAIInfo> layers) {
            final var esriInfo = new ArrayList<MapWithAIInfo>(300);
            for (var layer : layers) {
                if (MapWithAIType.ESRI == layer.getSourceType()) {
                    for (var future : parseEsri(layer)) {
                        try {
                            esriInfo.add(future.get());
                        } catch (InterruptedException e) {
                            Logging.error(e);
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            Logging.error(e);
                        }
                    }
                }
            }
            layers.addAll(esriInfo);
        }

        /**
         * Update the overture layers
         * @param layers The layers to iterate through and modify
         * @throws IOException If something happens while parsing overture layers
         */
        private void updateOvertureLayers(@Nonnull final Collection<MapWithAIInfo> layers) throws IOException {
            final var overtureLayers = new ArrayList<MapWithAIInfo>(4);
            for (var layer : layers) {
                if (MapWithAIType.OVERTURE == layer.getSourceType()) {
                    try (var reader = new OvertureSourceReader(layer)) {
                        reader.parse().ifPresent(overtureLayers::addAll);
                    }
                }
            }
            layers.removeIf(layer -> MapWithAIType.OVERTURE == layer.getSourceType());
            layers.addAll(overtureLayers);
        }

        protected void finish() {
            defaultLayers.clear();
            synchronized (allDefaultLayers) {
                allDefaultLayers.clear();
                defaultLayers.addAll(newLayers);
                allDefaultLayers.addAll(newLayers);
                allDefaultLayers.sort(new MapWithAIInfo.MapWithAIInfoCategoryComparator());
                allDefaultLayers.sort(Comparator.comparing(TileSourceInfo::getName));
                allDefaultLayers.sort(Comparator.comparing(info -> info.getCategory().getDescription()));
                allDefaultLayers.sort(Comparator
                        .comparingInt(info -> -info.getAdditionalCategories().indexOf(MapWithAICategory.FEATURED)));
                defaultLayerIds.clear();
                synchronized (defaultLayerIds) {
                    buildIdMap(allDefaultLayers, defaultLayerIds);
                }
            }
            updateEntriesFromDefaults(!loadError);
            buildIdMap(layers, layerIds);
            if (!loadError && !defaultLayerIds.isEmpty()) {
                dropOldEntries();
            }
            final var listenerList = MapWithAILayerInfo.this.finishListenerListenerList;
            MapWithAILayerInfo.this.finishListenerListenerList = null;
            Config.getPref().putLong("mapwithai.layerinfo.lastupdated", Instant.now().getEpochSecond());
            if (listenerList != null) {
                listenerList.fireEvent(FinishListener::onFinish);
            }
        }

        /**
         * Parse an Esri layer
         *
         * @param layer The layer to parse
         * @return The Feature Servers for the ESRI layer
         */
        private Collection<ForkJoinTask<MapWithAIInfo>> parseEsri(MapWithAIInfo layer) {
            try {
                return new ESRISourceReader(layer).parse();
            } catch (IOException e) {
                Logging.error(e);
            }
            return Collections.emptyList();
        }

        /**
         * Build the mapping of unique ids to {@link ImageryInfo}s.
         *
         * @param lst   input list
         * @param idMap output map
         */
        private void buildIdMap(List<MapWithAIInfo> lst, Map<String, MapWithAIInfo> idMap) {
            idMap.clear();
            final var notUnique = new HashSet<String>();
            for (var i : lst) {
                if (i.getId() != null) {
                    if (idMap.containsKey(i.getId())) {
                        notUnique.add(i.getId());
                        Logging.error("Id ''{0}'' is not unique - used by ''{1}'' and ''{2}''!", i.getId(), i.getName(),
                                idMap.get(i.getId()).getName());
                        continue;
                    }
                    idMap.put(i.getId(), i);
                }
            }
            for (var i : notUnique) {
                idMap.remove(i);
            }
        }
    }

    /**
     * Update user entries according to the list of default entries.
     *
     * @param dropold if <code>true</code> old entries should be removed
     * @since 11706
     */
    public void updateEntriesFromDefaults(boolean dropold) {
        // add new default entries to the user selection
        var changed = false;
        final var knownDefaults = new TreeSet<>(Config.getPref().getList(CONFIG_PREFIX + "layers.default"));
        final var newKnownDefaults = new TreeSet<String>();
        synchronized (defaultLayers) {
            for (var def : defaultLayers) {
                if (def.isDefaultEntry()) {
                    var isKnownDefault = false;
                    for (var entry : knownDefaults) {
                        if (entry.equals(def.getId())) {
                            isKnownDefault = true;
                            newKnownDefaults.add(entry);
                            knownDefaults.remove(entry);
                            break;
                        } else if (isSimilar(entry, def.getUrl())) {
                            isKnownDefault = true;
                            if (def.getId() != null) {
                                newKnownDefaults.add(def.getId());
                            }
                            knownDefaults.remove(entry);
                            break;
                        }
                    }
                    var isInUserList = false;
                    if (!isKnownDefault) {
                        if (def.getId() != null) {
                            newKnownDefaults.add(def.getId());
                            for (var i : layers) {
                                if (isSimilar(def, i)) {
                                    isInUserList = true;
                                    break;
                                }
                            }
                        } else {
                            Logging.error("Default imagery ''{0}'' has no id. Skipping.", def.getName());
                        }
                    }
                    if (!isKnownDefault && !isInUserList) {
                        add(new MapWithAIInfo(def));
                        changed = true;
                    }
                }
            }
        }
        if (!dropold && !knownDefaults.isEmpty()) {
            newKnownDefaults.addAll(knownDefaults);
        }
        Config.getPref().putList(CONFIG_PREFIX + "layers.default", new ArrayList<>(newKnownDefaults));

        // automatically update user entries with same id as a default entry
        for (var i = 0; i < layers.size(); i++) {
            final var info = layers.get(i);
            if (info.getId() == null) {
                continue;
            }
            final var matchingDefault = defaultLayerIds.get(info.getId());
            if (matchingDefault != null && !matchingDefault.equalsPref(info)) {
                layers.set(i, matchingDefault);
                Logging.info(tr("Update imagery ''{0}''", info.getName()));
                changed = true;
            }
        }

        if (changed) {
            MainApplication.worker.execute(this::save);
        }
    }

    /**
     * Drop entries with Id which do no longer exist (removed from defaults).
     *
     * @since 11527
     */
    public void dropOldEntries() {
        final var drop = new ArrayList<String>();

        for (var info : layerIds.entrySet()) {
            if (!defaultLayerIds.containsKey(info.getKey())) {
                remove(info.getValue());
                drop.add(info.getKey());
                Logging.info(tr("Drop old imagery ''{0}''", info.getValue().getName()));
            }
        }

        if (!drop.isEmpty()) {
            for (var id : drop) {
                layerIds.remove(id);
            }
            save();
        }
    }

    private static boolean isSimilar(MapWithAIInfo iiA, MapWithAIInfo iiB) {
        if (iiA == null || iiB == null) {
            return false;
        }
        if (iiA.getId() != null && iiB.getId() != null) {
            return iiA.getId().equals(iiB.getId());
        }
        return isSimilar(iiA.getUrl(), iiB.getUrl());
    }

    // some additional checks to respect extended URLs in preferences (legacy
    // workaround)
    private static boolean isSimilar(String a, String b) {
        return Objects.equals(a, b)
                || (a != null && b != null && !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a)));
    }

    /**
     * Add a new imagery entry.
     *
     * @param info imagery entry to add
     */
    public void add(MapWithAIInfo info) {
        layers.add(info);
        this.listeners.fireEvent(l -> l.changeEvent(info));
    }

    /**
     * Remove an imagery entry.
     *
     * @param info imagery entry to remove
     */
    public void remove(MapWithAIInfo info) {
        layers.remove(info);
        this.listeners.fireEvent(l -> l.changeEvent(info));
    }

    /**
     * Save the list of imagery entries to preferences.
     */
    public synchronized void save() {
        final var entries = new ArrayList<MapWithAIPreferenceEntry>();
        synchronized (layers) {
            for (var info : layers) {
                entries.add(new MapWithAIPreferenceEntry(info));
            }
        }
        StructUtils.putListOfStructs(Config.getPref(), CONFIG_PREFIX + "entries", entries,
                MapWithAIPreferenceEntry.class);
    }

    /**
     * List of usable layers
     *
     * @return unmodifiable list containing usable layers
     */
    public List<MapWithAIInfo> getLayers() {
        return Collections.unmodifiableList(filterPreview(layers));
    }

    /**
     * List of available default layers
     *
     * @return unmodifiable list containing available default layers
     */
    public List<MapWithAIInfo> getDefaultLayers() {
        return Collections.unmodifiableList(filterPreview(defaultLayers));
    }

    /**
     * List of all available default layers (including mirrors)
     *
     * @return unmodifiable list containing available default layers
     * @since 11570
     */
    public List<MapWithAIInfo> getAllDefaultLayers() {
        return Collections.unmodifiableList(filterPreview(allDefaultLayers));
    }

    /**
     * Remove preview layers, if {@link #SHOW_PREVIEW} is not {@code true}
     *
     * @param layers The layers to filter
     * @return The layers without any preview layers, if {@link #SHOW_PREVIEW} is
     *         not {@code true}.
     */
    private static List<MapWithAIInfo> filterPreview(List<MapWithAIInfo> layers) {
        final var newList = new ArrayList<>(layers);
        newList.removeIf(MapWithAILayerInfo::isFiltered);
        return newList;
    }

    /**
     * Check if the layer should be filtered out
     *
     * @param info The layer to check
     * @return {@code true} if the layer should be filtered
     */
    public static boolean isFiltered(MapWithAIInfo info) {
        if (info == null || !info.hasValidUrl()) {
            return true;
        }
        if (ExpertToggleAction.isExpert() && Boolean.TRUE.equals(SHOW_PREVIEW.get())) {
            return false;
        }
        return info.hasCategory(MapWithAICategory.PREVIEW);
    }

    /**
     * Add a data source
     *
     * @param info The source to add
     */
    public static void addLayer(MapWithAIInfo info) {
        instance.add(info);
        instance.save();
    }

    /**
     * Add multiple data sources
     *
     * @param infos The sources to add
     */
    public static void addLayers(Collection<MapWithAIInfo> infos) {
        infos.forEach(instance::add);
        instance.save();
        Collections.sort(instance.layers);
    }

    /**
     * Get unique id for ImageryInfo.
     * <p>
     * This takes care, that no id is used twice (due to a user error)
     *
     * @param info the ImageryInfo to look up
     * @return null, if there is no id or the id is used twice, the corresponding id
     *         otherwise
     */
    public String getUniqueId(MapWithAIInfo info) {
        if (info != null && info.getId() != null && info.equals(layerIds.get(info.getId()))) {
            return info.getId();
        }
        return null;
    }

    /**
     * Returns imagery layer info for the given id.
     *
     * @param id imagery layer id.
     * @return imagery layer info for the given id, or {@code null}
     * @since 13797
     */
    public MapWithAIInfo getLayer(String id) {
        return layerIds.get(id);
    }

    /**
     * Listen for the data source info to finish downloading
     */
    public interface FinishListener {
        /**
         * Called when information is finished loading
         */
        void onFinish();
    }

    /**
     * Add a listener that is called on layer change events. Only fires on single
     * add/remove events.
     *
     * @param listener The listener to be called.
     */
    public void addListener(LayerChangeListener listener) {
        this.listeners.addListener(listener);
    }

    /**
     * An interface to tell listeners what info object has changed
     *
     * @author Taylor Smock
     *
     */
    public interface LayerChangeListener {
        /**
         * Fired when an info object has been added/removed to the layer list
         *
         * @param modified A MapWithAIInfo object that has been removed or added to the
         *                 layers
         */
        void changeEvent(MapWithAIInfo modified);
    }
}
