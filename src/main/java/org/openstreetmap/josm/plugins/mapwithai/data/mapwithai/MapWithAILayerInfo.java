// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.data.mapwithai;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.BufferedReader;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonValue;

import org.openstreetmap.josm.data.StructUtils;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryBounds;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.imagery.ImageryReader;
import org.openstreetmap.josm.plugins.mapwithai.data.mapwithai.MapWithAIInfo.MapWithAIPreferenceEntry;
import org.openstreetmap.josm.plugins.mapwithai.io.mapwithai.MapWithAISourceReader;
import org.openstreetmap.josm.spi.preferences.Config;
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
        private FinishListener listener;

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
                if (MapWithAIInfo.MapWithAIType.ESRI.equals(layer.getSourceType())) {
                    allDefaultLayers.addAll(addEsriLayer(layer));
                } else {
                    allDefaultLayers.add(layer);
                }
            }
            defaultLayerIds.clear();
            Collections.sort(defaultLayers);
            Collections.sort(allDefaultLayers);
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
    }

    /**
     * Take a {@link MapWithAIInfo.MapWithAIType#ESRI} layer and convert it to a
     * list of "true" layers.
     *
     * @param layer The ESRI layer (no checks performed here)
     * @return The layers to be added instead of the ESRI layer.
     */
    public static Collection<MapWithAIInfo> addEsriLayer(MapWithAIInfo layer) {
        Pattern startReplace = Pattern.compile("\\{start\\}");
        String search = "/search?sortField=added&sortOrder=desc&num=12&start={start}&f=json";
        String url = layer.getUrl();
        String group = layer.getId();
        if (!url.endsWith("/")) {
            url = url.concat("/");
        }

        Collection<MapWithAIInfo> information = new HashSet<>();

        String next = "1";
        String searchUrl = startReplace.matcher(search).replaceAll(next);
        while (!next.equals("-1")) {
            try (CachedFile layers = new CachedFile(url + "content/groups/" + group + searchUrl);
                    BufferedReader i = layers.getContentReader();
                    JsonReader reader = Json.createReader(i)) {
                JsonStructure parser = reader.read();
                if (parser.getValueType().equals(JsonValue.ValueType.OBJECT)) {
                    JsonObject obj = parser.asJsonObject();
                    next = obj.getString("nextStart", "-1");
                    searchUrl = startReplace.matcher(search).replaceAll(next);
                    JsonArray features = obj.getJsonArray("results");
                    for (JsonObject feature : features.getValuesAs(JsonObject.class)) {
                        // Use the initial esri server information to keep conflation info
                        MapWithAIInfo newInfo = new MapWithAIInfo(layer);
                        newInfo.setId(feature.getString("id"));
                        if (feature.getString("type", "").equals("Feature Service")) {
                            newInfo.setUrl(featureService(newInfo, feature.getString("url")));
                        } else {
                            newInfo.setUrl(feature.getString("url"));
                        }
                        newInfo.setName(feature.getString("title", feature.getString("name")));
                        String[] extent = feature.getJsonArray("extent").getValuesAs(JsonArray.class).stream()
                                .flatMap(array -> array.getValuesAs(JsonNumber.class).stream())
                                .map(JsonNumber::doubleValue).map(Object::toString).toArray(String[]::new);
                        ImageryBounds imageryBounds = new ImageryBounds(
                                String.join(",", extent[1], extent[0], extent[3], extent[2]), ",");
                        newInfo.setBounds(imageryBounds);
                        newInfo.setSourceType(MapWithAIInfo.MapWithAIType.ESRI_FEATURE_SERVER);
                        newInfo.setTermsOfUseText(feature.getString("licenseInfo", null));
                        if (feature.containsKey("thumbnail")) {
                            newInfo.setAttributionImageURL(url + "content/items/" + newInfo.getId() + "/info/"
                                    + feature.getString("thumbnail"));
                        }
                        // TODO groupCategories
                        // TODO snippet/description
                        information.add(newInfo);
                    }
                }
            } catch (ClassCastException | IOException e) {
                Logging.error(e);
                next = "-1";
            }
        }
        return information;
    }

    private static String featureService(MapWithAIInfo mapwithaiInfo, String url) {
        String toGet = url.endsWith("pjson") ? url : url.concat("?f=pjson");
        try (CachedFile featureServer = new CachedFile(toGet);
                BufferedReader br = featureServer.getContentReader();
                JsonReader reader = Json.createReader(br)) {
            JsonObject info = reader.readObject();
            JsonArray layers = info.getJsonArray("layers");
            // TODO use all the layers?
            JsonObject layer = layers.get(0).asJsonObject();
            String partialUrl = (url.endsWith("/") ? url : url + "/") + layer.getInt("id");
            mapwithaiInfo.setReplacementTags(getReplacementTags(partialUrl));

            return partialUrl;
        } catch (IOException e) {
            Logging.error(e);
            return null;
        }
    }

    private static Map<String, String> getReplacementTags(String layerUrl) {
        String toGet = layerUrl.endsWith("pjson") ? layerUrl : layerUrl.concat("?f=pjson");
        try (CachedFile featureServer = new CachedFile(toGet);
                BufferedReader br = featureServer.getContentReader();
                JsonReader reader = Json.createReader(br)) {
            JsonObject info = reader.readObject();

            return info.getJsonArray("fields").getValuesAs(JsonObject.class).stream().collect(Collectors.toMap(
                    o -> o.getString("name"), o -> o.getBoolean("editable", true) ? o.getString("alias", "") : ""));
        } catch (IOException e) {
            Logging.error(e);
        }
        return Collections.emptyMap();
    }

    /**
     * Build the mapping of unique ids to {@link ImageryInfo}s.
     *
     * @param lst   input list
     * @param idMap output map
     */
    private static void buildIdMap(List<MapWithAIInfo> lst, Map<String, MapWithAIInfo> idMap) {
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
    }

    /**
     * Remove an imagery entry.
     *
     * @param info imagery entry to remove
     */
    public void remove(MapWithAIInfo info) {
        layers.remove(info);
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
        if (info.getId() != null && layerIds.get(info.getId()) == info) {
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

    public static interface FinishListener {
        public void onFinish();
    }
}
