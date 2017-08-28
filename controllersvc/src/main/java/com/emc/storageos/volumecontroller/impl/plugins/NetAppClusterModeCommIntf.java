/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileQuotaDirectory;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.file.FileExportUpdateParams.ExportSecurityType;
import com.emc.storageos.netapp.NetAppException;
import com.emc.storageos.netappc.NetAppCException;
import com.emc.storageos.netappc.NetAppClusterApi;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.iwave.ext.netapp.AggregateInfo;
import com.iwave.ext.netapp.model.ExportsHostnameInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.Qtree;
import com.iwave.ext.netapp.model.Quota;
import com.iwave.ext.netapp.model.SecurityRuleInfo;
import com.iwave.ext.netappc.SVMNetInfo;
import com.iwave.ext.netappc.StorageVirtualMachineInfo;
import com.iwave.ext.netappc.model.CifsAcl;



public class NetAppClusterModeCommIntf extends
        ExtendedCommunicationInterfaceImpl {

    private static final String SYSTEM_SERIAL_NUM = "system-serial-number";
    private static final String SYSTEM_FIRMWARE_REL = "version";
    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private static final String MANAGEMENT_INTERFACE = "_mgmt";
    private static final String DEFAULT_SVM = "vserver0";
    private static final int BYTESCONVERTER = 1024;
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String UNMANAGED_FILEQUOTADIR = "UnManagedFileQuotaDirectory";
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedExportRule";
    private static final String UNMANAGED_SHARE_ACL = "UnManagedCifsShareACL";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;
    private static final String RO = "ro";
    private static final String RW = "rw";
    private static final String ROOT = "root";
    private static final String DEFAULT_ANONMOUS_ACCESS = "nobody";
    private static final String ROOT_USER_ACCESS = "root";
    private static final String ROOT_UID = "0";
    private static final String NFS = "NFS";
    private static final String SPACE_GUARANTEE_NONE = "none";
    private static final String VOLUME_STATE_OFFLINE = "offline";
    private static final String VOL_ROOT = "/vol/";
    private static final String ROOT_VOL = "/vol0";

    List<UnManagedFileExportRule> unManagedExportRulesInsert = null;
    List<UnManagedFileExportRule> unManagedExportRulesUpdate = null;

    private static final Logger _logger = LoggerFactory
            .getLogger(NetAppClusterModeCommIntf.class);

    public static final List<String> ntpPropertiesList = Collections
            .unmodifiableList(Arrays.asList(
                    SupportedNtpFileSystemInformation.ALLOCATED_CAPACITY
                            .toString(),
                    SupportedNtpFileSystemInformation.PROVISIONED_CAPACITY
                            .toString(),
                    SupportedNtpFileSystemInformation.STORAGE_POOL.toString(),
                    SupportedNtpFileSystemInformation.NAME.toString(),
                    SupportedNtpFileSystemInformation.SVM.toString(),
                    SupportedNtpFileSystemInformation.IS_SVM_ROOT.toString(),
                    SupportedNtpFileSystemInformation.IS_NODE_ROOT.toString(),
                    SupportedNtpFileSystemInformation.PATH.toString(),
                    SupportedNtpFileSystemInformation.SPACE_GUARANTEE.toString(),
                    SupportedNtpFileSystemInformation.STATE.toString()));

    public enum SupportedNtpFileSystemInformation {
        ALLOCATED_CAPACITY("size-used"), PROVISIONED_CAPACITY("size-total"), STORAGE_POOL(
                "containing-aggregate-name"), NATIVE_GUID("NativeGuid"), NAME("name"), SVM(
                        "owning-vserver-name"), IS_SVM_ROOT("is-vserver-root"), IS_NODE_ROOT(
                                "is-node-root"), PATH("junction-path"), SPACE_GUARANTEE(
                                        "space-guarantee"), STATE("state");

        private String _infoKey;

        SupportedNtpFileSystemInformation(String infoKey) {
            _infoKey = infoKey;
        }

        public String getInfoKey() {
            return _infoKey;
        }

        public static String getFileSystemInformation(String infoKey) {
            for (SupportedNtpFileSystemInformation info : values()) {
                if (infoKey.equals(info.name().toString())) {
                    return info.getInfoKey();
                }
            }
            return null;
        }
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

    @Override
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

    /**
     * Discover a NetApp Cluster mode array along with its Storage Pools, Port Groups and Storage Ports
     * 
     * @param accessProfile
     *            access profile contains credentials to contact the device.
     * @throws BaseCollectionException
     */
    @Override
    public void discover(AccessProfile accessProfile)
            throws BaseCollectionException {
        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                        .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                                .toString()))) {
            discoverUmanagedFileSystems(accessProfile);
            discoverUmanagedFileQuotaDirectory(accessProfile);
            discoverUnManagedCifsShares(accessProfile);
            discoverUnManagedNewExports(accessProfile);
        } else {
            discoverAll(accessProfile);
        }
    }

    private void discoverUmanagedFileSystems(AccessProfile profile) {
        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);

        if (null == storageSystem) {
            return;
        }

        String detailedStatusMessage = "Discovery of NetApp Cluster Mode Unmanaged FileSystem started";

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        int newFileSystemsCount = 0;
        int existingFileSystemsCount = 0;
        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();

        NetAppClusterApi netAppCApi = new NetAppClusterApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                        .https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(property));
        }

        try {

            StoragePort storagePort = getStoragePortPool(storageSystem);

            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);

            HashMap<String, StoragePool> pools = new HashMap();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                pools.put(storagePool.getNativeGuid(), storagePool);
            }

            // Retrieve all the file system and SVM info.
            List<Map<String, String>> fileSystemInfo = netAppCApi.listVolumeInfo(null, attrs);
            List<StorageVirtualMachineInfo> svms = netAppCApi.listSVM();

            for (Map<String, String> fileSystemChar : fileSystemInfo) {
                String poolName = fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.STORAGE_POOL
                                        .toString()));

                String filesystem = fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                                        .toString()));

                if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(filesystem)) {
                    // skipping this file system because the filter doesn't match
                    continue;
                }

                boolean isSVMRootVolume = Boolean.valueOf(fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.IS_SVM_ROOT
                                        .toString())));

                boolean isNodeRootVolume = Boolean.valueOf(fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.IS_NODE_ROOT
                                        .toString())));

                String path = fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.PATH
                                        .toString()));

                String state = fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.STATE
                                        .toString()));

                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystem,
                        poolName, NativeGUIDGenerator.POOL);
                StoragePool pool = pools.get(poolNativeGuid);
                
                String nativeId;
                if("".equals(filesystem)){
                    continue;
                }
                
                if (filesystem.startsWith(VOL_ROOT)) {
                    nativeId = filesystem;
                } else {
                    nativeId = VOL_ROOT + filesystem;
                }

                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem.getSystemType(),
                        storageSystem.getSerialNumber(), nativeId);

                // Ignore export for root volume and don't pull it into ViPR db.
                if (isNodeRootVolume || isSVMRootVolume) {
                    _logger.info("Ignore and not discover root" + filesystem + "on NTP array");
                    continue;
                }

                // Ignore volume that is offline and don't pull it into ViPR db.
                if (state.equalsIgnoreCase(VOLUME_STATE_OFFLINE)) {
                    _logger.info("Ignoring volume " + filesystem + " as it is offline");
                    continue;
                }

                // If the filesystem already exists in db..just continue.No Need
                // to create an UnManaged Filesystems.
                if (checkStorageFileSystemExistsInDB(fsNativeGuid)) {
                    continue;
                }

                _logger.debug("retrieve info for file system: " + filesystem);
                String svm = getOwningSVM(filesystem, fileSystemInfo);

                String address = getSVMAddress(svm, svms);
                if (svm != null && !svm.isEmpty()) {
                    // Need to use storage port for SVM.
                    String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystem,
                            address,
                            NativeGUIDGenerator.PORT);

                    storagePort = getSVMStoragePort(storageSystem, portNativeGuid, svm);
                }

                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                        storageSystem.getSystemType(), storageSystem.getSerialNumber().toUpperCase(), nativeId);

                UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                boolean alreadyExist = unManagedFs == null ? false : true;
                unManagedFs = createUnManagedFileSystem(unManagedFs, profile,
                        fsUnManagedFsNativeGuid, nativeId, storageSystem, pool, filesystem, storagePort, fileSystemChar);
                if (alreadyExist) {
                    existingUnManagedFileSystems.add(unManagedFs);
                    existingFileSystemsCount++;
                } else {
                    unManagedFileSystems.add(unManagedFs);
                    newFileSystemsCount++;
                }
                allDiscoveredUnManagedFileSystems.add(unManagedFs.getId());
                /**
                 * Persist 200 objects and clear them to avoid memory issue
                 */
                validateListSizeLimitAndPersist(unManagedFileSystems, existingUnManagedFileSystems, Constants.DEFAULT_PARTITION_SIZE * 2);
            }

            // Process those active unmanaged fs objects available in database but not in newly discovered items, to
            // mark them inactive.
            markUnManagedFSObjectsInActive(storageSystem, allDiscoveredUnManagedFileSystems);
            _logger.info("New unmanaged NetappC file systems count: {}", newFileSystemsCount);
            _logger.info("Update unmanaged NetappC file systems count: {}", existingFileSystemsCount);
            if (!unManagedFileSystems.isEmpty()) {
                // Add UnManagedFileSystem
                _partitionManager.insertInBatches(unManagedFileSystems,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_FILESYSTEM);
            }

            if (!existingUnManagedFileSystems.isEmpty()) {
                // Update UnManagedFilesystem
                _partitionManager.updateAndReIndexInBatches(existingUnManagedFileSystems,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_FILESYSTEM);
            }
            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for NetAppC: %s",
                    storageSystemId.toString());

        } catch (NetAppCException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + storageSystemId);
            throw ve;
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
            throw NetAppCException.exceptions.discoveryFailed(storageSystemId.toString(), e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    private void discoverUmanagedFileQuotaDirectory(AccessProfile profile) {
        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);

        if (null == storageSystem) {
            return;
        }

        NetAppClusterApi netAppCApi = new NetAppClusterApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                        .https(true).build();
        try {
            // Retrieve all the qtree info.
            List<Qtree> qtrees = netAppCApi.listQtrees();
            List<Quota> quotas;
            try {// Currently there are no API's available to check the quota status in general
                quotas = netAppCApi.listQuotas();// TODO check weather quota is on before doing this call
            } catch (Exception e) {
                _logger.error("Error while fetching quotas", e.getMessage());
                return;
            }
            if (quotas != null) {
                Map<String, Qtree> qTreeNameQTreeMap = new HashMap<>();
                qtrees.forEach(qtree -> {
                    if (qtree.getQtree() != null && !qtree.getQtree().equals("")) {
                        qTreeNameQTreeMap.put(qtree.getVolume() + qtree.getQtree(), qtree);
                    }
                });

                List<UnManagedFileQuotaDirectory> unManagedFileQuotaDirectories = new ArrayList<>();
                List<UnManagedFileQuotaDirectory> existingUnManagedFileQuotaDirectories = new ArrayList<>();
                String tempVolume=null;
                String tempQuotaTree=null;
                String tempQuotaTarget=null;

                for (Quota quota : quotas) {
                    /* Temporary fix TODO 
                     * Fix for situations where QuotaTree is null
                     * Extracting QuotaTree id from quotaTarget.
                     */
                    if ("".equals(quota.getQtree())) {
                        tempQuotaTarget = quota.getQuotaTarget();
                        tempVolume = quota.getVolume();
                        if (!"".equals(tempVolume)) {
                            Pattern pattern = Pattern.compile(tempVolume + "/(.*$)");
                            Matcher matcher = pattern.matcher(tempQuotaTarget);
                            if (matcher.find()) {
                                tempQuotaTree = matcher.group(1);
                            }
                            if ("".equals(tempQuotaTree)) {
                                continue;
                            }
                            quota.setQtree(tempQuotaTree);
                        } else {
                            continue;
                        }
                    }
                    String fsNativeId;
                    if (quota.getVolume().startsWith(VOL_ROOT)) {
                        fsNativeId = quota.getVolume();
                    } else {
                        fsNativeId = VOL_ROOT + quota.getVolume();
                    }

                    if (fsNativeId.contains(ROOT_VOL)) {
                        _logger.info("Ignore and not discover root filesystem on NTP array");
                        continue;
                    }

                    String fsNativeGUID = NativeGUIDGenerator.generateNativeGuid(storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), fsNativeId);

                    String nativeGUID = NativeGUIDGenerator.generateNativeGuidForQuotaDir(storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), quota.getQtree(), quota.getVolume());

                    String nativeUnmanagedGUID = NativeGUIDGenerator.generateNativeGuidForUnManagedQuotaDir(storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), quota.getQtree(), quota.getVolume());
                    if (checkStorageQuotaDirectoryExistsInDB(nativeGUID)) {
                        continue;
                    }

                    UnManagedFileQuotaDirectory unManagedFileQuotaDirectory = new UnManagedFileQuotaDirectory();
                    unManagedFileQuotaDirectory.setId(URIUtil.createId(UnManagedFileQuotaDirectory.class));
                    unManagedFileQuotaDirectory.setLabel(quota.getQtree());
                    unManagedFileQuotaDirectory.setNativeGuid(nativeUnmanagedGUID);
                    unManagedFileQuotaDirectory.setParentFSNativeGuid(fsNativeGUID);
                    unManagedFileQuotaDirectory.setNativeId("/vol/" + quota.getVolume() + "/" + quota.getQtree());
                    if ("enabled".equals(qTreeNameQTreeMap.get(quota.getVolume() + quota.getQtree()).getOplocks())) {
                        unManagedFileQuotaDirectory.setOpLock(true);
                    }
                    unManagedFileQuotaDirectory.setSize(Long.valueOf(quota.getDiskLimit()));

                    if (!checkUnManagedQuotaDirectoryExistsInDB(nativeUnmanagedGUID)) {
                        unManagedFileQuotaDirectories.add(unManagedFileQuotaDirectory);
                    } else {
                        existingUnManagedFileQuotaDirectories.add(unManagedFileQuotaDirectory);
                    }

                }

                if (!unManagedFileQuotaDirectories.isEmpty()) {
                    _partitionManager.insertInBatches(unManagedFileQuotaDirectories,
                            Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                            UNMANAGED_FILEQUOTADIR);
                }

                if (!existingUnManagedFileQuotaDirectories.isEmpty()) {
                    _partitionManager.updateAndReIndexInBatches(existingUnManagedFileQuotaDirectories,
                            Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                            UNMANAGED_FILEQUOTADIR);
                }
            }

        } catch (NetAppException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + storageSystemId);
            throw ve;
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
            throw NetAppException.exceptions.discoveryFailed(storageSystemId.toString(), e);
        }
    }

    /**
     * check Storage quotadir exists in DB
     * 
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    private boolean checkStorageQuotaDirectoryExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getQuotaDirsByNativeGuid(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    private boolean checkUnManagedQuotaDirectoryExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedFileQuotaDirectoryInfoNativeGUIdConstraint(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    private void discoverUnManagedNewExports(AccessProfile profile) {
        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        storageSystem
                .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                        .toString());
        String detailedStatusMessage = "Discovery of NetAppC Unmanaged Exports started";

        unManagedExportRulesInsert = new ArrayList<UnManagedFileExportRule>();
        unManagedExportRulesUpdate = new ArrayList<UnManagedFileExportRule>();

        // Used to Save the rules to DB
        List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();

        NetAppClusterApi netAppCApi = new NetAppClusterApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                        .https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(property));
        }

        try {
            List<Map<String, String>> fileSystemInfo = netAppCApi.listVolumeInfo(null, attrs);
            List<StorageVirtualMachineInfo> svms = netAppCApi.listSVM();
            for (StorageVirtualMachineInfo svmInfo : svms) {
                netAppCApi = new NetAppClusterApi.Builder(
                        storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                        storageSystem.getUsername(), storageSystem.getPassword())
                                .https(true).svm(svmInfo.getName()).build();
                // Get exports on the array and loop through each export.
                List<ExportsRuleInfo> exports = netAppCApi.listNFSExportRules(null);

                // Verification Utility
                UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
                        _dbClient);

                for (ExportsRuleInfo deviceExport : exports) {
                    String filesystem = deviceExport.getPathname();
                    _logger.info("Export Path {}", filesystem);

                    String nativeId = filesystem;

                    String fsUnManagedFsNativeGuid = NativeGUIDGenerator
                            .generateNativeGuidForPreExistingFileSystem(
                                    storageSystem.getSystemType(), storageSystem
                                            .getSerialNumber().toUpperCase(),
                                    nativeId);

                    UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                    boolean fsAlreadyExists = unManagedFs == null ? false : true;

                    // Used as for rules validation
                    List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();
                    List<UnManagedFileExportRule> unManagedExportRulesToInsert = new ArrayList<UnManagedFileExportRule>();
                    List<UnManagedFileExportRule> unManagedExportRulesToUpdate = new ArrayList<UnManagedFileExportRule>();

                    if (fsAlreadyExists) {
                        _logger.debug("retrieve info for file system: " + filesystem);
                        String svm = getOwningSVM(filesystem, fileSystemInfo);
                        // Use IP address of SVM.
                        String addr = getSVMAddress(svm, svms);

                        UnManagedFSExportMap tempUnManagedExpMap = new UnManagedFSExportMap();

                        createExportMap(deviceExport, tempUnManagedExpMap, addr);
                        if (tempUnManagedExpMap.size() > 0) {
                            unManagedFs
                                    .setFsUnManagedExportMap(tempUnManagedExpMap);
                            _logger.debug("Export map for NetAppC UMFS {} = {}", unManagedFs.getLabel(),
                                    unManagedFs.getFsUnManagedExportMap());
                        }

                        List<UnManagedFileExportRule> exportRules = applyAllSecurityRules(deviceExport, addr, unManagedFs.getId());
                        _logger.info("Number of export rules discovered for file system {} is {}", unManagedFs.getId(), exportRules.size());

                        for (UnManagedFileExportRule dbExportRule : exportRules) {
                            _logger.info("Un Managed File Export Rule : {}", dbExportRule);
                            String fsExportRulenativeId = dbExportRule.getFsExportIndex();
                            _logger.info("Native Id using to build Native Guid {}", fsExportRulenativeId);
                            String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
                                    .generateNativeGuidForPreExistingFileExportRule(
                                            storageSystem, fsExportRulenativeId);
                            _logger.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);

                            UnManagedFileExportRule unManagedExportRule = checkUnManagedFsExportRuleExistsInDB(_dbClient,
                                    fsUnManagedFileExportRuleNativeGuid);
                            UnManagedFileExportRule unManagedExpRule = null;

                            if (unManagedExportRule == null) {
                                unManagedExportRule = new UnManagedFileExportRule();
                                unManagedExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
                                unManagedExportRule.setFileSystemId(unManagedFs.getId());
                                unManagedExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
                                unManagedExpRule = copyProperties(unManagedExportRule, dbExportRule);
                                unManagedExportRulesToInsert.add(unManagedExpRule);
                                // Build all export rules list.
                                unManagedExportRules.add(unManagedExpRule);
                                _logger.info("Unmanaged File Export Rule : {}", unManagedExpRule);
                            } else {
                                dbExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
                                dbExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
                                unManagedExportRulesToInsert.add(dbExportRule);
                                // Build all export rules list.
                                unManagedExportRules.add(dbExportRule);
                                // Delete the existing rule!!
                                unManagedExportRule.setInactive(true);
                                unManagedExportRulesToUpdate.add(unManagedExportRule);
                                _logger.info("Unmanaged File Export Rule : {}", dbExportRule);
                            }

                        }

                        // Validate Rules Compatible with ViPR - Same rules should
                        // apply as per API SVC Validations.
                        if (!unManagedExportRules.isEmpty()) {
                            boolean isAllRulesValid = validationUtility
                                    .validateUnManagedExportRules(unManagedExportRules, false);
                            if (isAllRulesValid) {
                                _logger.info("Validating rules success for export {}", filesystem);
                                unManagedExportRulesInsert.addAll(unManagedExportRulesToInsert);
                                unManagedExportRulesUpdate.addAll(unManagedExportRulesToUpdate);
                                unManagedFs.setHasExports(true);
                                unManagedFs.putFileSystemCharacterstics(
                                        UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                                .toString(),
                                        TRUE);
                                _dbClient.persistObject(unManagedFs);
                                _logger.info("File System {} has Exports and their size is {}", unManagedFs.getId(),
                                        newUnManagedExportRules.size());
                            } else {
                                _logger.warn("Validating rules failed for export {}. Ignroing to import these rules into ViPR DB",
                                        filesystem);
                                // Delete the UMFS as it having invalid rule!!!
                                unManagedFs.setInactive(true);
                                _dbClient.persistObject(unManagedFs);
                            }
                        }
                        // Adding this additional logic to avoid OOM
                        if (unManagedExportRulesInsert.size() == MAX_UMFS_RECORD_SIZE) {
                            // Add UnManagedFileSystem
                            _partitionManager.insertInBatches(unManagedExportRulesInsert,
                                    Constants.DEFAULT_PARTITION_SIZE, _dbClient, UNMANAGED_EXPORT_RULE);
                            unManagedExportRulesInsert.clear();
                            unManagedExportRulesToInsert.clear();
                        }

                        if (unManagedExportRulesUpdate.size() == MAX_UMFS_RECORD_SIZE) {
                            // Update UnManagedFilesystem
                            _partitionManager.updateInBatches(unManagedExportRulesUpdate,
                                    Constants.DEFAULT_PARTITION_SIZE, _dbClient, UNMANAGED_EXPORT_RULE);
                            unManagedExportRulesUpdate.clear();
                            unManagedExportRulesToUpdate.clear();
                        }

                    } else {
                        _logger.info("FileSystem " + unManagedFs
                                + "is not present in ViPR DB. Hence ignoring "
                                + deviceExport + " export");
                    }
                }
            }

            if (!unManagedExportRulesInsert.isEmpty()) {
                // Add UnManagedFileSystem
                _partitionManager.insertInBatches(unManagedExportRulesInsert,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient, UNMANAGED_EXPORT_RULE);
                unManagedExportRulesInsert.clear();
            }

            if (!unManagedExportRulesUpdate.isEmpty()) {
                // Update UnManagedFilesystem
                _partitionManager.updateInBatches(unManagedExportRulesUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient, UNMANAGED_EXPORT_RULE);
                unManagedExportRulesUpdate.clear();
            }

            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                            .toString());
            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for NetAppC: %s",
                    storageSystemId.toString());

        } catch (NetAppCException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + storageSystemId);
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    public void discoverAll(AccessProfile accessProfile)
            throws BaseCollectionException {

        URI storageSystemId = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";

        try {
            _logger.info(
                    "Access Profile Details :  IpAddress : {}, PortNumber : {}",
                    accessProfile.getIpAddress(), accessProfile.getPortNumber());
            storageSystemId = accessProfile.getSystemId();
            storageSystem = _dbClient.queryObject(StorageSystem.class,
                    storageSystemId);
            // Retrieve NetAppC Filer information.
            discoverFilerInfo(storageSystem);

            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(storageSystem.getSystemType()));
            String firmwareVersion = storageSystem.getFirmwareVersion();
            // Example version String for NetappC looks like 8.2
            _logger.info("Verifying version details : Minimum Supported Version {} - Discovered NetApp Cluster Mode Version {}",
                    minimumSupportedVersion, firmwareVersion);
            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, firmwareVersion) < 0) {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                storageSystem.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, storageSystem.getId());
                throw new NetAppCException(String.format(
                        " ** This version of NetApp Cluster Mode is not supported ** Should be a minimum of %s", minimumSupportedVersion));
            }
            storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            storageSystem.setReachableStatus(true);

            _dbClient.persistObject(storageSystem);
            if (!storageSystem.getReachableStatus()) {
                throw new NetAppCException("Failed to connect to "
                        + storageSystem.getIpAddress());
            }
            _completer.statusPending(_dbClient, "Identified physical storage");

            List<StorageVirtualMachineInfo> svms = new ArrayList<StorageVirtualMachineInfo>();
            Map<String, List<StorageHADomain>> groups = discoverPortGroups(storageSystem, svms);
            _logger.info("No of newly discovered groups {}", groups.get(NEW).size());
            _logger.info("No of existing discovered groups {}", groups.get(EXISTING).size());
            if (!groups.get(NEW).isEmpty()) {
                _dbClient.createObject(groups.get(NEW));
            }

            if (!groups.get(EXISTING).isEmpty()) {
                _dbClient.persistObject(groups.get(EXISTING));
            }

            List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();
            Map<String, List<StoragePool>> pools = discoverStoragePools(storageSystem, poolsToMatchWithVpool);
            _logger.info("No of newly discovered pools {}", pools.get(NEW).size());
            _logger.info("No of existing discovered pools {}", pools.get(EXISTING).size());
            if (!pools.get(NEW).isEmpty()) {
                allPools.addAll(pools.get(NEW));
                _dbClient.createObject(pools.get(NEW));
            }

            if (!pools.get(EXISTING).isEmpty()) {
                allPools.addAll(pools.get(EXISTING));
                _dbClient.persistObject(pools.get(EXISTING));
            }
            List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(
                    allPools, _dbClient, storageSystemId);
            if (notVisiblePools != null && !notVisiblePools.isEmpty()) {
                poolsToMatchWithVpool.addAll(notVisiblePools);
            }
            _completer.statusPending(_dbClient, "Completed pool discovery");

            // discover ports
            List<StoragePort> allPorts = new ArrayList<StoragePort>();
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, svms, groups.get(NEW));
            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if (!ports.get(NEW).isEmpty()) {
                allPorts.addAll(ports.get(NEW));
                _dbClient.createObject(ports.get(NEW));
            }

            if (!ports.get(EXISTING).isEmpty()) {
                allPorts.addAll(ports.get(EXISTING));
                _dbClient.persistObject(ports.get(EXISTING));
            }
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(
                    allPorts, _dbClient, storageSystemId);
            _completer.statusPending(_dbClient, "Completed port discovery");
            List<StoragePort> allExistingPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                allExistingPorts.addAll(notVisiblePorts);
            }
            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports.get(NEW), allExistingPorts, _dbClient, _coordinator,
                    poolsToMatchWithVpool);

            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for Storage System: %s",
                    storageSystemId.toString());
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format(
                    "Discovery failed for Storage System: %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new NetAppCException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Discover the Control Station for the specified NTAP File storage array.
     * Since the StorageSystem object currently exists, this method updates
     * information in the object.
     * 
     * @param system
     * @throws NetAppCException
     */
    private void discoverFilerInfo(StorageSystem system) throws NetAppCException {
        _logger.info("Start Control Station discovery for storage system {}", system.getId());
        Map<String, String> systemInfo = new HashMap<String, String>();
        Map<String, String> systemVer = new HashMap<String, String>();
        NetAppClusterApi ncApi = new NetAppClusterApi.Builder(system.getIpAddress(),
                system.getPortNumber(), system.getUsername(),
                system.getPassword()).https(true).build();
        try {
            systemInfo = ncApi.clusterSystemInfo();
            systemVer = ncApi.systemVer();

            if ((null == systemInfo) || (systemInfo.size() <= 0)) {
                _logger.error("Failed to retrieve NetAppC Filer info!");
                system.setReachableStatus(false);
                return;
            }

            if ((null == systemVer) || (systemVer.size() <= 0)) {
                _logger.error("Failed to retrieve NetAppC Filer info!");
                system.setReachableStatus(false);
                return;
            }
            system.setReachableStatus(true);
            system.setSerialNumber(systemInfo.get(SYSTEM_SERIAL_NUM));
            String sysNativeGuid = NativeGUIDGenerator.generateNativeGuid(system);
            system.setNativeGuid(sysNativeGuid);
            system.setFirmwareVersion(systemVer.get(SYSTEM_FIRMWARE_REL));
            _logger.info(
                    "NetAppC Filer discovery for storage system {} complete",
                    system.getId());
        } catch (Exception e) {
            _logger.error("Failed to retrieve NetAppC Filer info!");
            system.setReachableStatus(false);
            String msg = "exception occurred while attempting to retrieve NetAppC filer information. Storage system: "
                    + system.getIpAddress() + " " + e.getMessage();
            _logger.error(msg);
            throw new NetAppCException(msg);
        }
    }

    /**
     * Discover the IP Interfaces/Storage Ports for NetApp Cluster mode array
     * 
     * @param system
     *            Storage system information
     * @return Map of new and existing storage ports
     * @throws NetAppCException
     */
    private Map<String, List<StoragePort>> discoverPorts(StorageSystem storageSystem,
            List<StorageVirtualMachineInfo> svms,
            List<StorageHADomain> haDomains)
                    throws NetAppCException {

        URI storageSystemId = storageSystem.getId();
        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();
        // Discover storage ports
        try {

            _logger.info("discoverPorts for storage system {} - start",
                    storageSystemId);

            StoragePort storagePort = null;
            if (svms != null && !svms.isEmpty()) {
                for (StorageVirtualMachineInfo svm : svms) {
                    for (SVMNetInfo intf : svm.getInterfaces()) {
                        if (intf.getRole().contains(MANAGEMENT_INTERFACE)) {
                            continue;
                        }
                        URIQueryResultList results = new URIQueryResultList();
                        String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystem,
                                intf.getIpAddress(),
                                NativeGUIDGenerator.PORT);
                        _dbClient.queryByConstraint(
                                AlternateIdConstraint.Factory
                                        .getStoragePortByNativeGuidConstraint(portNativeGuid),
                                results);
                        storagePort = null;
                        if (results.iterator().hasNext()) {
                            StoragePort tmpPort = _dbClient.queryObject(
                                    StoragePort.class, results.iterator()
                                            .next());
                            if (tmpPort.getStorageDevice().equals(storageSystem.getId())
                                    && tmpPort.getPortGroup().equals(svm.getName())) {
                                storagePort = tmpPort;
                                _logger.debug("found duplicate intf {}", intf.getIpAddress());
                            }
                        }

                        if (storagePort == null) {
                            storagePort = new StoragePort();
                            storagePort.setId(URIUtil
                                    .createId(StoragePort.class));
                            storagePort.setTransportType("IP");
                            storagePort.setNativeGuid(portNativeGuid);
                            storagePort.setLabel(portNativeGuid);
                            storagePort.setStorageDevice(storageSystemId);
                            storagePort.setPortName(intf.getIpAddress());
                            storagePort.setPortNetworkId(intf.getIpAddress());
                            storagePort.setPortGroup(svm.getName());
                            storagePort.setStorageHADomain(
                                    findMatchingHADomain(svm.getName(), haDomains));
                            storagePort.setRegistrationStatus(
                                    RegistrationStatus.REGISTERED.toString());
                            _logger.info(
                                    "Creating new storage port using NativeGuid : {}",
                                    portNativeGuid);
                            newStoragePorts.add(storagePort);
                        } else {
                            existingStoragePorts.add(storagePort);
                        }
                        storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                        storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                    }
                }
            } else {
                // Check if storage port was already discovered
                URIQueryResultList results = new URIQueryResultList();
                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, storageSystem.getIpAddress(),
                        NativeGUIDGenerator.PORT);
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePortByNativeGuidConstraint(portNativeGuid),
                        results);

                if (results.iterator().hasNext()) {
                    StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                            results.iterator().next());
                    if (tmpPort.getStorageDevice().equals(storageSystem.getId())
                            && tmpPort.getPortGroup().equals(
                                    storageSystem.getSerialNumber())) {
                        storagePort = tmpPort;
                        _logger.debug("found duplicate dm intf {}",
                                storageSystem.getSerialNumber());
                    }
                }

                if (storagePort == null) {
                    // Create NetAppC storage port for IP address
                    storagePort = new StoragePort();
                    storagePort.setId(URIUtil.createId(StoragePort.class));
                    storagePort.setTransportType("IP");
                    storagePort.setNativeGuid(portNativeGuid);
                    storagePort.setLabel(portNativeGuid);
                    storagePort.setStorageDevice(storageSystemId);
                    storagePort.setPortName(storageSystem.getIpAddress());
                    storagePort.setPortNetworkId(storageSystem.getIpAddress());
                    storagePort.setPortGroup(storageSystem.getSerialNumber());
                    storagePort.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    _logger.info("Creating new storage port using NativeGuid : {}",
                            portNativeGuid);
                    newStoragePorts.add(storagePort);
                } else {
                    existingStoragePorts.add(storagePort);
                }

                storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }

            _logger.info("discoverPorts for storage system {} - complete",
                    storageSystemId);
            storagePorts.put(NEW, newStoragePorts);
            storagePorts.put(EXISTING, existingStoragePorts);
            return storagePorts;
        } catch (Exception e) {
            _logger.error("discoverPorts failed. Storage system: "
                    + storageSystemId, e);
            throw new NetAppCException(
                    "discoverPorts failed. Storage system: " + storageSystemId,
                    e);
        }
    }

    /**
     * Discover the Storage Virtual Machines (Port Groups) for NetApp Cluster mode array
     * 
     * @param system
     *            Storage system information
     * @return Map of new and existing port groups
     * @throws NetAppCException
     */
    private HashMap<String, List<StorageHADomain>> discoverPortGroups(StorageSystem system,
            List<StorageVirtualMachineInfo> vServerList)
                    throws NetAppCException {

        HashMap<String, List<StorageHADomain>> portGroups = new HashMap<String, List<StorageHADomain>>();
        List<StorageHADomain> newPortGroups = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingPortGroups = new ArrayList<StorageHADomain>();

        _logger.info("Start port group discovery (vfilers) for storage system {}", system.getId());

        NetAppClusterApi netAppCApi = new NetAppClusterApi.Builder(system.getIpAddress(),
                system.getPortNumber(), system.getUsername(),
                system.getPassword()).https(true).build();

        StorageHADomain portGroup = null;
        List<StorageVirtualMachineInfo> svms = netAppCApi.listSVM();
        if (null == svms || svms.isEmpty()) {

            // Check if default port group was previously created.
            URIQueryResultList results = new URIQueryResultList();
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, DEFAULT_SVM, NativeGUIDGenerator.ADAPTER);
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                    results);

            if (results.iterator().hasNext()) {
                StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, results.iterator().next());

                if (tmpGroup.getStorageDeviceURI().equals(system.getId())) {
                    portGroup = tmpGroup;
                    _logger.debug("Found existing port group {} ", tmpGroup.getName());
                }
            }

            if (portGroup == null) {
                portGroup = new StorageHADomain();
                portGroup.setId(URIUtil.createId(StorageHADomain.class));
                portGroup.setName("NetAppC");
                portGroup.setVirtual(false);
                portGroup.setNativeGuid(adapterNativeGuid);
                portGroup.setStorageDeviceURI(system.getId());

                StringSet protocols = new StringSet();
                protocols.add(StorageProtocol.File.NFS.name());
                protocols.add(StorageProtocol.File.CIFS.name());
                portGroup.setFileSharingProtocols(protocols);

                newPortGroups.add(portGroup);
            } else {
                existingPortGroups.add(portGroup);
            }
        } else {
            _logger.debug("Number svms found: {}", svms.size());
            vServerList.addAll(svms);

            StringSet protocols = new StringSet();
            protocols.add(StorageProtocol.File.NFS.name());
            protocols.add(StorageProtocol.File.CIFS.name());

            for (StorageVirtualMachineInfo vs : svms) {
                _logger.debug("SVM name: {}", vs.getName());

                // Check if port group was previously discovered
                URIQueryResultList results = new URIQueryResultList();
                String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, vs.getName(), NativeGUIDGenerator.ADAPTER);
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                        results);

                portGroup = null;
                if (results.iterator().hasNext()) {
                    StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, results.iterator().next());

                    if (tmpGroup.getStorageDeviceURI().equals(system.getId())) {
                        portGroup = tmpGroup;
                        _logger.debug("Found duplicate {} ", vs.getName());
                    }
                }

                if (portGroup == null) {
                    portGroup = new StorageHADomain();
                    portGroup.setId(URIUtil.createId(StorageHADomain.class));
                    portGroup.setName(vs.getName());
                    portGroup.setVirtual(true);
                    portGroup.setAdapterType(StorageHADomain.HADomainType.VIRTUAL.toString());
                    portGroup.setNativeGuid(adapterNativeGuid);
                    portGroup.setStorageDeviceURI(system.getId());
                    portGroup.setFileSharingProtocols(protocols);

                    newPortGroups.add(portGroup);
                } else {
                    existingPortGroups.add(portGroup);
                }
            }
        }

        portGroups.put(NEW, newPortGroups);
        portGroups.put(EXISTING, existingPortGroups);
        return portGroups;
    }

    /**
     * get All Cifs shares in NetApp Cluster-mode Device
     * 
     * @param listShares
     * @return
     */
    private HashMap<String, HashSet<UnManagedSMBFileShare>> getAllCifsShares(
            List<Map<String, String>> listShares) {
        // Discover All FileSystem
        HashMap<String, HashSet<UnManagedSMBFileShare>> sharesHashMap = new HashMap<String, HashSet<UnManagedSMBFileShare>>();
        UnManagedSMBFileShare unManagedSMBFileShare = null;
        HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet = null;
        // prepare smb shares map elem for each fs path
        for (Map<String, String> shareMap : listShares) {
            String shareName = "";
            String mountPath = "";
            String description = "";
            String maxUsers = "-1";
            for (String key : shareMap.keySet()) {
                Object value = shareMap.get(key);
                _logger.info("cifs share - key : {} and value : {}", key, value);
                if (null != key && value != null) {
                    switch (key) {
                        case "share-name":
                            shareName = (String) value;
                            break;
                        case "path":
                            mountPath = (String) value;
                            break;
                        case "comment":
                            description = (String) value;
                            break;
                        case "maxusers":
                            maxUsers = (String) value;
                            break;
                        default:
                            break;
                    }
                }
            }
            _logger.info("cifs share details- share-name:{} mount-point: {} ",
                    shareName, mountPath);
            unManagedSMBFileShare = new UnManagedSMBFileShare();
            unManagedSMBFileShare.setName(shareName);
            unManagedSMBFileShare.setMountPoint(mountPath);
            unManagedSMBFileShare.setDescription(description);
            unManagedSMBFileShare.setMaxUsers(Integer.parseInt(maxUsers));

            unManagedSMBFileShareHashSet = sharesHashMap.get(mountPath);
            if (null == unManagedSMBFileShareHashSet) {
                unManagedSMBFileShareHashSet = new HashSet<UnManagedSMBFileShare>();
            }
            unManagedSMBFileShareHashSet.add(unManagedSMBFileShare);
            sharesHashMap.put(mountPath, unManagedSMBFileShareHashSet);
        }
        return sharesHashMap;

    }

    /**
     * add Unmanaged SMB share to FS Object
     * 
     * @param unManagedSMBFileShareHashSet
     * @param unManagedSMBShareMap
     * @param addr
     * @param nativeid
     */
    private void createSMBShareMap(
            HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet,
            UnManagedSMBShareMap unManagedSMBShareMap, String addr,
            String nativeid) {

        UnManagedSMBFileShare newUnManagedSMBFileShare = null;
        for (UnManagedSMBFileShare unManagedSMBFileShare : unManagedSMBFileShareHashSet) {
            String mountPoint = "\\\\" + addr + "\\" + unManagedSMBFileShare.getName();
            newUnManagedSMBFileShare = new UnManagedSMBFileShare(unManagedSMBFileShare.getName(),
                    unManagedSMBFileShare.getDescription(),
                    // for netApp c mode permission and permission type is not used ,setting to default
                    FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW,
                    FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE,
                    unManagedSMBFileShare.getMaxUsers(),
                    mountPoint);
            newUnManagedSMBFileShare.setPath(nativeid);
            newUnManagedSMBFileShare.setNativeId(nativeid);
            newUnManagedSMBFileShare.setPortGroup(addr);
            // add new cifs share to File Object
            unManagedSMBShareMap.put(unManagedSMBFileShare.getName(), newUnManagedSMBFileShare);
            _logger.info("New SMB share name: {} has mount point : {}",
                    unManagedSMBFileShare.getName(), mountPoint);
        }
    }

    /**
     * get ACLs for smb shares of fs object
     * 
     * @param unManagedSMBFileShareHashSet
     * @param netAppClusterApi
     * @param fsId
     * @return
     */
    private List<UnManagedCifsShareACL> getACLs(HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet,
            NetAppClusterApi netAppClusterApi, StorageSystem storageSystem, URI fsId) {
        _logger.info("gets all acls of fileshares given fsid ", fsId);
        // get list of acls for given set of shares
        UnManagedCifsShareACL unManagedCifsShareACL = null;
        List<UnManagedCifsShareACL> unManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
        // get acls for each share
        List<CifsAcl> cifsAclList = null;
        for (UnManagedSMBFileShare unManagedSMBFileShare : unManagedSMBFileShareHashSet) {
            // find acl for given share
            String unManagedSMBFileShareName = unManagedSMBFileShare.getName();
            _logger.info("new smb share name: {} and fs: {}",
                    unManagedSMBFileShareName, fsId);
            cifsAclList = netAppClusterApi.listCIFSShareAcl(unManagedSMBFileShareName);
            if (cifsAclList != null && !cifsAclList.isEmpty()) {
                for (CifsAcl cifsAcl : cifsAclList) {
                    _logger.info("cifs share ACL: {} ", cifsAcl.toString());
                    unManagedCifsShareACL = new UnManagedCifsShareACL();
                    unManagedCifsShareACL.setShareName(unManagedSMBFileShareName);
                    String user = cifsAcl.getUserName();
                    if (user != null) {
                        unManagedCifsShareACL.setUser(user);
                    } else {
                        unManagedCifsShareACL.setGroup(cifsAcl.getGroupName());
                    }
                    // permission
                    unManagedCifsShareACL.setPermission(cifsAcl.getAccess().name());
                    unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));
                    // filesystem id
                    unManagedCifsShareACL.setFileSystemId(fsId);
                    // set the native guid
                    String fsShareNativeId = unManagedCifsShareACL.getFileSystemShareACLIndex();
                    // _logger.info("UMFS Share ACL index {}", fsShareNativeId);
                    String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                            .generateNativeGuidForPreExistingFileShare(storageSystem, fsShareNativeId);
                    _logger.info("Native GUID {}", fsUnManagedFileShareNativeGuid);
                    unManagedCifsShareACL.setNativeGuid(fsUnManagedFileShareNativeGuid);
                    // add the acl to acl-list
                    unManagedCifsShareACLList.add(unManagedCifsShareACL);
                }
                _logger.info("new smb share name-: {} and ACL count: {}",
                        unManagedSMBFileShareName, cifsAclList.size());
            }
        }
        return unManagedCifsShareACLList;
    }

    /**
     * discover the unmanaged cifs shares and add shares to ViPR db
     * 
     * @param profile
     */
    private void discoverUnManagedCifsShares(AccessProfile profile) {

        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        storageSystem
                .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                        .toString());
        String detailedStatusMessage = "Discovery of NetAppC Unmanaged Cifs started";

        NetAppClusterApi netAppCApi = new NetAppClusterApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                        .https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(property));
        }

        try {
            // Used to Save the Acl to DB
            List<UnManagedCifsShareACL> unManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
            List<UnManagedCifsShareACL> oldunManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
            HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet = null;

            List<Map<String, String>> fileSystemInfo = netAppCApi.listVolumeInfo(null, attrs);
            List<StorageVirtualMachineInfo> svms = netAppCApi.listSVM();

            for (StorageVirtualMachineInfo svmInfo : svms) {
                netAppCApi = new NetAppClusterApi.Builder(
                        storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                        storageSystem.getUsername(), storageSystem.getPassword())
                                .https(true).svm(svmInfo.getName()).build();

                // Get All cifs shares and ACLs
                List<Map<String, String>> listShares = netAppCApi.listShares(null);
                if (listShares != null && !listShares.isEmpty()) {
                    _logger.info("total no of shares in netappC system (s) {}", listShares.size());
                }
                // prepare the unmanagedSmbshare
                HashMap<String, HashSet<UnManagedSMBFileShare>> unMangedSMBFileShareMapSet = getAllCifsShares(listShares);

                for (String key : unMangedSMBFileShareMapSet.keySet()) {
                    unManagedSMBFileShareHashSet = unMangedSMBFileShareMapSet.get(key);
                    String fileSystem = key;
                    String nativeId = fileSystem;

                    // get a fileSystem name from the path
                    int index = fileSystem.indexOf('/', 1);
                    if (-1 != index) {
                        fileSystem = fileSystem.substring(0, index);
                        _logger.info("Unmanaged FileSystem Name {}", fileSystem);
                    }
                    // build native id
                    String fsUnManagedFsNativeGuid = NativeGUIDGenerator
                            .generateNativeGuidForPreExistingFileSystem(
                                    storageSystem.getSystemType(), storageSystem
                                            .getSerialNumber().toUpperCase(),
                                    fileSystem);

                    UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                    boolean fsAlreadyExists = unManagedFs == null ? false : true;

                    if (fsAlreadyExists) {
                        _logger.debug("retrieve info for file system: " + fileSystem);
                        String svm = getOwningSVM(fileSystem, fileSystemInfo);
                        String addr = getSVMAddress(svm, svms);

                        UnManagedSMBShareMap tempUnManagedSMBShareMap = new UnManagedSMBShareMap();
                        // get the SMB shares
                        createSMBShareMap(unManagedSMBFileShareHashSet, tempUnManagedSMBShareMap,
                                addr, nativeId);
                        // add shares to fs object and set hasShare to true
                        if (tempUnManagedSMBShareMap.size() > 0 && !tempUnManagedSMBShareMap.isEmpty()) {
                            unManagedFs.setUnManagedSmbShareMap(tempUnManagedSMBShareMap);
                            unManagedFs.setHasShares(true);
                            unManagedFs.putFileSystemCharacterstics(
                                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                            .toString(),
                                    TRUE);
                            _logger.debug("SMB Share map for NetAppC UMFS {} = {}",
                                    unManagedFs.getLabel(), unManagedFs.getUnManagedSmbShareMap());
                        }
                        // get the acls details for given fileshare of given fs
                        UnManagedCifsShareACL existingACL = null;
                        List<UnManagedCifsShareACL> tempUnManagedCifsShareAclList = getACLs(unManagedSMBFileShareHashSet, netAppCApi,
                                storageSystem, unManagedFs.getId());
                        if (tempUnManagedCifsShareAclList != null &&
                                !tempUnManagedCifsShareAclList.isEmpty()) {
                            for (UnManagedCifsShareACL unManagedCifsShareACL : tempUnManagedCifsShareAclList) {
                                // Check whether the CIFS share ACL was present in ViPR DB.
                                existingACL = checkUnManagedFsCifsACLExistsInDB(_dbClient,
                                        unManagedCifsShareACL.getNativeGuid());
                                if (existingACL == null) {
                                    // add new acl
                                    unManagedCifsShareACLList.add(unManagedCifsShareACL);
                                } else {
                                    // delete the existing acl by setting object to inactive to true
                                    existingACL.setInactive(true);
                                    oldunManagedCifsShareACLList.add(existingACL);
                                    // then add new acl and save
                                    unManagedCifsShareACLList.add(unManagedCifsShareACL);
                                }
                            }
                        }

                        // store or update the FS object into DB
                        if (unManagedSMBFileShareHashSet != null &&
                                !unManagedSMBFileShareHashSet.isEmpty()) {
                            _dbClient.persistObject(unManagedFs);
                            _logger.info("File System {} has Shares and their Count is {}",
                                    unManagedFs.getId(), tempUnManagedSMBShareMap.size());
                        }
                        // Adding this additional logic to avoid OOM
                        if (unManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                            _logger.info("Saving Number of New UnManagedCifsShareACL(s) {}",
                                    unManagedCifsShareACLList.size());
                            _partitionManager.insertInBatches(
                                    unManagedCifsShareACLList,
                                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                                    UNMANAGED_SHARE_ACL);
                            unManagedCifsShareACLList.clear();
                        }
                        if (!oldunManagedCifsShareACLList.isEmpty() &&
                                oldunManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                            _logger.info("Update Number of Old UnManagedCifsShareACL(s) {}",
                                    oldunManagedCifsShareACLList.size());
                            _partitionManager.updateInBatches(oldunManagedCifsShareACLList,
                                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                                    UNMANAGED_SHARE_ACL);
                            oldunManagedCifsShareACLList.clear();
                        }
                    } else {
                        _logger.info("FileSystem " + unManagedFs
                                + "is not present in ViPR DB. Hence ignoring "
                                + fileSystem + " share");
                    }
                }
            }

            if (unManagedCifsShareACLList != null &&
                    !unManagedCifsShareACLList.isEmpty()) {
                _logger.info("Saving Number of New UnManagedCifsShareACL(s) {}",
                        unManagedCifsShareACLList.size());
                _partitionManager.insertInBatches(unManagedCifsShareACLList,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_SHARE_ACL);
                unManagedCifsShareACLList.clear();
            }
            if (oldunManagedCifsShareACLList != null &&
                    !oldunManagedCifsShareACLList.isEmpty()) {
                _logger.info("Saving Number of Old UnManagedCifsShareACL(s) {}",
                        oldunManagedCifsShareACLList.size());
                _partitionManager.updateInBatches(oldunManagedCifsShareACLList,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_SHARE_ACL);
                oldunManagedCifsShareACLList.clear();
            }
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                            .toString());
            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for NetAppC: %s",
                    storageSystemId.toString());

        } catch (NetAppCException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + storageSystemId);
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Discover the storage pools for NetApp Cluster mode array
     * 
     * @param system
     *            Storage system information
     * @return Map of new and existing storage pools
     * @throws NetAppCException
     */
    private Map<String, List<StoragePool>> discoverStoragePools(StorageSystem system, List<StoragePool> poolsToMatchWithVpool)
            throws NetAppCException {

        Map<String, List<StoragePool>> storagePools = new HashMap<String, List<StoragePool>>();

        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> existingPools = new ArrayList<StoragePool>();

        _logger.info("Start storage pool discovery for storage system {}",
                system.getId());
        try {
            NetAppClusterApi netAppCApi = new NetAppClusterApi.Builder(system.getIpAddress(),
                    system.getPortNumber(), system.getUsername(),
                    system.getPassword()).https(true).build();

            List<AggregateInfo> pools = netAppCApi.listClusterAggregates(null);

            for (AggregateInfo netAppPool : pools) {
                StoragePool pool = null;

                URIQueryResultList results = new URIQueryResultList();
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, netAppPool.getName(), NativeGUIDGenerator.POOL);
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePoolByNativeGuidConstraint(poolNativeGuid),
                        results);
                if (results.iterator().hasNext()) {
                    StoragePool tmpPool = _dbClient.queryObject(
                            StoragePool.class, results.iterator().next());

                    if (tmpPool.getStorageDevice().equals(system.getId())) {
                        pool = tmpPool;
                    }
                }
                if (pool == null) {
                    pool = new StoragePool();
                    pool.setId(URIUtil.createId(StoragePool.class));
                    pool.setLabel(poolNativeGuid);
                    pool.setNativeGuid(poolNativeGuid);
                    pool.setPoolServiceType(PoolServiceType.file.toString());
                    pool.setStorageDevice(system.getId());
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY
                            .toString());

                    StringSet protocols = new StringSet();
                    protocols.add("NFS");
                    protocols.add("CIFS");
                    pool.setProtocols(protocols);
                    pool.setPoolName(netAppPool.getName());
                    pool.setNativeId(netAppPool.getName());
                    pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_AND_THICK.toString());
                    Map<String, String> params = new HashMap<String, String>();
                    params.put(StoragePool.ControllerParam.PoolType.name(),
                            "File Pool");
                    pool.addControllerParams(params);
                    pool.setRegistrationStatus(RegistrationStatus.REGISTERED
                            .toString());
                    _logger.info(
                            "Creating new storage pool using NativeGuid : {}",
                            poolNativeGuid);
                    newPools.add(pool);
                } else {
                    existingPools.add(pool);
                }
                // Update Pool details with new discovery run
                pool.setTotalCapacity(netAppPool.getSizeTotal()
                        / BYTESCONVERTER);
                pool.setFreeCapacity(netAppPool.getSizeAvailable()
                        / BYTESCONVERTER);
                pool.setSubscribedCapacity(netAppPool.getSizeUsed()
                        / BYTESCONVERTER);

                if (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                        DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()) ||
                        ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(), DiscoveryStatus.VISIBLE.name())) {
                    poolsToMatchWithVpool.add(pool);
                }
                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
        } catch (NumberFormatException e) {
            _logger.error(
                    "Data Format Exception:  Discovery of storage pools failed for storage system {} for {}",
                    system.getId(), e);

            NetAppCException ntpe = new NetAppCException(
                    "Storage pool discovery data error for storage system "
                            + system.getId());
            ntpe.initCause(e);
            throw ntpe;
        }

        _logger.info("Storage pool discovery for storage system {} complete",
                system.getId());
        storagePools.put(NEW, newPools);
        storagePools.put(EXISTING, existingPools);
        return storagePools;

    }

    private URI findMatchingHADomain(String domainName, List<StorageHADomain> haDomains) {
        _logger.debug("domain name to search for: {}", domainName);
        for (StorageHADomain domain : haDomains) {
            _logger.debug("current domain name: {}", domain.getName());
            if (domainName.equals(domain.getName())) {
                _logger.debug("found match for {}", domainName);
                return domain.getId();
            }
        }

        _logger.debug("no match for {}", domainName);
        return null;
    }

    /**
     * If discovery fails, then mark the system as unreachable. The discovery
     * framework will remove the storage system from the database.
     * 
     * @param system
     *            the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system) {
        try {
            system.setReachableStatus(false);
            _dbClient.persistObject(system);
        } catch (DatabaseException e) {
            _logger.error(
                    "discoverStorage failed.  Failed to update discovery status to ERROR.",
                    e);
        }
    }

    private StoragePort getStoragePortPool(StorageSystem storageSystem)
            throws IOException {
        StoragePort storagePort = null;
        // Check if storage port was already discovered
        URIQueryResultList results = new URIQueryResultList();
        String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                storageSystem, storageSystem.getIpAddress(),
                NativeGUIDGenerator.PORT);
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePortByNativeGuidConstraint(portNativeGuid), results);

        if (results.iterator().hasNext()) {
            StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                    results.iterator().next());
            if (tmpPort.getStorageDevice().equals(storageSystem.getId())
                    && tmpPort.getPortGroup().equals(
                            storageSystem.getSerialNumber())) {
                storagePort = tmpPort;
                _logger.debug("found a port for storage system dm intf {}",
                        storageSystem.getSerialNumber());
            }
        }

        return storagePort;
    }

    /**
     * check Storage fileSystem exists in DB
     * 
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    protected boolean checkStorageFileSystemExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemNativeGUIdConstraint(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    /**
     * check Pre Existing Storage filesystem exists in DB
     * 
     * @param nativeGuid
     * @return unManageFileSystem
     * @throws IOException
     */
    protected UnManagedFileSystem checkUnManagedFileSystemExistsInDB(
            String nativeGuid) {
        UnManagedFileSystem filesystemInfo = null;
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemInfoNativeGUIdConstraint(nativeGuid), result);
        List<URI> filesystemUris = new ArrayList<URI>();
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unFileSystemtURI = iter.next();
            filesystemUris.add(unFileSystemtURI);
        }

        if (!filesystemUris.isEmpty()) {
            filesystemInfo = _dbClient.queryObject(UnManagedFileSystem.class,
                    filesystemUris.get(0));
        }
        return filesystemInfo;

    }

    /**
     * Based on the fileSystem (volume) name, return the name of the SVM that it belongs to.
     * 
     * File system names are of the form: /vol/<fs name>, /<fs name>, or <fs name>.
     * Names returned from array are only of the form: <fs name>.
     * Therefore, match occurs if file system name 'ends' with the name returned from array.
     * 
     * @param fileSystem
     *            name of the file system (volume in NetAppC terminology)
     * @param fileSystemInfo
     *            list of file system attributes for each file.
     * @return
     */
    private String getOwningSVM(String fileSystem, List<Map<String, String>> fileSystemInfo) {
        if (fileSystem == null || fileSystem.isEmpty()) {
            _logger.warn("No file system name");
            return null;
        }

        if (fileSystemInfo == null || fileSystemInfo.isEmpty()) {
            _logger.warn("No file system information");
            return null;
        }

        String name = null;
        String junctionPath = null;
        for (Map<String, String> fileSystemAttrs : fileSystemInfo) {
            name = fileSystemAttrs.get(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                            .toString()));
            junctionPath = fileSystemAttrs.get(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(SupportedNtpFileSystemInformation.PATH
                            .toString()));

            if (name != null && junctionPath != null && (fileSystem.endsWith(name) ||
                    (fileSystem.contains("/" + name + "/")))) {
                _logger.debug("found matching file system: " + name);
                return fileSystemAttrs.get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.SVM
                                .toString()));
            }
        }

        return null;
    }

    /**
     * Return the IP address for the specified SVM.
     * 
     * @param svmName
     *            name of the SVM looking for.
     * @param svmInfo
     *            List of SVMs with their information.
     * @return IP address of the specified SVM if found, otherwise null.
     */
    private String getSVMAddress(String svmName, List<StorageVirtualMachineInfo> svmInfo) {
        if (svmName == null || svmName.isEmpty()) {
            _logger.warn("No SVM name specified");
            return null;
        }

        if (svmInfo == null || svmInfo.isEmpty()) {
            _logger.warn("No SVM Information");
            return null;
        }

        for (StorageVirtualMachineInfo info : svmInfo) {
            _logger.debug("SVM info for: " + info.getName());
            if (svmName.equals(info.getName())) {
                List<SVMNetInfo> netInfo = info.getInterfaces();
                for (SVMNetInfo intf : netInfo) {
                    // If role ends with _mgmt e.g. cluster_mgmt, node_mgmt, it is the management interface which should
                    // be excluded while
                    // assigning ports to unmanaged file systems or exports
                    if (intf.getRole().contains(MANAGEMENT_INTERFACE)) {
                        continue;
                    }
                    return intf.getIpAddress();
                }
            }
        }
        return null;
    }

    private StoragePort getSVMStoragePort(StorageSystem storageSystem, String portNativeGuid, String svm) {
        URIQueryResultList results = new URIQueryResultList();

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory
                        .getStoragePortByNativeGuidConstraint(portNativeGuid),
                results);

        StoragePort port;
        if (results.iterator().hasNext()) {
            port = _dbClient.queryObject(
                    StoragePort.class, results.iterator()
                            .next());
            if (port.getStorageDevice().equals(storageSystem.getId())
                    && port.getPortGroup().equals(svm)) {
                _logger.debug("found storage port for SVM");
                return port;
            }
        }

        return null;
    }

    /**
     * create StorageFileSystem Info Object
     * 
     * @param unManagedFileSystem
     * @param unManagedFileSystemNativeGuid
     * @param storageSystemUri
     * @param storagePool
     * @param fileSystem
     * @param storagePort
     * @param fileSystemChars
     * @return UnManagedFileSystem
     * @throws IOException
     * @throws NetAppCException
     */
    private UnManagedFileSystem createUnManagedFileSystem(
            UnManagedFileSystem unManagedFileSystem, AccessProfile profile,
            String unManagedFileSystemNativeGuid, String unManangedFileSystemNativeId,
            StorageSystem system, StoragePool pool,
            String fileSystem, StoragePort storagePort, Map<String, String> fileSystemChars) throws IOException, NetAppCException {
        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil
                    .createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(system.getId());
            unManagedFileSystem.setHasExports(false);
            unManagedFileSystem.setHasShares(false);
        }

        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        unManagedFileSystemCharacteristics.put(
                SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                FALSE);

        if (fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.SPACE_GUARANTEE
                                .toString()))
                .equalsIgnoreCase(SPACE_GUARANTEE_NONE)) {
            unManagedFileSystemCharacteristics.put(
                    SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(),
                    TRUE);
        } else {
            unManagedFileSystemCharacteristics.put(
                    SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(),
                    FALSE);
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(),
                TRUE);

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(),
                FALSE);

        if (null != storagePort) {
            StringSet storagePorts = new StringSet();
            storagePorts.add(storagePort.getId().toString());
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
        }

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            unManagedFileSystem.setStoragePoolUri(pool.getId());
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId());
            _logger.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // Clear all existing matching vpools.
                unManagedFileSystem.getSupportedVpoolUris().clear();
            } else {
                // replace with new StringSet
                unManagedFileSystem.getSupportedVpoolUris().replace(matchedVPools);
                _logger.info("Replaced Pools :"
                        + Joiner.on("\t").join(unManagedFileSystem.getSupportedVpoolUris()));
            }
        }

        if (null != system) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(system.getSystemType());
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        // Get FileSystem used Space.
        if (null != fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.ALLOCATED_CAPACITY
                                .toString()))) {
            StringSet allocatedCapacity = new StringSet();
            allocatedCapacity
                    .add(fileSystemChars.get(SupportedNtpFileSystemInformation
                            .getFileSystemInformation(SupportedNtpFileSystemInformation.ALLOCATED_CAPACITY
                                    .toString())));
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.ALLOCATED_CAPACITY
                            .toString(),
                    allocatedCapacity);
        }

        // Get FileSystem used Space.
        if (null != fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.PROVISIONED_CAPACITY
                                .toString()))) {
            StringSet provisionedCapacity = new StringSet();
            provisionedCapacity
                    .add(fileSystemChars.get(SupportedNtpFileSystemInformation
                            .getFileSystemInformation(SupportedNtpFileSystemInformation.PROVISIONED_CAPACITY
                                    .toString())));
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.PROVISIONED_CAPACITY
                            .toString(),
                    provisionedCapacity);
        }

        // Save off FileSystem Name, Path, Mount and label information
        if (null != fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                                .toString()))) {
            StringSet fsName = new StringSet();
            String fileSystemName = fileSystemChars.get(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                            .toString()));
            fsName.add(fileSystemName);

            unManagedFileSystem.setLabel(fileSystemName);

            StringSet fsPath = new StringSet();
            fsPath.add(unManangedFileSystemNativeId);

            StringSet fsMountPath = new StringSet();
            fsMountPath.add(unManangedFileSystemNativeId);

            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.NAME.toString(), fsName);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.NATIVE_ID.toString(), fsPath);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.DEVICE_LABEL.toString(), fsName);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.PATH.toString(), fsPath);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.MOUNT_PATH.toString(), fsMountPath);

        }

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .setFileSystemCharacterstics(unManagedFileSystemCharacteristics);
        return unManagedFileSystem;
    }

    private void validateListSizeLimitAndPersist(List<UnManagedFileSystem> newUnManagedFileSystems,
            List<UnManagedFileSystem> existingUnManagedFileSystems, int limit) {

        if (newUnManagedFileSystems != null && !newUnManagedFileSystems.isEmpty() &&
                newUnManagedFileSystems.size() >= limit) {
            _partitionManager.insertInBatches(newUnManagedFileSystems,
                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                    UNMANAGED_FILESYSTEM);
            newUnManagedFileSystems.clear();
        }

        if (existingUnManagedFileSystems != null && !existingUnManagedFileSystems.isEmpty() &&
                existingUnManagedFileSystems.size() >= limit) {
            _partitionManager.updateInBatches(existingUnManagedFileSystems,
                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                    UNMANAGED_FILESYSTEM);
            existingUnManagedFileSystems.clear();
        }
    }

    private void createExportMap(ExportsRuleInfo export,
            UnManagedFSExportMap tempUnManagedExpMap, String storagePort) {

        List<ExportsHostnameInfo> readonlyHosts = export.getSecurityRuleInfos()
                .get(0).getReadOnly();
        List<ExportsHostnameInfo> readwriteHosts = export
                .getSecurityRuleInfos().get(0).getReadWrite();
        List<ExportsHostnameInfo> rootHosts = export.getSecurityRuleInfos()
                .get(0).getRoot();

        UnManagedFSExport tempUnManagedFSROExport = createUnManagedExport(
                export, readonlyHosts, RO, storagePort);
        if (tempUnManagedFSROExport != null) {
            tempUnManagedExpMap.put(tempUnManagedFSROExport.getFileExportKey(),
                    tempUnManagedFSROExport);
        }

        UnManagedFSExport tempUnManagedFSRWExport = createUnManagedExport(
                export, readwriteHosts, RW, storagePort);
        if (tempUnManagedFSRWExport != null) {
            tempUnManagedExpMap.put(tempUnManagedFSRWExport.getFileExportKey(),
                    tempUnManagedFSRWExport);
        }

        UnManagedFSExport tempUnManagedFSROOTExport = createUnManagedExport(
                export, rootHosts, ROOT, storagePort);
        if (tempUnManagedFSROOTExport != null) {
            tempUnManagedExpMap.put(tempUnManagedFSROOTExport.getFileExportKey(),
                    tempUnManagedFSROOTExport);
        }
    }

    private UnManagedFileExportRule copyProperties(UnManagedFileExportRule dest,
            UnManagedFileExportRule orig) {
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setMountPoint(orig.getMountPoint());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        return dest;
    }

    /**
     * check Pre Existing Storage File Export Rules exists in DB
     * 
     * @param nativeGuid
     * @return unManageFileExport Rule
     * @throws IOException
     */

    // TODO:Account for multiple security rules and security flavors
    private List<UnManagedFileExportRule> applyAllSecurityRules(
            ExportsRuleInfo export, String storagePortAddress, URI fileSystemId) {
        List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();
        for (SecurityRuleInfo deviceSecurityRule : export
                .getSecurityRuleInfos()) {

            ExportSecurityType[] securityFlavors = ExportSecurityType.values();
            boolean secFlavorSupported = false;
            for (ExportSecurityType sec : securityFlavors) {
                if (sec.name().equalsIgnoreCase(deviceSecurityRule.getSecFlavor())) {
                    secFlavorSupported = true;
                    break;
                }
            }

            if (secFlavorSupported) {
                UnManagedFileExportRule expRule = new UnManagedFileExportRule();
                expRule.setFileSystemId(fileSystemId);
                expRule.setExportPath(export.getPathname());
                expRule.setSecFlavor(deviceSecurityRule.getSecFlavor());
                expRule.setMountPoint(storagePortAddress + ":" + export.getPathname());
                String anon = deviceSecurityRule.getAnon();
                // TODO: This functionality has to be revisited to handle uids for anon.
                if ((null != anon) && (anon.equals(ROOT_UID))) {
                    anon = ROOT_USER_ACCESS;
                } else {
                    anon = DEFAULT_ANONMOUS_ACCESS;
                }
                expRule.setAnon(anon);
                if ((null != deviceSecurityRule.getRoot())
                        && !(deviceSecurityRule.getRoot()).isEmpty()) {
                    StringSet rootHosts = new StringSet();
                    for (ExportsHostnameInfo exportHost : deviceSecurityRule
                            .getRoot()) {
                        boolean negate = false;
                        if (exportHost.getNegate() != null) {
                            negate = exportHost.getNegate();
                        }
                        if (!negate) {
                            if (null != exportHost.getName()) {
                                rootHosts.add(exportHost.getName());
                            }
                        }
                    }
                    expRule.setRootHosts(rootHosts);
                }
                if ((null != deviceSecurityRule.getReadWrite())
                        && !(deviceSecurityRule.getReadWrite()).isEmpty()) {
                    StringSet readWriteHosts = new StringSet();
                    for (ExportsHostnameInfo exportHost : deviceSecurityRule
                            .getReadWrite()) {
                        boolean negate = false;
                        if (exportHost.getNegate() != null) {
                            negate = exportHost.getNegate();
                        }
                        if (!negate) {
                            if (null != exportHost.getName()) {
                                if (expRule.getRootHosts() != null) {
                                    if (!expRule.getRootHosts().contains(exportHost.getName())) {
                                        readWriteHosts.add(exportHost.getName());
                                    }
                                } else {
                                    readWriteHosts.add(exportHost.getName());
                                }
                            }
                        }
                    }
                    expRule.setReadWriteHosts(readWriteHosts);
                }
                if ((null != deviceSecurityRule.getReadOnly())
                        && !(deviceSecurityRule.getReadOnly()).isEmpty()) {
                    StringSet readOnlyHosts = new StringSet();
                    for (ExportsHostnameInfo exportHost : deviceSecurityRule
                            .getReadOnly()) {
                        boolean negate = false;
                        if (exportHost.getNegate() != null) {
                            negate = exportHost.getNegate();
                        }
                        if (!negate) {
                            if (null != exportHost.getName()) {
                                boolean checkRWPermissions = false;
                                if (expRule.getRootHosts() != null) {
                                    if (!expRule.getRootHosts().contains(exportHost.getName())) {
                                        checkRWPermissions = true;
                                    }
                                } else {
                                    checkRWPermissions = true;
                                }
                                if (checkRWPermissions) {
                                    if (expRule.getReadWriteHosts() != null) {
                                        if (!expRule.getReadWriteHosts().contains(exportHost.getName())) {
                                            readOnlyHosts.add(exportHost.getName());
                                        }
                                    } else {
                                        readOnlyHosts.add(exportHost.getName());
                                    }
                                }
                            }
                        }
                    }
                    expRule.setReadOnlyHosts(readOnlyHosts);
                }

                if (!((expRule.getReadOnlyHosts() == null || expRule.getReadOnlyHosts().isEmpty())
                        && (expRule.getReadWriteHosts() == null || expRule.getReadWriteHosts().isEmpty())
                        && (expRule.getRootHosts() == null || expRule.getRootHosts().isEmpty()))) {
                    expRules.add(expRule);
                }
            }
        }
        return expRules;
    }

    private UnManagedFSExport createUnManagedExport(ExportsRuleInfo export,
            List<ExportsHostnameInfo> typeHosts,
            String permission, String port) {

        List<String> clientList = new ArrayList<String>();
        UnManagedFSExport tempUnManagedFSExport = null;
        if ((null != typeHosts) && (!typeHosts.isEmpty())) {

            for (ExportsHostnameInfo client : typeHosts) {
                boolean negate = false;
                if (client.getNegate() != null) {
                    negate = client.getNegate();
                }
                if (!negate) {
                    if ((null != client.getName() && !(clientList.contains(client
                            .getName())))) {
                        if (!clientList.contains(client.getName())) {
                            clientList.add(client.getName());
                        }
                    } else if ((null != client.getAllHosts())
                            && (client.getAllHosts())) {
                        // All hosts means empty clientList in ViPR.
                        clientList.clear();
                        _logger.info("Settng ClientList to empty as the export is meant to be accsible to all hosts");
                    }
                }
            }

            String anon = export.getSecurityRuleInfos().get(0).getAnon();
            if ((null != anon) && (anon.equals(ROOT_UID))) {
                anon = ROOT_USER_ACCESS;
            } else {
                anon = DEFAULT_ANONMOUS_ACCESS;
            }

            tempUnManagedFSExport = new UnManagedFSExport(clientList, port,
                    port + ":" + export.getPathname(), export.getSecurityRuleInfos().get(0)
                            .getSecFlavor(),
                    permission, anon, NFS,
                    port, export.getPathname(), export.getPathname());
        }
        return tempUnManagedFSExport;
    }
}
