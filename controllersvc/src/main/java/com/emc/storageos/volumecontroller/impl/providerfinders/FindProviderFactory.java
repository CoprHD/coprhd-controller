/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.providerfinders;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

/**
 * Created by bibbyi1 on 4/17/2015.
 */
public class FindProviderFactory {
    private DbClient dbClient;
    private SmisCommandHelper helper;
    private CIMObjectPathFactory cimPathFactory;

    public FindProviderFactory(DbClient dbClient, SmisCommandHelper helper) {
        this.dbClient = dbClient;
        this.helper = helper;
    }

    public FindProviderFactory(DbClient dbClient, SmisCommandHelper helper, CIMObjectPathFactory cimPathFactory) {
        this.dbClient = dbClient;
        this.helper = helper;
        this.cimPathFactory = cimPathFactory;
    }

    public FindProviderStrategy withGroup(Volume target) {
        return new FindProviderWithGroup(dbClient, helper, target);
    }

    public FindProviderStrategy anyReachable(Volume target) {
        return new FindReachableProvider(dbClient, helper, target);
    }

    /**
     * FindProviderStrategyByCG returns
     * storage system (as is) if CG is found on its active provider,
     * or storage system (updated with provider info) if CG is found on its passive provider,
     * or null (if CG is not found on any of its provider).
     */
    public FindProviderStrategy withGroup(StorageSystem system, String groupName) {
        return new FindProviderStrategyByCG(dbClient, system, groupName, helper, cimPathFactory);
    }
}
