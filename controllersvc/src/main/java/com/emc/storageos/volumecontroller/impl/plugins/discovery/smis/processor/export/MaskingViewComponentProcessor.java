/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.MigrationStatus;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * This processor is responsible for processing associated Storage Group information for masking views created on array.
 */
public class MaskingViewComponentProcessor extends Processor {
    private Logger logger = LoggerFactory.getLogger(MaskingViewComponentProcessor.class);
    private List<Object> args;
    private DbClient dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        String maskingViewName = null;
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            URI systemId = profile.getSystemId();
            // Storage group names for book-keeping
            @SuppressWarnings("unchecked")
            Set<String> storageGroupNames = (Set<String>) keyMap.get(Constants.MIGRATION_STORAGE_GROUPS);
            CIMObjectPath lunMaskingView = getObjectPathfromCIMArgument(args);
            maskingViewName = lunMaskingView.getKey(Constants.DEVICEID).getValue().toString();
            logger.info("Processing Masking View to get associated storage group: {}", maskingViewName);
            while (it.hasNext()) {
                CIMObjectPath deviceMaskingGroup = it.next();
                String instanceID = deviceMaskingGroup.getKey(Constants.INSTANCEID).getValue().toString();
                instanceID = instanceID.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                BlockConsistencyGroup storageGroup = checkStorageGroupExistsInDB(instanceID, dbClient);
                if (storageGroup == null) {
                    storageGroup = new BlockConsistencyGroup();
                    storageGroup.setId(URIUtil.createId(BlockConsistencyGroup.class));
                    storageGroup.setLabel(instanceID);
                    storageGroup.setAlternateLabel(instanceID); // needed ?
                    storageGroup.addConsistencyGroupTypes(Types.MIGRATION.name());
                    storageGroup.setStorageController(systemId);
                    storageGroup.setMigrationStatus(MigrationStatus.NONE.toString());
                    // storageGroup.addSystemConsistencyGroup(systemId.toString(), instanceID);
                    // TODO project, tenant, vArray, systemConsistencyGroups ?
                    dbClient.createObject(storageGroup);
                } else {
                    // storageGroup.setMigrationStatus(MigrationStatus.NONE.toString()); // TODO see how to get latest migration status
                    dbClient.updateObject(storageGroup);
                }
                storageGroupNames.add(instanceID);
            }
        } catch (Exception e) {
            logger.error(
                    String.format("Processing associated storage group information for Masking View %s failed: ", maskingViewName), e);
        }
    }

    /**
     * Check if Storage Group exists in DB.
     *
     * @param instanceID the label
     * @param dbClient the db client
     * @return BlockConsistencyGroup
     */
    protected BlockConsistencyGroup checkStorageGroupExistsInDB(String instanceID, DbClient dbClient) {
        BlockConsistencyGroup storageGroup = null;
        URIQueryResultList storageGroupResults = new URIQueryResultList();
        dbClient.queryByConstraint(PrefixConstraint.Factory.
                getFullMatchConstraint(BlockConsistencyGroup.class, "label", instanceID),
                storageGroupResults);
        if (storageGroupResults.iterator().hasNext()) {
            storageGroup = dbClient.queryObject(
                    BlockConsistencyGroup.class, storageGroupResults.iterator().next());
        }
        return storageGroup;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        args = inputArgs;
    }
}
