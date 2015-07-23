/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.xtremio;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.model.block.VolumeIngest;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumeInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumeResponse;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolumesInfo;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class XtremIOUnManagedVolumeDiscoverer {
    
    private static final Logger log = LoggerFactory.getLogger(XtremIOUnManagedVolumeDiscoverer.class);
    private static final String UNMANAGED_VOLUME = "UnManagedVolume";  
    private static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";
    
    List<UnManagedVolume> unManagedVolumesToCreate  = null;
    List<UnManagedVolume> unManagedVolumesToUpdate  = null;
    Set<URI> allCurrentUnManagedVolumeUris = new HashSet<URI>();
    
    private List<UnManagedExportMask> unManagedExportMasksToCreate = null;
    private List<UnManagedExportMask> unManagedExportMasksToUpdate = null;
    private Set<URI> allCurrentUnManagedExportMaskUris = new HashSet<URI>();
    
    private XtremIOClientFactory xtremioRestClientFactory;
    private NetworkDeviceController networkDeviceController;
    
    public void setXtremioRestClientFactory(XtremIOClientFactory xtremioRestClientFactory) {
        this.xtremioRestClientFactory = xtremioRestClientFactory;
    }    
    
    public void setNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        this.networkDeviceController = networkDeviceController;
    }
    
    private StoragePool getXtremIOStoragePool(URI systemId, DbClient dbClient) {
        StoragePool storagePool = null;
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePoolConstraint(systemId),
                storagePoolURIs);
        Iterator<URI> poolsItr = storagePoolURIs.iterator();
        while (poolsItr.hasNext()) {
            URI storagePoolURI = poolsItr.next();
            storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
        }
        
        return storagePool;
    }
    
    private StringSet discoverVolumeSnaps(StorageSystem system, List<List<Object>> snapDetails, String parentGUID, StringSet parentMatchedVPools, 
            XtremIOClient xtremIOClient, DbClient dbClient, Map<String, List<UnManagedVolume>> igUnmanagedVolumesMap, 
            Map<String, StringSet> igKnownVolumesMap) throws Exception{
        
        StringSet snaps = new StringSet();
        
        for (List<Object> snapDetail : snapDetails) {
            //This can't be null
            if (null == snapDetail.get(1) || null == snapDetail.get(2)){
                log.warn("Snap Name is null in returned snap list response for volume {}",parentGUID);
                continue;
            }
            String snapNameToProcess = (String) snapDetail.get(1);
            XtremIOVolume snap = xtremIOClient.getSnapShotDetails(snapNameToProcess);
            UnManagedVolume unManagedVolume = null;
            boolean isExported = !snap.getLunMaps().isEmpty();
            String managedSnapNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                    system.getNativeGuid(), snap.getVolInfo().get(0));
            BlockSnapshot viprSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
            if (null != viprSnap) {
                log.info("Skipping snapshot {} as it is already managed by ViPR", managedSnapNativeGuid);
                //Check if the xtremIO snap is exported. If yes, we need to store it to add to unmanaged 
                //export masks.
                if(isExported) {
                    populateKnownVolsMap(snap, viprSnap, igKnownVolumesMap);
                }
                snaps.add(managedSnapNativeGuid);
                continue;
            }
            
            String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                    system.getNativeGuid(), snap.getVolInfo().get(0));
            
            unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                    unManagedVolumeNatvieGuid);
            
            unManagedVolume = createUnManagedVolume(unManagedVolume, unManagedVolumeNatvieGuid, snap, igUnmanagedVolumesMap, system, null, dbClient);
            populateSnapInfo(unManagedVolume, parentGUID, parentMatchedVPools);
            snaps.add(unManagedVolumeNatvieGuid);
            allCurrentUnManagedVolumeUris.add(unManagedVolume.getId());
        }
        
        return snaps;
    }
    
    public void discoverUnManagedObjects(AccessProfile accessProfile, DbClient dbClient, 
            PartitionManager partitionManager) throws Exception {
        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        XtremIOClient xtremIOClient = (XtremIOClient) xtremioRestClientFactory
                .getRESTClient(URI.create(XtremIOConstants.getXIOBaseURI(accessProfile.getIpAddress(),
                        accessProfile.getPortNumber())),
                        accessProfile.getUserName(),
                        accessProfile.getPassword(), true);
        
        unManagedVolumesToCreate = new ArrayList<UnManagedVolume>();
        unManagedVolumesToUpdate = new ArrayList<UnManagedVolume>();
        
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        //get the storage pool associated with the xtremio system        
        StoragePool storagePool = getXtremIOStoragePool(storageSystem.getId(), dbClient);
        if(storagePool == null) {
            log.error("Skipping unmanaged volume discovery as the volume storage pool doesn't exist in ViPR");
            return;
        }        
        
        Map<String, List<UnManagedVolume>> igUnmanagedVolumesMap = new HashMap<String, List<UnManagedVolume>>();
        Map<String, StringSet> igKnownVolumesMap = new HashMap<String, StringSet>();
        Map<String, List<String>> volumeSnapsMap = new HashMap<String, List<String>>();
        
        //get the xtremio volume links and process them in batches
        List<XtremIOVolumeInfo> volLinks = xtremIOClient.getXtremIOVolumeLinks();
        
        //Get the volume details
        List<List<XtremIOVolumeInfo>> volume_partitions = Lists.partition(volLinks, Constants.DEFAULT_PARTITION_SIZE);
        for (List<XtremIOVolumeInfo> partition : volume_partitions) {
            List<XtremIOVolume> volumes = xtremIOClient.getXtremIOVolumesForLinks(partition);
            for(XtremIOVolume volume : volumes) {
                //If the volume is a snap don't process it. We will get the snap info from the
                //volumes later
                if(volume.getAncestoVolInfo() != null && !volume.getAncestoVolInfo().isEmpty()) {
                    log.debug("Skipping volume {} as it is a snap", volume.getVolInfo().get(0));
                    continue;
                }
                UnManagedVolume unManagedVolume = null;
                boolean isExported = !volume.getLunMaps().isEmpty();
                boolean hasSnaps = !volume.getSnaps().isEmpty();
                String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), volume.getVolInfo().get(0));
                Volume viprVolume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid);
                if (null != viprVolume) {
                    log.info("Skipping volume {} as it is already managed by ViPR", managedVolumeNativeGuid);
                    //Check if the xtremIO vol is exported. If yes, we need to store it to add to unmanaged 
                    //export masks.
                    if(isExported) {
                        populateKnownVolsMap(volume, viprVolume, igKnownVolumesMap);
                    }
                    
                    //retrieve snap info to be processed later
                    if(hasSnaps) {
                        StringSet vpoolUriSet = new StringSet();
                        vpoolUriSet.add(viprVolume.getVirtualPool().toString());
                        discoverVolumeSnaps(storageSystem, volume.getSnaps(), viprVolume.getNativeGuid(), vpoolUriSet, 
                                xtremIOClient, dbClient, igUnmanagedVolumesMap, igKnownVolumesMap);
                    }
                    
                    continue;
                }
                
                String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                        storageSystem.getNativeGuid(), volume.getVolInfo().get(0));
                //retrieve snap info to be processed later
                unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                        unManagedVolumeNatvieGuid);
                
                unManagedVolume = createUnManagedVolume(unManagedVolume, unManagedVolumeNatvieGuid, volume, igUnmanagedVolumesMap, storageSystem, storagePool, dbClient);  
                
                if(hasSnaps) {
                    StringSet parentMatchedVPools = unManagedVolume.getVolumeInformation().get(SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString());
                    StringSet discoveredSnaps = discoverVolumeSnaps(storageSystem, volume.getSnaps(), unManagedVolumeNatvieGuid, 
                            parentMatchedVPools, xtremIOClient, dbClient, igUnmanagedVolumesMap, igKnownVolumesMap);
                    //set the HAS_REPLICAS property
                    unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                            Boolean.TRUE.toString());
                    StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
                    if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.SNAPSHOTS.toString())) 
                    {
                        log.debug("Snaps :"+Joiner.on("\t").join(discoveredSnaps));
                        if (null != discoveredSnaps && discoveredSnaps.isEmpty()) {
                            // replace with empty string set doesn't work, hence added explicit code to remove all
                            unManagedVolumeInformation.get(
                                    SupportedVolumeInformation.SNAPSHOTS.toString()).clear();
                        } else {
                            // replace with new StringSet
                            unManagedVolumeInformation.get(
                                    SupportedVolumeInformation.SNAPSHOTS.toString()).replace(discoveredSnaps);
                            log.info("Replaced snaps :"+Joiner.on("\t").join( unManagedVolumeInformation.get(
                                    SupportedVolumeInformation.SNAPSHOTS.toString())));
                        }
                    } else {
                        unManagedVolumeInformation.put(
                                SupportedVolumeInformation.SNAPSHOTS.toString(),discoveredSnaps);
                    }
                } else {
                    unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                            Boolean.FALSE.toString()); 
                }
                
                allCurrentUnManagedVolumeUris.add(unManagedVolume.getId());
            }
        }
        
               
        if (!unManagedVolumesToCreate.isEmpty()) {
            partitionManager.insertInBatches(unManagedVolumesToCreate,
                Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            unManagedVolumesToCreate.clear();
        }
        if (!unManagedVolumesToUpdate.isEmpty()) {
            partitionManager.updateInBatches(unManagedVolumesToUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            unManagedVolumesToUpdate.clear();
        }
        
        
        // Process those active unmanaged volume objects available in database but not in newly discovered items, to mark them inactive.
        DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, allCurrentUnManagedVolumeUris, dbClient, partitionManager);        
        
        //Next discover the unmanaged export masks
        discoverUnmanagedExportMasks(storageSystem.getId(), igUnmanagedVolumesMap, igKnownVolumesMap, xtremIOClient, dbClient, partitionManager);
    }
    
    private void populateKnownVolsMap(XtremIOVolume vol, BlockObject viprObj, Map<String, StringSet> igKnownVolumesMap) {
        for (List<Object> lunMapEntries : vol.getLunMaps()) {
            @SuppressWarnings("unchecked")
            //This can't be null
            List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
            if (null == igDetails.get(1) || null == lunMapEntries.get(2)){
                log.warn("IG Name is null in returned lun map response for snap {}",vol.toString());
                continue;
            }
            String igNameToProcess = (String) igDetails.get(1);
            StringSet knownVolumes = igKnownVolumesMap.get(igNameToProcess);
            if(knownVolumes == null) {
                knownVolumes = new StringSet();
                igKnownVolumesMap.put(igNameToProcess, knownVolumes);
            }
            knownVolumes.add(viprObj.getId().toString());
        }
    }
    
    private void populateSnapInfo(UnManagedVolume unManagedVolume, String parentVolumeNatvieGuid, StringSet parentMatchedVPools) {
        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString(), Boolean.TRUE.toString());
        
        StringSet parentVol = new StringSet();
        parentVol.add(parentVolumeNatvieGuid);
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);
        
        StringSet isSyncActive = new StringSet();
        isSyncActive.add(Boolean.TRUE.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);
        
        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);
        
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        
        if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())) {
            log.debug("Matched Pools :"+Joiner.on("\t").join(parentMatchedVPools));
            if (null != parentMatchedVPools && parentMatchedVPools.isEmpty()) {
                // replace with empty string set doesn't work, hence added explicit code to remove all
                unManagedVolumeInformation.get(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).clear();
            } else {
                // replace with new StringSet
                unManagedVolumeInformation.get(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).replace( parentMatchedVPools);
                log.info("Replaced Pools :"+Joiner.on("\t").join( unManagedVolumeInformation.get(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())));
            }
        } else {
            unManagedVolumeInformation.put(
                    SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString(),parentMatchedVPools);
        }
    }
    
    
    /**
     * Group existing known initiators by Host.
     * For each HostName in HostGroup
     *   1. Get List of IGs associated with Host
     *   2. For each IG, get unmanaged and managed volumes
     *   3. create/update mask with volumes/initiators/ports
     *   
     * @param systemId
     * @param igUnmanagedVolumesMap IgName--Unmanaged volume list
     * @param igKnownVolumesMap  IgName -- managed volume list
     * @param xtremIOClient
     * @param dbClient
     * @param partitionManager
     * @throws Exception
     */
    private void discoverUnmanagedExportMasks(URI systemId, Map<String, List<UnManagedVolume>> igUnmanagedVolumesMap, 
            Map<String, StringSet> igKnownVolumesMap, XtremIOClient xtremIOClient, 
            DbClient dbClient, PartitionManager partitionManager) 
            throws Exception {
        unManagedExportMasksToCreate = new ArrayList<UnManagedExportMask>();
        unManagedExportMasksToUpdate = new ArrayList<UnManagedExportMask>();
        
        List<UnManagedVolume> unManagedExportVolumesToUpdate = new ArrayList<UnManagedVolume>();
        //In XtremIO, the volumes are exposed through all the storage ports.
        //Get all the storage ports of xtremIO to be added as known ports in the unmanaged export mask
        //If the host ports are FC, then all add all FC storage ports to the mask
        //else add all IP ports 
        StringSet knownFCStoragePortUris = new StringSet();
        StringSet knownIPStoragePortUris = new StringSet();
        List<StoragePort> matchedFCPorts = new ArrayList<StoragePort>();
        
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(systemId),
                storagePortURIs);
        Iterator<URI> portsItr = storagePortURIs.iterator();
        while (portsItr.hasNext()) {
            URI storagePortURI = portsItr.next();
            StoragePort port = dbClient.queryObject(StoragePort.class, storagePortURI);
            if(TransportType.FC.toString().equals(port.getTransportType())) {
                knownFCStoragePortUris.add(storagePortURI.toString());                
                matchedFCPorts.add(port);
            } else if(TransportType.IP.toString().equals(port.getTransportType())) {
                knownIPStoragePortUris.add(storagePortURI.toString());  
            }
        }
        
        //Group all the initiators and their initiator groups based on ViPR host.
        //To be used for constructing unmanaged export masks
        Map<String, List<Initiator>> hostInitiatorsMap = new HashMap<String, List<Initiator>>();
        Map<String, Set<String>> hostIGNamesMap = new HashMap<String, Set<String>>();
        List<XtremIOInitiator> initiators = xtremIOClient.getXtremIOInitiatorsInfo();
        for(XtremIOInitiator initiator : initiators) {
            String initiatorNetworkId = initiator.getPortAddress();
            // check if a host initiator exists for this id
            Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, dbClient);
            if(knownInitiator == null) {
                //TODO need to think of ways to handle unknown initiators
                continue;
            }
            String hostName = knownInitiator.getHostName();
            if(hostName != null && !hostName.isEmpty()) {
                log.info("   found an initiator in ViPR on host " + hostName);
                String igName = initiator.getInitiatorGroup().get(1);
                List<Initiator> hostInitiators = hostInitiatorsMap.get(hostName);
                Set<String> hostIGNames = hostIGNamesMap.get(hostName);
                if(hostInitiators == null) {
                    hostInitiators = new ArrayList<Initiator>();
                    hostInitiatorsMap.put(hostName, hostInitiators);
                }                
                if(hostIGNames == null) {
                    hostIGNames = new HashSet<String>();
                    hostIGNamesMap.put(hostName, hostIGNames);
                }
                
                hostInitiators.add(knownInitiator);
                hostIGNames.add(igName);
            }
        }
        
        //create export mask per vipr host
        for(String hostname : hostInitiatorsMap.keySet()) {
            StringSet knownIniSet = new StringSet();
            StringSet knownNetworkIdSet = new StringSet();
            StringSet knownVolumeSet = new StringSet();
            List<Initiator> matchedFCInitiators = new ArrayList<Initiator>();
            List<Initiator> hostInitiators = hostInitiatorsMap.get(hostname);
            Set<String> hostIGs = hostIGNamesMap.get(hostname);
            
            for(Initiator hostInitiator : hostInitiators) {
                knownIniSet.add(hostInitiator.getId().toString());
                knownNetworkIdSet.add(hostInitiator.getInitiatorPort());
                if(HostInterface.Protocol.FC.toString().equals(hostInitiator.getProtocol())) {
                    matchedFCInitiators.add(hostInitiator);
                }
            }
                           
            UnManagedExportMask mask = getUnManagedExportMask(hostInitiators.get(0).getInitiatorPort(), dbClient);
            mask.setStorageSystemUri(systemId);
            //set the host name as the mask name 
            mask.setMaskName(hostname);
            
            allCurrentUnManagedExportMaskUris.add(mask.getId());
            for(String igName : hostIGs) {
                StringSet knownVols = igKnownVolumesMap.get(igName);
                if(knownVols != null) {
                    knownVolumeSet.addAll(knownVols);
                }                
                List<UnManagedVolume> hostUnManagedVols = igUnmanagedVolumesMap.get(igName);
                if(hostUnManagedVols != null) {                    
                    for(UnManagedVolume hostUnManagedVol : hostUnManagedVols) {
                        hostUnManagedVol.setInitiatorNetworkIds(knownNetworkIdSet);
                        hostUnManagedVol.setInitiatorUris(knownIniSet);
                        hostUnManagedVol.getUnmanagedExportMasks().add(mask.getId().toString());
                        mask.getUnmanagedVolumeUris().add(hostUnManagedVol.getId().toString());
                        unManagedExportVolumesToUpdate.add(hostUnManagedVol);
                    }
                }
            }
            
            mask.replaceNewWithOldResources(knownIniSet, knownNetworkIdSet, knownVolumeSet, !matchedFCInitiators.isEmpty() ? knownFCStoragePortUris : knownIPStoragePortUris);
            
            updateZoningMap(mask, matchedFCInitiators, matchedFCPorts);
            
            if (!unManagedExportMasksToCreate.isEmpty()) {
                partitionManager.insertInBatches(unManagedExportMasksToCreate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
                unManagedExportMasksToCreate.clear();
            }
            if (!unManagedExportMasksToUpdate.isEmpty()) {
                partitionManager.updateInBatches(unManagedExportMasksToUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
                unManagedExportMasksToUpdate.clear();
            }
            
            if(!unManagedExportVolumesToUpdate.isEmpty()) {
                partitionManager.updateAndReIndexInBatches(unManagedExportVolumesToUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
                unManagedExportVolumesToUpdate.clear();
            }
            
            DiscoveryUtils.markInActiveUnManagedExportMask(systemId, allCurrentUnManagedExportMaskUris, dbClient, partitionManager);
            
        }
        
    }
    
    private void updateZoningMap(UnManagedExportMask mask, List<Initiator> initiators, List<StoragePort> storagePorts) {        
        ZoneInfoMap zoningMap = networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts);
        for (ZoneInfo zoneInfo : zoningMap.values()) {
            log.info("Found zone: {} for initiator {} and port {}", new Object[] {zoneInfo.getZoneName(), 
                    zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn()});
        }
        mask.setZoningMap(zoningMap);
    }
    
    private UnManagedExportMask getUnManagedExportMask(String knownInitiatorNetworkId, DbClient dbClient) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedExportMaskKnownInitiatorConstraint(knownInitiatorNetworkId), result);
        UnManagedExportMask uem = null;
        Iterator<URI> it = result.iterator();
        if (it.hasNext()) {
            uem = dbClient.queryObject(UnManagedExportMask.class, it.next());
            unManagedExportMasksToUpdate.add(uem);
        }
        if (uem != null && !uem.getInactive()) {
         // clean up collections (we'll be refreshing them)
            uem.getKnownInitiatorUris().clear();
            uem.getKnownInitiatorNetworkIds().clear();
            uem.getKnownStoragePortUris().clear();
            uem.getKnownVolumeUris().clear();
            uem.getUnmanagedInitiatorNetworkIds().clear();
            uem.getUnmanagedStoragePortNetworkIds().clear();
            uem.getUnmanagedVolumeUris().clear();
        } else {
            uem = new UnManagedExportMask();
            uem.setId(URIUtil.createId(UnManagedExportMask.class));
            unManagedExportMasksToCreate.add(uem);
        }
        return uem; 
    }
    
    /**
     * Creates a new UnManagedVolume with the given arguments.
     * @param unManagedVolumeNativeGuid
     * @param volume
     * @param system
     * @param pool
     * @param dbClient
     * @return
     */
    private UnManagedVolume createUnManagedVolume(UnManagedVolume unManagedVolume, String unManagedVolumeNativeGuid,
            XtremIOVolume volume, Map<String, List<UnManagedVolume>> igVolumesMap, StorageSystem system, 
            StoragePool pool, DbClient dbClient) {
        boolean created= false;
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNativeGuid);
            unManagedVolume.setStorageSystemUri(system.getId());
            if(pool != null) {
                unManagedVolume.setStoragePoolUri(pool.getId());
            }
            created = true;
        }
        
        unManagedVolume.setLabel(volume.getVolInfo().get(1));
        
        
        Map<String, StringSet> unManagedVolumeInformation = new HashMap<String, StringSet>();
        Map<String, String> unManagedVolumeCharacteristics = new HashMap<String, String>();
        
        Boolean isVolumeExported = false;
        if(!volume.getLunMaps().isEmpty()) {
            //clear the previous unmanaged export masks, initiators if any. The latest export masks will be updated later.
            unManagedVolume.getUnmanagedExportMasks().clear();
            unManagedVolume.getInitiatorNetworkIds().clear();
            unManagedVolume.getInitiatorUris().clear();
            isVolumeExported = true;       
            for (List<Object> lunMapEntries : volume.getLunMaps()) {
                @SuppressWarnings("unchecked")
                //This can't be null
                List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                if (null == igDetails.get(1) || null == lunMapEntries.get(2)){
                    log.warn("IG Name is null in returned lun map response for volume {}",volume.toString());
                    continue;
                }
                String igNameToProcess = (String) igDetails.get(1);
                List<UnManagedVolume> igVolumes = igVolumesMap.get(igNameToProcess);
                if(igVolumes == null) {
                    igVolumes = new ArrayList<UnManagedVolume>();
                    igVolumesMap.put(igNameToProcess, igVolumes);
                }
                igVolumes.add(unManagedVolume);
            }
        }
        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());        
        
        StringSet deviceLabel = new StringSet();
        deviceLabel.add(volume.getVolInfo().get(1));
        unManagedVolumeInformation.put(SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);
        
        String volumeWWN = volume.getWwn().isEmpty()? volume.getVolInfo().get(0) :volume.getWwn();
        unManagedVolume.setWwn(volumeWWN);
        
        StringSet systemTypes = new StringSet();
        systemTypes.add(system.getSystemType());
        
        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(Long.parseLong(volume.getAllocatedCapacity()) * 1024));
        unManagedVolumeInformation.put(SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);
        
        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(Long.parseLong(volume.getAllocatedCapacity()) * 1024));
        unManagedVolumeInformation.put(SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);
        
        unManagedVolumeInformation.put(SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);
        
        StringSet nativeId = new StringSet();
        nativeId.add(volume.getVolInfo().get(0));
        unManagedVolumeInformation.put(SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);
        
        unManagedVolumeCharacteristics.put(
                SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), Boolean.TRUE.toString());

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(), 
                Boolean.TRUE.toString());       
        
        // Set up default MAXIMUM_IO_BANDWIDTH and MAXIMUM_IOPS 
        StringSet bwValues = new StringSet();
        bwValues.add("0");

        if (unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString()) == null) {
            unManagedVolumeInformation.put(SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString(), bwValues);
        } else {
            unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString()).replace(
                    bwValues);
        }

        StringSet iopsVal = new StringSet();
        iopsVal.add("0");

        if (unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString()) == null) {
            unManagedVolumeInformation.put(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString(), iopsVal);
        } else {
            unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString()).replace(iopsVal);
        }
        
        if (null != pool) {
            unManagedVolume.setStoragePoolUri(pool.getId());
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedVolumeInformation.put(SupportedVolumeInformation.STORAGE_POOL.toString(), pools);
            StringSet driveTypes = pool.getSupportedDriveTypes();
            if (null != driveTypes) {
                unManagedVolumeInformation.put(
                        SupportedVolumeInformation.DISK_TECHNOLOGY.toString(),
                        driveTypes);
            }
            StringSet matchedVPools =  DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient,pool.getId(),
                    unManagedVolumeCharacteristics.get(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));
            if (unManagedVolumeInformation
                    .containsKey(SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())) {

                log.debug("Matched Pools :"+Joiner.on("\t").join(matchedVPools));
                if (null != matchedVPools && matchedVPools.isEmpty()) {
                    // replace with empty string set doesn't work, hence added explicit code to remove all
                    unManagedVolumeInformation.get(
                            SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).clear();
                } else {
                    // replace with new StringSet
                    unManagedVolumeInformation.get(
                            SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).replace( matchedVPools);
                    log.info("Replaced Pools :"+Joiner.on("\t").join( unManagedVolumeInformation.get(
                            SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())));
                }
            } else {
                unManagedVolumeInformation.put(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString(),matchedVPools);
            }


        }
        
        unManagedVolume.addVolumeInformation(unManagedVolumeInformation);
        
        if (unManagedVolume.getVolumeCharacterstics() == null) {
            unManagedVolume.setVolumeCharacterstics(new StringMap());
        }
        unManagedVolume.getVolumeCharacterstics().replace(unManagedVolumeCharacteristics);
        
        if (created) {
            unManagedVolumesToCreate.add(unManagedVolume);
        } else {
            unManagedVolumesToUpdate.add(unManagedVolume);
        }
        
        return unManagedVolume;
    }
}
