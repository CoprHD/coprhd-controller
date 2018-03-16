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
import com.emc.storageos.db.client.model.StringSet;
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

    @SuppressWarnings("unchecked")
	@Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            URI systemId = profile.getSystemId();
            Set<String> discoveredStorageGroups = (Set<String>) keyMap.get(Constants.MIGRATION_STORAGE_GROUPS);
			Set<String> storageGroupsWithUnmanagedInitiators = (Set<String>) keyMap.get(Constants.UNMANAGED_MIGRATION_STORAGE_GROUPS);
            Map<String, StringSet> storageGroupToInitiatorMapping = (Map<String, StringSet>) keyMap
                    .get(Constants.MIGRATION_STORAGE_GROUPS_TO_INITATOR_MAPPING);            
            List<BlockConsistencyGroup> inactiveStorageGroups = new ArrayList<>();
            List<BlockConsistencyGroup> storageGroupsTobeUpdated = new ArrayList<>();

            URIQueryResultList storageGroupURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceBlockConsistencyGroupConstraint(systemId),
                    storageGroupURIs);
            Iterator<URI> storageGroupItr = storageGroupURIs.iterator();
            while (storageGroupItr.hasNext()) {
                URI storageGroupURI = storageGroupItr.next();
                BlockConsistencyGroup storageGroupInDB = dbClient.queryObject(BlockConsistencyGroup.class, storageGroupURI);
                String sgLabel = storageGroupInDB.getLabel();
                //Remove SG's from the DB if they are not part of recent discover and if they have unmanaged initiators
                if (storageGroupInDB.getTypes().contains(Types.MIGRATION.name())
                        && (!discoveredStorageGroups.contains(sgLabel) || storageGroupsWithUnmanagedInitiators.contains(sgLabel))) {
                    logger.info("Storage group {} not found on array or has unmanaged initiators. Migration status {}",
                            sgLabel, storageGroupInDB.getMigrationStatus());
                    // Only remove if the MigrationStatus is NONE
                    if (MigrationStatus.None.name().equalsIgnoreCase(storageGroupInDB.getMigrationStatus())) {
                        logger.info("Marking storage group {} inactive as its migration status is NONE", sgLabel);
                        inactiveStorageGroups.add(storageGroupInDB);
                    }
                } else if(storageGroupInDB.getTypes().contains(Types.MIGRATION.name()) && storageGroupToInitiatorMapping.containsKey(storageGroupInDB.getLabel())){
                	StringSet dbInitiators = storageGroupInDB.getInitiators();
               		StringSet discoveredInitiators = storageGroupToInitiatorMapping.get(storageGroupInDB.getLabel());
               		if ((discoveredInitiators.size() != dbInitiators.size()) || (!discoveredInitiators.containsAll(dbInitiators))){
               			storageGroupInDB.setInitiators(discoveredInitiators);
               			storageGroupsTobeUpdated.add(storageGroupInDB);               			
               		}              	                	
                }
            }
            if (!inactiveStorageGroups.isEmpty()) {
                dbClient.markForDeletion(inactiveStorageGroups);
            }
            if (!storageGroupsTobeUpdated.isEmpty()){
                dbClient.updateObject(storageGroupsTobeUpdated);
            }
        } catch (Exception e) {
            logger.error("Exception caught while trying to run bookkeeping on VMAX storage groups", e);
        }
    }
}
