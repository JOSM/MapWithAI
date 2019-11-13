package org.openstreetmap.josm.plugins.mapwithai.backend.commands.conflation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DataUrl {
    private List<Object> dataList;

    public DataUrl(String source, String url, Boolean enabled) {
        this(source, url, enabled, Collections.emptyMap());
    }

    public DataUrl(String source, String url, Boolean enabled, Map<String, Map<String, Boolean>> parameters) {
        setDataList(Arrays.asList(source, url, enabled, parameters));
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
        return map;
    }
}
