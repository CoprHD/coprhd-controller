/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf.collectors;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;

import javax.cim.CIMObjectPath;
import java.util.Collection;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public class AllStorageSyncsInRDFGroupCollector extends AbstractCollector {

    public AllStorageSyncsInRDFGroupCollector(DbClient dbClient, SRDFUtils utils) {
        super(dbClient, utils);
    }

    @Override
    public Collection<CIMObjectPath> collect(StorageSystem provider, Volume targetVolume) {
        return utils.getStorageSynchronizationsInRemoteGroup(provider, targetVolume);
    }
}
