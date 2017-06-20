package com.emc.storageos.api.service.impl.resource.unmanaged;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.DataObjectUtils;

public class ReportInfoCache {

    private static Logger _log = LoggerFactory.getLogger(ReportInfoCache.class);
    DbClient _dbClient = null;
    Map<URI, StoragePool> _storagePoolMap;
    Map<URI, StorageSystem> _storageSystemsMap;
    Map<URI, VirtualPool> _virtualPoolsMap;
    Map<URI, UnManagedExportMask> _unmanagedExportMasksMap;

    ReportInfoCache(DbClient dbClient) {
        this._dbClient = dbClient;
        init();
    }

    @SuppressWarnings("unchecked")
    private void init() {

        _log.info("initiatlizing ReportInfoCache");

        List<StoragePool> storagePools = (List<StoragePool>) getDataObjects(StoragePool.class, _dbClient);
        _storagePoolMap = DataObjectUtils.toMap(storagePools);

        List<StorageSystem> storageSystems = (List<StorageSystem>) getDataObjects(StorageSystem.class, _dbClient);
        _storageSystemsMap = DataObjectUtils.toMap(storageSystems);

        List<VirtualPool> virtualPools = (List<VirtualPool>) getDataObjects(VirtualPool.class, _dbClient);
        _virtualPoolsMap = DataObjectUtils.toMap(virtualPools);

        List<UnManagedExportMask> unmanagedExportMasks = (List<UnManagedExportMask>) getDataObjects(UnManagedExportMask.class, _dbClient);
        _unmanagedExportMasksMap = DataObjectUtils.toMap(unmanagedExportMasks);

    }

    public List<? extends DataObject> getDataObjects(Class<? extends DataObject> T, DbClient dbClient) {
        List<URI> ids = dbClient.queryByType(T, true);
        List<? extends DataObject> dataObjects = dbClient.queryObject(T, ids);
        return dataObjects;
    }

    public StoragePool getStoragePool(URI uri) {
        return _storagePoolMap.get(uri);
    }

    public String getStoragePoolName(URI uri) {
        StoragePool object = getStoragePool(uri);
        return object != null ? object.getPoolName() : uri.toString();
    }

    public StorageSystem getStorageSystem(URI uri) {
        return _storageSystemsMap.get(uri);
    }

    public String getStorageSystemName(URI uri) {
        StorageSystem object = getStorageSystem(uri);
        return object != null ? object.getLabel() : uri.toString();
    }

    public VirtualPool getVirtualPool(URI uri) {
        return _virtualPoolsMap.get(uri);
    }

    public String getVirtualPoolName(URI uri) {
        VirtualPool object = getVirtualPool(uri);
        return object != null ? object.getLabel() : uri.toString();
    }

    public UnManagedExportMask getUnManagedExportMask(URI uri) {
        return _unmanagedExportMasksMap.get(uri);
    }

    public String getUnManagedExportMaskName(URI uri) {
        UnManagedExportMask object = getUnManagedExportMask(uri);
        return object != null ? object.getMaskName() : uri.toString();
    }

    private Map<String, Map<String, Set<String>>> umvTree = null;
    
    
    public String renderUnManagedVolumeTree() {
        StringBuilder builder = new StringBuilder();
        if (umvTree != null) {
            builder.append("<ul>");
            for (Entry<String, Map<String, Set<String>>> storageArrayEntry : umvTree.entrySet()) {
                if (!storageArrayEntry.getValue().isEmpty()) {
                    builder.append("<li>Storage Array:" + storageArrayEntry.getKey() + "<ul>");
                    for (Entry<String, Set<String>> storagePoolEntry : storageArrayEntry.getValue().entrySet()) {
                        if (!storagePoolEntry.getValue().isEmpty()) {
                            builder.append("<li>Storage Pool: " + storagePoolEntry.getKey() + "<ul>");
                            for (String umvEntry : storagePoolEntry.getValue()) {
                                builder.append(umvEntry);
                            }
                            builder.append("</ul></li>");
                        }
                    }
                    builder.append("</li></ul>");
                }
            }
            builder.append("</ul>");
        }
        return builder.toString();
    }
    
    public void mapUnManagedVolume(UnManagedVolume umv) {
        if (umvTree == null) {
            umvTree = new TreeMap<String, Map<String, Set<String>>>();
        }
        String umvStorageArrayName = getStorageSystemName(umv.getStorageSystemUri());
        if (umvStorageArrayName != null) {
            Map<String, Set<String>> arrayTree = umvTree.get(umvStorageArrayName);
            if (arrayTree == null) {
                arrayTree = new TreeMap<String, Set<String>>();
                umvTree.put(umvStorageArrayName, arrayTree);
            }
            String storagePoolName = "No Storage Pool";
            if (URIUtil.isValid(umv.getStoragePoolUri())) {
                storagePoolName = getStoragePoolName(umv.getStoragePoolUri());
            }
            if (storagePoolName != null) {
                Set<String> umvsInPool = arrayTree.get(storagePoolName);
                if (umvsInPool == null) {
                    umvsInPool = new TreeSet<String>();
                    arrayTree.put(storagePoolName, umvsInPool);
                }
                
                String uri = String.format(Templates.URI_UMV, umv.getId());
                umvsInPool.add(String.format(Templates.TEMPLATE_LI_LINK_SORTABLE, umv.getLabel(), uri, umv.getLabel()));
            }
        } else {
            _log.warn("storage system not found for URI: " + umv.getStorageSystemUri());
        }
    }
    
}
