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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

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
        for (VirtualPool vpool : vpoolList) {
            matchVirtualPoolsWithUnManagedVolumes(vpool, dbClient);
        }
    }

    public static void matchVirtualPoolsWithUnManagedVolumes(VirtualPool virtualPool,
            DbClient dbClient) {
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
                    StringSet supportedVPoolsList = unManagedVolumeInfo
                            .get(SupportedVolumeInformation.SUPPORTED_VPOOL_LIST
                                    .toString());

                    String unManagedVolumeProvisioningType = UnManagedVolume.SupportedProvisioningType
                            .getProvisioningType(unManagedVolume.getVolumeCharacterstics().get(
                                    SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));

                    // remove the vpool from supported Vpool List if present
                    if (INVALID.equalsIgnoreCase(key) ||
                            !unManagedVolumeProvisioningType.equalsIgnoreCase(virtualPool.getSupportedProvisioningType())) {
                        if (removeVPoolFromUnManagedVolumeObjectVPools(
                                supportedVPoolsList, virtualPool, unManagedVolumeInfo)) {
                            modifiedUnManagedVolumes.add(unManagedVolume);
                        }
                    } else if (addVPoolToUnManagedObjectSupportedVPools(
                            supportedVPoolsList, virtualPool, unManagedVolumeInfo, unManagedVolume.getId(), system)) {
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
     * @param unManagedObjectInfo
     * @return
     */
    private static boolean removeVPoolFromUnManagedVolumeObjectVPools(
            StringSet supportedVPoolsList, VirtualPool virtualPool,
            StringSetMap unManagedObjectInfo) {
        if (null != supportedVPoolsList
                && supportedVPoolsList.contains(virtualPool.getId().toString())) {
            _log.info("Removing Invalid VPool {}", virtualPool.getId().toString());
            unManagedObjectInfo.remove(
                    SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString(),
                    virtualPool.getId().toString());
            return true;
        }
        return false;
    }

    /**
     * add VPool to Supported VPool List of UnManaged Objects.
     * 
     * @param supportedVPoolsList the supported v pools list
     * @param virtualPool the virtual pool
     * @param unManagedObjectInfo the un managed object info
     * @param unManagedObjectURI the un managed object uri
     * @param system the system (for Block systems, to verify policy matching)
     * @return true, if successful
     */
    private static boolean addVPoolToUnManagedObjectSupportedVPools(
            StringSet supportedVPoolsList, VirtualPool virtualPool,
            StringSetMap unManagedObjectInfo, URI unManagedObjectURI, StorageSystem system) {
        // if virtual pool is already part of supported vpool
        // List, then continue;
        if (null != supportedVPoolsList
                && supportedVPoolsList.contains(virtualPool.getId().toString())) {
            _log.debug("Matched VPool already there {}", virtualPool.getId().toString());
            return false;
        }

        // Before adding this vPool to supportedVPoolList, check if Tiering policy matches
        if (VirtualPool.Type.block.name().equals(virtualPool.getType())
                && system != null && system.getAutoTieringEnabled()) {
            String autoTierPolicyId = null;
            if (null != unManagedObjectInfo.get(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString())) {
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
                _log.debug(String.format(msg, new Object[] { virtualPool.getId(), unManagedObjectURI,
                        autoTierPolicyId, virtualPool.getAutoTierPolicyName() }));
                return false;
            }
        }

        // Adding a fresh new VPool, if supportedVPoolList is
        // empty
        if (null == supportedVPoolsList) {
            _log.debug("Adding a new Supported VPool List {}", virtualPool.getId()
                    .toString());
            supportedVPoolsList = new StringSet();
            supportedVPoolsList.add(virtualPool.getId().toString());
            unManagedObjectInfo.put(
                    SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString(),
                    supportedVPoolsList);
        } // updating the vpool list with new vpool
        else {
            _log.debug("Updating a new Supported VPool List {}", virtualPool.getId()
                    .toString());
            unManagedObjectInfo.put(
                    SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString(),
                    virtualPool.getId().toString());
        }
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

                    StringSet supportedVPoolsList = unManagedFileSystemInfo
                            .get(SupportedFileSystemInformation.SUPPORTED_VPOOL_LIST
                                    .toString());

                    String unManagedFileSystemProvisioningType = UnManagedFileSystem.SupportedProvisioningType
                            .getProvisioningType(unManagedFileSystem.getFileSystemCharacterstics().get(
                                    SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED.toString()));

                    boolean isNetApp = unManagedFileSystemInfo.get(SupportedFileSystemInformation.SYSTEM_TYPE.toString()).
                            contains(DiscoveredDataObject.Type.netapp.name());

                    // Since, Provisioning Type for NetApp FileSystem is set as Thick by default,
                    // Ignoring the provisioning type check for NetApp.
                    if (INVALID.equalsIgnoreCase(key) ||
                            ((!isNetApp) && !unManagedFileSystemProvisioningType.
                                    equalsIgnoreCase(virtualPool.getSupportedProvisioningType()))) {
                        if (removeVPoolFromUnManagedVolumeObjectVPools(
                                supportedVPoolsList, virtualPool, unManagedFileSystemInfo)) {
                            modifiedUnManagedFileSystems.add(unManagedFileSystem);
                        }

                    } else if (addVPoolToUnManagedObjectSupportedVPools(
                            supportedVPoolsList, virtualPool, unManagedFileSystemInfo, unManagedFileSystem.getId(), null)) {
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
            dbClient.persistObject(records);
        } catch (DatabaseException e) {
            _log.error("Error inserting {} records into the database", type, e);
        }
    }
}
