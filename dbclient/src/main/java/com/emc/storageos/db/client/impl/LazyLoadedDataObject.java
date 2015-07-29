/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.db.client.model.DataObject;

/**
 * @author cgarber
 * 
 */
public class LazyLoadedDataObject<E extends DataObject> implements DataObjectInstrumented<E> {

    private Set<String> loaded = new HashSet<String>();
    private LazyLoader loader;
    private boolean lazyLoadingEnabled;

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.impl.DataObjectInstrumented#setLazyLoader(com.emc.storageos.db.client.impl.LazyLoader)
     */
    @Override
    public synchronized void initLazyLoading(LazyLoader loader) {
        this.loader = loader;
        lazyLoadingEnabled = false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.impl.DataObjectInstrumented#enableLazyLoading()
     */
    @Override
    public synchronized void enableLazyLoading() {
        lazyLoadingEnabled = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.impl.DataObjectInstrumented#load()
     */
    @Override
    public synchronized void load(String lazyLoadedFieldName, DataObject obj) {
        if (lazyLoadingEnabled && !loaded.contains(lazyLoadedFieldName)) {
            lazyLoadingEnabled = false;
            loader.load(lazyLoadedFieldName, obj);
            loaded.add(lazyLoadedFieldName);
            lazyLoadingEnabled = true;
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.impl.DataObjectInstrumented#refreshMappedByField(java.lang.String,
     * com.emc.storageos.db.client.model.DataObject)
     */
    @Override
    public synchronized void refreshMappedByField(String lazyLoadedFieldName, DataObject obj) {
        if (lazyLoadingEnabled) {
            lazyLoadingEnabled = false;
            loader.refreshMappedByField(lazyLoadedFieldName, obj);
            lazyLoadingEnabled = true;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.impl.DataObjectInstrumented#invalidate(java.lang.String,
     * com.emc.storageos.db.client.model.DataObject)
     */
    @Override
    public synchronized void invalidate(String lazyLoadedFieldName) {
        loaded.remove(lazyLoadedFieldName);
    }
}
