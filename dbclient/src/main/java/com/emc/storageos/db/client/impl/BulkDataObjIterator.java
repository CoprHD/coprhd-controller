/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.net.URI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.exceptions.DatabaseException;

/**
 * Persist Data object Query iterator
 */
abstract class BulkDataObjIterator<T1, T2> implements Iterator<T2> {
    private static final Logger log = LoggerFactory.getLogger(BulkDataObjIterator.class);

    private final int DEFAULT_BATCH_SIZE = 100;
    protected Iterator<T1> _resourceIt;
    protected List<T1> nextBatch = new ArrayList<T1>(DEFAULT_BATCH_SIZE);

    public BulkDataObjIterator(Iterator<T1> resources) {
        _resourceIt = resources;
    }

    protected abstract void run() throws DatabaseException;

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
