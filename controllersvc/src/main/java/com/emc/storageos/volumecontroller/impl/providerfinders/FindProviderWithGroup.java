/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.providerfinders;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.net.URI;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public class FindProviderWithGroup implements FindProviderStrategy {
    private static final Logger log = LoggerFactory.getLogger(FindProviderWithGroup.class);

    private DbClient dbClient;
    private SmisCommandHelper helper;
    private Volume target;

    public FindProviderWithGroup(DbClient dbClient, SmisCommandHelper helper, Volume target) {
        this.dbClient = dbClient;
        this.helper = helper;
        this.target = target;
    }

    @Override
    public StorageSystem find() {
        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class, target.getStorageController());
        Volume source = dbClient.queryObject(Volume.class, target.getSrdfParent().getURI());
        StorageSystem sourceSystem = dbClient.queryObject(StorageSystem.class, source.getStorageController());
        URI cgUri = source.getConsistencyGroup();

        if (NullColumnValueGetter.isNullURI(cgUri)) {
            return sourceSystem;
        }

        BlockConsistencyGroup cgObj = dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
        String cgLabel = cgObj.getLabel();
        if (null != cgObj.getAlternateLabel()) {
            cgLabel = cgObj.getAlternateLabel();
        }
        CIMObjectPath groupPath = helper.checkDeviceGroupExists(cgLabel, sourceSystem, sourceSystem);
        if (null == groupPath) {
            log.info("Replication Group {} not available in source Provider {}", cgLabel,
                    sourceSystem.getActiveProviderURI());
            groupPath = helper.checkDeviceGroupExists(cgLabel, targetSystem, targetSystem);
            if (null == groupPath) {
                log.info("Replication Group {} not available in target Provider {}",
                        cgLabel, targetSystem.getActiveProviderURI());
                return null;
            } else {
                log.info("Replication Group {}  available in target Provider {}", cgLabel,
                        targetSystem.getActiveProviderURI());
                return targetSystem;
            }
        }
        log.info("Replication Group {} available in source Provider {}", cgLabel,
                sourceSystem.getActiveProviderURI());
        return sourceSystem;
    }
}
