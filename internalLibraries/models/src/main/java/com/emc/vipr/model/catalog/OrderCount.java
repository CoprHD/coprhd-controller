package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.Map;

@XmlRootElement(name = "ordercount")
public class OrderCount {
    private Map<String, Long> countMap = new HashMap();

    public OrderCount() {
    }

    public void put(String key, long count) {
        countMap.put(key, count);
    }

    public Map<String, Long> get() {
        return countMap;
    }
}
