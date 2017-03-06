/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.lang.ref.SoftReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.DataObject;

/**
 * This class should be used instead of DbClient if activity is expected to query DB for the same set of objects.
 * This class is NOT thread safe. The cache should be local with a scope of a single thread.
 * An example where it is appropriate might be in matching and placement activities run by apisvc and also, to a lesser extend by
 * controllers.
 * Activities are moved through a net of methods where each method is likely to query DB for the same set of objects.
 * To avoid OOM, the cache contains SoftReferences to cached DataObjects. Java's GC guarantees to clear them before JVM
 * can go OOM.
 * Cache is not kept in sync with DB. Any update to the database is not visible to the cache.
 * Use methods
 * clearCache(URI cached)
 * or
 * refresh (Class<T> clazz, URI id)
 * to eliminate stale objects from the cache.
 */
public class DbClientObjectLocalCache extends DbClientImpl {

    private int maxHashSize = 50000;

    /**
     * Maps URI/String to a Soft Reference to DataObjects
     * Soft References are guaranteed to get GCed before the process goes OOM
     */
    private final Map<URI, SoftReference<DataObject>> CACHE_MAP = new HashMap<>();

    public DbClientObjectLocalCache(DbClient dbClient) {
        this.dbClient = dbClient;
        enabled = true;
    }

    public DbClientObjectLocalCache(DbClient dbClient, boolean enabled) {
        this.dbClient = dbClient;
        this.enabled = enabled;
    }

    private boolean enabled;

    private DbClient dbClient;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return this.dbClient;
    }

    public void setMaxHashSize(int max) {
        this.maxHashSize = max;
    }

    public int getMaxHashSize() {
        return maxHashSize;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnable(boolean flag) {
        enabled = flag;
    }

    /**
     * Returns thread local map instance
     * 
     * @return
     */
    private Map<URI, SoftReference<DataObject>> getCached() {
        return Collections.unmodifiableMap(CACHE_MAP);
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI id) {
        T obj = getObject(id, clazz);
        if (obj == null) {
            obj = dbClient.queryObject(clazz, id);
            if (obj != null && !obj.getInactive()) {
                putObject(id, obj);
            }
        }
        return obj;
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids) {
        List<URI> missed = new ArrayList<>();
        List<T> result = new ArrayList<>();
        for (URI id : ids) {
            T obj = getObject(id, clazz);
            if (obj == null) {
                missed.add(id);
            }
            else {
                result.add(obj);
            }
        }
        List<T> more = dbClient.queryObject(clazz, missed);
        for (T obj : more) {
            if (!obj.getInactive()) {
                putObject(obj.getId(), obj);
            }
            result.add(obj);
        }
        return result;
    }

    private <T extends DataObject> T getObject(URI id, Class<T> clazz) {
        SoftReference<DataObject> objRef = CACHE_MAP.get(id);
        return objRef == null ? null : clazz.cast(objRef.get());
    }

    private <T extends DataObject> void putObject(URI id, T object) {
        if (enabled && CACHE_MAP.size() < maxHashSize) {
            CACHE_MAP.put(id, new SoftReference<DataObject>(object));
        }
    }

    public void clearCache() {
        CACHE_MAP.clear();
    }

    public void clearCache(URI cached) {
        CACHE_MAP.remove(cached);
    }

    public <T extends DataObject> T refresh(Class<T> clazz, URI id) {
        T obj = dbClient.queryObject(clazz, id);
        if (obj == null) {
            clearCache(id);
        }
        else {
            putObject(id, obj);
        }
        return obj;
    }
}
