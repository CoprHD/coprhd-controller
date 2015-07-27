/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.net.URI;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Data object Query result hit iterator
 */
public abstract class BulkDataObjQueryResultIterator<T> extends BulkDataObjIterator<URI, T> {
    protected Iterator<T> currentIt;

    public BulkDataObjQueryResultIterator(Iterator<URI> resources) {
        super(resources);
        run();
    }

    @Override
    public boolean hasNext() {
        if (currentIt != null && currentIt.hasNext()) {
            return true;
}

        run();
        return currentIt != null;
    }

    @Override
    public T next() {
        if (currentIt == null) {
            throw new NoSuchElementException();
        }
        return currentIt.next();
    }
}
