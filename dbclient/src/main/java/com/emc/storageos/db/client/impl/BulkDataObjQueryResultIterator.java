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

import java.net.URI;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.emc.storageos.db.exceptions.DatabaseException;

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
