/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.Collection;
import java.util.List;

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
 * 
 * This version of the local cache extends DbClientImpl so that all methods of DbClient are valid. Only these methods will result in
 * caching of the returned objects:
 *    queryObject(clazz, id)
 *    queryObject(clazz, ids)
 */
public class DbClientObjectLocalCache extends DbClientImpl {

    private DbClient dbClientCache;
    private ObjectLocalCache cache;
    
    public void init() {
        if (cache == null) {
            this.start();
            cache = new ObjectLocalCache(dbClientCache, true);
        } else {
            throw new IllegalStateException();
        }
    }

    public void setMaxHashSize(int max) {
        if (cache == null) {
            throw new IllegalStateException();
        }
        this.cache.setMaxHashSize(max);
    }

    public int getMaxHashSize() {
        if (cache == null) {
            throw new IllegalStateException();
        }
        return this.cache.getMaxHashSize();
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(boolean enabled) {
        if (cache == null) {
            throw new IllegalStateException();
        }
        this.cache.setEnable(enabled);
    }

    public boolean getEnabled() {
        if (cache == null) {
            throw new IllegalStateException();
        }
        return this.cache.getEnabled();
    }

    @Override
    public <T extends DataObject> T queryObject(Class<T> clazz, URI id) {
        if (cache == null) {
            throw new IllegalStateException();
        }
        return cache.queryObject(clazz, id);
    }

    @Override
    public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids) {
        if (cache == null) {
            throw new IllegalStateException();
        }
        return cache.queryObject(clazz, ids);
    }

    public void clearCache() {
        if (cache == null) {
            throw new IllegalStateException();
        }
        this.cache.clearCache();
    }

    public void clearCache(URI cached) {
        if (cache == null) {
            throw new IllegalStateException();
        }
        this.cache.clearCache(cached);
    }

    public <T extends DataObject> T refresh(Class<T> clazz, URI id) {
        if (cache == null) {
            throw new IllegalStateException();
        }
        return this.cache.refresh(clazz, id);
    }

    /**
     * @param dbClientCache the dbClientCache to set
     */
    public void setDbClientCache(DbClient dbClientCache) {
        this.dbClientCache = dbClientCache;
    }
}
