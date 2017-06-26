/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.SRDF;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.ConnectivityStatus;
import com.emc.storageos.db.client.model.RemoteDirectorGroup.SupportedCopyModes;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedReplicationTypes;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationMode;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDataClient;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationDataClientImpl;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;

//Processor used in finding out active SRDF RA Groups
public class RemoteConnectivityCollectionProcessor extends StorageProcessor {

    private static final Logger _log = LoggerFactory.getLogger(RemoteConnectivityCollectionProcessor.class);
    private static final String ACTIVE = "Active";
    private static final String CONNECTIVITY_STATUS = "ConnectivityStatus";
    private static final String SYMMETRIX = "SYMMETRIX";
    private static final String TWO = "2";
    private static final String ELEMENT_NAME = "ElementName";

    private DbClient _dbClient;
    private Set<String> raGroupIds = new HashSet<String>();
    private List<RemoteDirectorGroup> newlyAddedGroups = new ArrayList<RemoteDirectorGroup>();
    private List<RemoteDirectorGroup> modifiedGroups = new ArrayList<RemoteDirectorGroup>();

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        StorageSystem device = null;
        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            device = getStorageSystem(_dbClient, profile.getSystemId());
            @SuppressWarnings("unchecked")
            Iterator<CIMInstance> iterator = (Iterator<CIMInstance>) resultObj;
            Set<String> remoteConnectedStorageSystems = new HashSet<String>();
            boolean srdfSupported = false;
            while (iterator.hasNext()) {
                srdfSupported = true;
                CIMInstance instance = iterator.next();
                RemoteDirectorGroup remoteRAGroup = checkRAGroupExistsInDB(_dbClient, instance);
                remoteRAGroup = createRAGroup(instance, remoteRAGroup, device);
                raGroupIds.add(remoteRAGroup.getNativeGuid());
                addRemoteConnectedStorageSystems(instance, device, remoteConnectedStorageSystems);
                addPath(keyMap, operation.getResult(), instance.getObjectPath());
            }

            updateSupportedCopyModes(srdfSupported, device);
            updateRemoteConnectedStorageSystems(device, remoteConnectedStorageSystems);
            _dbClient.updateObject(device);

            if (!newlyAddedGroups.isEmpty()) {
                _dbClient.createObject(newlyAddedGroups);
            }

            if (!modifiedGroups.isEmpty()) {
                _dbClient.updateObject(modifiedGroups);
            }

            performRAGroupsBookKeeping(raGroupIds, device.getId());
            
            processRemoteReplicationObjects(device);

        } catch (Exception e) {
            _log.error("Finding out Active RA Groups Failed.SRDF will not be supported on this Array {} ", device.getNativeGuid(), e);

        } finally {
            raGroupIds = null;
            newlyAddedGroups = null;
            modifiedGroups = null;
        }

    }

    private void updateSupportedCopyModes(boolean srdfSupported, StorageSystem device) {
        if (srdfSupported) {
            StringSet replicationModes = new StringSet();
            replicationModes.add(SupportedReplicationTypes.SRDF.toString());
            replicationModes.add(SupportedReplicationTypes.LOCAL.toString());
            if (checkSupportedSRDFActiveModeProvider(device)) {
                replicationModes.add(SupportedReplicationTypes.SRDFMetro.toString());
            }
            device.setSupportedReplicationTypes(replicationModes);
        }
    }

    private boolean checkSupportedSRDFActiveModeProvider(StorageSystem storageSystem) {
        if (storageSystem.checkIfVmax3() && storageSystem.getUsingSmis80()) {
            try {
                StorageProvider storageProvider = _dbClient.queryObject(StorageProvider.class, storageSystem.getActiveProviderURI());
                String providerVersion = storageProvider.getVersionString();
                String versionSubstring = providerVersion.split("\\.")[1];
                return (Integer.parseInt(versionSubstring) >= 2);
            } catch (Exception e) {
                _log.error("Exception get provider version for the storage system {} {}.", storageSystem.getLabel(),
                        storageSystem.getId());
                return false;
            }
        } else {
            return false;
        }
    }

    private void updateRemoteConnectedStorageSystems(StorageSystem device,
            Set<String> remoteConnectedStorageSystems) {

        if (null == device.getRemotelyConnectedTo()
                || device.getRemotelyConnectedTo().isEmpty()) {
            device.setRemotelyConnectedTo(new StringSet(remoteConnectedStorageSystems));
        } else {
            device.getRemotelyConnectedTo().replace(remoteConnectedStorageSystems);
        }

    }

    /**
     * if the RAGroup had been deleted from the Array, the rediscovery cycle should set the RAGroup to inactive.
     * 
     * @param policyNames
     * @param storageSystemURI
     * @throws IOException
     */
    private void performRAGroupsBookKeeping(Set<String> raGroupIds, URI storageSystemURI)
            throws IOException {
        @SuppressWarnings("deprecation")
        List<URI> raGroupsInDB = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceRemoteGroupsConstraint(storageSystemURI));
        for (URI raGroupUri : raGroupsInDB) {
            RemoteDirectorGroup raGroup = _dbClient.queryObject(
                    RemoteDirectorGroup.class, raGroupUri);
            if (null == raGroup || raGroup.getInactive()) {
                continue;
            }
            if (!raGroupIds.contains(raGroup.getNativeGuid())) {
                _log.info("RA Group set to inactive", raGroup);
                raGroup.setSourceStorageSystemUri(NullColumnValueGetter.getNullURI());
                raGroup.setInactive(true);
                _dbClient.updateAndReindexObject(raGroup);
            }

        }
    }

    private RemoteDirectorGroup createRAGroup(CIMInstance instance, RemoteDirectorGroup raGroup, StorageSystem system) {
        boolean newRAGroup = false;
        if (null == raGroup) {
            newRAGroup = true;
            raGroup = new RemoteDirectorGroup();
            raGroup.setId(URIUtil.createId(RemoteDirectorGroup.class));
            raGroup.setNativeGuid(NativeGUIDGenerator
                    .generateRAGroupNativeGuid(instance));
            raGroup.setSourceGroupId(getSourceGroupId(system, instance));
            raGroup.setRemoteGroupId(getRemoteGroupId(system, instance));

        }
        // moved outside, as during 1st time discovery, if the remote system was not detected,
        // we could end up in not updating this field during re-discovery, even though the remote system had been detected in ViPR later.
        raGroup.setSourceStorageSystemUri(system.getId());
        raGroup.setRemoteStorageSystemUri(getRemoteConnectedSystemURI(system, instance));
        raGroup.setLabel(getCIMPropertyValue(instance, ELEMENT_NAME));
        raGroup.setActive(Boolean.parseBoolean(getCIMPropertyValue(instance, ACTIVE)));
        raGroup.setConnectivityStatus(ConnectivityStatus
                .getConnectivityStatus(getCIMPropertyValue(instance, CONNECTIVITY_STATUS)));
        if (newRAGroup) {
            newlyAddedGroups.add(raGroup);
        } else {
            modifiedGroups.add(raGroup);
        }
        return raGroup;
    }

    private String getSourceGroupId(StorageSystem system, CIMInstance instance) {
        String instanceId = (String) instance.getPropertyValue(Constants.INSTANCEID);
        String[] idArray = instanceId.split(Constants.PATH_DELIMITER_REGEX);
        String sourceGroupId = null;
        if (system.getUsingSmis80()) {
            if (system.getNativeGuid().contains(idArray[1])) {
                sourceGroupId = idArray[2];
            } else {
                sourceGroupId = idArray[4];
            }
        } else {
            if (system.getNativeGuid().contains(idArray[3])) {
                sourceGroupId = idArray[4];
            } else {
                sourceGroupId = idArray[6];
            }
        }
        _log.info("Generated Source Group Id {} from Instance ID {}", sourceGroupId, instanceId);
        return sourceGroupId;
    }

    private String getRemoteGroupId(StorageSystem system, CIMInstance instance) {
        String instanceId = (String) instance.getPropertyValue(Constants.INSTANCEID);
        String[] idArray = instanceId.split(Constants.PATH_DELIMITER_REGEX);
        String remoteGroupId = null;
        if (system.getUsingSmis80()) {
            if (system.getNativeGuid().contains(idArray[1])) {
                remoteGroupId = idArray[4];
            } else {
                remoteGroupId = idArray[2];
            }
        } else {
            if (system.getNativeGuid().contains(idArray[3])) {
                remoteGroupId = idArray[6];
            } else {
                remoteGroupId = idArray[4];
            }
        }
        _log.info("Generated Remote Group Id {} from Instance ID {}", remoteGroupId, instanceId);
        return remoteGroupId;
    }

    private URI getRemoteConnectedSystemURI(StorageSystem system, CIMInstance instance) {
        String instanceId = (String) instance.getPropertyValue(Constants.INSTANCEID);
        String[] idArray = instanceId.split(Constants.PATH_DELIMITER_REGEX);
        String remoteArrayNativeGuid = null;
        if (system.getUsingSmis80()) {
            if (system.getNativeGuid().contains(idArray[1])) {
                remoteArrayNativeGuid = SYMMETRIX + Constants.PLUS + idArray[3];
            } else {
                remoteArrayNativeGuid = SYMMETRIX + Constants.PLUS + idArray[1];
            }
        } else {
            if (system.getNativeGuid().contains(idArray[3])) {
                remoteArrayNativeGuid = SYMMETRIX + Constants.PLUS + idArray[5];
            } else {
                remoteArrayNativeGuid = SYMMETRIX + Constants.PLUS + idArray[3];
            }
        }
        _log.debug("Remote Array Native Guid {}", remoteArrayNativeGuid);
        List<URI> remoteSystemuris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageSystemByNativeGuidConstraint(remoteArrayNativeGuid));
        if (!remoteSystemuris.isEmpty()) {
            return remoteSystemuris.get(0);
        }
        return NullColumnValueGetter.getNullURI();

    }

    private void addRemoteConnectedStorageSystems(CIMInstance instance,
            StorageSystem system, Set<String> remoteConnectedStorageSystems) {
        // there is no other field which gives direct values, hence need to use
        // split
        // Format : SYMMETRIX+000195701573+NAME+000195701505+27+000195701573+27
        boolean isActive = Boolean.parseBoolean(instance.getPropertyValue(ACTIVE).toString());
        String connectivityStatus = instance.getPropertyValue(CONNECTIVITY_STATUS).toString();

        if (isActive && TWO.equalsIgnoreCase(connectivityStatus)) {
            URI remoteSystemUri = getRemoteConnectedSystemURI(system, instance);
            if (null != remoteSystemUri) {
                remoteConnectedStorageSystems.add(remoteSystemUri.toString());
            }

        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

    /**
     * Utility method that would Create a RemoteReplicationSet between a StorageSystem and its Remote counterpart
     * 
     * @param storageSystem to storageSystem
     * @param remoteSystem to remoteSystem
     * @return RemoteReplicationSet
     */
    private RemoteReplicationSet createRemoteReplicationSet(StorageSystem storageSystem, StorageSystem remoteSystem) {

        Set<RemoteReplicationSet.ElementType> supportedElementTypes = new HashSet<>();
        supportedElementTypes.add(RemoteReplicationSet.ElementType.REPLICATION_GROUP);
        supportedElementTypes.add(RemoteReplicationSet.ElementType.REPLICATION_PAIR);

        HashSet<RemoteReplicationSet.ReplicationRole> replicationRoles = new HashSet<>();
        replicationRoles.add(RemoteReplicationSet.ReplicationRole.SOURCE);
        replicationRoles.add(RemoteReplicationSet.ReplicationRole.TARGET);

        // TODO: VMAX Does not enforce consistency but ViPR maintains consistency for the Groups? Should
        // isGroupConsistencyEnforcedAutomatically be set to TRUE?

        Set<RemoteReplicationMode> SRDFReplicationModes = new HashSet<>();
        SRDFReplicationModes.add(new RemoteReplicationMode(SupportedCopyModes.SYNCHRONOUS.name(), false, false));
        SRDFReplicationModes.add(new RemoteReplicationMode(SupportedCopyModes.ASYNCHRONOUS.name(), false, false));
        SRDFReplicationModes.add(new RemoteReplicationMode(SupportedCopyModes.ADAPTIVECOPY.name(), false, false));
        if (null != storageSystem.getSupportedReplicationTypes()
                && storageSystem.getSupportedReplicationTypes().contains(SupportedReplicationTypes.SRDFMetro.toString()) &&
                null != remoteSystem.getSupportedReplicationTypes()
                && remoteSystem.getSupportedReplicationTypes().contains(SupportedReplicationTypes.SRDFMetro.toString())) {
            SRDFReplicationModes.add(new RemoteReplicationMode(SupportedCopyModes.ACTIVE.name(), false, false));
        }

        List<StorageSystem> storageSystems = Arrays.asList(storageSystem, remoteSystem);
        String labelFormat = RemoteReplicationUtils.getRemoteReplicationSetNativeIdForSrdfSet(storageSystems);
        RemoteReplicationSet rrSet = new RemoteReplicationSet();
        rrSet.setDeviceLabel(labelFormat);
        rrSet.setNativeId(labelFormat);
        rrSet.setSupportedElementTypes(supportedElementTypes);
        rrSet.setReplicationLinkGranularity(supportedElementTypes);
        rrSet.setReplicationState("UNKNOWN");
        rrSet.setSupportedReplicationModes(SRDFReplicationModes);
        Map<String, Set<RemoteReplicationSet.ReplicationRole>> systemMapSet = new HashMap<>();
        systemMapSet.put(storageSystem.getSerialNumber(), replicationRoles);
        systemMapSet.put(remoteSystem.getSerialNumber(), replicationRoles);
        rrSet.setSystemMap(systemMapSet);

        return rrSet;
    }

    /**
     * Utility method that would Create a RemoteReplicationGroup for a given RemoteDirectorGroup
     * 
     * @param raGroup  RemoteDirectorGroup/RAGroup
     * @param storageSystem local storageSystem
     * @param remoteSystem  remote System
     * @return RemoteReplicationGroup
     */
    private RemoteReplicationGroup createRemoteReplicationGroup(RemoteDirectorGroup raGroup, StorageSystem storageSystem,
            StorageSystem remoteSystem) {
        RemoteReplicationGroup rrGroup = new RemoteReplicationGroup();
        rrGroup.setNativeId(RemoteReplicationUtils.getRemoteReplicationGroupNativeIdForSrdfGroup(storageSystem, remoteSystem, raGroup));
        rrGroup.setDisplayName(raGroup.getLabel() + ": " + storageSystem.getSerialNumber() + " --> " + remoteSystem.getSerialNumber());
        rrGroup.setDeviceLabel(raGroup.getLabel());
        // Need to figure out how to capture this from an RDF group if it has associated CGs
        rrGroup.setIsGroupConsistencyEnforced(false);
        rrGroup.setReplicationMode(raGroup.getSupportedCopyMode());
        // Need to figure out how to capture this from an RDF group. Is it the state on the links or the Connectivity Status
        rrGroup.setReplicationState(raGroup.getConnectivityStatus());
        rrGroup.setSourceSystemNativeId(storageSystem.getSerialNumber());
        rrGroup.setTargetSystemNativeId(remoteSystem.getSerialNumber());
        return rrGroup;
    }
    
    /**
     * Processes the RemoteReplicationSets and RemoteReplicationGroup needs for RemoteReplicationDataClient discovery
     * 
     * @param sourceSystem storage system
     * @return NONE
     */

    private void processRemoteReplicationObjects(StorageSystem sourceSystem) {
        List<RemoteReplicationSet> replicationSets = new ArrayList<RemoteReplicationSet>();
        List<RemoteReplicationGroup> replicationGroups = new ArrayList<RemoteReplicationGroup>();
        for (String remoteSystemUri : sourceSystem.getRemotelyConnectedTo()) {
            if (NullColumnValueGetter.isNullValue(remoteSystemUri)) {
                continue;
            }
            StorageSystem remoteSystem = _dbClient.queryObject(StorageSystem.class,
                    URI.create(remoteSystemUri));
            if (remoteSystem != null) {
                // Deal with RRSet
                replicationSets.add(createRemoteReplicationSet(sourceSystem, remoteSystem));
                // Deal with RRGroups...
                URIQueryResultList raGroupsInDB = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceRemoteGroupsConstraint(sourceSystem.getId()), raGroupsInDB);
                Iterator<RemoteDirectorGroup> raGroupIter = _dbClient.queryIterativeObjects(RemoteDirectorGroup.class, raGroupsInDB);
                while (raGroupIter.hasNext()) {
                    RemoteDirectorGroup raGroup = raGroupIter.next();
                    if (raGroup != null && !raGroup.getInactive()
                            && remoteSystem.getId().equals(raGroup.getRemoteStorageSystemUri())) {
                        replicationGroups.add(createRemoteReplicationGroup(raGroup, sourceSystem, remoteSystem));
                    }
                }
            }
        }
        _log.info("Processing RemoteReplicationSets and RemoteReplication Groups for {}", sourceSystem.getSerialNumber());
        RemoteReplicationDataClient remoteReplicationDataClient = new RemoteReplicationDataClientImpl(_dbClient);
        remoteReplicationDataClient.processRemoteReplicationSetsForStorageSystem(sourceSystem, replicationSets);
        remoteReplicationDataClient.processRemoteReplicationGroupsForStorageSystem(sourceSystem, replicationGroups);
    }

}
