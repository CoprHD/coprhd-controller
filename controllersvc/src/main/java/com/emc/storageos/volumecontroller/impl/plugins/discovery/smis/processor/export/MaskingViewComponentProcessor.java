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
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * This processor is responsible for processing associated members information for masking views created on array.
 */
public class MaskingViewComponentProcessor extends Processor {
    private Logger logger = LoggerFactory.getLogger(MaskingViewComponentProcessor.class);
    private List<Object> args;
    private DbClient dbClient;
    private static final String MIGRATION_PROJECT = "Migration_Project";
    private static final String MIGRATION_ONLY = "MIGRATION_ONLY";
    private static final String ISCSI_PATTERN = "^(iqn|IQN|eui).*$";

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
            @SuppressWarnings("unchecked")
			Set<String> unmanagedStorageGroupNames = (Set<String>) keyMap.get(Constants.UNMANAGED_MIGRATION_STORAGE_GROUPS);
            // Add all the storage groups to a Project named Migration
            Project project = (Project) keyMap.get(MIGRATION_PROJECT);

            CIMObjectPath lunMaskingView = getObjectPathfromCIMArgument(args);
            maskingViewName = lunMaskingView.getKey(Constants.DEVICEID).getValue().toString();
            logger.info("Processing associated memebers for Masking View {}", maskingViewName);
            BlockConsistencyGroup storageGroup = null;
            StringSet initiators = new StringSet();
            boolean sgCreated = false;
            boolean sgHasUnknownInitiator = false;
            boolean newInitiatorsAreAdded = false;
            while (it.hasNext()) {
                CIMObjectPath associatedInstancePath = it.next();
                if (associatedInstancePath.toString().contains(SmisConstants.SE_DEVICE_MASKING_GROUP)) {
                    String instanceID = associatedInstancePath.getKey(Constants.INSTANCEID).getValue().toString();
                    instanceID = instanceID.replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                    /**
                     * InstandID format: SYMMETRIX+<SERIAL_NUMBER>+<SG_NAME>
                     * All SGs from different arrays will be put under one project. It is
                     * better to have the label format like this.
                     */
                    storageGroup = checkStorageGroupExistsInDB(instanceID, dbClient);
                    if (storageGroup == null) {
                        // This CG cannot be used for provisioning operations currently.
                        sgCreated = true;
                        storageGroup = new BlockConsistencyGroup();
                        storageGroup.setId(URIUtil.createId(BlockConsistencyGroup.class));
                        storageGroup.setLabel(instanceID);
                        storageGroup.addConsistencyGroupTypes(Types.MIGRATION.name());
                        storageGroup.setStorageController(systemId);
                        storageGroup.setMigrationStatus(MigrationStatus.None.name());
                        storageGroup.addSystemConsistencyGroup(systemId.toString(), instanceID);
                        storageGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
                        storageGroup.setTenant(project.getTenantOrg());
                        // set tag for CG
                        ScopedLabelSet tagSet = new ScopedLabelSet();
                        ScopedLabel tagLabel = new ScopedLabel(project.getTenantOrg().getURI().toString(), MIGRATION_ONLY);
                        tagSet.add(tagLabel);
                        storageGroup.setTag(tagSet);
                    } else {
                    	initiators = storageGroup.getInitiators();
                    }
                	storageGroupNames.add(instanceID);
                } else if (associatedInstancePath.toString().contains(SmisConstants.CP_SE_STORAGE_HARDWARE_ID)) {
                    // SE_StorageHardwareID.InstanceID="I-+-iqn.1994-05.com.redhat:xxxx9999"
                    // SE_StorageHardwareID.InstanceID="W-+-10000000FFFFFFFF"
                    String initiatorStr = associatedInstancePath.getKey(Constants.INSTANCEID).getValue().toString()
                            .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                    String initiatorNetworkId = initiatorStr.substring(initiatorStr.lastIndexOf(Constants.PLUS) + 1);
                    logger.info("looking at initiator network id {}", initiatorNetworkId);
                    if (WWNUtility.isValidNoColonWWN(initiatorNetworkId)) {
                        initiatorNetworkId = WWNUtility.getWWNWithColons(initiatorNetworkId);
                        logger.debug("wwn normalized to {}", initiatorNetworkId);
                    } else if (WWNUtility.isValidWWN(initiatorNetworkId)) {
                        initiatorNetworkId = initiatorNetworkId.toUpperCase();
                        logger.debug("wwn normalized to {}", initiatorNetworkId);
                    } else if (initiatorNetworkId.matches(ISCSI_PATTERN)
                            && (iSCSIUtility.isValidIQNPortName(initiatorNetworkId) || iSCSIUtility
                                    .isValidEUIPortName(initiatorNetworkId))) {
                        logger.debug("iSCSI storage port normalized to {}", initiatorNetworkId);
                    } else {
                        logger.warn("this is not a valid FC or iSCSI network id format, skipping.");
                        continue;
                    }

                    // check if a host initiator exists for this id
                    Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, dbClient);
                    if (knownInitiator != null && !initiators.contains(knownInitiator.getId().toString())) {
                        logger.info("Found an initiator ({}) in ViPR for network id {} not in current Initiator list ",
                                knownInitiator.getId(), initiatorNetworkId);
                        if (knownInitiator.checkInternalFlags(Flag.RECOVERPOINT)) {
                        	logger.info("This initiator ({}) is RecoverPoint based",
                                    knownInitiator.getId());
                        	sgHasUnknownInitiator = true;
                        }
                        else if (NetworkUtil.getStoragePort(initiatorNetworkId, dbClient) != null) {
                        	logger.info("This network id ({}) is associated to Storage Port as well (VPLEX)",
                        			initiatorNetworkId);
                        	sgHasUnknownInitiator = true;                        	
                        }
                        initiators.add(knownInitiator.getId().toString());
                        newInitiatorsAreAdded = true;
                    } else {
                        logger.info("No hosts in ViPR found configured for network id {}", initiatorNetworkId);
                        sgHasUnknownInitiator = true;
                    }
                } else {
                    logger.debug("Skipping associator {}", associatedInstancePath.toString());
                }
            }

            //If a single unknown initiator is associated with the Storage Group, we should treat it
            // as a unmanaged Storage Group that is not suitable for Migration..
            if (sgHasUnknownInitiator){
            	//The reason we are adding is to make sure to remove it from the list as part of Storage GroupBookkeepingProceesor
            	unmanagedStorageGroupNames.add(storageGroup.getLabel());
            }
            
            //Create/Update only if the SG does not have unknown initiators and new Initiators are added
            if (storageGroup != null && !sgHasUnknownInitiator && newInitiatorsAreAdded){
            	storageGroup.setInitiators(initiators);
            	if (sgCreated){
            		dbClient.createObject(storageGroup);
            	} else {
            		dbClient.updateObject(storageGroup);
            	}
            }
        } catch (Exception e) {
            logger.error(
                    String.format("Processing associated member information for Masking View %s failed: ", maskingViewName), e);
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
