/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.GlobalLock;

/**
 * Encapsulate Global Lock
 */
public class GlobalLockType {
    private static final Logger log = LoggerFactory.getLogger(GlobalLockType.class);

    private final Class type = GlobalLock.class;
    private ColumnFamilyDefinition cf;

    /**
     * Constructor
     * 
     * @param clazz
     */
    public GlobalLockType() {
        cf = new ColumnFamilyDefinition(((Cf) type.getAnnotation(Cf.class)).value(),
                ColumnFamilyDefinition.ComparatorType.ByteBuffer);
    }

    /**
     * Get CF for global lock
     * 
     * @return
     */
    public ColumnFamilyDefinition getCf() {
        return cf;
    }
}
