/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.catalog;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "order_count")
public class OrderCount {
    private Map<String, Long> counts = new HashMap();

    public OrderCount() {
    }

    public OrderCount(Map<String, Long> map) {
        counts = map;
    }

    public void put(String key, long count) {
        counts.put(key, count);
    }

    @XmlElementWrapper(name = "counts")
    public Map<String, Long> getCounts() {
        return counts;
    }
}
