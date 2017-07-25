/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.joiner.Joiner;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplexcontroller.VplexBackendIngestionContext;
import com.emc.storageos.vplexcontroller.utils.VPlexControllerUtils;

public class ImplicitUnManagedObjectsMatcher {
    private static final String LOCAL = "LOCAL";
    private static final String DISTRIBUTED = "DISTRIBUTED";
    private static final Logger _log = LoggerFactory
            .getLogger(ImplicitUnManagedObjectsMatcher.class);
    private static final String INVALID = "Invalid";
    private static final String MATCHED = "Matched";
    private static final int VOLUME_BATCH_SIZE = 200;
    private static final int FILESHARE_BATCH_SIZE = 200;
    private static final String TRUE = "TRUE";
    private static final Executor _executor = Executors.newCachedThreadPool();

    /**
     * run implicit unmanaged matcher during rediscovery
     * 
     * @param dbClient
     */
    public static void runImplicitUnManagedObjectsMatcher(DbClient dbClient) {
        List<URI> vpoolURIs = dbClient.queryByType(VirtualPool.class, true);
        List<VirtualPool> vpoolList = dbClient.queryObject(VirtualPool.class, vpoolURIs);
        Set<URI> srdfEnabledTargetVPools = SRDFUtils.fetchSRDFTargetVirtualPools(dbClient);
        Set<URI> rpEnabledTargetVPools = RPHelper.fetchRPTargetVirtualPools(dbClient);
        for (VirtualPool vpool : vpoolList) {
            matchVirtualPoolsWithUnManagedVolumes(vpool, srdfEnabledTargetVPools, rpEnabledTargetVPools, dbClient, false);
        }
    }

    /**
     * Execute UnManagedVolume to VirtualPool matching
     * on a separate background thread, so that the caller (for example, the Virtual Pool
     * edit API) can return more quickly.
     * 
     * @param virtualPool the virtual pool being matched
     * @param srdfEnabledTargetVPools a cached Set of SRDF enabled target Virtual Pools
     * @param rpEnabledTargetVPools a cached Set of RecoverPoint enabled target Virtual Pools
     * @param dbClient a reference to the VPLEX client
     * @param recalcVplexVolumes flag indicating whether or not VPLEX volumes should be rematched
     */
    public static void matchVirtualPoolsWithUnManagedVolumesInBackground(VirtualPool virtualPool, Set<URI> srdfEnabledTargetVPools,
            Set<URI> rpEnabledTargetVPools, DbClient dbClient, boolean recalcVplexVolumes) {
        ImplicitUnManagedObjectsMatcherThread matcherThread = 
                new ImplicitUnManagedObjectsMatcherThread(virtualPool, srdfEnabledTargetVPools, rpEnabledTargetVPools, dbClient, recalcVplexVolumes);
        _executor.execute(matcherThread);
    }

    /**
     * Execute UnManagedVolume to VirtualPool matching.
     * 
     * @param virtualPool the virtual pool being matched
     * @param srdfEnabledTargetVPools a cached Set of SRDF enabled target Virtual Pools
     * @param rpEnabledTargetVPools a cached Set of RecoverPoint enabled target Virtual Pools
     * @param dbClient a reference to the VPLEX client
     * @param recalcVplexVolumes flag indicating whether or not VPLEX volumes should be rematched
     */
    public static void matchVirtualPoolsWithUnManagedVolumes(VirtualPool virtualPool, Set<URI> srdfEnabledTargetVPools,
            Set<URI> rpEnabledTargetVPools, DbClient dbClient, boolean recalcVplexVolumes) {
        List<UnManagedVolume> modifiedUnManagedVolumes = new ArrayList<UnManagedVolume>();
        Map<String, StringSet> poolMapping = new HashMap<String, StringSet>();

        StringSet invalidPools = null;
        // if a virtual pool has assigned pools, use them for matching.
        // Otherwise use matched pools
        if (virtualPool.getUseMatchedPools() && null != virtualPool.getMatchedStoragePools()) {
            poolMapping.put(MATCHED, virtualPool.getMatchedStoragePools());
        } else if (null != virtualPool.getAssignedStoragePools()) {
            poolMapping.put(MATCHED, virtualPool.getAssignedStoragePools());
            if (null != virtualPool.getMatchedStoragePools()) {
                // Find out the storage pools which are in matched pools but not in assigned pools.
                // These pools should not be in the supported vpool list
                invalidPools = (StringSet) virtualPool.getMatchedStoragePools().clone();
                invalidPools.removeAll(virtualPool.getAssignedStoragePools());
            }
        }

        if (null != virtualPool.getInvalidMatchedPools()) {
            if (invalidPools == null) {
                invalidPools = virtualPool.getInvalidMatchedPools();
            } else {
                invalidPools.addAll(virtualPool.getInvalidMatchedPools());
            }
        }

        if (invalidPools != null) {
            poolMapping.put(INVALID, invalidPools);
        }
        // too many loops, as I am trying to encapsulate both invalid and
        // matched pools within the same logic.T
        for (Entry<String, StringSet> entry : poolMapping.entrySet()) {
            String key = entry.getKey();
            // loop through matchedPools and later on invalid pools
            for (String pool : entry.getValue()) {
                StorageSystem system = null;
                @SuppressWarnings("deprecation")
                List<URI> unManagedVolumeUris = dbClient
                        .queryByConstraint(ContainmentConstraint.Factory
                                .getPoolUnManagedVolumeConstraint(URI.create(pool)));
                Iterator<UnManagedVolume> unManagedVolumes = dbClient
                        .queryIterativeObjects(UnManagedVolume.class, unManagedVolumeUris);
                while (unManagedVolumes.hasNext()) {
                    UnManagedVolume unManagedVolume = unManagedVolumes.next();
                    StringSetMap unManagedVolumeInfo = unManagedVolume
                            .getVolumeInformation();
                    if (null == unManagedVolumeInfo) {
                        continue;
                    }
                    if (system == null) {
                        system = dbClient.queryObject(StorageSystem.class, unManagedVolume.getStorageSystemUri());
                    }

                    String unManagedVolumeProvisioningType = UnManagedVolume.SupportedProvisioningType
                            .getProvisioningType(unManagedVolume.getVolumeCharacterstics().get(
                                    SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));

                    // remove the vpool from supported Vpool List if present
                    if (INVALID.equalsIgnoreCase(key) ||
                            !unManagedVolumeProvisioningType.equalsIgnoreCase(virtualPool.getSupportedProvisioningType())) {
                        if (removeVPoolFromUnManagedVolumeObjectVPools(virtualPool, unManagedVolume)) {
                            modifiedUnManagedVolumes.add(unManagedVolume);
                        }
                    } else if (addVPoolToUnManagedObjectSupportedVPools(virtualPool, unManagedVolumeInfo,
                            unManagedVolume, system, srdfEnabledTargetVPools, rpEnabledTargetVPools)) {
                        modifiedUnManagedVolumes.add(unManagedVolume);
                    }

                    if (modifiedUnManagedVolumes.size() > VOLUME_BATCH_SIZE) {
                        insertInBatches(modifiedUnManagedVolumes, dbClient, "UnManagedVolumes");
                        modifiedUnManagedVolumes.clear();
                    }
                }
            }
        }
        
        if (recalcVplexVolumes) {
            // VPLEX unmanaged volumes need to be matched by different rules.
            matchVirtualPoolWithUnManagedVolumeVPLEX(modifiedUnManagedVolumes, virtualPool, dbClient);
        }
        
        insertInBatches(modifiedUnManagedVolumes, dbClient, "UnManagedVolumes");
    }

    /**
     * Match virtual pool with unmanaged VPLEX volumes.  Uses a different criteria than straight block matchers.
     * Currently this method will only add virtual pools to an unmanaged volume.  It will not remove them.
     * This code is loosely based on VPlexCommunicationInterface.updateUnmanagedVolume() content, but is changed
     * to suit this specific case where a single virtual pool is getting added/updated.
     * 
     * @param modifiedUnManagedVolumes list of volumes to add to
     * @param vpool virtual pool (new or updated)
     * @param dbClient dbclient
     */
    private static void matchVirtualPoolWithUnManagedVolumeVPLEX(List<UnManagedVolume> modifiedUnManagedVolumes, VirtualPool vpool, 
            DbClient dbClient) {
        // This method only applies to VPLEX vpools
        if (!VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
            return;
        }
        
        _log.info("START: matching virtual pool with unmanaged volume for VPLEX");
        // Get all UnManagedVolumes where storageDevice is a StorageSystem where type = VPLEX
        Joiner j = new Joiner(dbClient).join(StorageSystem.class, "ss").match("systemType", "vplex")
                .join("ss", UnManagedVolume.class, "umv", "storageDevice").go();

        // From the joiner, get the StorageSystems (which is a small amount of objects) and the UMVs (which is large, so get URIs and use iter)
        Map<StorageSystem, List<URI>> ssToUmvMap = j.pushList("ss").pushUris("umv").map();
        for (Entry<StorageSystem, List<URI>> ssToUmvEntry : ssToUmvMap.entrySet()) {
            StorageSystem vplex = ssToUmvEntry.getKey();

            // fetch the current mapping of VPLEX cluster ids to cluster names (e.g., "1"=>"cluster-1";"2"=>"cluster-2")
            // the cluster names can be changed by the VPLEX admin, so we cannot rely on the default cluster-1 or cluster-2
            Map<String, String> clusterIdToNameMap = null;
            try {
                VPlexApiClient client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), vplex, dbClient); 
                clusterIdToNameMap = client.getClusterIdToNameMap();
            } catch (Exception ex) {
                _log.warn("Exception caught while getting cluster name info from VPLEX {}", vplex.forDisplay());
            }
            if (null == clusterIdToNameMap || clusterIdToNameMap.isEmpty()) {
                _log.warn("Could not update virtual pool matches for VPLEX {} because cluster name info couldn't be retrieved", 
                        vplex.forDisplay());
                continue;
            }

            // Create a map of virtual arrays to their respective VPLEX cluster (a varray is not allowed to have both VPLEX clusters)
            Map<String, String> varrayToClusterIdMap = new HashMap<String, String>();

            // Since there may be a lot of unmanaged volumes to process, we use the iterative query
            Iterator<UnManagedVolume> volumeIter = dbClient.queryIterativeObjects(UnManagedVolume.class, ssToUmvEntry.getValue());
            while (volumeIter.hasNext()) {
                UnManagedVolume volume = volumeIter.next();

                String highAvailability = null;
                if (volume.getVolumeInformation().get(SupportedVolumeInformation.VPLEX_LOCALITY.toString()) != null) {
                    String haFound = volume.getVolumeInformation().get(SupportedVolumeInformation.VPLEX_LOCALITY.toString()).iterator().next();
                    if (haFound.equalsIgnoreCase(LOCAL)) {
                        highAvailability = VirtualPool.HighAvailabilityType.vplex_local.name();
                    } else if (haFound.equalsIgnoreCase(DISTRIBUTED)) {
                        highAvailability = VirtualPool.HighAvailabilityType.vplex_distributed.name();
                    } else {
                        _log.warn(String.format("could not determine high availability setting for the unmanaged volume %s", 
                                volume.forDisplay()));
                        continue;
                    }
                }

                _log.info("finding valid virtual pools for UnManagedVolume {}", volume.getLabel());

                // Check to see if:
                // - The vpool's HA type doesn't match the volume's, unless...
                // - The vpool is RPVPLEX and this is a VPLEX local volume (likely a journal)
                if (!vpool.getHighAvailability().equals(highAvailability) &&  
                        !(VirtualPool.vPoolSpecifiesRPVPlex(vpool) && highAvailability.equals(VirtualPool.HighAvailabilityType.vplex_local.name()))) {
                    _log.info(String.format("   virtual pool %s is not valid because "
                            + "its high availability setting does not match the unmanaged volume %s",
                            vpool.getLabel(), volume.forDisplay()));
                    continue;
                }

                // If the volume is in a CG, the vpool must specify multi-volume consistency.
                Boolean mvConsistency = vpool.getMultivolumeConsistency();
                if ((TRUE.equals(volume.getVolumeCharacterstics().get(
                        SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString()))) &&
                        ((mvConsistency == null) || (mvConsistency == Boolean.FALSE))) {
                    _log.info(String.format("   virtual pool %s is not valid because it does not have the "
                            + "multi-volume consistency flag set, and the unmanaged volume %s is in a consistency group",
                            vpool.getLabel(), volume.forDisplay()));
                    continue;
                }

                StringSet volumeClusters = new StringSet();
                if (volume.getVolumeInformation().get(SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString()) != null) {
                    volumeClusters.addAll(volume.getVolumeInformation().get(SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString()));
                }

                // VPool must be assigned to a varray corresponding to volume's clusters.
                StringSet varraysForVpool = vpool.getVirtualArrays();
                for (String varrayId : varraysForVpool) {
                    String varrayClusterId = varrayToClusterIdMap.get(varrayId);
                    if (null == varrayClusterId) {
                        varrayClusterId = ConnectivityUtil.getVplexClusterForVarray(URI.create(varrayId), vplex.getId(), dbClient);
                        varrayToClusterIdMap.put(varrayId, varrayClusterId);
                    }

                    if (!ConnectivityUtil.CLUSTER_UNKNOWN.equals(varrayClusterId)) {
                        String varrayClusterName = clusterIdToNameMap.get(varrayClusterId);
                        if (volumeClusters.contains(varrayClusterName)) {
                            // default to true in the case of no backend vols discovered
                            List<UnManagedVolume> backendVols = 
                                    VplexBackendIngestionContext.findBackendUnManagedVolumes(volume, dbClient);
                            if (backendVols != null) {
                                for (UnManagedVolume backendVol : backendVols) {
                                    if (backendVol.getSupportedVpoolUris() == null) {
                                        backendVol.setSupportedVpoolUris(new StringSet());
                                    }
                                    backendVol.getSupportedVpoolUris().add(vpool.getId().toString());
                                    modifiedUnManagedVolumes.add(backendVol);
                                }
                            }
                            if (volume.getSupportedVpoolUris() == null) {
                                volume.setSupportedVpoolUris(new StringSet());
                            }
                            volume.getSupportedVpoolUris().add(vpool.getId().toString());
                            modifiedUnManagedVolumes.add(volume);
                            break;
                        }
                    }
                }

                if (!modifiedUnManagedVolumes.contains(volume)) {
                    _log.info(String.format("   virtual pool %s is not valid because "
                            + "volume %s resides on a cluster that does not match the varray(s) associated with the vpool",
                            vpool.getLabel(), volume.forDisplay()));
                }
            } 
        }
        _log.info("END: matching virtual pool with unmanaged volume for VPLEX");
    }

    /**
     * remove VPool from Supported VPool List of UnManaged Objects
     * 
     * @param supportedVPoolsList
     * @param virtualPool
     * @param unManagedVolume
     * @return
     */
    private static boolean removeVPoolFromUnManagedVolumeObjectVPools(VirtualPool virtualPool,
            UnManagedDiscoveredObject unManagedObject) {
        if (unManagedObject.getSupportedVpoolUris().contains(virtualPool.getId().toString())) {
            _log.info("Removing Invalid VPool {}", virtualPool.getId().toString());
            unManagedObject.getSupportedVpoolUris().remove(virtualPool.getId().toString());
            return true;
        }
        return false;
    }

    /**
     * add VPool to Supported VPool List of UnManaged Objects.
     * @param virtualPool the virtual pool
     * @param unManagedObjectInfo the un managed object info
     * @param system the system (for Block systems, to verify policy matching)
     * @param srdfEnabledTargetVPools SRDF enabled target vpools
     * @param rpEnabledTargetVPools RP enabled target vpools
     * @param supportedVPoolsList the supported v pools list
     * @param unManagedObjectURI the un managed object uri
     * 
     * @return true, if successful
     */
    private static boolean addVPoolToUnManagedObjectSupportedVPools(VirtualPool virtualPool,
            StringSetMap unManagedObjectInfo, UnManagedDiscoveredObject unManagedObject, StorageSystem system,
            Set<URI> srdfEnabledTargetVPools, Set<URI> rpEnabledTargetVPools) {
        // if virtual pool is already part of supported vpool
        // List, then continue;
        StringSet supportedVPoolsList = unManagedObject.getSupportedVpoolUris();
        if (null != supportedVPoolsList
                && supportedVPoolsList.contains(virtualPool.getId().toString())) {
            _log.debug("Matched VPool already there {}", virtualPool.getId().toString());
            return false;
        }

        if (VirtualPool.Type.block.name().equals(virtualPool.getType())) {
            // Before adding this vPool to supportedVPoolList, check if Tiering policy matches
            if (system != null && system.getAutoTieringEnabled()) {
                String autoTierPolicyId = null;
                if (unManagedObjectInfo.containsKey(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString())) {
                    for (String policyName : unManagedObjectInfo.get(SupportedVolumeInformation.AUTO_TIERING_POLICIES
                            .toString())) {
                        autoTierPolicyId = NativeGUIDGenerator
                                .generateAutoTierPolicyNativeGuid(
                                        system.getNativeGuid(), policyName, NativeGUIDGenerator.getTieringPolicyKeyForSystem(system));
                        break;
                    }
                }
                if (!DiscoveryUtils.checkVPoolValidForUnManagedVolumeAutoTieringPolicy(virtualPool, autoTierPolicyId, system)) {
                    String msg = "VPool %s is not added to UnManaged Volume's (%s) supported vPool list "
                            + "since Auto-tiering Policy %s in UnManaged Volume does not match with vPool's (%s)";
                    _log.debug(String.format(msg, new Object[] { virtualPool.getId(), unManagedObject.getId(),
                            autoTierPolicyId, virtualPool.getAutoTierPolicyName() }));
                    return false;
                }
            }

            // don't add vplex virtual pools to non vplex volumes
            if (VirtualPool.vPoolSpecifiesHighAvailability(virtualPool) && (unManagedObject instanceof UnManagedVolume)) {
                UnManagedVolume unManagedVolume = (UnManagedVolume) unManagedObject;
                if (null != unManagedVolume.getVolumeCharacterstics()) {
                    String isVplexVolume = unManagedVolume.getVolumeCharacterstics()
                            .get(SupportedVolumeCharacterstics.IS_VPLEX_VOLUME.toString());
                    if (isVplexVolume == null || isVplexVolume.isEmpty() || !TRUE.equals(isVplexVolume)) {
                        _log.debug(String.format("VPool %s is not added to UnManaged Volume's (%s) supported vPool list "
                                + "since the vpool has high availability set and the volume is non VPLEX.", 
                                new Object[] { virtualPool.getId(), unManagedVolume.forDisplay() }));
                        return false;
                    }
                }
            }

            // Verify whether unmanaged volume SRDF properties with the Vpool
            boolean srdfSourceVpool = (null != virtualPool.getProtectionRemoteCopySettings() && !virtualPool
                    .getProtectionRemoteCopySettings().isEmpty());
            boolean srdfTargetVpool = srdfEnabledTargetVPools == null ? false : (srdfEnabledTargetVPools.contains(virtualPool.getId()));
            StringSet remoteVolType = unManagedObjectInfo.get(SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString());
            boolean isRegularVolume = (null == remoteVolType);
            boolean isSRDFSourceVolume = (null != remoteVolType && remoteVolType.contains(RemoteMirrorObject.Types.SOURCE.toString()));
            boolean isSRDFTargetVolume = (null != remoteVolType && remoteVolType.contains(RemoteMirrorObject.Types.TARGET.toString()));

            if (isRegularVolume && (srdfSourceVpool || srdfTargetVpool)) {
                _log.debug("Found a regular volume with SRDF Protection Virtual Pool. No need to update.");
                return false;
            } else if (isSRDFSourceVolume && !(srdfSourceVpool || srdfTargetVpool)) {
                _log.debug("Found a SRDF unmanaged volume with non-srdf virtualpool. No need to update.");
                return false;
            } else if (isSRDFSourceVolume && srdfTargetVpool) {
                _log.debug("Found a SRDF source volume & target srdf vpool. No need to update.");
                return false;
            } else if (isSRDFTargetVolume && srdfSourceVpool) {
                _log.debug("Found a SRDFTarget volume & source srdf source vpool No need to update.");
                return false;
            }

            // Verify whether unmanaged volume RP properties with the Vpool
            boolean isRPSourceVpool = (null != virtualPool.getProtectionVarraySettings() && !virtualPool
                    .getProtectionVarraySettings().isEmpty());
            boolean isRPTargetVpool = rpEnabledTargetVPools == null ? false : (rpEnabledTargetVPools.contains(virtualPool.getId()));
            remoteVolType = unManagedObjectInfo.get(SupportedVolumeInformation.RP_PERSONALITY.toString());
            isRegularVolume = (null == remoteVolType);
            boolean isRPSourceVolume = (null != remoteVolType && remoteVolType.contains(Volume.PersonalityTypes.SOURCE.toString()));

            if (isRegularVolume && (isRPSourceVpool || isRPTargetVpool)) {
                _log.debug("Found a regular volume with RP Protection Virtual Pool. No need to update.");
                return false;
            } else if (isRPSourceVolume && !isRPSourceVpool) {
                _log.debug("Found a RP unmanaged volume with non-rp virtualpool. No need to update.");
                return false;
            } else if (isRPSourceVolume && isRPTargetVpool) {
                _log.debug("Found a RP source volume & target rp vpool. No need to update.");
                return false;
            }        
        }

        // Adding a fresh new VPool, if supportedVPoolList is
        // empty
        if (null == supportedVPoolsList) {
            _log.debug("Adding a new Supported VPool List {}", virtualPool.getId().toString());
            supportedVPoolsList = new StringSet();
        } // updating the vpool list with new vpool
        supportedVPoolsList.add(virtualPool.getId().toString());
        return true;
    }

    public static void matchVirtualPoolsWithUnManagedFileSystems(VirtualPool virtualPool,
            DbClient dbClient) {

        List<UnManagedFileSystem> modifiedUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        Map<String, StringSet> poolMapping = new HashMap<String, StringSet>();

        StringSet invalidPools = null;
        // if a virtual pool has assigned pools, use them for matching.
        // Otherwise use matched pools
        if (virtualPool.getUseMatchedPools() && null != virtualPool.getMatchedStoragePools()) {
            poolMapping.put(MATCHED, virtualPool.getMatchedStoragePools());
        } else if (null != virtualPool.getAssignedStoragePools()) {
            poolMapping.put(MATCHED, virtualPool.getAssignedStoragePools());
            // Find out the storage pools which are in matched pools but not in assigned pools.
            // These pools should not be in the supported vpool list
            invalidPools = (StringSet) virtualPool.getMatchedStoragePools().clone();
            invalidPools.removeAll(virtualPool.getAssignedStoragePools());
        }

        if (null != virtualPool.getInvalidMatchedPools()) {
            if (invalidPools == null) {
                invalidPools = virtualPool.getInvalidMatchedPools();
            } else {
                invalidPools.addAll(virtualPool.getInvalidMatchedPools());
            }
        }

        if (invalidPools != null) {
            poolMapping.put(INVALID, invalidPools);
        }

        for (Entry<String, StringSet> entry : poolMapping.entrySet()) {
            String key = entry.getKey();

            for (String pool : entry.getValue()) {
                List<URI> unManagedFileSystemUris = dbClient
                        .queryByConstraint(ContainmentConstraint.Factory
                                .getPoolUnManagedFileSystemConstraint(URI.create(pool)));
                Iterator<UnManagedFileSystem> unManagedFileSystems = dbClient
                        .queryIterativeObjects(UnManagedFileSystem.class,
                                unManagedFileSystemUris);
                while (unManagedFileSystems.hasNext()) {
                    UnManagedFileSystem unManagedFileSystem = unManagedFileSystems.next();
                    StringSetMap unManagedFileSystemInfo = unManagedFileSystem
                            .getFileSystemInformation();
                    if (null == unManagedFileSystemInfo) {
                        continue;
                    }

                    String unManagedFileSystemProvisioningType = UnManagedFileSystem.SupportedProvisioningType
                            .getProvisioningType(unManagedFileSystem.getFileSystemCharacterstics().get(
                                    SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED.toString()));

                    boolean isNetApp = unManagedFileSystemInfo.get(SupportedFileSystemInformation.SYSTEM_TYPE.toString()).
                            contains(DiscoveredDataObject.Type.netapp.name());

                    // Since, Provisioning Type for NetApp FileSystem is set as Thick by default,
                    // Ignoring the provisioning type check for NetApp.
                    if (INVALID.equalsIgnoreCase(key)
                            || ((!isNetApp) && !unManagedFileSystemProvisioningType
                                    .equalsIgnoreCase(virtualPool.getSupportedProvisioningType()))) {
                        if (removeVPoolFromUnManagedVolumeObjectVPools(virtualPool, unManagedFileSystem)) {
                            modifiedUnManagedFileSystems.add(unManagedFileSystem);
                        }
                    } else if (addVPoolToUnManagedObjectSupportedVPools(virtualPool, unManagedFileSystemInfo,
                            unManagedFileSystem, null, null, null)) {
                        modifiedUnManagedFileSystems.add(unManagedFileSystem);
                    }

                    if (modifiedUnManagedFileSystems.size() > FILESHARE_BATCH_SIZE) {
                        insertInBatches(modifiedUnManagedFileSystems, dbClient, "UnManagedFileSystems");
                        modifiedUnManagedFileSystems.clear();
                    }

                }
            }
        }
        insertInBatches(modifiedUnManagedFileSystems, dbClient,
                "UnManagedFileSystems");
    }

    private static <T extends DataObject> void insertInBatches(List<T> records,
            DbClient dbClient, String type) {
        try {
            dbClient.updateAndReindexObject(records);
        } catch (DatabaseException e) {
            _log.error("Error inserting {} records into the database", type, e);
        }
    }
}
