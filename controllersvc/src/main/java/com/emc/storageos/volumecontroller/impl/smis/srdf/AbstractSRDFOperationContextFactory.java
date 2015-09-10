/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorFactory;

/**
 * Created by bibbyi1 on 4/9/2015.
 */
public abstract class AbstractSRDFOperationContextFactory {
    protected DbClient dbClient;
    protected SmisCommandHelper helper;
    protected SRDFUtils utils;

    private FindProviderFactory findProviderFactory;
    private CollectorFactory collectorFactory;

    public enum SRDFOperation {
        SUSPEND, SUSPEND_CONS_EXEMPT,
        SPLIT, ESTABLISH, FAIL_OVER, RESTORE, SWAP, FAIL_BACK,
        DELETE_PAIR, DELETE_GROUP_PAIRS, FAIL_MECHANISM, 
        RESET_TO_ADAPTIVE, RESET_TO_SYNC, RESET_TO_ASYNC
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setHelper(SmisCommandHelper helper) {
        this.helper = helper;
    }

    public void setUtils(SRDFUtils utils) {
        this.utils = utils;
    }

    public FindProviderFactory findProviderFactory() {
        if (findProviderFactory == null) {
            findProviderFactory = new FindProviderFactory(dbClient, helper);
        }
        return findProviderFactory;
    }

    public CollectorFactory collectorFactory() {
        if (collectorFactory == null) {
            collectorFactory = new CollectorFactory(dbClient, utils);
        }
        return collectorFactory;
    }

    public abstract SRDFOperationContext build(SRDFOperation operation, Volume target);
}
