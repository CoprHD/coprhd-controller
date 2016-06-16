/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxunity;

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
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileQuotaDirectory;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.vnxe.models.BlockHostAccess;
import com.emc.storageos.vnxe.models.Snap;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.vnxe.models.VNXUnityQuotaConfig;
import com.emc.storageos.vnxe.models.VNXUnityTreeQuota;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCifsShare;
import com.emc.storageos.vnxe.models.VNXeFileInterface;
import com.emc.storageos.vnxe.models.VNXeFileSystem;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeLun;
import com.emc.storageos.vnxe.models.VNXeNfsShare;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

public class VNXUnityUnManagedObjectDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(VNXUnityUnManagedObjectDiscoverer.class);
    public static final String UNMANAGED_VOLUME = "UnManagedVolume";
    public static final String UNMANAGED_FILESYSTEM = "UnManagedFileSystem";
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedFileExportRule";
    private static final String UNMANAGED_CIFS_SHARE_ACL = "UnManagedCifsShareACL";
    private static final String UNMANAGED_FILEQUOTADIR = "UnManagedFileQuotaDirectory";
    private static final String ROOT_USER_ACCESS = "root";
    private static final String SECURITY_FLAVOR = "sys";
    private static final String CIFS_MAX_USERS = "2147483647";
    private static final String UNMANAGED_CONSISTENCY_GROUP = "UnManagedConsistencyGroup";
    private static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";

    private VNXeApiClientFactory vnxeApiClientFactory;
    private NetworkDeviceController networkDeviceController;

    public void setNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        this.networkDeviceController = networkDeviceController;
    }

    List<UnManagedVolume> unManagedVolumesInsert = null;
    List<UnManagedVolume> unManagedVolumesUpdate = null;
    Set<URI> unManagedVolumesReturnedFromProvider = new HashSet<URI>();
    private Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap = null;
    private final Set<URI> allCurrentUnManagedCgURIs = new HashSet<URI>();
    private List<UnManagedConsistencyGroup> unManagedCGToUpdate = null;
    private List<UnManagedExportMask> unManagedExportMasksToCreate = null;
    private List<UnManagedExportMask> unManagedExportMasksToUpdate = null;
    private final Set<URI> allCurrentUnManagedExportMaskUris = new HashSet<URI>();

    List<UnManagedFileSystem> unManagedFilesystemsInsert = null;
    List<UnManagedFileSystem> unManagedFilesystemsUpdate = null;
    Set<URI> unManagedFilesystemsReturnedFromProvider = new HashSet<URI>();

    List<UnManagedFileExportRule> unManagedExportRulesInsert = null;
    List<UnManagedFileExportRule> unManagedExportRulesUpdate = null;

    List<UnManagedCifsShareACL> unManagedCifsAclInsert = null;
    List<UnManagedCifsShareACL> unManagedCifsAclUpdate = null;

    List<UnManagedFileQuotaDirectory> unManagedTreeQuotaInsert = null;
    List<UnManagedFileQuotaDirectory> unManagedTreeQuotaUpdate = null;

    public void setVnxeApiClientFactory(VNXeApiClientFactory vnxeApiClientFactory) {
        this.vnxeApiClientFactory = vnxeApiClientFactory;
    }

    public void discoverUnManagedVolumes(AccessProfile accessProfile, DbClient dbClient,
            CoordinatorClient coordinator, PartitionManager partitionManager) throws Exception {

        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        VNXeApiClient apiClient = getVnxUnityClient(accessProfile);

        unManagedVolumesInsert = new ArrayList<UnManagedVolume>();
        unManagedVolumesUpdate = new ArrayList<UnManagedVolume>();
        unManagedCGToUpdateMap = new HashMap<String, UnManagedConsistencyGroup>();

        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());

        List<VNXeLun> luns = apiClient.getAllLuns();

        if (luns != null && !luns.isEmpty()) {
            Map<String, StoragePool> pools = getStoragePoolMap(storageSystem, dbClient);
            Map<String, List<UnManagedVolume>> hostVolumesMap = new HashMap<String, List<UnManagedVolume>>();
            for (VNXeLun lun : luns) {
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
                        dbClient, hostVolumesMap);

                unManagedVolumesReturnedFromProvider.add(unManagedVolume.getId());
                Boolean isVolumeInCG = lun.getType() == VNXeApiClient.GENERIC_STORAGE_LUN_TYPE ? true : false;
                String cgId = null;
                if (isVolumeInCG) {
                    cgId = lun.getStorageResource().getId();
                    addObjectToUnManagedConsistencyGroup(apiClient, unManagedVolume, cgId, storageSystem, dbClient);
                } else {
                    // Make sure the unManagedVolume object does not contain CG information from previous discovery
                    unManagedVolume.getVolumeCharacterstics().put(
                            SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
                    // set the uri of the unmanaged CG in the unmanaged volume object to empty
                    unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
                            "");

                }

                // Discover snaps
                Integer snapCount = lun.getSnapCount();
                boolean hasSnap = false;
                if (snapCount > 0) {
                    List<Snap> snaps = apiClient.getSnapshotsForLun(lun.getId());
                    if (snaps != null && !snaps.isEmpty()) {
                        StringSet parentMatchedVPools = unManagedVolume.getSupportedVpoolUris();
                        StringSet discoveredSnaps = discoverVolumeSnaps(storageSystem, snaps, unManagedVolumeNatvieGuid,
                                parentMatchedVPools, apiClient,
                                dbClient, hostVolumesMap, lun, isVolumeInCG, cgId);
                        if (discoveredSnaps != null && !discoveredSnaps.isEmpty()) {
                            hasSnap = true;
                            unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                                    Boolean.TRUE.toString());
                            StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
                            if (unManagedVolumeInformation.containsKey(SupportedVolumeInformation.SNAPSHOTS.toString())) {

                                // replace with new StringSet
                                unManagedVolumeInformation.get(
                                        SupportedVolumeInformation.SNAPSHOTS.toString()).replace(discoveredSnaps);
                                log.info("Replaced snaps :" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                                        SupportedVolumeInformation.SNAPSHOTS.toString())));
                            } else {
                                unManagedVolumeInformation.put(
                                        SupportedVolumeInformation.SNAPSHOTS.toString(), discoveredSnaps);
                            }
                        }
                    }

                }
                if (!hasSnap) {
                    // no snap
                    unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.HAS_REPLICAS.toString(),
                            Boolean.FALSE.toString());
                    StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
                    if (unManagedVolumeInformation != null &&
                            unManagedVolumeInformation.containsKey(SupportedVolumeInformation.SNAPSHOTS.toString())) {
                        // replace with empty string set doesn't work, hence added explicit code to remove all
                        unManagedVolumeInformation.get(
                                SupportedVolumeInformation.SNAPSHOTS.toString()).clear();
                    }
                }
            }

            if (!unManagedCGToUpdateMap.isEmpty()) {
                unManagedCGToUpdate = new ArrayList<UnManagedConsistencyGroup>(unManagedCGToUpdateMap.values());
                partitionManager.updateAndReIndexInBatches(unManagedCGToUpdate,
                        unManagedCGToUpdate.size(), dbClient, UNMANAGED_CONSISTENCY_GROUP);
                unManagedCGToUpdate.clear();
            }

            if (!unManagedVolumesInsert.isEmpty()) {
                partitionManager.insertInBatches(unManagedVolumesInsert,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            }
            if (!unManagedVolumesUpdate.isEmpty()) {
                partitionManager.updateAndReIndexInBatches(unManagedVolumesUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            }

            // Process those active unmanaged volume objects available in database but not in newly discovered items, to
            // mark them inactive.
            DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, unManagedVolumesReturnedFromProvider, dbClient, partitionManager);
            // Process those active unmanaged consistency group objects available in database but not in newly
            // discovered items, to mark them
            // inactive.
            DiscoveryUtils.performUnManagedConsistencyGroupsBookKeeping(storageSystem, allCurrentUnManagedCgURIs, dbClient,
                    partitionManager);

            // Next discover the unmanaged export masks
            discoverUnmanagedExportMasks(storageSystem.getId(), hostVolumesMap, apiClient, dbClient, partitionManager);
        } else {
            log.info("There are no luns found on the system: {}", storageSystem.getId());
        }

    }

    public void discoverUnManagedFileSystems(AccessProfile accessProfile, DbClient dbClient,
            CoordinatorClient coordinator, PartitionManager partitionManager) throws Exception {
        log.info("Started discovery of UnManagedFilesystems for system {}", accessProfile.getSystemId());
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());

        VNXeApiClient apiClient = getVnxUnityClient(accessProfile);

        unManagedFilesystemsInsert = new ArrayList<UnManagedFileSystem>();
        unManagedFilesystemsUpdate = new ArrayList<UnManagedFileSystem>();

        List<VNXeFileSystem> filesystems = apiClient.getAllFileSystems();
        if (filesystems != null && !filesystems.isEmpty()) {
            Map<String, StoragePool> pools = getStoragePoolMap(storageSystem, dbClient);

            for (VNXeFileSystem fs : filesystems) {
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

            // Process those active unmanaged fs objects available in database but not in newly discovered items, to
            // mark them inactive.
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
        return (result.iterator().hasNext());
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
        VNXeApiClient apiClient = getVnxUnityClient(accessProfile);
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
                                            .toString(),
                                    Boolean.TRUE.toString());
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
        }

        if (!unManagedExportRulesInsert.isEmpty()) {
            // Add UnManage export rules
            partitionManager.insertInBatches(unManagedExportRulesInsert,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_RULE);
        }

        if (!unManagedExportRulesUpdate.isEmpty()) {
            // Update UnManage export rules
            partitionManager.updateInBatches(unManagedExportRulesUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_RULE);
        }

        if (!unManagedFilesystemsUpdate.isEmpty()) {
            // Update UnManagedFilesystem
            partitionManager.updateInBatches(unManagedFilesystemsUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILESYSTEM);
        }
    }

    public void discoverAllCifsShares(AccessProfile accessProfile,
            DbClient dbClient, PartitionManager partitionManager) {

        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        VNXeApiClient apiClient = getVnxUnityClient(accessProfile);
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
                                    .toString(),
                            Boolean.TRUE.toString());
                    dbClient.updateObject(unManagedFs);

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
            unManagedSMBFileShare.setNativeId(exp.getId());
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
     * @param hostVolumeMap
     *            hosts and exported volumes map
     * @return
     */
    private UnManagedVolume createUnManagedVolume(UnManagedVolume unManagedVolume, String unManagedVolumeNativeGuid,
            VNXeLun lun, StorageSystem system, StoragePool pool, DbClient dbClient, Map<String, List<UnManagedVolume>> hostVolumeMap) {
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

        Map<String, StringSet> unManagedVolumeInformation = new HashMap<String, StringSet>();
        Map<String, String> unManagedVolumeCharacteristics = new HashMap<String, String>();

        Boolean isVolumeExported = false;

        if (lun.getHostAccess() != null && !lun.getHostAccess().isEmpty()) {
            // clear the previous unmanaged export masks, initiators if any. The latest export masks will be updated
            // later.
            unManagedVolume.getUnmanagedExportMasks().clear();
            unManagedVolume.getInitiatorNetworkIds().clear();
            unManagedVolume.getInitiatorUris().clear();
            for (BlockHostAccess access : lun.getHostAccess()) {
                int accessMask = access.getAccessMask();
                if (accessMask == BlockHostAccess.HostLUNAccessEnum.BOTH.getValue() ||
                        accessMask == BlockHostAccess.HostLUNAccessEnum.PRODUCTION.getValue()) {
                    isVolumeExported = true;
                    String hostId = access.getHost().getId();
                    List<UnManagedVolume> exportedVolumes = hostVolumeMap.get(hostId);
                    if (exportedVolumes == null) {
                        exportedVolumes = new ArrayList<UnManagedVolume>();
                        hostVolumeMap.put(hostId, exportedVolumes);
                    }
                    exportedVolumes.add(unManagedVolume);
                }
            }
        }

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

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

        unManagedVolume.addVolumeInformation(unManagedVolumeInformation);

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
                            .toString(),
                    Boolean.TRUE.toString());
        } else {
            unManagedFileSystemCharacteristics.put(
                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(),
                    Boolean.FALSE.toString());
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(),
                Boolean.FALSE.toString());

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
                        .toString(),
                Boolean.TRUE.toString());
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
                        .toString(),
                allocatedCapacity);

        StringSet provisionedCapacity = new StringSet();
        String capacity = String.valueOf(fileSystem.getSizeTotal());
        provisionedCapacity.add(capacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(),
                provisionedCapacity);

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
     * Get the Vnx Unity service client for making requests to the Vnxe based
     * on the passed profile.
     * 
     * @param accessProfile
     *            A reference to the access profile.
     * 
     * @return A reference to the Vnxe service client.
     */
    private VNXeApiClient getVnxUnityClient(AccessProfile accessProfile) {
        VNXeApiClient client = vnxeApiClientFactory.getUnityClient(accessProfile.getIpAddress(),
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

    public void discoverAllTreeQuotas(AccessProfile accessProfile, DbClient dbClient, PartitionManager partitionManager) {

        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
        VNXeApiClient apiClient = getVnxUnityClient(accessProfile);
        log.info("discoverAllTreeQuotas for storage system {} - start", storageSystem.getId());

        unManagedTreeQuotaInsert = new ArrayList<UnManagedFileQuotaDirectory>();
        unManagedTreeQuotaUpdate = new ArrayList<UnManagedFileQuotaDirectory>();

        List<VNXUnityTreeQuota> treeQuotas = apiClient.getAllTreeQuotas();

        for (VNXUnityTreeQuota quota : treeQuotas) {
            log.info("Discovered fS tree quota {}", quota.toString());

            VNXeFileSystem fs = null;
            if (quota.getFilesystem() != null) {
                fs = apiClient.getFileSystemByFSId(quota.getFilesystem().getId());
                String fsNativeGUID = NativeGUIDGenerator.generateNativeGuid(storageSystem.getSystemType(), storageSystem.getSerialNumber(),
                        fs.getId());

                try {
                    if (checkStorageFileSystemExistsInDB(fsNativeGUID, dbClient)) {
                        log.info("Skipping file system {} as it is already managed by ViPR", fsNativeGUID);
                        continue;
                    }
                    String nativeUnmanagedGUID = NativeGUIDGenerator.generateNativeGuidForPreExistingQuotaDirectory(
                            storageSystem.getSystemType(), storageSystem.getSerialNumber(), quota.getId());

                    VNXUnityQuotaConfig qc = apiClient.getQuotaConfigById(quota.getQuotaConfigId());

                    UnManagedFileQuotaDirectory unManagedFileQuotaDirectory = new UnManagedFileQuotaDirectory();
                    unManagedFileQuotaDirectory.setId(URIUtil.createId(UnManagedFileQuotaDirectory.class));
                    unManagedFileQuotaDirectory.setLabel(quota.getPath().substring(1));
                    unManagedFileQuotaDirectory.setNativeGuid(nativeUnmanagedGUID);
                    unManagedFileQuotaDirectory.setParentFSNativeGuid(fsNativeGUID);
                    unManagedFileQuotaDirectory.setSize(quota.getHardLimit());
                    Long size = quota.getHardLimit() > 0 ? quota.getHardLimit() : fs.getSizeAllocated();
                    Long softLimit = 0L;
                    if (quota.getSoftLimit() > 0) {
                        softLimit = quota.getSoftLimit() * 100 / size;
                        int softGrace = qc.getGracePeriod() / (24 * 60 * 60);
                        unManagedFileQuotaDirectory.setSoftGrace(softGrace);
                    }
                    unManagedFileQuotaDirectory.setSoftLimit(softLimit.intValue());
                    unManagedFileQuotaDirectory.setNotificationLimit(0);
                    unManagedFileQuotaDirectory.setNativeId(quota.getId());
                    if (!checkUnManagedQuotaDirectoryExistsInDB(dbClient, nativeUnmanagedGUID)) {
                        unManagedTreeQuotaInsert.add(unManagedFileQuotaDirectory);
                    } else {
                        unManagedTreeQuotaUpdate.add(unManagedFileQuotaDirectory);
                    }

                } catch (IOException e) {
                    log.error("IOException occured in discoverAllTreeQuotas()", e);
                }
            }
        }
        if (!unManagedTreeQuotaInsert.isEmpty()) {
            // Add UnManagedFileSystem
            partitionManager.insertInBatches(unManagedTreeQuotaInsert,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILEQUOTADIR);
            unManagedTreeQuotaInsert.clear();
        }

        if (!unManagedTreeQuotaUpdate.isEmpty()) {
            // Update UnManagedFilesystem
            partitionManager.updateInBatches(unManagedTreeQuotaUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_FILEQUOTADIR);
            unManagedTreeQuotaUpdate.clear();
        }
    }

    private boolean checkUnManagedQuotaDirectoryExistsInDB(DbClient _dbClient, String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedFileQuotaDirectoryInfoNativeGUIdConstraint(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    /**
     * Adds the passed in unmanaged volume to an unmanaged consistency group object
     *
     * @param apiClient
     *            - connection to Unity REST interface
     * @param unManagedVolume
     *            - unmanaged volume associated with a consistency group
     * @param cgNameToProcess
     *            - consistency group being processed
     * @param storageSystem
     *            - storage system the objects are on
     * @param dbClient
     *            - dbclient
     * @throws Exception
     */
    private void addObjectToUnManagedConsistencyGroup(VNXeApiClient apiClient, UnManagedVolume unManagedVolume,
            String cgNameToProcess, StorageSystem storageSystem, DbClient dbClient) throws Exception {

        log.info("Unmanaged volume {} belongs to consistency group {} on the array", unManagedVolume.getLabel(), cgNameToProcess);
        // Update the unManagedVolume object with CG information
        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(),
                Boolean.TRUE.toString());

        String unManagedCGNativeGuid = NativeGUIDGenerator.generateNativeGuidForCG(storageSystem.getNativeGuid(), cgNameToProcess);
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
                StorageResource res = apiClient.getStorageResource(cgNameToProcess);
                unManagedCG = createUnManagedCG(unManagedCGNativeGuid, res, storageSystem.getId(), dbClient);
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
        // add the unmanaged CG to the map of unmanaged CGs to be updated in the database once all volumes have been
        // processed
        unManagedCGToUpdateMap.put(unManagedCGNativeGuid, unManagedCG);
        // add the unmanaged CG to the current set of CGs being discovered on the array. This is for book keeping later.
        allCurrentUnManagedCgURIs.add(unManagedCG.getId());
    }

    /**
     * Creates a new UnManagedConsistencyGroup object in the database
     *
     * @param unManagedCGNativeGuid
     *            - nativeGuid of the unmanaged consistency group
     * @param res
     *            - unity consistency group returned from REST client
     * @param storageSystemURI
     *            - storage system of the consistency group
     * @param dbClient
     *            - database client
     * @return the new UnManagedConsistencyGroup object
     */
    private UnManagedConsistencyGroup createUnManagedCG(String unManagedCGNativeGuid,
            StorageResource res, URI storageSystemURI, DbClient dbClient) {
        UnManagedConsistencyGroup unManagedCG = new UnManagedConsistencyGroup();
        unManagedCG.setId(URIUtil.createId(UnManagedConsistencyGroup.class));
        unManagedCG.setLabel(res.getName());
        unManagedCG.setName(res.getName());
        unManagedCG.setNativeGuid(unManagedCGNativeGuid);
        unManagedCG.setStorageSystemUri(storageSystemURI);
        unManagedCG.setNumberOfVols(Integer.toString(res.getLuns().size()));
        dbClient.createObject(unManagedCG);
        return unManagedCG;
    }

    /**
     * Create unmanaged export masks per host
     *
     * @param systemId
     * @param hostVolumesMap
     *            host-- exportedvolume list
     * @param apiClient
     * @param dbClient
     * @param partitionManager
     * @throws Exception
     */
    private void discoverUnmanagedExportMasks(URI systemId, Map<String, List<UnManagedVolume>> hostVolumesMap,
            VNXeApiClient apiClient, DbClient dbClient, PartitionManager partitionManager)
                    throws Exception {
        unManagedExportMasksToCreate = new ArrayList<UnManagedExportMask>();
        unManagedExportMasksToUpdate = new ArrayList<UnManagedExportMask>();

        List<UnManagedVolume> unManagedExportVolumesToUpdate = new ArrayList<UnManagedVolume>();
        // In Unity, the volumes are exposed through all the storage ports.
        // Get all the storage ports to be added as known ports in the unmanaged export mask
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

        for (Map.Entry<String, List<UnManagedVolume>> entry : hostVolumesMap.entrySet()) {
            String hostId = entry.getKey();
            List<UnManagedVolume> volumes = entry.getValue();
            StringSet knownInitSet = new StringSet();
            StringSet knownNetworkIdSet = new StringSet();
            StringSet knownVolumeSet = new StringSet();
            List<Initiator> matchedFCInitiators = new ArrayList<Initiator>();

            VNXeHost host = apiClient.getHostById(hostId);
            List<VNXeBase> fcInits = host.getFcHostInitiators();
            List<VNXeBase> iScsiInits = host.getIscsiHostInitiators();
            if (fcInits != null && !fcInits.isEmpty()) {
                for (VNXeBase init : fcInits) {
                    VNXeHostInitiator initiator = apiClient.getHostInitiator(init.getId());
                    String portwwn = initiator.getPortWWN();
                    if (portwwn == null || portwwn.isEmpty()) {
                        continue;
                    }
                    Initiator knownInitiator = NetworkUtil.getInitiator(portwwn, dbClient);
                    if (knownInitiator != null) {
                        knownInitSet.add(knownInitiator.getId().toString());
                        knownNetworkIdSet.add(portwwn);
                        matchedFCInitiators.add(knownInitiator);
                    }
                }
            }
            if (iScsiInits != null && !iScsiInits.isEmpty()) {
                for (VNXeBase init : iScsiInits) {
                    VNXeHostInitiator initiator = apiClient.getHostInitiator(init.getId());
                    String portwwn = initiator.getPortWWN();
                    if (portwwn == null || portwwn.isEmpty()) {
                        continue;
                    }
                    Initiator knownInitiator = NetworkUtil.getInitiator(portwwn, dbClient);
                    if (knownInitiator != null) {
                        knownInitSet.add(knownInitiator.getId().toString());
                        knownNetworkIdSet.add(portwwn);
                    }
                }
            }
            if (knownNetworkIdSet.isEmpty()) {
                log.info(String.format("The host %s does not have any known initiators", hostId));
                continue;
            }
            String firstNetworkId = knownNetworkIdSet.iterator().next();
            UnManagedExportMask mask = getUnManagedExportMask(firstNetworkId, dbClient, systemId);
            mask.setStorageSystemUri(systemId);
            // set the host name as the mask name
            mask.setMaskName(host.getName());
            allCurrentUnManagedExportMaskUris.add(mask.getId());
            for (UnManagedVolume hostUnManagedVol : volumes) {
                hostUnManagedVol.getInitiatorNetworkIds().addAll(knownNetworkIdSet);
                hostUnManagedVol.getInitiatorUris().addAll(knownInitSet);
                hostUnManagedVol.getUnmanagedExportMasks().add(mask.getId().toString());
                mask.getUnmanagedVolumeUris().add(hostUnManagedVol.getId().toString());
                unManagedExportVolumesToUpdate.add(hostUnManagedVol);
            }

            mask.replaceNewWithOldResources(knownInitSet, knownNetworkIdSet, knownVolumeSet,
                    !matchedFCInitiators.isEmpty() ? knownFCStoragePortUris : knownIPStoragePortUris);

            updateZoningMap(mask, matchedFCInitiators, matchedFCPorts);
        }

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

    /**
     * Get existing unmanaged export mask, or create a new one.
     * 
     * @param knownInitiatorNetworkId
     *            The initiator network id (pwwn)
     * @param dbClient
     * @param systemURI
     * @return
     */
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
     * Set mask zoning map
     * 
     * @param mask
     * @param initiators
     * @param storagePorts
     */
    private void updateZoningMap(UnManagedExportMask mask, List<Initiator> initiators, List<StoragePort> storagePorts) {
        ZoneInfoMap zoningMap = networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts);
        for (ZoneInfo zoneInfo : zoningMap.values()) {
            log.info("Found zone: {} for initiator {} and port {}", new Object[] { zoneInfo.getZoneName(),
                    zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn() });
        }
        mask.setZoningMap(zoningMap);
    }

    /**
     * Discover Lun Snaps, and create UnManagedVolume for the snaps
     * 
     * @param system
     * @param snaps
     * @param parentGUID
     * @param parentMatchedVPools
     * @param apiClient
     * @param dbClient
     * @param hostVolumesMap
     * @param lun
     * @param isSnapInCG
     * @param cgName
     * @return
     * @throws Exception
     */
    private StringSet discoverVolumeSnaps(StorageSystem system, List<Snap> snaps, String parentGUID,
            StringSet parentMatchedVPools, VNXeApiClient apiClient, DbClient dbClient,
            Map<String, List<UnManagedVolume>> hostVolumesMap, VNXeLun lun, boolean isSnapInCG, String cgName) throws Exception {

        StringSet snapsets = new StringSet();

        for (Snap snapDetail : snaps) {

            UnManagedVolume unManagedVolume = null;

            String managedSnapNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                    system.getNativeGuid(), snapDetail.getId());
            BlockSnapshot viprSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
            if (null != viprSnap) {
                log.info("Skipping snapshot {} as it is already managed by ViPR", managedSnapNativeGuid);
                snapsets.add(managedSnapNativeGuid);
                continue;
            }

            String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                    system.getNativeGuid(), snapDetail.getId());

            unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                    unManagedVolumeNatvieGuid);

            unManagedVolume = createUnManagedVolumeForSnap(unManagedVolume, unManagedVolumeNatvieGuid, lun, system,
                    dbClient, hostVolumesMap, snapDetail);
            populateSnapInfo(unManagedVolume, snapDetail, parentGUID, parentMatchedVPools);
            snapsets.add(unManagedVolumeNatvieGuid);
            unManagedVolumesReturnedFromProvider.add(unManagedVolume.getId());

            if (isSnapInCG) {
                addObjectToUnManagedConsistencyGroup(apiClient, unManagedVolume, cgName, system, dbClient);

            }
        }

        return snapsets;
    }

    /**
     * Creates a new UnManagedVolume with the given arguments for a snap.
     * 
     * @param unManagedVolumeNativeGuid
     * @param lun
     * @param system
     * @param pool
     * @param dbClient
     * @param hostVolumeMap
     *            hosts and exported volumes map
     * @param snap
     *            detail of the snap
     * @return
     */
    private UnManagedVolume createUnManagedVolumeForSnap(UnManagedVolume unManagedVolume, String unManagedVolumeNativeGuid,
            VNXeLun lun, StorageSystem system, DbClient dbClient, Map<String, List<UnManagedVolume>> hostVolumeMap, Snap snap) {
        boolean created = false;
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNativeGuid);
            unManagedVolume.setStorageSystemUri(system.getId());
            created = true;
        }

        unManagedVolume.setLabel(snap.getName());

        Map<String, StringSet> unManagedVolumeInformation = new HashMap<String, StringSet>();
        Map<String, String> unManagedVolumeCharacteristics = new HashMap<String, String>();

        Boolean isSnapExported = false;

        if (lun.getHostAccess() != null && !lun.getHostAccess().isEmpty()) {
            // clear the previous unmanaged export masks, initiators if any. The latest export masks will be updated
            // later.
            unManagedVolume.getUnmanagedExportMasks().clear();
            unManagedVolume.getInitiatorNetworkIds().clear();
            unManagedVolume.getInitiatorUris().clear();
            for (BlockHostAccess access : lun.getHostAccess()) {
                int accessMask = access.getAccessMask();
                if (accessMask == BlockHostAccess.HostLUNAccessEnum.BOTH.getValue() ||
                        accessMask == BlockHostAccess.HostLUNAccessEnum.SNAPSHOT.getValue()) {
                    isSnapExported = true;
                    String hostId = access.getHost().getId();
                    List<UnManagedVolume> exportedSnaps = hostVolumeMap.get(hostId);
                    if (exportedSnaps == null) {
                        exportedSnaps = new ArrayList<UnManagedVolume>();
                        hostVolumeMap.put(hostId, exportedSnaps);
                    }
                    exportedSnaps.add(unManagedVolume);
                }
            }
        }

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isSnapExported.toString());

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(snap.getName());
        unManagedVolumeInformation.put(SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);

        String snapWWN = snap.getAttachedWWN();
        if (snapWWN != null && !snapWWN.isEmpty()) {
            String volumeWWN = snapWWN.replaceAll(":", "");
            unManagedVolume.setWwn(volumeWWN);
        }

        StringSet systemTypes = new StringSet();
        systemTypes.add(system.getSystemType());

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(snap.getSize()));
        unManagedVolumeInformation.put(SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(snap.getSize()));
        unManagedVolumeInformation.put(SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        unManagedVolumeInformation.put(SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(snap.getId());
        unManagedVolumeInformation.put(SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), Boolean.TRUE.toString());

        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                lun.getIsThinEnabled().toString());

        unManagedVolume.addVolumeInformation(unManagedVolumeInformation);

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
     * Populate snap detail info
     * 
     * @param unManagedVolume
     * @param snap
     * @param parentVolumeNatvieGuid
     * @param parentMatchedVPools
     */
    private void populateSnapInfo(UnManagedVolume unManagedVolume, Snap snap, String parentVolumeNatvieGuid,
            StringSet parentMatchedVPools) {
        log.info(String.format("populate snap:", snap.getName()));
        unManagedVolume.getVolumeCharacterstics().put(SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString(), Boolean.TRUE.toString());

        StringSet parentVol = new StringSet();
        parentVol.add(parentVolumeNatvieGuid);
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(Boolean.TRUE.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = snap.getIsReadOnly();
        isReadOnly.add(readOnly.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        VNXeBase snapGroup = snap.getSnapGroup();
        if (snapGroup != null) {
            unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(),
                snapGroup.getId());
        }

        log.debug("Matched Pools : {}", Joiner.on("\t").join(parentMatchedVPools));
        if (null == parentMatchedVPools || parentMatchedVPools.isEmpty()) {
            // Clearn all vpools as no matching vpools found.
            log.info("no parent pool");
            unManagedVolume.getSupportedVpoolUris().clear();
        } else {
            // replace with new StringSet
            unManagedVolume.getSupportedVpoolUris().replace(parentMatchedVPools);
            log.info("Replaced Pools :{}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
        }
    }
}
