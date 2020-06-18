// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import org.openstreetmap.josm.data.StructUtils;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.imagery.ImageryReader;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAIPreferenceEntry;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.ESRISourceReader;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.MapWithAISourceReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * Manages the list of imagery entries that are shown in the imagery menu.
 */
public class MapWithAILayerInfo {

    /** List of all usable layers */
    private final List<MapWithAIInfo> layers = Collections.synchronizedList(new ArrayList<>());
    /** List of layer ids of all usable layers */
    private final Map<String, MapWithAIInfo> layerIds = new HashMap<>();
    private ListenerList<LayerChangeListener> listeners = ListenerList.create();
    /** List of all available default layers */
    static final List<MapWithAIInfo> defaultLayers = new ArrayList<>();
    /** List of all available default layers (including mirrors) */
    static final List<MapWithAIInfo> allDefaultLayers = new ArrayList<>();
    /** List of all layer ids of available default layers (including mirrors) */
    static final Map<String, MapWithAIInfo> defaultLayerIds = new HashMap<>();

    /** The prefix for configuration of the MapWithAI sources */
    public static final String CONFIG_PREFIX = "mapwithai.sources.";

    private static final String[] DEFAULT_LAYER_SITES = {
            "https://gitlab.com/gokaart/JOSM_MapWithAI/-/raw/pages/public/json/sources.json" };

    /** Unique instance -- MUST be after DEFAULT_LAYER_SITES */
    public static final MapWithAILayerInfo instance = new MapWithAILayerInfo();

    public static MapWithAILayerInfo getInstance() {
        return instance;
    }

    /**
     * Returns the list of source layers sites.
     *
     * @return the list of source layers sites
     * @since 7434
     */
    public static Collection<String> getImageryLayersSites() {
        return Config.getPref().getList(CONFIG_PREFIX + "layers.sites", Arrays.asList(DEFAULT_LAYER_SITES));
    }

    private MapWithAILayerInfo() {
        load(false);
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
     */
    public void load(boolean fastFail) {
        clear();
        List<MapWithAIPreferenceEntry> entries = StructUtils.getListOfStructs(Config.getPref(),
                CONFIG_PREFIX + "entries", null, MapWithAIPreferenceEntry.class);
        if (entries != null) {
            for (MapWithAIPreferenceEntry prefEntry : entries) {
                try {
                    MapWithAIInfo i = new MapWithAIInfo(prefEntry);
                    add(i);
                } catch (IllegalArgumentException e) {
                    Logging.warn("Unable to load imagery preference entry:" + e);
                }
            }
            Collections.sort(layers);
        }
        loadDefaults(false, null, fastFail, null);
    }

    /**
     * Loads the available imagery entries.
     *
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
    public void loadDefaults(boolean clearCache, ExecutorService worker, boolean fastFail, FinishListener listener) {
        final DefaultEntryLoader loader = new DefaultEntryLoader(clearCache, fastFail, listener);
        if (worker == null) {
            loader.realRun();
        } else {
            worker.execute(loader);
        }
    }

    /**
     * Loader/updater of the available imagery entries
     */
    class DefaultEntryLoader extends PleaseWaitRunnable {

        private final boolean clearCache;
        private final boolean fastFail;
        private final List<MapWithAIInfo> newLayers = new ArrayList<>();
        private MapWithAISourceReader reader;
        private boolean canceled;
        private boolean loadError;
        private final FinishListener listener;

        DefaultEntryLoader(boolean clearCache, boolean fastFail, FinishListener listener) {
            super(tr("Update default entries"));
            this.clearCache = clearCache;
            this.fastFail = fastFail;
            this.listener = listener;
        }

        @Override
        protected void cancel() {
            canceled = true;
            Utils.close(reader);
        }

        @Override
        protected void realRun() {
            for (String source : getImageryLayersSites()) {
                if (canceled) {
                    return;
                }
                loadSource(source);
            }
            GuiHelper.runInEDT(this::finish);
        }

        protected void loadSource(String source) {
            boolean online = NetworkManager.isOffline(source);
            if (clearCache && online) {
                CachedFile.cleanup(source);
            }
            try {
                reader = new MapWithAISourceReader(source);
                reader.setFastFail(fastFail);
                Collection<MapWithAIInfo> result = reader.parse();
                newLayers.addAll(result);
            } catch (IOException ex) {
                loadError = true;
                Logging.log(Logging.LEVEL_ERROR, ex);
            }
        }

        @Override
        protected void finish() {
            defaultLayers.clear();
            allDefaultLayers.clear();
            defaultLayers.addAll(newLayers);
            for (MapWithAIInfo layer : newLayers) {
                if (MapWithAIType.ESRI == layer.getSourceType()) {
                    allDefaultLayers.addAll(parseEsri(layer));
                } else {
                    allDefaultLayers.add(layer);
                }
            }
            defaultLayerIds.clear();

            Collections.sort(defaultLayers, new MapWithAIInfo.MapWithAIInfoCategoryComparator());
            Collections.sort(allDefaultLayers, new MapWithAIInfo.MapWithAIInfoCategoryComparator());
            buildIdMap(allDefaultLayers, defaultLayerIds);
            updateEntriesFromDefaults(!loadError);
            buildIdMap(layers, layerIds);
            if (!loadError && !defaultLayerIds.isEmpty()) {
                dropOldEntries();
            }
            if (listener != null) {
                listener.onFinish();
            }
        }

        /**
         * Parse an Esri layer
         *
         * @param layer The layer to parse
         * @return The Feature Servers for the ESRI layer
         */
        private Collection<MapWithAIInfo> parseEsri(MapWithAIInfo layer) {
            try (ESRISourceReader esriReader = new ESRISourceReader(layer)) {
                return esriReader.parse();
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
            Set<String> notUnique = new HashSet<>();
            for (MapWithAIInfo i : lst) {
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
            for (String i : notUnique) {
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
        boolean changed = false;
        Collection<String> knownDefaults = new TreeSet<>(Config.getPref().getList(CONFIG_PREFIX + "layers.default"));
        Collection<String> newKnownDefaults = new TreeSet<>();
        for (MapWithAIInfo def : defaultLayers) {
            if (def.isDefaultEntry()) {
                boolean isKnownDefault = false;
                for (String entry : knownDefaults) {
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
                boolean isInUserList = false;
                if (!isKnownDefault) {
                    if (def.getId() != null) {
                        newKnownDefaults.add(def.getId());
                        for (MapWithAIInfo i : layers) {
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
        if (!dropold && !knownDefaults.isEmpty()) {
            newKnownDefaults.addAll(knownDefaults);
        }
        Config.getPref().putList(CONFIG_PREFIX + "layers.default", new ArrayList<>(newKnownDefaults));

        // automatically update user entries with same id as a default entry
        for (int i = 0; i < layers.size(); i++) {
            MapWithAIInfo info = layers.get(i);
            if (info.getId() == null) {
                continue;
            }
            MapWithAIInfo matchingDefault = defaultLayerIds.get(info.getId());
            if (matchingDefault != null && !matchingDefault.equalsPref(info)) {
                layers.set(i, matchingDefault);
                Logging.info(tr("Update imagery ''{0}''", info.getName()));
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    /**
     * Drop entries with Id which do no longer exist (removed from defaults).
     *
     * @since 11527
     */
    public void dropOldEntries() {
        List<String> drop = new ArrayList<>();

        for (Map.Entry<String, MapWithAIInfo> info : layerIds.entrySet()) {
            if (!defaultLayerIds.containsKey(info.getKey())) {
                remove(info.getValue());
                drop.add(info.getKey());
                Logging.info(tr("Drop old imagery ''{0}''", info.getValue().getName()));
            }
        }

        if (!drop.isEmpty()) {
            for (String id : drop) {
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
        List<MapWithAIPreferenceEntry> entries = new ArrayList<>();
        synchronized (layers) {
            for (MapWithAIInfo info : layers) {
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
        return Collections.unmodifiableList(layers);
    }

    /**
     * List of available default layers
     *
     * @return unmodifiable list containing available default layers
     */
    public List<MapWithAIInfo> getDefaultLayers() {
        return Collections.unmodifiableList(defaultLayers);
    }

    /**
     * List of all available default layers (including mirrors)
     *
     * @return unmodifiable list containing available default layers
     * @since 11570
     */
    public List<MapWithAIInfo> getAllDefaultLayers() {
        return Collections.unmodifiableList(allDefaultLayers);
    }

    public static void addLayer(MapWithAIInfo info) {
        instance.add(info);
        instance.save();
    }

    public static void addLayers(Collection<MapWithAIInfo> infos) {
        for (MapWithAIInfo i : infos) {
            instance.add(i);
        }
        instance.save();
        Collections.sort(instance.layers);
    }

    /**
     * Get unique id for ImageryInfo.
     *
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
