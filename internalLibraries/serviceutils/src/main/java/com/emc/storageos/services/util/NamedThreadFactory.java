/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * This util class generates threads in a pool with a name prefix, for example:
 * dbsvc.
 * 
 * Goal is to identify the pool when it is created by calling service.
 */
public class NamedThreadFactory implements ThreadFactory {
    private String poolPrefix; // name for the pool
    private ThreadFactory factory;

    /**
     * Constructor takes a pool name prefix
     * 
     * @param poolName
     *            - name of the pool as a prefix
     */
    public NamedThreadFactory(String poolName) {
        poolPrefix = poolName;
        this.factory = Executors.defaultThreadFactory();
    }

    /**
     * Constructor takes a pool name prefix and a ThreadFactory
     * 
     * @param poolName
     * @param f
     */
    public NamedThreadFactory(String poolName, ThreadFactory f) {
        poolPrefix = poolName;
        factory = f;
    }

    /**
     * Override the method to create Threads with a formatted name: poolPrefix +
     * id(thread)
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread t = factory.newThread(r);
        if (!t.getName().startsWith(poolPrefix)) {
            t.setName(poolPrefix + "_" + t.getId());
        }
        return t;
    }

    public String getPoolPrefix() {
        return poolPrefix;
    }

}
