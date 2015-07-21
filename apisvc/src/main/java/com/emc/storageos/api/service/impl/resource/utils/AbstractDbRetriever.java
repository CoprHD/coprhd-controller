/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.util.concurrent.ExecutorService;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;

public abstract class AbstractDbRetriever {
    private static final int DEFAULT_THREAD_COUNT = 10;
    private int queryThreadCount = DEFAULT_THREAD_COUNT;
    // dbClient as a member so it can be injected from Spring context, and
    // also enable init tests injecting a dummy dbClient implementation.
    protected DbClient dbClient = null;
    private ExecutorService dbRetrieverPool = null;

    public void setQueryThreadCount(int queryThreadCount) {
        this.queryThreadCount = queryThreadCount;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected ExecutorService getThreadPool() {
        if (dbRetrieverPool == null)
            dbRetrieverPool = new NamedThreadPoolExecutor(
                    this.getClass().getSimpleName(), queryThreadCount);
        return dbRetrieverPool;
    }
}

