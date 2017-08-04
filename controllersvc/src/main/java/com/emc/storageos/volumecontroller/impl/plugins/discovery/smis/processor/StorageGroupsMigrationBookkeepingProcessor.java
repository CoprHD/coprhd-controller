/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.MigrationStatus;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.MaskingViewComponentProcessor;

/**
 * This processor responsibility is to delete the storage groups in the database that are deleted on the array.
 *
 */
public class StorageGroupsMigrationBookkeepingProcessor extends MaskingViewComponentProcessor {
    private Logger logger = LoggerFactory.getLogger(StorageGroupsMigrationBookkeepingProcessor.class);
    private DbClient dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            URI systemId = profile.getSystemId();
            @SuppressWarnings("unchecked")
            Set<String> discoveredStorageGroups = (Set<String>) keyMap.get(Constants.MIGRATION_STORAGE_GROUPS);
            List<BlockConsistencyGroup> inactiveStorageGroups = new ArrayList<>();

            URIQueryResultList storageGroupURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceBlockConsistencyGroupConstraint(systemId),
                    storageGroupURIs);
            Iterator<URI> storageGroupItr = storageGroupURIs.iterator();
            while (storageGroupItr.hasNext()) {
                URI storageGroupURI = storageGroupItr.next();
                BlockConsistencyGroup storageGroupInDB = dbClient.queryObject(BlockConsistencyGroup.class, storageGroupURI);
                String sgLabel = storageGroupInDB.getLabel();
                if (storageGroupInDB.getTypes().contains(Types.MIGRATION.name())
                        && !discoveredStorageGroups.contains(sgLabel)) {
                    logger.info("Storage group {} not found on array. Migration status {}",
                            sgLabel, storageGroupInDB.getMigrationStatus());
                    // Only remove if the MigrationStatus is NONE
                    if (MigrationStatus.None.name().equalsIgnoreCase(storageGroupInDB.getMigrationStatus())) {
                        logger.info("Marking storage group {} inactive as its migration status is NONE", sgLabel);
                        inactiveStorageGroups.add(storageGroupInDB);
                    }
                }
            }
            if (!inactiveStorageGroups.isEmpty()) {
                dbClient.markForDeletion(inactiveStorageGroups);
            }
        } catch (Exception e) {
            logger.error("Exception caught while trying to run bookkeeping on VMAX storage groups", e);
        }
    }
}
