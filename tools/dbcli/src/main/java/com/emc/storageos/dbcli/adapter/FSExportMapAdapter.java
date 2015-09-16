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

import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;

@XmlRootElement
public class FSExportMapAdapter extends
        XmlAdapter<FSExportMapAdapter.AdaptedMap, FSExportMap> {

    public static class AdaptedMap {
        public List<Entry> entry = new ArrayList<Entry>();
    }

    public static class Entry {
        public String key;
        public FileExport value;
    }

    @Override
    public FSExportMap unmarshal(AdaptedMap adaptedMap)
            throws Exception {
        FSExportMap map = new FSExportMap();
        for (Entry entry : adaptedMap.entry) {
            map.put(entry.key, entry.value);
        }
        return map;
    }

    @Override
    public AdaptedMap marshal(FSExportMap map) throws Exception {
        AdaptedMap adaptedMap = new AdaptedMap();
        for (Map.Entry<String, FileExport> mapEntry : map.entrySet()) {
            Entry entry = new Entry();
            entry.key = mapEntry.getKey();
            entry.value = mapEntry.getValue();
            adaptedMap.entry.add(entry);
        }
        return adaptedMap;
    }

}
