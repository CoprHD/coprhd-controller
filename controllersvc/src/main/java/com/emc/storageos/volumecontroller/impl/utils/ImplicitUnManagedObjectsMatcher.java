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
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;

public class ImplicitUnManagedObjectsMatcher {
    private static final Logger _log = LoggerFactory
            .getLogger(ImplicitUnManagedObjectsMatcher.class);
    private static final String INVALID = "Invalid";
    private static final String MATCHED = "Matched";
    private static final int VOLUME_BATCH_SIZE = 200;
    private static final int FILESHARE_BATCH_SIZE = 200;

    /**
     * run implicit unmanaged matcher during rediscovery
     * 
     * @param dbClient
     */
    public static void runImplicitUnManagedObjectsMatcher(DbClient dbClient) {
        List<URI> vpoolURIs = dbClient.queryByType(VirtualPool.class, true);
        List<VirtualPool> vpoolList = dbClient.queryObject(VirtualPool.class, vpoolURIs);
        Set<URI> srdfEnabledTargetVPools = SRDFUtils.fetchSRDFTargetVirtualPools(dbClient);
        for (VirtualPool vpool : vpoolList) {
            matchVirtualPoolsWithUnManagedVolumes(vpool, srdfEnabledTargetVPools, null, dbClient);
        }
    }

    public static void matchVirtualPoolsWithUnManagedVolumes(VirtualPool virtualPool, Set<URI> srdfEnabledTargetVPools,
            Set<URI> rpEnabledTargetVPools, DbClient dbClient) {
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
        insertInBatches(modifiedUnManagedVolumes, dbClient, "UnManagedVolumes");
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
            boolean isRPSourceVpool = (null != virtualPool.getProtectionRemoteCopySettings() && !virtualPool
                    .getProtectionRemoteCopySettings().isEmpty());
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
