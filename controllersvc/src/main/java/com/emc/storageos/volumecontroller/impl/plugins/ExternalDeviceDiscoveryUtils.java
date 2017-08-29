/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationMode;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

public class ExternalDeviceDiscoveryUtils {

    private static Logger _log = LoggerFactory.getLogger(ExternalDeviceDiscoveryUtils.class);

    /**
     * Process remote replication set discovered by driver.
     *
     * @param driverSet  driver remote replication set
     * @param objectsToCreate   new objects to create in database
     * @param objectsToUpdate   existing objects to update in database
     * @param storageSystemType storage type
     * @return system remote replication set
     */
    public static com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet
    processDriverRRSet(RemoteReplicationSet driverSet,
                       List<DataObject> objectsToCreate, List<DataObject> objectsToUpdate, String storageSystemType, DbClient dbClient) {

        // check if this replication set is already in database
        String nativeGuid = NativeGUIDGenerator.generateRemoteReplicationSetNativeGuid(storageSystemType,
                driverSet.getNativeId());
        _log.info("Processing replication set {}.", nativeGuid);

        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemSet =
                checkRemoteReplicationSetExistsInDB(nativeGuid, dbClient);
        if (systemSet == null) {
            _log.info("Replication set {} does not exist in database, we will create a new system replication set for it.", nativeGuid);
            systemSet = new com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet();
            systemSet.setId(URIUtil.createId(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class));
            systemSet.setIsDriverManaged(true);
            systemSet.setNativeGuid(nativeGuid);
            systemSet.setStorageSystemType(storageSystemType);
            systemSet.setNativeId(driverSet.getNativeId());
            prepareSystemRemoteReplicationSet(driverSet, storageSystemType, systemSet, dbClient);
            objectsToCreate.add(systemSet);
        } else {
            // replication set already exists --- update with the latest discovery data
            _log.info("Replication set {} already exists in database, we will update this set in db.", nativeGuid);
            prepareSystemRemoteReplicationSet(driverSet, storageSystemType, systemSet, dbClient);
            objectsToUpdate.add(systemSet);
        }
        return systemSet;
    }
    
    /**
     * set the field connectedTo on the db storage system object corresponding to the system set source systems
     * 
     * @param systemSet
     * @param objectsToUpdate
     * @param dbClient
     */
    public static void setStorageSystemConnectedTo(List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet> systemSets, 
            List<DataObject> objectsToUpdate, DbClient dbClient, String storageSystemType) {
        
        Map<URI, StorageSystem> updatedStorageSystems = new HashMap<URI, StorageSystem>();
        
        // list of storage system of type storageSystemType
        List<StorageSystem> dbStorageSystemsSystemType = new ArrayList<StorageSystem>();
        Iterator<StorageSystem> systemsItr = dbClient.queryIterativeObjectFields(StorageSystem.class, Arrays.asList("connectedTo", "systemType"), dbClient.queryByType(StorageSystem.class, true));
        while(systemsItr.hasNext()) {
            StorageSystem system = systemsItr.next();
            if (system.getSystemType().equals(storageSystemType)) {
                dbStorageSystemsSystemType.add(system);
            }
        }
        
        // map of source storage system URI to list of targets from the environment
        Map<URI, StringSet> envStorageSystemConnectedToMap = new HashMap<URI, StringSet>();
        for (com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemSet : systemSets) {
            if (systemSet.getSourceSystems() != null && !systemSet.getSourceSystems().isEmpty()) {
                for (String srcSystemId : systemSet.getSourceSystems()) {
                    if (systemSet.getTargetSystems() != null && !systemSet.getTargetSystems().isEmpty()) {
                        for (String tgtSystemId : systemSet.getTargetSystems()) {
                            if (!tgtSystemId.equalsIgnoreCase(srcSystemId)) {
                                URI srcSystemUri = URI.create(srcSystemId);
                                if (envStorageSystemConnectedToMap.get(srcSystemUri) == null) {
                                    envStorageSystemConnectedToMap.put(srcSystemUri, new StringSet());
                                }
                                envStorageSystemConnectedToMap.get(srcSystemUri).addAll(systemSet.getTargetSystems());
                            }
                        }
                    }
                }
            }
        }
        
        // process each existing storage system (storage systems with existing remote connections in the DB)
        for (StorageSystem srcStorageSystem : dbStorageSystemsSystemType) {
            if (envStorageSystemConnectedToMap.get(srcStorageSystem.getId()) == null) {
                if (srcStorageSystem.getRemotelyConnectedTo() != null && !srcStorageSystem.getRemotelyConnectedTo().isEmpty()) {
                    // the discovered source has no targets but the db source does
                    srcStorageSystem.getRemotelyConnectedTo().clear();
                    updatedStorageSystems.put(srcStorageSystem.getId(), srcStorageSystem);
                }
            } else {
                // source system has connections; check each target
                List<String> targetsToRemove = new ArrayList<String>();
                StringSet envTargets = envStorageSystemConnectedToMap.get(srcStorageSystem.getId());
                StringSet dbTargets = srcStorageSystem.getRemotelyConnectedTo() == null ? new StringSet() : srcStorageSystem.getRemotelyConnectedTo();
                boolean storageSystemUpdated = false;
                
                // remove targets that exist in the DB but don't exist in the environment
                for (String targetId : dbTargets) {
                    if (!envTargets.contains(targetId)) {
                        targetsToRemove.add(targetId);
                    }
                }
                if (!targetsToRemove.isEmpty()) {
                    srcStorageSystem.getRemotelyConnectedTo().removeAll(targetsToRemove);
                    storageSystemUpdated = true;
                }
                
                // add targets that exist in the environment but don't exist in the DB
                for (String envTarget : envTargets) {
                    if (!dbTargets.contains(envTarget)) {
                        if (srcStorageSystem.getRemotelyConnectedTo() == null) {
                            srcStorageSystem.setRemotelyConnectedTo(new StringSet());
                        }
                        srcStorageSystem.getRemotelyConnectedTo().add(envTarget);
                        storageSystemUpdated = true;
                    }
                }
                
                if (storageSystemUpdated) {
                    updatedStorageSystems.put(srcStorageSystem.getId(), srcStorageSystem);
                }
            }
        }
        
        objectsToUpdate.addAll(updatedStorageSystems.values());
    }

    /**
     * Check remote replication set exists in database.
     * @param nativeGuid
     * @return existing set or null
     */
    public static com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet checkRemoteReplicationSetExistsInDB(String nativeGuid, DbClient dbClient) {

        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet rrSet = null;
        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet> rrSets =
                queryActiveResourcesByAltId(dbClient, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class,
                        "nativeGuid", nativeGuid);

        if (rrSets !=null && !rrSets.isEmpty()) {
            rrSet = rrSets.get(0);
        }
        return rrSet;
    }

    /**
     * Prepare system remote replication set based on discovered remote replication set by driver.
     *
     * @param driverSet discovered remote replication set
     * @param storageSystemType storage system type
     * @param systemSet output system remote replication set
     */
    public static void prepareSystemRemoteReplicationSet(RemoteReplicationSet driverSet, String storageSystemType,
                                                         com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet systemSet,
                                                         DbClient dbClient) {
        try {
            systemSet.setReachable(true);
            if (driverSet.getDeviceLabel() != null) {
                systemSet.setLabel(driverSet.getDeviceLabel());
                systemSet.setDeviceLabel(driverSet.getDeviceLabel());
            }
            // set supported replication link granularity
            StringSet supportedReplicationLinkGranularity = new StringSet();
            for (RemoteReplicationSet.ElementType linGranularity : driverSet.getReplicationLinkGranularity()) {
                supportedReplicationLinkGranularity.add(linGranularity.toString());
            }
            systemSet.setSupportedReplicationLinkGranularity(supportedReplicationLinkGranularity);

            // set supported element types
            StringSet supportedElementTypes = new StringSet();
            for (RemoteReplicationSet.ElementType elementType : driverSet.getSupportedElementTypes()) {
                supportedElementTypes.add(elementType.toString());
            }
            systemSet.setSupportedElementTypes(supportedElementTypes);

            // set replication state
            String replicationState = driverSet.getReplicationState();
            systemSet.setReplicationState(replicationState);


            // set systems to roles map
            // clear old map
            if (systemSet.getSystemToRolesMap() != null) {
                systemSet.getSystemToRolesMap().clear();
            }
            for (Map.Entry<String, Set<RemoteReplicationSet.ReplicationRole>> entry : driverSet.getSystemMap().entrySet()) {
                Set<RemoteReplicationSet.ReplicationRole> roles = entry.getValue();
                StringSet roleSet = new StringSet();
                for (RemoteReplicationSet.ReplicationRole role : roles) {
                    roleSet.add(role.toString());
                }
                // check that storage system exist in system database
                String systemNativeId = entry.getKey();
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystemType, systemNativeId);
                List<com.emc.storageos.db.client.model.StorageSystem> sourceSystems =
                        queryActiveResourcesByAltId(dbClient, com.emc.storageos.db.client.model.StorageSystem.class, "nativeGuid", nativeGuid);
                if (sourceSystems.isEmpty()) {
                    String message = String.format("Cannot find system %s, defined in remote replication set %s for roles %s . Set set to not reachable.",
                            systemNativeId, driverSet.getNativeId(), roles);
                    _log.error(message);
                    systemSet.setReachable(false);
                    // Add system native id to the map
                    systemSet.addSystemRolesEntry(entry.getKey(), roleSet);
                } else {
                    com.emc.storageos.db.client.model.StorageSystem storageSystem = sourceSystems.get(0);
                    systemSet.addSystemRolesEntry(storageSystem.getId().toString(), roleSet);
                }
            }

            // process data related to remote replication modes
            StringSet systemSupportedReplicationModes = new StringSet();
            StringSet systemReplicationModesNoGroupConsistency = new StringSet();
            StringSet systemReplicationModeGroupConsistencyEnforced = new StringSet();
            // set replication mode for this set
            //if (driverSet.getReplicationMode() != null) {
            //    systemSet.setReplicationMode(driverSet.getReplicationMode().getReplicationModeName());
            //}
            // process supported replication modes
            Set<RemoteReplicationMode> supportedReplicationModes = driverSet.getSupportedReplicationModes();
            for (RemoteReplicationMode rMode : supportedReplicationModes) {
                systemSupportedReplicationModes.add(rMode.getReplicationModeName());
                if (rMode.isGroupConsistencyNotSupported()) {
                    systemReplicationModesNoGroupConsistency.add(rMode.getReplicationModeName());
                } else {
                    if (rMode.isGroupConsistencyEnforcedAutomatically()) {
                        systemReplicationModeGroupConsistencyEnforced.add(rMode.getReplicationModeName());
                    }
                }
            }
            systemSet.setSupportedReplicationModes(systemSupportedReplicationModes);
            systemSet.setReplicationModesNoGroupConsistency(systemReplicationModesNoGroupConsistency);
            systemSet.setReplicationModesGroupConsistencyEnforced(systemReplicationModeGroupConsistencyEnforced);
        } catch (Exception e) {
            String message = String.format("Failed to prepare remote replication set %s . Error: %s . Set replication set to not reachable.",
                    systemSet.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            systemSet.setReachable(false);
        }
    }

    /**
     * Process remote replication groups for storage system
     *
     * @param driverGroups groups to process
     * @param systemNativeId storage system native id
     * @param objectsToCreate   new objects to create in database
     * @param objectsToUpdate   existing objects to update in database
     * @param storageSystemType storage type
     * @return system remote replication group
     */
    public static List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup>
    processDriverRRGroups(List<RemoteReplicationGroup> driverGroups, String systemNativeId,
                          List<DataObject> objectsToCreate, List<DataObject> objectsToUpdate,
                          String storageSystemType, DbClient dbClient) {

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> systemGroups =
                new ArrayList<>();
        if (driverGroups == null || driverGroups.isEmpty()) {
            _log.info("No replication groups to process.");
            return systemGroups;
        }
        _log.info("Processing replication groups for storage system {} .Number of groups: {} .", systemNativeId, driverGroups.size());
        for (RemoteReplicationGroup driverGroup : driverGroups) {
            try {
                // check if this replication group is already in database
                String nativeGuid = NativeGUIDGenerator.generateRemoteReplicationGroupNativeGuid(storageSystemType,
                        driverGroup.getSourceSystemNativeId(), driverGroup.getNativeId());
                _log.info("Processing replication group {} .", nativeGuid);

                com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup systemGroup =
                        checkRemoteReplicationGroupExistsInDB(nativeGuid, dbClient);
                if (systemGroup == null) {
                    _log.info("Replication group {} does not exist in database, we will create a new system group for it.", nativeGuid);
                    systemGroup = new com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup();
                    systemGroup.setId(URIUtil.createId(com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class));
                    systemGroup.setIsDriverManaged(true);
                    systemGroup.setNativeGuid(nativeGuid);
                    systemGroup.setNativeId(driverGroup.getNativeId());
                    prepareSystemRemoteReplicationGroup(driverGroup, storageSystemType, systemGroup, dbClient);
                    objectsToCreate.add(systemGroup);
                } else {
                    // replication group already exists --- update with the latest discovery data
                    _log.info("Replication group {} already exists in database, we will update this group in db.", nativeGuid);
                    prepareSystemRemoteReplicationGroup(driverGroup, storageSystemType, systemGroup, dbClient);
                    objectsToUpdate.add(systemGroup);
                }
                systemGroups.add(systemGroup);
            } catch (Exception e) {
                _log.error(String.format("Failed to process replication group %s, for system %s ." +
                        " Error: %s .", driverGroup.getNativeId(), systemNativeId, e.getMessage()), e);
            }
        }
        return systemGroups;

    }


    /**
     * Check remote replication group exist in database.
     * @param nativeGuid
     * @return existing group or null
     */
    public static com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup checkRemoteReplicationGroupExistsInDB(String nativeGuid,
                                                                                                                                   DbClient dbClient) {
        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup rrGroup = null;
        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> rrGroups =
                queryActiveResourcesByAltId(dbClient, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class,
                        "nativeGuid", nativeGuid);

        if (rrGroups != null && !rrGroups.isEmpty()) {
            rrGroup = rrGroups.get(0);
        }
        return rrGroup;
    }


    /**
     * Prepare system remote replication group.
     *
     * @param driverGroup driver remote replication group
     * @param storageSystemType storage system type
     * @param systemGroup output system remote replication group
     */
    public static void prepareSystemRemoteReplicationGroup(RemoteReplicationGroup driverGroup, String storageSystemType,
                                                     com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup systemGroup,
                                                           DbClient dbClient) {
        try {
            systemGroup.setReachable(true);
            systemGroup.setDeviceLabel(driverGroup.getDeviceLabel());
            if (driverGroup.getDisplayName() != null) {
                systemGroup.setDisplayName(driverGroup.getDisplayName());
                systemGroup.setLabel(driverGroup.getDisplayName());
            }
            // set replication state
            String replicationState = driverGroup.getReplicationState();
            systemGroup.setReplicationState(replicationState);

            // set replication mode for this group
            if (driverGroup.getReplicationMode() != null) {
                systemGroup.setReplicationMode(driverGroup.getReplicationMode());
            }

            // storage systemt type for this group
            systemGroup.setStorageSystemType(storageSystemType);

            // set flag to indicate if group consistency for link operations is enforced
            if (driverGroup.isGroupConsistencyEnforced()) {
                systemGroup.setIsGroupConsistencyEnforced(true);
            } else {
                systemGroup.setIsGroupConsistencyEnforced(false);
            }

            // set source and target systems of this replication group
            // source system
            String systemNativeId = driverGroup.getSourceSystemNativeId();
            String nativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystemType, systemNativeId);
            List<com.emc.storageos.db.client.model.StorageSystem> sourceSystems =
                    queryActiveResourcesByAltId(dbClient, com.emc.storageos.db.client.model.StorageSystem.class, "nativeGuid", nativeGuid);
            if (sourceSystems.isEmpty()) {
                String message = String.format("Cannot find source system %s, defined in remote replication group %s . Set group to not reachable.",
                        systemNativeId, systemGroup.getNativeGuid());
                _log.error(message);
                systemGroup.setReachable(false);
            } else {
                com.emc.storageos.db.client.model.StorageSystem sourceSystem = sourceSystems.get(0);
                systemGroup.setSourceSystem(sourceSystem.getId());
            }
            // target system
            systemNativeId = driverGroup.getTargetSystemNativeId();
            nativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystemType, systemNativeId);
            List<com.emc.storageos.db.client.model.StorageSystem> targetSystems =
                    queryActiveResourcesByAltId(dbClient, com.emc.storageos.db.client.model.StorageSystem.class, "nativeGuid", nativeGuid);
            if (targetSystems.isEmpty()) {
                String message = String.format("Cannot find target system %s, defined in remote replication group %s. Set group to not reachable. ",
                        systemNativeId, systemGroup.getNativeGuid());
                _log.error(message);
                systemGroup.setReachable(false);
            } else {
                com.emc.storageos.db.client.model.StorageSystem targetSystem = targetSystems.get(0);
                systemGroup.setTargetSystem(targetSystem.getId());
            }
        } catch (Exception e) {
            String message = String.format("Failed to prepare remote replication group %s . Error: %s .  Set group to not reachable.",
                    systemGroup.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            systemGroup.setReachable(false);
        }

    }







}
