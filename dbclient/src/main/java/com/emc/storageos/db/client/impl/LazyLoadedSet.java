/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringSet;

/**
 * @author cgarber
 * 
 */
public class LazyLoadedSet<E extends DataObject> extends LazyLoadedCollection<E> implements Set<E> {

    /**
     * @param name
     * @param parentObj
     * @param lazyLoader
     * @param mappedBy
     */
    public LazyLoadedSet(String name, E parentObj, LazyLoader lazyLoader, StringSet mappedBy) {
        super(name, parentObj, lazyLoader, mappedBy);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.client.constraint.LazyLoadedCollection#getNewCollection()
     */
    @Override
    protected Collection<E> getNewCollection() {
        return new HashSet<E>();
    }

}
