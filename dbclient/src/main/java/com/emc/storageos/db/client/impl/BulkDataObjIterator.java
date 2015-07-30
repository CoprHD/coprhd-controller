/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Persist Data object Query iterator
 */
abstract class BulkDataObjIterator<T1, T2> implements Iterator<T2> {
    private static final int DEFAULT_BATCH_SIZE = 100;
    protected Iterator<T1> _resourceIt;
    protected List<T1> nextBatch = new ArrayList<T1>(DEFAULT_BATCH_SIZE);

    public BulkDataObjIterator(Iterator<T1> resources) {
        _resourceIt = resources;
    }

    protected abstract void run();

    protected List<T1> getNextBatch() {
        nextBatch.clear();

        for (int i = 0; (i < DEFAULT_BATCH_SIZE) && (_resourceIt.hasNext()); i++) {
            nextBatch.add(_resourceIt.next());
        }
        return nextBatch;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
