/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
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
import com.emc.storageos.db.client.model.DataObject.Flag;
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
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOClientFactory;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOInitiator;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOObjectInfo;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class XtremIOUnManagedVolumeDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(XtremIOUnManagedVolumeDiscoverer.class);
    private static final String UNMANAGED_VOLUME = "UnManagedVolume";
    private static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";
    private static final String UNMANAGED_CONSISTENCY_GROUP = "UnManagedConsistencyGroup";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    List<UnManagedVolume> unManagedVolumesToCreate = null;
    List<UnManagedVolume> unManagedVolumesToUpdate = null;
    Set<URI> allCurrentUnManagedVolumeUris = new HashSet<URI>();
    private final Set<URI> allCurrentUnManagedCgURIs = new HashSet<URI>();

    private List<UnManagedConsistencyGroup> unManagedCGToUpdate = null;

    private List<UnManagedExportMask> unManagedExportMasksToCreate = null;
    private List<UnManagedExportMask> unManagedExportMasksToUpdate = null;
    private final Set<URI> allCurrentUnManagedExportMaskUris = new HashSet<URI>();

    private Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap = null;
    private Set<String> unSupportedCG = null;

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

    private StringSet discoverVolumeSnaps(StorageSystem system, List<List<Object>> snapDetails, String parentGUID,
            StringSet parentMatchedVPools, XtremIOClient xtremIOClient, String xioClusterName, DbClient dbClient,
            Map<String, List<UnManagedVolume>> igUnmanagedVolumesMap, Map<String, StringSet> igKnownVolumesMap) throws Exception {

        StringSet snaps = new StringSet();
        Object snapNameToProcess;

        for (List<Object> snapDetail : snapDetails) {
            // This can't be null
            snapNameToProcess = snapDetail.get(1);
            if ((null == snapNameToProcess || snapNameToProcess.toString().length() == 0) || null == snapDetail.get(2)) {
                log.warn("Skipping snapshot as it is null for volume {}", parentGUID);
                continue;
            }
            
            // If this name is a trigger/match for RP automated snapshots, ignore it as well
            String snapName = (String)snapNameToProcess;
            if ((snapName.contains("._RP_") || snapName.contains(".RP_")) && snapName.contains("_SMP")) {
                log.warn("Skipping snapshot {} because it is internal to RP for volume {}", snapName, parentGUID);
                continue;
            }
            
            XtremIOVolume snap = xtremIOClient.getSnapShotDetails(snapNameToProcess.toString(), xioClusterName);
            
            UnManagedVolume unManagedVolume = null;
            boolean isExported = !snap.getLunMaps().isEmpty();
            String managedSnapNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                    system.getNativeGuid(), snap.getVolInfo().get(0));
            BlockSnapshot viprSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
            if (null != viprSnap) {
                log.info("Skipping snapshot {} as it is already managed by ViPR", managedSnapNativeGuid);
                // Check if the xtremIO snap is exported. If yes, we need to store it to add to unmanaged
                // export masks.
                if (isExported) {
                    populateKnownVolsMap(snap, viprSnap, igKnownVolumesMap);
                }
                snaps.add(managedSnapNativeGuid);
                continue;
            }

            String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                    system.getNativeGuid(), snap.getVolInfo().get(0));

            unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                    unManagedVolumeNatvieGuid);

            unManagedVolume = createUnManagedVolume(unManagedVolume, unManagedVolumeNatvieGuid, snap, igUnmanagedVolumesMap, system, null,
                    dbClient);
            populateSnapInfo(unManagedVolume, snap, parentGUID, parentMatchedVPools);
            snaps.add(unManagedVolumeNatvieGuid);
            allCurrentUnManagedVolumeUris.add(unManagedVolume.getId());

            // determine the the snapsets associated with the snap and then determine if the snap set
            // is associated with a cg, if it is process the snap same as any unmanaged volume
            if (snap.getSnapSetList() != null && !snap.getSnapSetList().isEmpty()) {
                Object snapSetNameToProcess = null;
                if (snap.getSnapSetList().size() > 1) {
                    // snaps that belong to multiple snap sets are not supported in ViPR
                    log.warn("Skipping snapshot {} as it belongs to multiple snapSets and this is not supported", snap.getVolInfo().get(0));
                    // add all the snap sets that this volume belongs to to the list that are unsupported for ingestion
                    for (List<Object> snapSet : snap.getSnapSetList()) {
                        snapSetNameToProcess = snapSet.get(1);
                        log.warn("Skipping SnapSet {} as it contains volumes belonging to multiple CGs and this is not supported",
                                snapSetNameToProcess.toString());
                    }
                } else {
                    for (List<Object> snapSet : snap.getSnapSetList()) {
                        snapSetNameToProcess = snapSet.get(1);
                        XtremIOConsistencyGroup snapSetDetails = xtremIOClient.getSnapshotSetDetails(snapSetNameToProcess.toString(),
                                xioClusterName);
                        if (snapSetDetails != null && snapSetDetails.getCgName() != null) {
                            log.info("Snapshot {} belongs to consistency group {} on the array",
                                    snapSetNameToProcess.toString(), snapSetDetails.getCgName());
                            addObjectToUnManagedConsistencyGroup(xtremIOClient, unManagedVolume, snapSetDetails.getCgName(), system,
                                    xioClusterName, dbClient);
                        } else {
                            log.info("Snapshot {} does not belong to a consistency group.",
                                    snapSetNameToProcess.toString());
                        }
                    }
                }
            }
        }

        return snaps;
    }

    public void discoverUnManagedObjects(AccessProfile accessProfile, DbClient dbClient,
            PartitionManager partitionManager) throws Exception {
        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        XtremIOClient xtremIOClient = XtremIOProvUtils.getXtremIOClient(storageSystem, xtremioRestClientFactory);

        unManagedVolumesToCreate = new ArrayList<UnManagedVolume>();
        unManagedVolumesToUpdate = new ArrayList<UnManagedVolume>();

        unManagedCGToUpdateMap = new HashMap<String, UnManagedConsistencyGroup>();

        // get the storage pool associated with the xtremio system
        StoragePool storagePool = getXtremIOStoragePool(storageSystem.getId(), dbClient);
        if (storagePool == null) {
            log.error("Skipping unmanaged volume discovery as the volume storage pool doesn't exist in ViPR");
            return;
        }

        Map<String, List<UnManagedVolume>> igUnmanagedVolumesMap = new HashMap<String, List<UnManagedVolume>>();
        Map<String, StringSet> igKnownVolumesMap = new HashMap<String, StringSet>();

        String xioClusterName = xtremIOClient.getClusterDetails(storageSystem.getSerialNumber()).getName();
        // get the xtremio volume links and process them in batches
        List<XtremIOObjectInfo> volLinks = xtremIOClient.getXtremIOVolumeLinks(xioClusterName);

        // Get the volume details
        List<List<XtremIOObjectInfo>> volume_partitions = Lists.partition(volLinks, Constants.DEFAULT_PARTITION_SIZE);

        // Set containing cgs that cannot be ingested, for now that
        // means they contain volumes which belong to more than one cg
        unSupportedCG = new HashSet<String>();

        for (List<XtremIOObjectInfo> partition : volume_partitions) {
            List<XtremIOVolume> volumes = xtremIOClient.getXtremIOVolumesForLinks(partition, xioClusterName);
            for (XtremIOVolume volume : volumes) {
                try {
                    // If the volume is a snap don't process it. We will get the snap info from the
                    // volumes later
                    if (volume.getAncestoVolInfo() != null && !volume.getAncestoVolInfo().isEmpty()) {
                        log.debug("Skipping volume {} as it is a snap", volume.getVolInfo().get(0));
                        continue;
                    }

                    // check if cgs are null before trying to access, older versions of
                    // the XtremIO REST client do not return cgs as part of volume response
                    // flag indicating the volume is part of a cg
                    boolean hasCGs = false;
                    if (volume.getConsistencyGroups() != null && !volume.getConsistencyGroups().isEmpty()) {
                        hasCGs = true;
                        if (volume.getConsistencyGroups().size() > 1) {
                            // volumes that belong to multiple CGs are not supported in ViPR
                            log.warn("Skipping volume {} as it belongs to multiple CGs and this is not supported",
                                    volume.getVolInfo().get(0));
                            // add all the CGs that this volume belongs to to the list that are unsupported for ingestion
                            for (List<Object> cg : volume.getConsistencyGroups()) {
                                Object cgNameToProcess = cg.get(1);
                                unSupportedCG.add(cgNameToProcess.toString());
                                log.warn("Skipping CG {} as it contains volumes belonging to multiple CGs and this is not supported",
                                        cgNameToProcess.toString());
                            }
                            continue;
                        }
                    }

                    UnManagedVolume unManagedVolume = null;
                    boolean isExported = !volume.getLunMaps().isEmpty();
                    boolean hasSnaps = !volume.getSnaps().isEmpty();

                    String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                            storageSystem.getNativeGuid(), volume.getVolInfo().get(0));
                    Volume viprVolume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid);
                    if (null != viprVolume) {
                        log.info("Skipping volume {} as it is already managed by ViPR", managedVolumeNativeGuid);
                        // Check if the xtremIO vol is exported. If yes, we need to store it to add to unmanaged
                        // export masks.
                        if (isExported) {
                            populateKnownVolsMap(volume, viprVolume, igKnownVolumesMap);
                        }

                        // retrieve snap info to be processed later
                        if (hasSnaps) {
                            StringSet vpoolUriSet = new StringSet();
                            vpoolUriSet.add(viprVolume.getVirtualPool().toString());
                            discoverVolumeSnaps(storageSystem, volume.getSnaps(), viprVolume.getNativeGuid(), vpoolUriSet,
                                    xtremIOClient, xioClusterName, dbClient, igUnmanagedVolumesMap, igKnownVolumesMap);
                        }

                        continue;
                    }

                    String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                            storageSystem.getNativeGuid(), volume.getVolInfo().get(0));
                    // retrieve snap info to be processed later
                    unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                            unManagedVolumeNatvieGuid);

                    unManagedVolume = createUnManagedVolume(unManagedVolume, unManagedVolumeNatvieGuid, volume, igUnmanagedVolumesMap,
                            storageSystem, storagePool, dbClient);

                    // if the volume is associated with a CG, set up the unmanaged CG
                    if (hasCGs) {
                        for (List<Object> cg : volume.getConsistencyGroups()) {
                            // retrieve what should be the first and only consistency group from the list
                            // volumes belonging to multiple cgs are not supported and were excluded above
                            Object cgNameToProcess = cg.get(1);
                            addObjectToUnManagedConsistencyGroup(xtremIOClient, unManagedVolume, cgNameToProcess.toString(),
                                    storageSystem, xioClusterName, dbClient);
                        }
                    } else {
                        // Make sure the unManagedVolume object does not contain CG information from previous discovery
                        unManagedVolume.getVolumeCharacterstics().put(
                                SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
                        // set the uri of the unmanaged CG in the unmanaged volume object to empty
                        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
                                "");
                    }

                    if (hasSnaps) {
                        StringSet parentMatchedVPools = unManagedVolume.getSupportedVpoolUris();
                        StringSet discoveredSnaps = discoverVolumeSnaps(storageSystem, volume.getSnaps(), unManagedVolumeNatvieGuid,
                                parentMatchedVPools, xtremIOClient, xioClusterName, dbClient, igUnmanagedVolumesMap, igKnownVolumesMap);
                        if (!discoveredSnaps.isEmpty()) {
                            // set the HAS_REPLICAS property
                            unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                                    TRUE);
                            StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
                            if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.SNAPSHOTS.toString())) {
                                log.debug("Snaps :" + Joiner.on("\t").join(discoveredSnaps));
                                if (null != discoveredSnaps && discoveredSnaps.isEmpty()) {
                                    // replace with empty string set doesn't work, hence added explicit code to remove all
                                    unManagedVolumeInformation.get(
                                            SupportedVolumeInformation.SNAPSHOTS.toString()).clear();
                                } else {
                                    // replace with new StringSet
                                    unManagedVolumeInformation.get(
                                            SupportedVolumeInformation.SNAPSHOTS.toString()).replace(discoveredSnaps);
                                    log.info("Replaced snaps :" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                                            SupportedVolumeInformation.SNAPSHOTS.toString())));
                                }
                            } else {
                                unManagedVolumeInformation.put(
                                        SupportedVolumeInformation.SNAPSHOTS.toString(), discoveredSnaps);
                            }
                        } else {
                            unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                                    FALSE);
                        }
                    } else {
                        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                                FALSE);
                    }

                    allCurrentUnManagedVolumeUris.add(unManagedVolume.getId());

                } catch (Exception ex) {
                    log.error("Error processing XIO volume {}", volume, ex);
                }
            }
        }

        if (!unManagedCGToUpdateMap.isEmpty()) {
            unManagedCGToUpdate = new ArrayList<UnManagedConsistencyGroup>(unManagedCGToUpdateMap.values());
            partitionManager.updateAndReIndexInBatches(unManagedCGToUpdate,
                    unManagedCGToUpdate.size(), dbClient, UNMANAGED_CONSISTENCY_GROUP);
            unManagedCGToUpdate.clear();
        }

        if (!unManagedVolumesToCreate.isEmpty()) {
            partitionManager.insertInBatches(unManagedVolumesToCreate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            unManagedVolumesToCreate.clear();
        }
        if (!unManagedVolumesToUpdate.isEmpty()) {
            partitionManager.updateAndReIndexInBatches(unManagedVolumesToUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            unManagedVolumesToUpdate.clear();
        }

        // Process those active unmanaged volume objects available in database but not in newly discovered items, to mark them inactive.
        DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, allCurrentUnManagedVolumeUris, dbClient, partitionManager);

        // Process those active unmanaged consistency group objects available in database but not in newly discovered items, to mark them
        // inactive.
        DiscoveryUtils.performUnManagedConsistencyGroupsBookKeeping(storageSystem, allCurrentUnManagedCgURIs, dbClient, partitionManager);

        // Next discover the unmanaged export masks
        discoverUnmanagedExportMasks(storageSystem.getId(), igUnmanagedVolumesMap, igKnownVolumesMap, xtremIOClient, xioClusterName,
                dbClient, partitionManager);
    }

    private void populateKnownVolsMap(XtremIOVolume vol, BlockObject viprObj, Map<String, StringSet> igKnownVolumesMap) {
        for (List<Object> lunMapEntries : vol.getLunMaps()) {
            @SuppressWarnings("unchecked")
            // This can't be null
            List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
            if (null == igDetails.get(1) || null == lunMapEntries.get(2)) {
                log.warn("IG Name is null in returned lun map response for snap {}", vol.toString());
                continue;
            }
            String igNameToProcess = (String) igDetails.get(1);
            StringSet knownVolumes = igKnownVolumesMap.get(igNameToProcess);
            if (knownVolumes == null) {
                knownVolumes = new StringSet();
                igKnownVolumesMap.put(igNameToProcess, knownVolumes);
            }
            knownVolumes.add(viprObj.getId().toString());
        }
    }

    private void populateSnapInfo(UnManagedVolume unManagedVolume, XtremIOVolume xioSnap, String parentVolumeNatvieGuid,
            StringSet parentMatchedVPools) {
        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString(), TRUE);

        StringSet parentVol = new StringSet();
        parentVol.add(parentVolumeNatvieGuid);
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(TRUE);
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = XtremIOConstants.XTREMIO_READ_ONLY_TYPE.equals(xioSnap.getSnapshotType()) ? Boolean.TRUE : Boolean.FALSE;
        isReadOnly.add(readOnly.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        if (xioSnap.getSnapSetList() != null && !xioSnap.getSnapSetList().isEmpty()) {
            StringSet snapsetName = new StringSet();
            List<Object> snapsetDetails = xioSnap.getSnapSetList().get(0);
            snapsetName.add(snapsetDetails.get(1).toString());
            unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(),
                    snapsetName);
        }

        log.debug("Matched Pools : {}", Joiner.on("\t").join(parentMatchedVPools));
        if (null == parentMatchedVPools || parentMatchedVPools.isEmpty()) {
            // Clearn all vpools as no matching vpools found.
            unManagedVolume.getSupportedVpoolUris().clear();
        } else {
            // replace with new StringSet
            unManagedVolume.getSupportedVpoolUris().replace(parentMatchedVPools);
            log.info("Replaced Pools :{}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
        }
    }

    /**
     * Group existing known initiators by Host.
     * For each HostName in HostGroup
     * 1. Get List of IGs associated with Host
     * 2. For each IG, get unmanaged and managed volumes
     * 3. create/update mask with volumes/initiators/ports
     *
     * @param systemId
     * @param igUnmanagedVolumesMap IgName--Unmanaged volume list
     * @param igKnownVolumesMap IgName -- managed volume list
     * @param xtremIOClient
     * @param dbClient
     * @param partitionManager
     * @throws Exception
     */
    private void discoverUnmanagedExportMasks(URI systemId, Map<String, List<UnManagedVolume>> igUnmanagedVolumesMap,
            Map<String, StringSet> igKnownVolumesMap, XtremIOClient xtremIOClient, String xioClusterName,
            DbClient dbClient, PartitionManager partitionManager)
                    throws Exception {
        unManagedExportMasksToCreate = new ArrayList<UnManagedExportMask>();
        unManagedExportMasksToUpdate = new ArrayList<UnManagedExportMask>();

        List<UnManagedVolume> unManagedExportVolumesToUpdate = new ArrayList<UnManagedVolume>();
        // In XtremIO, the volumes are exposed through all the storage ports.
        // Get all the storage ports of xtremIO to be added as known ports in the unmanaged export mask
        // If the host ports are FC, then all add all FC storage ports to the mask
        // else add all IP ports
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
            if (TransportType.FC.toString().equals(port.getTransportType())) {
                knownFCStoragePortUris.add(storagePortURI.toString());
                matchedFCPorts.add(port);
            } else if (TransportType.IP.toString().equals(port.getTransportType())) {
                knownIPStoragePortUris.add(storagePortURI.toString());
            }
        }

        // Group all the initiators and their initiator groups based on ViPR host.
        // To be used for constructing unmanaged export masks
        Map<String, List<Initiator>> hostInitiatorsMap = new HashMap<String, List<Initiator>>();
        Map<String, Set<String>> hostIGNamesMap = new HashMap<String, Set<String>>();
        List<XtremIOInitiator> initiators = xtremIOClient.getXtremIOInitiatorsInfo(xioClusterName);
        for (XtremIOInitiator initiator : initiators) {
            String initiatorNetworkId = initiator.getPortAddress();
            // check if a host initiator exists for this id
            Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, dbClient);
            if (knownInitiator == null) {
                // TODO need to think of ways to handle unknown initiators
                continue;
            }
            // Special case for RP: group masks by cluster.
            String hostName = knownInitiator.checkInternalFlags(Flag.RECOVERPOINT) ? knownInitiator.getClusterName() : knownInitiator
                    .getHostName();
            if (hostName != null && !hostName.isEmpty()) {
                log.info("   found an initiator in ViPR on host " + hostName);
                String igName = initiator.getInitiatorGroup().get(1);
                List<Initiator> hostInitiators = hostInitiatorsMap.get(hostName);
                Set<String> hostIGNames = hostIGNamesMap.get(hostName);
                if (hostInitiators == null) {
                    hostInitiators = new ArrayList<Initiator>();
                    hostInitiatorsMap.put(hostName, hostInitiators);
                }
                if (hostIGNames == null) {
                    hostIGNames = new HashSet<String>();
                    hostIGNamesMap.put(hostName, hostIGNames);
                }

                hostInitiators.add(knownInitiator);
                hostIGNames.add(igName);
            }
        }

        // create export mask per vipr host
        for (String hostname : hostInitiatorsMap.keySet()) {
            StringSet knownIniSet = new StringSet();
            StringSet knownNetworkIdSet = new StringSet();
            StringSet knownVolumeSet = new StringSet();
            List<Initiator> matchedFCInitiators = new ArrayList<Initiator>();
            List<Initiator> hostInitiators = hostInitiatorsMap.get(hostname);
            Set<String> hostIGs = hostIGNamesMap.get(hostname);
            Map<String, List<String>> rpVolumeSnapMap = new HashMap<String, List<String>>();
            Map<String, UnManagedVolume> rpVolumeMap = new HashMap<String, UnManagedVolume>();
            boolean isRpBackendMask = false;
            if (ExportUtils.checkIfInitiatorsForRP(hostInitiators)) {
                log.info("host {} contains RP initiators, "
                        + "so this mask contains RP protected volumes", hostname);
                isRpBackendMask = true;
            }

            boolean isVplexBackendMask = false;
            for (Initiator hostInitiator : hostInitiators) {
                if (!isVplexBackendMask && VPlexControllerUtils.isVplexInitiator(hostInitiator, dbClient)) {
                    log.info("host {} contains VPLEX backend ports, "
                            + "so this mask contains VPLEX backend volumes", hostname);
                    isVplexBackendMask = true;
                }
                knownIniSet.add(hostInitiator.getId().toString());
                knownNetworkIdSet.add(hostInitiator.getInitiatorPort());
                if (HostInterface.Protocol.FC.toString().equals(hostInitiator.getProtocol())) {
                    matchedFCInitiators.add(hostInitiator);
                }
            }

            UnManagedExportMask mask = getUnManagedExportMask(hostInitiators.get(0).getInitiatorPort(), dbClient, systemId);
            mask.setStorageSystemUri(systemId);
            // set the host name as the mask name
            mask.setMaskName(hostname);

            allCurrentUnManagedExportMaskUris.add(mask.getId());
            for (String igName : hostIGs) {
                StringSet knownVols = igKnownVolumesMap.get(igName);
                if (knownVols != null) {
                    knownVolumeSet.addAll(knownVols);
                }
                List<UnManagedVolume> hostUnManagedVols = igUnmanagedVolumesMap.get(igName);
                if (hostUnManagedVols != null) {
                    for (UnManagedVolume hostUnManagedVol : hostUnManagedVols) {
                        hostUnManagedVol.setInitiatorNetworkIds(knownNetworkIdSet);
                        hostUnManagedVol.setInitiatorUris(knownIniSet);
                        hostUnManagedVol.getUnmanagedExportMasks().add(mask.getId().toString());

                        if (isVplexBackendMask) {
                            log.info("marking unmanaged Xtremio volume {} as a VPLEX backend volume",
                                    hostUnManagedVol.getLabel());
                            hostUnManagedVol.putVolumeCharacterstics(
                                    SupportedVolumeCharacterstics.IS_VPLEX_BACKEND_VOLUME.toString(),
                                    TRUE);
                        }

                        // RP processing for this volume in this mask. By default, the RP characteristics are set to false, so
                        // here we check if the volume is in any non-RP mask. If so, it's exported to something other than RP, and
                        // we need to mark that so we can do an exported-volume ingestion. Secondly if any mask associated with this
                        // volume IS associated with RP, we want to mark that volume as RP enabled.
                        if (!isRpBackendMask) {
                            log.info("unmanaged volume {} is exported to something other than RP.  Marking IS_NONRP_EXPORTED.",
                                    hostUnManagedVol.forDisplay());
                            hostUnManagedVol.putVolumeCharacterstics(
                                    SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(),
                                    TRUE);
                        } else {
                            log.info("unmanaged volume {} is an RP volume", hostUnManagedVol.getLabel());
                            hostUnManagedVol.putVolumeCharacterstics(
                                    SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(),
                                    TRUE);
                            rpVolumeMap.put(hostUnManagedVol.getNativeGuid(), hostUnManagedVol);
                        }

                        if (hostUnManagedVol != null) {
                            mask.getUnmanagedVolumeUris().add(hostUnManagedVol.getId().toString());
                            unManagedExportVolumesToUpdate.add(hostUnManagedVol);
                        }
                    }
                }
            }

            mask.replaceNewWithOldResources(knownIniSet, knownNetworkIdSet, knownVolumeSet,
                    !matchedFCInitiators.isEmpty() ? knownFCStoragePortUris : knownIPStoragePortUris);

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

            if (!unManagedExportVolumesToUpdate.isEmpty()) {
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
            log.info("Found zone: {} for initiator {} and port {}", new Object[] { zoneInfo.getZoneName(),
                    zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn() });
        }
        mask.setZoningMap(zoningMap);
    }

    private UnManagedExportMask getUnManagedExportMask(String knownInitiatorNetworkId, DbClient dbClient, URI systemURI) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedExportMaskKnownInitiatorConstraint(knownInitiatorNetworkId), result);
        UnManagedExportMask uem = null;
        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            UnManagedExportMask potentialUem = dbClient.queryObject(UnManagedExportMask.class, it.next());
            // Check whether the uem belongs to the same storage system. This to avoid in picking up the
            // vplex uem.
            if (URIUtil.identical(potentialUem.getStorageSystemUri(), systemURI)) {
                uem = potentialUem;
                unManagedExportMasksToUpdate.add(uem);
                break;
            }
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
     * Creates a new UnManagedConsistencyGroup object in the database
     *
     * @param unManagedCGNativeGuid - nativeGuid of the unmanaged consistency group
     * @param consistencyGroup - xtremio consistency group returned from REST client
     * @param storageSystemURI - storage system of the consistency group
     * @param dbClient - database client
     * @return the new UnManagedConsistencyGroup object
     */
    private UnManagedConsistencyGroup createUnManagedCG(String unManagedCGNativeGuid,
            XtremIOConsistencyGroup consistencyGroup, URI storageSystemURI, DbClient dbClient) {
        UnManagedConsistencyGroup unManagedCG = new UnManagedConsistencyGroup();
        unManagedCG.setId(URIUtil.createId(UnManagedConsistencyGroup.class));
        unManagedCG.setLabel(consistencyGroup.getName());
        unManagedCG.setName(consistencyGroup.getName());
        unManagedCG.setNativeGuid(unManagedCGNativeGuid);
        unManagedCG.setStorageSystemUri(storageSystemURI);
        unManagedCG.setNumberOfVols(consistencyGroup.getNumOfVols());
        dbClient.createObject(unManagedCG);

        return unManagedCG;
    }

    /**
     * Creates a new UnManagedVolume with the given arguments.
     *
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
        boolean created = false;
        StringSetMap unManagedVolumeInformation = null;
        Map<String, String> unManagedVolumeCharacteristics = null;

        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNativeGuid);
            unManagedVolume.setStorageSystemUri(system.getId());
            if (pool != null) {
                unManagedVolume.setStoragePoolUri(pool.getId());
            }
            created = true;
            unManagedVolumeInformation = new StringSetMap();
            unManagedVolumeCharacteristics = new HashMap<String, String>();
        } else {
            unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        }

        unManagedVolume.setLabel(volume.getVolInfo().get(1));

        Boolean isVolumeExported = false;
        if (!volume.getLunMaps().isEmpty()) {
            // clear the previous unmanaged export masks, initiators if any. The latest export masks will be updated later.
            unManagedVolume.getUnmanagedExportMasks().clear();
            unManagedVolume.getInitiatorNetworkIds().clear();
            unManagedVolume.getInitiatorUris().clear();
            isVolumeExported = true;
            for (List<Object> lunMapEntries : volume.getLunMaps()) {
                @SuppressWarnings("unchecked")
                // This can't be null
                List<Object> igDetails = (List<Object>) lunMapEntries.get(0);
                if (null == igDetails.get(1) || null == lunMapEntries.get(2)) {
                    log.warn("IG Name is null in returned lun map response for volume {}", volume.toString());
                    continue;
                }
                String igNameToProcess = (String) igDetails.get(1);
                List<UnManagedVolume> igVolumes = igVolumesMap.get(igNameToProcess);
                if (igVolumes == null) {
                    igVolumes = new ArrayList<UnManagedVolume>();
                    igVolumesMap.put(igNameToProcess, igVolumes);
                }
                igVolumes.add(unManagedVolume);
            }
        }

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(volume.getVolInfo().get(1));
        unManagedVolumeInformation.put(SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);

        String volumeWWN = volume.getWwn().isEmpty() ? volume.getVolInfo().get(0) : volume.getWwn();
        unManagedVolume.setWwn(volumeWWN);

        StringSet systemTypes = new StringSet();
        systemTypes.add(system.getSystemType());

        StringSet accessState = new StringSet();
        accessState.add(Volume.VolumeAccessState.READWRITE.getState());
        unManagedVolumeInformation.put(SupportedVolumeInformation.ACCESS.toString(), accessState);

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
                SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                TRUE);

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
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient, pool.getId(),
                    unManagedVolumeCharacteristics.get(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));
            log.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // clear all existing supported vpools.
                unManagedVolume.getSupportedVpoolUris().clear();
            } else {
                // replace with new StringSet
                unManagedVolume.getSupportedVpoolUris().replace(matchedVPools);
                log.info("Replaced Pools : {}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
            }

        }

        unManagedVolume.setVolumeInformation(unManagedVolumeInformation);

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

    /**
     * Adds the passed in unmanaged volume or unmanaged snapshot
     * to an unmanaged consistency group object
     *
     * @param xtremIOClient - connection to xtremio REST interface
     * @param unManagedVolume - either and unmanaged volume or unmanaged snapshot
     *            associated with a consistency group
     * @param cgNameToProcess - consistency group being processed
     * @param storageSystem - storage system the objects are on
     * @param xioClusterName - name of the xtremio cluster being managed by the xtremio XMS
     * @param dbClient - dbclient
     * @throws Exception
     */
    private void addObjectToUnManagedConsistencyGroup(XtremIOClient xtremIOClient, UnManagedVolume unManagedVolume,
            String cgNameToProcess, StorageSystem storageSystem, String xioClusterName, DbClient dbClient) throws Exception {
        // Check if the current CG is in the list of unsupported CGs
        if (!unSupportedCG.isEmpty() && unSupportedCG.contains(cgNameToProcess.toString())) {
            // leave the for loop and do nothing
            log.warn("Skipping CG {} as it contains volumes belonging to multiple CGs and this is not supported",
                    cgNameToProcess.toString());
            return;
        }
        log.info("Unmanaged volume {} belongs to consistency group {} on the array", unManagedVolume.getLabel(), cgNameToProcess);
        // Update the unManagedVolume object with CG information
        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(),
                Boolean.TRUE.toString());
        // Get the unmanaged CG details from the array
        XtremIOConsistencyGroup xioCG = xtremIOClient.getConsistencyGroupDetails(cgNameToProcess.toString(), xioClusterName);
        // determine the native guid for the unmanaged CG (storage system id + xio cg guid)
        String unManagedCGNativeGuid = NativeGUIDGenerator.generateNativeGuidForCG(storageSystem.getNativeGuid(), xioCG.getGuid());
        // determine if the unmanaged CG already exists in the unManagedCGToUpdateMap or in the database
        // if the the unmanaged CG is not in either create a new one
        UnManagedConsistencyGroup unManagedCG = null;
        if (unManagedCGToUpdateMap.containsKey(unManagedCGNativeGuid)) {
            unManagedCG = unManagedCGToUpdateMap.get(unManagedCGNativeGuid);
            log.info("Unmanaged consistency group {} was previously added to the unManagedCGToUpdateMap", unManagedCG.getLabel());
        } else {
            unManagedCG = DiscoveryUtils.checkUnManagedCGExistsInDB(dbClient, unManagedCGNativeGuid);
            if (null == unManagedCG) {
                // unmanaged CG does not exist in the database, create it
                unManagedCG = createUnManagedCG(unManagedCGNativeGuid, xioCG, storageSystem.getId(), dbClient);
                log.info("Created unmanaged consistency group: {}", unManagedCG.getId().toString());
            } else {
                log.info("Unmanaged consistency group {} was previously added to the database", unManagedCG.getLabel());
                // clean out the list of unmanaged volumes if this unmanaged cg was already
                // in the database and its first time being used in this discovery operation
                // the list should be re-populated by the current discovery operation
                log.info("Cleaning out unmanaged volume map from unmanaged consistency group: {}", unManagedCG.getLabel());
                unManagedCG.getUnManagedVolumesMap().clear();
            }
        }
        log.info("Adding unmanaged volume {} to unmanaged consistency group {}", unManagedVolume.getLabel(), unManagedCG.getLabel());
        // set the uri of the unmanaged CG in the unmanaged volume object
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
                unManagedCG.getId().toString());
        // add the unmanaged volume object to the unmanaged CG
        unManagedCG.getUnManagedVolumesMap().put(unManagedVolume.getNativeGuid(), unManagedVolume.getId().toString());
        // add the unmanaged CG to the map of unmanaged CGs to be updated in the database once all volumes have been processed
        unManagedCGToUpdateMap.put(unManagedCGNativeGuid, unManagedCG);
        // add the unmanaged CG to the current set of CGs being discovered on the array. This is for book keeping later.
        allCurrentUnManagedCgURIs.add(unManagedCG.getId());
    }
}
