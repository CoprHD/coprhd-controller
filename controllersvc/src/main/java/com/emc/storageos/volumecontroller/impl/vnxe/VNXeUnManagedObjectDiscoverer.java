/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe;

import java.io.IOException;
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

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCifsShare;
import com.emc.storageos.vnxe.models.VNXeFileInterface;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXeNfsShare;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class VNXeUnManagedObjectDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(VNXeUnManagedObjectDiscoverer.class);
    public static final String UNMANAGED_VOLUME = "UnManagedVolume";
    public static final String UNMANAGED_FILESYSTEM = "UnManagedFileSystem";
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedFileExportRule";
    private static final String UNMANAGED_CIFS_SHARE_ACL = "UnManagedCifsShareACL";
    private static final String ROOT_USER_ACCESS = "root";
    private static final String SECURITY_FLAVOR = "sys";
    private static final String CIFS_MAX_USERS = "2147483647";

    private VNXeApiClientFactory vnxeApiClientFactory;

    List<UnManagedVolume> unManagedVolumesInsert = null;
    List<UnManagedVolume> unManagedVolumesUpdate = null;
    Set<URI> unManagedVolumesReturnedFromProvider = new HashSet<URI>();

    List<UnManagedFileSystem> unManagedFilesystemsInsert = null;
    List<UnManagedFileSystem> unManagedFilesystemsUpdate = null;
    Set<URI> unManagedFilesystemsReturnedFromProvider = new HashSet<URI>();

    List<UnManagedFileExportRule> unManagedExportRulesInsert = null;
    List<UnManagedFileExportRule> unManagedExportRulesUpdate = null;

    List<UnManagedCifsShareACL> unManagedCifsAclInsert = null;
    List<UnManagedCifsShareACL> unManagedCifsAclUpdate = null;

    public void setVnxeApiClientFactory(VNXeApiClientFactory vnxeApiClientFactory) {
        this.vnxeApiClientFactory = vnxeApiClientFactory;
    }

    public void discoverUnManagedVolumes(AccessProfile accessProfile, DbClient dbClient,
            CoordinatorClient coordinator, PartitionManager partitionManager) throws Exception {

        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        VNXeApiClient apiClient = getVnxeClient(accessProfile);

        unManagedVolumesInsert = new ArrayList<UnManagedVolume>();
        unManagedVolumesUpdate = new ArrayList<UnManagedVolume>();

        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());

        List<VNXeLun> luns = apiClient.getAllLuns();

        if (luns != null && !luns.isEmpty()) {
            Map<String, StoragePool> pools = getStoragePoolMap(storageSystem, dbClient);

            for (VNXeLun lun : luns) {

                if (DiscoveryUtils.isUnmanagedDiscoveryKillSwitchOn()) {
                    log.warn("Discovery kill switch is on, discontinuing unmanaged volume discovery.");
                    return;
                }

                if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(lun.getName())) {
                    // skipping this volume because the filter doesn't match
                    continue;
                }

                UnManagedVolume unManagedVolume = null;
                String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), lun.getId());
                if (null != DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid)) {
                    log.info("Skipping volume {} as it is already managed by ViPR", managedVolumeNativeGuid);
                }

                StoragePool storagePool = getStoragePoolOfUnManagedObject(lun.getPool().getId(), storageSystem, pools);
                if (null == storagePool) {
                    log.error("Skipping unmanaged volume discovery as the volume {} storage pool doesn't exist in ViPR", lun.getId());
                    continue;
                }

                String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                        storageSystem.getNativeGuid(), lun.getId());

                unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                        unManagedVolumeNatvieGuid);

                unManagedVolume = createUnManagedVolume(unManagedVolume, unManagedVolumeNatvieGuid, lun, storageSystem, storagePool,
                        dbClient);

                unManagedVolumesReturnedFromProvider.add(unManagedVolume.getId());
            }

            if (!unManagedVolumesInsert.isEmpty()) {
                partitionManager.insertInBatches(unManagedVolumesInsert,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            }
            if (!unManagedVolumesUpdate.isEmpty()) {
                partitionManager.updateAndReIndexInBatches(unManagedVolumesUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            }

            // Process those active unmanaged volume objects available in database but not in newly discovered items, to mark them inactive.
            DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, unManagedVolumesReturnedFromProvider, dbClient, partitionManager);
        } else {
            log.info("There are no luns found on the system: {}", storageSystem.getId());
        }

    }

    public void discoverUnManagedFileSystems(AccessProfile accessProfile, DbClient dbClient,
            CoordinatorClient coordinator, PartitionManager partitionManager) throws Exception {
        log.info("Started discovery of UnManagedFilesystems for system {}", accessProfile.getSystemId());
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());

        VNXeApiClient apiClient = getVnxeClient(accessProfile);

        unManagedFilesystemsInsert = new ArrayList<UnManagedFileSystem>();
        unManagedFilesystemsUpdate = new ArrayList<UnManagedFileSystem>();

        List<VNXeFileSystem> filesystems = apiClient.getAllFileSystems();
        if (filesystems != null && !filesystems.isEmpty()) {
            Map<String, StoragePool> pools = getStoragePoolMap(storageSystem, dbClient);

            for (VNXeFileSystem fs : filesystems) {

                if (DiscoveryUtils.isUnmanagedDiscoveryKillSwitchOn()) {
                    log.warn("Discovery kill switch is on, discontinuing unmanaged file system discovery.");
                    return;
                }

                if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(fs.getName())) {
                    // skipping this file system because the filter doesn't match
                    continue;
                }

                StoragePort storagePort = getStoragePortPool(storageSystem, dbClient, apiClient, fs);
                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem.getSystemType(), storageSystem.getSerialNumber(), fs.getId());

                StoragePool pool = getStoragePoolOfUnManagedObject(fs.getPool().getId(), storageSystem, pools);
                if (null == pool) {
                    log.error("Skipping unmanaged volume discovery as the file system {} storage pool doesn't exist in ViPR", fs.getId());
                    continue;
                }

                if (checkStorageFileSystemExistsInDB(fsNativeGuid, dbClient)) {
                    log.info("Skipping file system {} as it is already managed by ViPR", fsNativeGuid);
                    continue;
                }
                // Create UnManaged FS
                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                        storageSystem.getSystemType(),
                        storageSystem.getSerialNumber().toUpperCase(), fs.getId());

                UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(dbClient, fsUnManagedFsNativeGuid);

                unManagedFs = createUnManagedFileSystem(unManagedFs, fsUnManagedFsNativeGuid, storageSystem,
                        pool, storagePort, fs, dbClient);

                unManagedFilesystemsReturnedFromProvider.add(unManagedFs.getId());
            }

            if (!unManagedFilesystemsInsert.isEmpty()) {
                // Add UnManagedFileSystem
                partitionManager.insertInBatches(unManagedFilesystemsInsert,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILESYSTEM);
            }

            if (!unManagedFilesystemsUpdate.isEmpty()) {
                // Update UnManagedFilesystem
                partitionManager.updateAndReIndexInBatches(unManagedFilesystemsUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILESYSTEM);
            }

            // Process those active unmanaged fs objects available in database but not in newly discovered items, to mark them inactive.
            performStorageUnManagedFSBookKeeping(storageSystem, dbClient, partitionManager);

        } else {
            log.info("There are no file systems found on the system: {}", storageSystem.getId());
        }

    }

    /**
     * check Storage fileSystem exists in DB
     * 
     * @param nativeGuid
     * @return
     * @throws java.io.IOException
     */
    private boolean checkStorageFileSystemExistsInDB(String nativeGuid, DbClient dbClient)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemNativeGUIdConstraint(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    private StoragePort getStoragePortPool(StorageSystem storageSystem, DbClient dbClient, VNXeApiClient apiClient, VNXeFileSystem fs)
            throws IOException {
        StoragePort storagePort = null;
        // Retrieve the list of data movers interfaces for the VNX File device.
        List<VNXeFileInterface> interfaces = apiClient.getFileInterfaces();
        VNXeBase fsNasserver = fs.getNasServer();
        if (interfaces == null || interfaces.isEmpty()) {
            log.info("No file interfaces found for the system: {} ", storageSystem.getId());
            return storagePort;
        }
        log.info("Number file interfaces found: {}", interfaces.size());
        // Create the list of storage ports.
        for (VNXeFileInterface intf : interfaces) {
            VNXeBase nasServer = intf.getNasServer();
            if (nasServer == null || (!fsNasserver.getId().equalsIgnoreCase(nasServer.getId()))) {
                continue;
            }
            // Check if storage port was already discovered
            URIQueryResultList results = new URIQueryResultList();
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    storageSystem, intf.getIpAddress(), NativeGUIDGenerator.PORT);
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePortByNativeGuidConstraint(portNativeGuid), results);
            Iterator<URI> storagePortIter = results.iterator();
            if (storagePortIter.hasNext()) {
                URI storagePortURI = storagePortIter.next();
                storagePort = dbClient.queryObject(StoragePort.class,
                        storagePortURI);
                if (storagePort.getStorageDevice().equals(storageSystem.getId()) &&
                        storagePort.getPortGroup().equals(nasServer.getId())) {
                    log.debug("found a port for storage system  {} {}",
                            storageSystem.getSerialNumber(), storagePort);
                    break;
                }
            }
        }

        return storagePort;
    }

    private Map<String, StoragePool> getStoragePoolMap(StorageSystem storageSystem, DbClient dbClient) {
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                storagePoolURIs);
        HashMap<String, StoragePool> pools = new HashMap<String, StoragePool>();
        Iterator<URI> poolsItr = storagePoolURIs.iterator();
        while (poolsItr.hasNext()) {
            URI storagePoolURI = poolsItr.next();
            StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
            pools.put(storagePool.getNativeGuid(), storagePool);
        }

        return pools;
    }

    public void discoverAllExportRules(AccessProfile accessProfile,
            DbClient dbClient, PartitionManager partitionManager) {

        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        VNXeApiClient apiClient = getVnxeClient(accessProfile);
        log.info("discoverAllExportRules for storage system {} - start", storageSystem.getId());

        unManagedExportRulesInsert = new ArrayList<UnManagedFileExportRule>();
        unManagedExportRulesUpdate = new ArrayList<UnManagedFileExportRule>();
        
        unManagedFilesystemsUpdate = new ArrayList<UnManagedFileSystem>();

        List<VNXeNfsShare> nfsExports = apiClient.getAllNfsShares();

        // Verification Utility
        UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
                dbClient);

        for (VNXeNfsShare exp : nfsExports) {
            log.info("Discovered fS export {}", exp.toString());

            VNXeFileSystem fs = null;
            if (exp.getParentFilesystem() != null) {
                fs = apiClient.getFileSystemByFSId(exp.getParentFilesystem().getId());
                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem.getSystemType(), storageSystem.getSerialNumber(), fs.getId());

                try {
                    if (checkStorageFileSystemExistsInDB(fsNativeGuid, dbClient)) {
                        log.info("Skipping file system {} as it is already managed by ViPR", fsNativeGuid);
                        continue;
                    }
                    // Create UnManaged FS
                    String fsUnManagedFsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber().toUpperCase(), fs.getId());

                    UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(dbClient, fsUnManagedFsNativeGuid);

                    StoragePort storagePort = getStoragePortPool(storageSystem, dbClient, apiClient, fs);
                    String mountPath = extractValueFromStringSet(SupportedFileSystemInformation.MOUNT_PATH.toString(),
                            unManagedFs.getFileSystemInformation());
                    String exportPath = exp.getPath();
                    if (!exportPath.equalsIgnoreCase("/")) {
                        mountPath = mountPath + exportPath;
                    }

                    String mountPoint = storagePort.getPortNetworkId() + ":" + mountPath;
                    String nfsShareId = exp.getId();

                    String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
                            .generateNativeGuidForPreExistingFileExportRule(
                                    storageSystem, nfsShareId);
                    log.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);

                    UnManagedFileExportRule unManagedExportRule = checkUnManagedFsExportRuleExistsInDB(dbClient,
                            fsUnManagedFileExportRuleNativeGuid);
                    UnManagedFileExportRule unManagedExpRule = null;
                    List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();

                    if (unManagedExportRule == null) {
                        unManagedExportRule = new UnManagedFileExportRule();
                        unManagedExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
                        unManagedExportRule.setFileSystemId(unManagedFs.getId());
                        unManagedExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
                        unManagedExpRule = createExportRules(unManagedFs.getId(), apiClient, exp, unManagedExportRule, mountPath,
                                mountPoint,
                                nfsShareId, storagePort.getPortName());
                        unManagedExportRulesInsert.add(unManagedExpRule);
                    } else {
                        unManagedExpRule = createExportRules(unManagedFs.getId(), apiClient, exp, unManagedExportRule, mountPath,
                                mountPoint,
                                nfsShareId, storagePort.getPortName());
                        unManagedExportRulesUpdate.add(unManagedExpRule);
                    }

                    log.info("Unmanaged File Export Rule : {}", unManagedExportRule);

                    // Build all export rules list.
                    unManagedExportRules.add(unManagedExpRule);

                    // Validate Rules Compatible with ViPR - Same rules should
                    // apply as per API SVC Validations.
                    if (!unManagedExportRules.isEmpty()) {
                        boolean isAllRulesValid = validationUtility
                                .validateUnManagedExportRules(unManagedExportRules, false);
                        if (isAllRulesValid) {
                            log.info("Validating rules success for export {}", unManagedFs.getPath());
                            unManagedFs.setHasExports(true);
                            unManagedFs.putFileSystemCharacterstics(
                                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                            .toString(), Boolean.TRUE.toString());
                            unManagedFilesystemsUpdate.add(unManagedFs);
                            log.info("File System {} has Exports and their size is {}", unManagedFs.getId(), unManagedExportRules.size());
                        } else {
                            log.warn("Validating rules failed for export {}. Ignroing to import these rules into ViPR DB", unManagedFs);
                            unManagedFs.setInactive(true);
                            unManagedFilesystemsUpdate.add(unManagedFs);
                        }
                    }
                } catch (IOException e) {
                    log.error("IOException occured in discoverAllExportRules()", e);
                }
            }
            
            if (!unManagedExportRulesInsert.isEmpty() && 
            		unManagedExportRulesInsert.size() >= Constants.DEFAULT_PARTITION_SIZE) {
                // Add UnManage export rules
                partitionManager.insertInBatches(unManagedExportRulesInsert,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_RULE);
                unManagedExportRulesInsert.clear();
            }

            if (!unManagedExportRulesUpdate.isEmpty() && 
            		unManagedExportRulesUpdate.size() >= Constants.DEFAULT_PARTITION_SIZE) {
                // Update UnManage export rules
                partitionManager.updateInBatches(unManagedExportRulesUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_RULE);
                unManagedExportRulesUpdate.clear();
            }
            
            if (!unManagedFilesystemsUpdate.isEmpty() && 
            		unManagedFilesystemsUpdate.size() >= Constants.DEFAULT_PARTITION_SIZE) {
                // Update UnManagedFilesystem
                partitionManager.updateInBatches(unManagedFilesystemsUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILESYSTEM);
                unManagedFilesystemsUpdate.clear();
            }
        }
        
        if (!unManagedExportRulesInsert.isEmpty()) {
            // Add UnManage export rules
            partitionManager.insertInBatches(unManagedExportRulesInsert,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_RULE);
            unManagedExportRulesInsert.clear();
        }

        if (!unManagedExportRulesUpdate.isEmpty()) {
            // Update UnManage export rules
            partitionManager.updateInBatches(unManagedExportRulesUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_RULE);
            unManagedExportRulesUpdate.clear();
        }
        
        if (!unManagedFilesystemsUpdate.isEmpty() ) {
            // Update UnManagedFilesystem
            partitionManager.updateInBatches(unManagedFilesystemsUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILESYSTEM);
            unManagedFilesystemsUpdate.clear();
        }
    }

    public void discoverAllCifsShares(AccessProfile accessProfile,
            DbClient dbClient, PartitionManager partitionManager) {

        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        VNXeApiClient apiClient = getVnxeClient(accessProfile);
        log.info("discoverAllCifsShares for storage system {} - start", storageSystem.getId());

        unManagedCifsAclInsert = new ArrayList<UnManagedCifsShareACL>();
        unManagedCifsAclUpdate = new ArrayList<UnManagedCifsShareACL>();

        List<VNXeCifsShare> cifsExports = apiClient.getAllCifsShares();

        for (VNXeCifsShare exp : cifsExports) {
            log.info("Discovered fS share {}", exp.toString());

            VNXeFileSystem fs = null;
            if (exp.getFilesystem() != null) {
                fs = apiClient.getFileSystemByFSId(exp.getFilesystem().getId());
                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem.getSystemType(), storageSystem.getSerialNumber(), fs.getId());

                try {
                    if (checkStorageFileSystemExistsInDB(fsNativeGuid, dbClient)) {
                        log.info("Skipping file system {} as it is already managed by ViPR", fsNativeGuid);
                        continue;
                    }
                    // Create UnManaged FS
                    String fsUnManagedFsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber().toUpperCase(), fs.getId());

                    UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(dbClient, fsUnManagedFsNativeGuid);

                    StoragePort storagePort = getStoragePortPool(storageSystem, dbClient, apiClient, fs);
                    String mountPath = extractValueFromStringSet(SupportedFileSystemInformation.MOUNT_PATH.toString(),
                            unManagedFs.getFileSystemInformation());
                    String exportPath = exp.getPath();
                    if (!exportPath.equalsIgnoreCase("/")) {
                        mountPath = mountPath + exportPath;
                    }

                    // String mountPoint = storagePort.getPortNetworkId() + ":" + mountPath;
                    String mountPoint = "\\\\" + storagePort.getPortNetworkId() + "\\" + exp.getName();
                    String cifsShareId = exp.getId();

                    associateCifsExportWithUMFS(unManagedFs, mountPoint, exp, storagePort);
                    List<UnManagedCifsShareACL> cifsACLs = applyCifsSecurityRules(unManagedFs, mountPoint, exp, storagePort);

                    log.info("Number of export rules discovered for file system {} is {}",
                            unManagedFs.getId() + ":" + unManagedFs.getLabel(), cifsACLs.size());

                    for (UnManagedCifsShareACL cifsAcl : cifsACLs) {
                        log.info("Unmanaged File share acls : {}", cifsAcl);
                        String fsShareNativeId = cifsAcl.getFileSystemShareACLIndex();
                        log.info("UMFS Share ACL index {}", fsShareNativeId);
                        String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                .generateNativeGuidForPreExistingFileShare(
                                        storageSystem, fsShareNativeId);
                        log.info("Native GUID {}", fsUnManagedFileShareNativeGuid);

                        cifsAcl.setNativeGuid(fsUnManagedFileShareNativeGuid);

                        // Check whether the CIFS share ACL was present in ViPR DB.
                        UnManagedCifsShareACL existingACL = checkUnManagedFsCifsACLExistsInDB(dbClient, cifsAcl.getNativeGuid());
                        if (existingACL == null) {
                            unManagedCifsAclInsert.add(cifsAcl);
                        } else {
                            unManagedCifsAclInsert.add(cifsAcl);
                            existingACL.setInactive(true);
                            unManagedCifsAclUpdate.add(existingACL);
                        }

                    }
                    // Persist the UMFS as it changed the SMB Share Map.
                    unManagedFs.setHasShares(true);
                    unManagedFs.putFileSystemCharacterstics(
                            UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                    .toString(), Boolean.TRUE.toString());
                    dbClient.persistObject(unManagedFs);

                } catch (IOException e) {
                    log.error("IOException occured in discoverAllCifsShares()", e);
                }
            }
        }

        if (!unManagedCifsAclInsert.isEmpty()) {
            // Add UnManagedFileSystem
            partitionManager.insertInBatches(unManagedCifsAclInsert,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_CIFS_SHARE_ACL);
            unManagedCifsAclInsert.clear();
        }

        if (!unManagedCifsAclUpdate.isEmpty()) {
            // Update UnManagedFilesystem
            partitionManager.updateInBatches(unManagedCifsAclUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_CIFS_SHARE_ACL);
            unManagedCifsAclUpdate.clear();
        }
    }

    /**
     * check Pre Existing Storage CIFS ACLs exists in DB
     * 
     * @param dbClient
     * @param cifsNativeGuid
     * @return UnManagedCifsShareACL
     * @throws java.io.IOException
     */
    protected UnManagedCifsShareACL checkUnManagedFsCifsACLExistsInDB(DbClient dbClient,
            String cifsACLNativeGuid) {
        UnManagedCifsShareACL unManagedCifsAcl = null;
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileCifsACLNativeGUIdConstraint(cifsACLNativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI cifsAclURI = iter.next();
            unManagedCifsAcl = dbClient.queryObject(UnManagedCifsShareACL.class, cifsAclURI);
            return unManagedCifsAcl;
        }
        return unManagedCifsAcl;
    }

    private void associateCifsExportWithUMFS(UnManagedFileSystem vnxufs,
            String mountPoint, VNXeCifsShare exp, StoragePort storagePort) {

        try {

            // Assign storage port to unmanaged FS
            if (storagePort != null) {
                StringSet storagePorts = new StringSet();
                storagePorts.add(storagePort.getId().toString());
                vnxufs.getFileSystemInformation().remove(UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
                vnxufs.getFileSystemInformation().put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
            }

            String shareName = exp.getName();
            UnManagedSMBFileShare unManagedSMBFileShare = new UnManagedSMBFileShare();

            unManagedSMBFileShare.setName(shareName);
            unManagedSMBFileShare.setMountPoint(mountPoint);
            unManagedSMBFileShare.setMaxUsers(Integer.parseInt(CIFS_MAX_USERS));
            unManagedSMBFileShare.setPortGroup(storagePort.getPortGroup());
            // setting to default permission type for VNXe
            unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);
            unManagedSMBFileShare.setPermission(ShareACL.SupportedPermissions.change.toString());
            unManagedSMBFileShare.setPath(exp.getPath());

            UnManagedSMBShareMap currUnManagedExportMap = vnxufs.getUnManagedSmbShareMap();
            if (currUnManagedExportMap == null) {
                currUnManagedExportMap = new UnManagedSMBShareMap();
                vnxufs.setUnManagedSmbShareMap(currUnManagedExportMap);
            }

            if (currUnManagedExportMap.get(shareName) == null) {
                currUnManagedExportMap.put(shareName, unManagedSMBFileShare);
                log.debug("associateCifsExportWithFS - no SMBs already exists for share {}",
                        shareName);
            } else {
                // Remove the existing and add the new share
                currUnManagedExportMap.remove(shareName);
                currUnManagedExportMap.put(shareName, unManagedSMBFileShare);
                log.warn("associateExportMapWithFS - Identical export already exists for mount path {} Overwrite",
                        shareName);
            }

        } catch (Exception ex) {
            log.warn("VNXe file share retrieve processor failed for path {}, cause {}",
                    exp.getName(), ex);
        }
    }

    private List<UnManagedCifsShareACL> applyCifsSecurityRules(UnManagedFileSystem vnxeufs, String expPath,
            VNXeCifsShare exp, StoragePort storagePort) {

        List<UnManagedCifsShareACL> cifsACLs = new ArrayList<UnManagedCifsShareACL>();
        UnManagedCifsShareACL unManagedCifsShareACL = new UnManagedCifsShareACL();
        String shareName = exp.getName();
        unManagedCifsShareACL.setShareName(shareName);
        // user
        unManagedCifsShareACL.setUser(FileControllerConstants.CIFS_SHARE_USER_EVERYONE);
        // permission
        unManagedCifsShareACL.setPermission(FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE);
        unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));
        // filesystem id
        unManagedCifsShareACL.setFileSystemId(vnxeufs.getId());
        cifsACLs.add(unManagedCifsShareACL);
        return cifsACLs;
    }

    public static String extractValueFromStringSet(String key, StringSetMap volumeInformation) {
        try {
            StringSet availableValueSet = volumeInformation.get(key);
            if (null != availableValueSet) {
                for (String value : availableValueSet) {
                    return value;
                }
            }
        } catch (Exception e) {
            log.error("extractValueFromStringSet Exception: ", e);
        }
        return null;
    }

    private UnManagedFileExportRule createExportRules(URI umfsId, VNXeApiClient apiClient, VNXeNfsShare export,
            UnManagedFileExportRule unManagedExpRule, String mountPath, String mountPoint, String nfsShareId,
            String storagePort) {

        StringSet roHosts = new StringSet();
        if (export.getReadOnlyHosts() != null && !export.getReadOnlyHosts().isEmpty()) {
            for (VNXeBase roHost : export.getReadOnlyHosts()) {
                roHosts.add(apiClient.getHostById(roHost.getId()).getName());
            }
            unManagedExpRule.setReadOnlyHosts(roHosts);
        }

        StringSet rwHosts = new StringSet();
        if (export.getReadWriteHosts() != null && !export.getReadWriteHosts().isEmpty()) {
            for (VNXeBase rwHost : export.getReadWriteHosts()) {
                rwHosts.add(apiClient.getHostById(rwHost.getId()).getName());
            }
            unManagedExpRule.setReadWriteHosts(rwHosts);
        }

        StringSet rootHosts = new StringSet();
        if (export.getRootAccessHosts() != null && !export.getRootAccessHosts().isEmpty()) {
            for (VNXeBase rootHost : export.getRootAccessHosts()) {
                rootHosts.add(apiClient.getHostById(rootHost.getId()).getName());
            }
            unManagedExpRule.setRootHosts(rootHosts);
        }

        unManagedExpRule.setAnon(ROOT_USER_ACCESS);
        unManagedExpRule.setExportPath(export.getPath());
        unManagedExpRule.setFileSystemId(umfsId);
        unManagedExpRule.setSecFlavor(SECURITY_FLAVOR);
        unManagedExpRule.setMountPoint(mountPoint);
        unManagedExpRule.setExportPath(mountPath);
        unManagedExpRule.setDeviceExportId(nfsShareId);
        unManagedExpRule.setLabel(export.getName());
        return unManagedExpRule;
    }

    /**
     * Creates a new UnManagedVolume with the given arguments.
     * 
     * @param unManagedVolumeNativeGuid
     * @param lun
     * @param system
     * @param pool
     * @param dbClient
     * @return
     */
    private UnManagedVolume createUnManagedVolume(UnManagedVolume unManagedVolume, String unManagedVolumeNativeGuid,
            VNXeLun lun, StorageSystem system, StoragePool pool, DbClient dbClient) {
        boolean created = false;
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNativeGuid);
            unManagedVolume.setStorageSystemUri(system.getId());
            unManagedVolume.setStoragePoolUri(pool.getId());
            created = true;
        }

        unManagedVolume.setLabel(lun.getName());

        StringSetMap unManagedVolumeInformation = new StringSetMap();
        Map<String, String> unManagedVolumeCharacteristics = new HashMap<String, String>();

        Boolean isVolumeExported = false;
        if (lun.getHostAccess() != null && !lun.getHostAccess().isEmpty()) {
            isVolumeExported = true;
        }
        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        Boolean isVolumeInCG = lun.getType() == VNXeApiClient.GENERIC_STORAGE_LUN_TYPE ? true : false;
        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(),
                isVolumeInCG.toString());

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(lun.getName());
        unManagedVolumeInformation.put(SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);

        String volumeWWN = lun.getWwn().replaceAll(":", "");
        unManagedVolume.setWwn(volumeWWN);

        StringSet systemTypes = new StringSet();
        systemTypes.add(system.getSystemType());

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(lun.getSizeTotal()));
        unManagedVolumeInformation.put(SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(lun.getSizeAllocated()));
        unManagedVolumeInformation.put(SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        unManagedVolumeInformation.put(SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(lun.getId());
        unManagedVolumeInformation.put(SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), Boolean.TRUE.toString());

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                lun.getIsThinEnabled().toString());

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
                    unManagedVolumeCharacteristics.get(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()),
                    unManagedVolume);
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
            unManagedVolumesInsert.add(unManagedVolume);
        } else {
            unManagedVolumesUpdate.add(unManagedVolume);
        }

        return unManagedVolume;
    }

    /**
     * create StorageFileSystem Info Object
     * 
     * @param unManagedFileSystem
     * @param unManagedFileSystemNativeGuid
     * @param system
     * @param pool
     * @param storagePort
     * @param fileSystem
     * @return UnManagedFileSystem
     */
    private UnManagedFileSystem createUnManagedFileSystem(UnManagedFileSystem unManagedFileSystem,
            String unManagedFileSystemNativeGuid, StorageSystem system, StoragePool pool,
            StoragePort storagePort, VNXeFileSystem fileSystem, DbClient dbClient) {
        boolean created = false;
        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil.createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(system.getId());
            unManagedFileSystem.setStoragePoolUri(pool.getId());
            unManagedFileSystem.setHasExports(false);
            unManagedFileSystem.setHasShares(false);
            created = true;
        }

        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                Boolean.FALSE.toString());

        if (fileSystem.getIsThinEnabled()) {
            unManagedFileSystemCharacteristics.put(
                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(), Boolean.TRUE.toString());
        }
        else {
            unManagedFileSystemCharacteristics.put(
                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(), Boolean.FALSE.toString());
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(), Boolean.FALSE.toString());

        if (null != system) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(system.getSystemType());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient, pool.getId(),
                    unManagedFileSystemCharacteristics.get(
                            UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                                    .toString()));
            log.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // clear all existing supported vpools.
                unManagedFileSystem.getSupportedVpoolUris().clear();
            } else {
                // replace with new StringSet
                unManagedFileSystem.getSupportedVpoolUris().replace(matchedVPools);
                log.info("Replaced Pools :"
                        + Joiner.on("\t").join(unManagedFileSystem.getSupportedVpoolUris()));
            }

        }

        if (null != storagePort) {
            StringSet storagePorts = new StringSet();
            storagePorts.add(storagePort.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(), Boolean.TRUE.toString());
        // Set attributes of FileSystem
        StringSet fsPath = new StringSet();
        fsPath.add("/" + fileSystem.getName());

        StringSet fsMountPath = new StringSet();
        fsMountPath.add("/" + fileSystem.getName());

        StringSet fsName = new StringSet();
        fsName.add(fileSystem.getName());

        StringSet fsId = new StringSet();
        fsId.add(fileSystem.getId() + "");

        unManagedFileSystem.setLabel(fileSystem.getName());

        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NAME.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NATIVE_ID.toString(), fsId);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.DEVICE_LABEL.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PATH.toString(), fsPath);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.MOUNT_PATH.toString(), fsMountPath);

        StringSet allocatedCapacity = new StringSet();
        String usedCapacity = String.valueOf(fileSystem.getSizeAllocated());
        allocatedCapacity.add(usedCapacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(), allocatedCapacity);

        StringSet provisionedCapacity = new StringSet();
        String capacity = String.valueOf(fileSystem.getSizeTotal());
        provisionedCapacity.add(capacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(), provisionedCapacity);

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem.addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem.setFileSystemCharacterstics(unManagedFileSystemCharacteristics);

        if (created) {
            unManagedFilesystemsInsert.add(unManagedFileSystem);
        } else {
            unManagedFilesystemsUpdate.add(unManagedFileSystem);
        }

        return unManagedFileSystem;
    }

    /**
     * Return the pool of the UnManaged volume.
     * 
     * @param storageResource
     * @param system
     * @param dbClient
     * @return
     * @throws IOException
     */
    private StoragePool getStoragePoolOfUnManagedObject(String poolNativeId,
            StorageSystem system, Map<String, StoragePool> pools) throws IOException {
        String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(system, poolNativeId, NativeGUIDGenerator.POOL);
        if (pools.containsKey(poolNativeGuid)) {
            return pools.get(poolNativeGuid);
        }
        return null;
    }

    /**
     * Get the Vnxe service client for making requests to the Vnxe based
     * on the passed profile.
     * 
     * @param accessProfile A reference to the access profile.
     * 
     * @return A reference to the Vnxe service client.
     */
    private VNXeApiClient getVnxeClient(AccessProfile accessProfile) {
        VNXeApiClient client = vnxeApiClientFactory.getClient(accessProfile.getIpAddress(),
                accessProfile.getPortNumber(), accessProfile.getUserName(),
                accessProfile.getPassword());

        return client;

    }

    /**
     * check Pre Existing Storage filesystem exists in DB
     * 
     * @param nativeGuid
     * @return unManageFileSystem
     * @throws IOException
     */
    protected UnManagedFileSystem checkUnManagedFileSystemExistsInDB(DbClient dbClient, String nativeGuid) throws IOException {
        UnManagedFileSystem filesystemInfo = null;
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemInfoNativeGUIdConstraint(nativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unFileSystemtURI = iter.next();
            filesystemInfo = dbClient.queryObject(UnManagedFileSystem.class, unFileSystemtURI);
            return filesystemInfo;
        }

        return filesystemInfo;

    }

    /**
     * check Pre Existing Storage Export Rule exists in DB
     * 
     * @param nativeGuid
     * @return unManagedFileExportRule
     * @throws IOException
     */
    protected UnManagedFileExportRule checkUnManagedFsExportRuleExistsInDB(DbClient dbClient,
            String fsExportRuleNativeId) {
        UnManagedFileExportRule unManagedExportRule = null;
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileExporRuleNativeGUIdConstraint(fsExportRuleNativeId), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unExportRuleURI = iter.next();
            unManagedExportRule = dbClient.queryObject(UnManagedFileExportRule.class, unExportRuleURI);
            return unManagedExportRule;
        }
        return unManagedExportRule;
    }

    private void performStorageUnManagedFSBookKeeping(StorageSystem storageSystem, DbClient dbClient,
            PartitionManager partitionManager) throws IOException {

        // Get all available existing unmanaged FS URIs for this array from DB
        URIQueryResultList allAvailableUnManagedFileSystemsInDB = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceUnManagedFileSystemConstraint(storageSystem.getId()),
                allAvailableUnManagedFileSystemsInDB);

        Set<URI> unManagedFSInDBSet = new HashSet<URI>();
        for (URI uri : allAvailableUnManagedFileSystemsInDB) {
            unManagedFSInDBSet.add(uri);
        }

        SetView<URI> onlyAvailableinDB = Sets.difference(unManagedFSInDBSet, unManagedFilesystemsReturnedFromProvider);

        log.info("Diff :" + Joiner.on("\t").join(onlyAvailableinDB));
        if (!onlyAvailableinDB.isEmpty()) {
            List<UnManagedFileSystem> unManagedFsTobeDeleted = new ArrayList<UnManagedFileSystem>();
            Iterator<UnManagedFileSystem> unManagedFs = dbClient.queryIterativeObjects(UnManagedFileSystem.class,
                    new ArrayList<URI>(onlyAvailableinDB));

            while (unManagedFs.hasNext()) {
                UnManagedFileSystem fs = unManagedFs.next();
                if (null == fs || fs.getInactive()) {
                    continue;
                }

                log.info("Setting unManagedVolume {} inactive", fs.getId());
                fs.setStoragePoolUri(NullColumnValueGetter.getNullURI());
                fs.setStorageSystemUri(NullColumnValueGetter.getNullURI());
                fs.setInactive(true);
                unManagedFsTobeDeleted.add(fs);
            }
            if (!unManagedFsTobeDeleted.isEmpty()) {
                partitionManager.updateAndReIndexInBatches(unManagedFsTobeDeleted, 1000,
                        dbClient, UNMANAGED_FILESYSTEM);
            }
        }

    }

}
