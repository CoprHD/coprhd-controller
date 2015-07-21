/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.dbcli.adapter;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.emc.storageos.db.client.model.StringMap;

@XmlRootElement
public class StringMapAdapter extends
     XmlAdapter<StringMapAdapter.AdaptedMap, StringMap> {

    public static class AdaptedMap {
        public List<Entry> entry = new ArrayList<Entry>();
    }
    
    public static class Entry {
        public String key;
        public String value;
    }
    
    @Override
    public StringMap unmarshal(AdaptedMap adaptedMap)
         throws Exception {
        StringMap map = new StringMap();
        for (Entry entry : adaptedMap.entry) {
            map.put(entry.key, entry.value);
        }
        return map;
    }
    
    @Override
    public AdaptedMap marshal(StringMap map) throws Exception {
        AdaptedMap adaptedMap = new AdaptedMap();
        for (Map.Entry<String, String> mapEntry : map.entrySet()) {
            Entry entry = new Entry();
            entry.key = mapEntry.getKey();
            entry.value = mapEntry.getValue();
            adaptedMap.entry.add(entry);
        }
        return adaptedMap;
    }

}
