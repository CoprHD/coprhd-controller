package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.Map;

@XmlRootElement(name = "order_count")
public class OrderCount {
    private Map<String, Long> countMap = new HashMap();

    public OrderCount() {
    }

    public void put(String key, long count) {
        countMap.put(key, count);
    }

    public void setCountMap(Map<String, Long> countMap) {
        this.countMap = countMap;
    }

    @XmlElement(name = "counts")
    public Map<String, Long> getCounts() {
        return countMap;
    }
}
