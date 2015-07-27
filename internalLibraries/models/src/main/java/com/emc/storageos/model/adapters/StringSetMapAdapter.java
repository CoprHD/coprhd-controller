/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.adapters;

import java.util.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.codehaus.jackson.annotate.JsonProperty;

public class StringSetMapAdapter extends XmlAdapter<List<StringSetMapAdapter.Entry>, Map<String,? extends Set<String>>> {
    
    public static class EntryList {
        public List<Entry> entryList = new ArrayList<StringSetMapAdapter.Entry>();
    }
    
    public static class Entry {
        private String key;
        private String value;

        @XmlElement(name = "name")
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @XmlElement(name = "value")
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
        
    }
    
    @Override
    public List<Entry> marshal(Map<String,? extends Set<String>> map) throws Exception {
        List<Entry> entryList = new ArrayList<StringSetMapAdapter.Entry>();
        for(Map.Entry<String,? extends Set<String>> mapEntry : map.entrySet()) {
            for (String value : mapEntry.getValue()) {
                Entry entry = new Entry();
                entry.setKey(mapEntry.getKey());
                entry.setValue(value);
                entryList.add(entry);
            }
            
        }
        return entryList;
    }

    @Override
    public Map<String,Set<String>> unmarshal(List<StringSetMapAdapter.Entry> entryList)
            throws Exception {
        Map<String,Set<String>> map = new HashMap<String,Set<String>>();
        for (Entry entry : entryList) {
            if (map.containsKey(entry.getKey())) {
                map.get(entry.getKey()).add(entry.getValue());
            } else {
                Set<String> valueSet = new HashSet<String>();
                valueSet.add(entry.getValue());
                map.put(entry.getKey(), valueSet);
            }
        }
        return map;
    }
}
