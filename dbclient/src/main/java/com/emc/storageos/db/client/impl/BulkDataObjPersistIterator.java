/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.Iterator;
import java.util.List;

/**
 * Persist Data object iterator
 */
public abstract class BulkDataObjPersistIterator<T> extends BulkDataObjIterator<T, List<T>>{
    public BulkDataObjPersistIterator(Iterator<T> resources) {
        super(resources);
    }

    @Override
    public List<T> next() {
        getNextBatch();

        return nextBatch;
}

    @Override
    public boolean hasNext() { 
        return _resourceIt.hasNext();
    }
}
