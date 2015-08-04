/*
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

import java.util.Iterator;
import java.util.List;

/**
 * Persist Data object iterator
 */
public abstract class BulkDataObjPersistIterator<T> extends BulkDataObjIterator<T, List<T>> {
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
