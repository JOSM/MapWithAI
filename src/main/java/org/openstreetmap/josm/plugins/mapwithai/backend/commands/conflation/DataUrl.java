// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

public class DataUrl {
    private List<Object> dataList;

    public DataUrl(String source, String url, Boolean enabled) {
        this(source, url, enabled, "[]");
    }

    public DataUrl(String source, String url, Boolean enabled, String jsonString) {
        setDataList(Arrays.asList(source, url, enabled, readJsonStringArraySimple(jsonString)));
    }

    public static DataUrl emptyData() {
        return new DataUrl(tr("Default Source"), "http://example.com", false);
    }

    /**
     * Naively read a json string into an array
     *
     * @param jsonString A json array (e.g. [0, {"this": "that"}])
     * @return A JsonArray
     */
    public static JsonArray readJsonStringArraySimple(String jsonString) {
        JsonArray returnArray = JsonValue.EMPTY_JSON_ARRAY;
        try (JsonReader parser = Json.createReader(new StringReader(jsonString))) {
            returnArray = parser.readArray();
        }
        return returnArray;
    }

    /**
     * @return the dataList (source, url, enabled)
     */
    public List<Object> getDataList() {
        return dataList;
    }

    /**
     * @param dataList the dataList to set
     */
    public void setDataList(List<Object> dataList) {
        if (this.dataList == null || dataList != null && dataList.size() == this.dataList.size()) {
            this.dataList = dataList;
        }
    }

    public void reset() {
        dataList = Arrays.asList("", "", false);
    }

    public Map<String, String> getMap() {
        Map<String, String> map = new TreeMap<>();
        map.put("source", dataList.get(0).toString());
        map.put("url", dataList.get(1).toString());
        map.put("enabled", dataList.get(2).toString());
        map.put("parameters", dataList.size() > 3 ? dataList.get(3).toString() : "[]");
        return map;
    }

    public static Map<String, String> addUrlParameters(Map<String, String> map) {
        if (map.containsKey("parameters") && map.containsKey("url")) {
            JsonArray array = readJsonStringArraySimple(map.get("parameters"));
            for (Object val : array.toArray()) {
                if (val instanceof JsonObject) {
                    JsonObject obj = (JsonObject) val;
                    if (obj.getBoolean("enabled", false)) {
                        map.put("url", map.get("url").concat("&").concat(obj.getString("parameter")));
                    }
                }
            }
        }
        return map;
    }
}
