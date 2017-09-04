/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil.FileSystemObjectProperties;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Joiner;

public class FileSystemIngestionUtil {
    private static Logger _logger = LoggerFactory.getLogger(FileSystemIngestionUtil.class);
    public static final String UNMANAGEDFILESYSTEM = "UNMANAGEDFILESYSTEM";
    public static final String FILESYSTEM = "FILESYSTEM";
    public static final String IS_MIRROR_TARGET = "IS_MIRROR_TARGET";
    public static final String IS_MIRROR_SOURCE = "IS_MIRROR_SOURCE";
    public static final String FORWARD_SLASH = "/";

    /**
     * Validation Steps
     * 1. validate PreExistingFileSystem uri.
     * 2. Check PreExistingFileSystem is under Bourne Management already.
     * 3. Check whether given CoS is present in the PreExistingFileSystems Supported CoS List
     * 
     * @param UnManagedFileSystems
     * @param cos
     * @throws Exception
     */
    public static void isIngestionRequestValidForUnManagedFileSystems(
            List<URI> UnManagedFileSystems, VirtualPool cos, DbClient dbClient)
            throws DatabaseException {
        for (URI unManagedFileSystemUri : UnManagedFileSystems) {
            ArgValidator.checkUri(unManagedFileSystemUri);
            UnManagedFileSystem unManagedFileSystem = dbClient.queryObject(UnManagedFileSystem.class,
                    unManagedFileSystemUri);
            ArgValidator.checkEntityNotNull(unManagedFileSystem, unManagedFileSystemUri, false);

            if (null == unManagedFileSystem.getFileSystemCharacterstics() || null == unManagedFileSystem.getFileSystemInformation()) {
                continue;
            }
            StringSetMap unManagedFileSystemInformation = unManagedFileSystem
                    .getFileSystemInformation();

            String fileSystemNativeGuid = unManagedFileSystem.getNativeGuid().replace(
                    UNMANAGEDFILESYSTEM, FILESYSTEM);

            if (VirtualPoolUtil.checkIfFileSystemExistsInDB(fileSystemNativeGuid, dbClient)) {
                throw APIException.internalServerErrors.objectAlreadyManaged("FileSystem", fileSystemNativeGuid);
            }

            checkStoragePoolValidForUnManagedFileSystemUri(unManagedFileSystemInformation,
                    dbClient, unManagedFileSystemUri);

            checkVirtualPoolValidForGivenUnManagedFileSystemUris(unManagedFileSystem.getSupportedVpoolUris(), unManagedFileSystemUri,
                    cos.getId());
            // TODO: Today, We bring in all the volumes that are exported.We need to add support to bring in all the related FS exports
            // checkUnManagedFileSystemAlreadyExported(unManagedFileSystem);
        }
    }

    /**
     * check if unManagedFileSystem is already exported to Host
     * 
     * @param unManagedFileSystem
     * @throws Exception
     */
    private static void checkUnManagedFileSystemAlreadyExported(
            UnManagedFileSystem unManagedFileSystem) throws Exception {
        StringMap unManagedFileSystemCharacteristics = unManagedFileSystem

                .getFileSystemCharacterstics();
        String isFileSystemExported = unManagedFileSystemCharacteristics
                .get(SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED.toString());
        if (null != isFileSystemExported && Boolean.parseBoolean(isFileSystemExported)) {
            throw APIException.internalServerErrors.objectAlreadyExported("FileSystem", unManagedFileSystem.getId());
        }
    }

    /**
     * Get Supported Vpool from PreExistingFileSystem Storage Pools.
     * Verify if the given vpool is part of the supported vpool List.
     * 
     * @param stringSetVpoolUris
     * @param unManagedFileSystemUri
     * @param vpoolUri
     */
    private static void checkVirtualPoolValidForGivenUnManagedFileSystemUris(
            StringSet stringSetVpoolUris, URI unManagedFileSystemUri, URI vpoolUri) {
        // Currently the assumption is that vpool already exists prior to discovey of unmanaged fileystems.
        if (null == stringSetVpoolUris) {
            throw APIException.internalServerErrors.storagePoolNotMatchingVirtualPool("FileSystem", unManagedFileSystemUri);
        }
        _logger.info("supported vpools :" + Joiner.on("\t").join(stringSetVpoolUris));
        if (!stringSetVpoolUris.contains(vpoolUri.toString())) {
            throw APIException.internalServerErrors.virtualPoolNotMatchingStoragePool(vpoolUri, "FileSystem", unManagedFileSystemUri,
                    Joiner
                            .on("\t").join(stringSetVpoolUris));
        }
    }

    /**
     * Check if valid storage Pool is associated with UnManaged FileSystem Uri is valid.
     * 
     * @param unManagedFileSystemInformation
     * @param dbClient
     * @param unManagedFileSystemUri
     * @throws Exception
     */
    private static void checkStoragePoolValidForUnManagedFileSystemUri(
            StringSetMap unManagedFileSystemInformation, DbClient dbClient,
            URI unManagedFileSystemUri) throws DatabaseException {
        String pool = PropertySetterUtil.extractValueFromStringSet(FileSystemObjectProperties.STORAGE_POOL.toString(),
                unManagedFileSystemInformation);
        if (null == pool) {
            throw APIException.internalServerErrors.storagePoolError("", "FileSystem", unManagedFileSystemUri);
        }
        StoragePool poolObj = dbClient.queryObject(StoragePool.class, URI.create(pool));
        if (null == poolObj) {
            throw APIException.internalServerErrors.noStoragePool(pool, "FileSystem", unManagedFileSystemUri);
        }
    }

    /**
     * Gets and verifies the CoS passed in the request.
     * 
     * @param project
     *            A reference to the project.
     * @param param
     *            The FileSystem create post data.
     * @return A reference to the CoS.
     */
    public static VirtualPool getVirtualPoolForFileSystemCreateRequest(
            Project project, URI cosUri, PermissionsHelper permissionsHelper,
            DbClient dbClient) {
        ArgValidator.checkUri(cosUri);
        VirtualPool cos = dbClient.queryObject(VirtualPool.class, cosUri);
        ArgValidator.checkEntity(cos, cosUri, false);

        if (!VirtualPool.Type.file.name().equals(cos.getType())) {
            throw APIException.badRequests.virtualPoolNotForFileBlockStorage(VirtualPool.Type.file.name());
        }

        permissionsHelper.checkTenantHasAccessToVirtualPool(project.getTenantOrg().getURI(), cos);

        return cos;
    }

    /**
     * Gets and verifies that the varray passed in the request is
     * accessible to the tenant.
     * 
     * @param project
     *            A reference to the project.
     * @param neighborhoodUri
     *            The Varray URI.
     * 
     * @return A reference to the varray.
     */
    public static VirtualArray getVirtualArrayForFileSystemCreateRequest(
            Project project, URI neighborhoodUri, PermissionsHelper permissionsHelper,
            DbClient dbClient) {
        ArgValidator.checkUri(neighborhoodUri);
        VirtualArray neighborhood = dbClient.queryObject(VirtualArray.class,
                neighborhoodUri);
        ArgValidator.checkEntity(neighborhood, neighborhoodUri, false);
        permissionsHelper.checkTenantHasAccessToVirtualArray(project.getTenantOrg().getURI(), neighborhood);

        return neighborhood;
    }

    public static long getTotalUnManagedFileSystemCapacity(DbClient dbClient,
            List<URI> unManagedFileSystemUris) {
        BigInteger totalUnManagedFileSystemCapacity = new BigInteger("0");
        try {
            Iterator<UnManagedFileSystem> unManagedFileSystems = dbClient.queryIterativeObjects(UnManagedFileSystem.class,
                    unManagedFileSystemUris);

            while (unManagedFileSystems.hasNext()) {
                UnManagedFileSystem unManagedFileSystem = unManagedFileSystems.next();
                StringSetMap unManagedFileSystemInfo = unManagedFileSystem
                        .getFileSystemInformation();
                if (null == unManagedFileSystemInfo) {
                    continue;
                }
                String unManagedFileSystemCapacity = PropertySetterUtil
                        .extractValueFromStringSet(SupportedFileSystemInformation.ALLOCATED_CAPACITY
                                .toString(), unManagedFileSystemInfo);
                if (null != unManagedFileSystemCapacity && !unManagedFileSystemCapacity.isEmpty()) {
                    totalUnManagedFileSystemCapacity = totalUnManagedFileSystemCapacity
                            .add(new BigInteger(unManagedFileSystemCapacity));
                }

            }
        } catch (Exception e) {
            throw APIException.internalServerErrors.capacityComputationFailed();
        }
        return totalUnManagedFileSystemCapacity.longValue();
    }

    /**
     * Get Supported characteristics of virtual pool.
     * Verify the virtual pool is capable of holding the unmanaged file systems.
     * 
     * @param stringSetVpoolUris
     * @param unManagedFileSystemUri
     * @param vpoolUri
     */
    public static boolean checkVirtualPoolValidForUnManagedFileSystem(
            DbClient dbClient, VirtualPool vPool, URI unManagedFileSystemUri) {

        UnManagedFileSystem unManagedFileSystem = dbClient.queryObject(
                UnManagedFileSystem.class, unManagedFileSystemUri);

        if (unManagedFileSystem != null) {
            StringSet vPoolProtocols = vPool.getProtocols();
            if (unManagedFileSystem.getHasNFSAcl() && !vPoolProtocols.contains("NFSv4")) {
                _logger.warn("UnManaged FileSystem {} has NFS ACLs, But vPool protocol(s) are {}, Hence skipping filesystem.",
                        unManagedFileSystem.getLabel(), vPoolProtocols);
                return false;
            }
        }
        return true;
    }

    /**
     * This method verifies the duplicate named file system present in data base
     * for the given project
     * 1. It verifies file system name in existing data base
     * 2. It also verifies for the file system in current processing file system list
     * 
     * @param _dbClient
     * @param project - project id
     * @param fsName - name of the file system to be ingested
     * @param filesystems - list of file systems yet to write to db.
     * 
     * @return true - file system exists in db or in processing list, false otherwise
     */
    public static boolean checkForDuplicateFSName(DbClient _dbClient, URI project, String fsName, List<FileShare> filesystems) {
        List<FileShare> objectList = new ArrayList<>();
        objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileShare.class,
                ContainmentPrefixConstraint.Factory.getFullMatchConstraint(FileShare.class, "project",
                        project, fsName));
        if (objectList != null && !objectList.isEmpty()) {
            return true;
        }
        for (FileShare fs : filesystems) {
            // As ViPR Green field does not allow two file system with same name
            // even with different case, so do not ingest file systems with similar name!!!
            if (fs.getLabel().equalsIgnoreCase(fsName) || fs.getName().equalsIgnoreCase(fsName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method verifies the duplicate named file system present in data base
     * for the given project
     * 1. It verifies file system name in existing data base
     * 2. It also verifies for the file system in current processing file system list
     * 3. If duplicate name found, add suffix (n) to the file system label
     * 
     * @param _dbClient
     * @param project - project id
     * @param label - label of the file system to be ingested
     * @param filesystems - list of file systems yet to write to db.
     * 
     * @return String - same label, if no duplicate found in the project; otherwise add suffix as said above
     *         and return the updated label.
     */
    public static String validateAndGetFileShareLabel(DbClient _dbClient, URI project, String label, List<FileShare> filesystems) {

        if (label == null || label.isEmpty()) {
            return "";
        }
        List<FileShare> fileShareList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileShare.class,
                ContainmentPrefixConstraint.Factory.getFullMatchConstraint(FileShare.class, "project", project, label));

        // Add file systems from current batch!!
        for (FileShare fs : filesystems) {
            if (fs.getLabel().equalsIgnoreCase(label) || fs.getName().equalsIgnoreCase(label)) {
                fileShareList.add(fs);
            }
        }

        String fsName = label;
        if (!fileShareList.isEmpty()) {
            StringSet existingFsNames = new StringSet();
            for (FileShare fs : fileShareList) {
                existingFsNames.add(fs.getLabel());
            }
            // Concatenate the number!!!
            int numFs = fileShareList.size();
            do {
                String fsLabelWithNumberSuffix = label + "(" + numFs + ")";
                if (!existingFsNames.contains(fsLabelWithNumberSuffix)) {
                    fsName = fsLabelWithNumberSuffix;
                    break;
                }
                numFs++;
            } while (true);
        }
        if (!fileShareList.isEmpty()) {
            _logger.info("Duplicate labled file systems found, the original label {} has been changed to {} ", label, fsName);
        }
        return fsName;
    }

    private static boolean getBooleanEntry(StringMap entries, String key) {
        String value = entries.get(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                // nothing to do here. We'll just return 0;
            }
        }
        return false;
    }

    /*
     * Verify the file system is enable with replication.
     * The replication policy can be at any directory level.
     */
    private static boolean isReplicationSource(UnManagedFileSystem unManagedFileSystem) {
        StringMap fsCharacterstics = unManagedFileSystem.getFileSystemCharacterstics();
        return getBooleanEntry(fsCharacterstics, IS_MIRROR_SOURCE);
    }

    /*
     * Verify the file system is part of replication target.
     * The replication policy can be at any directory level.
     */
    private static boolean isReplicationTarget(UnManagedFileSystem unManagedFileSystem) {
        StringMap fsCharacterstics = unManagedFileSystem.getFileSystemCharacterstics();
        return getBooleanEntry(fsCharacterstics, IS_MIRROR_TARGET);
    }

    /*
     * Verify the file system path is part of replication, either it can be part of
     * source directory or target directory
     */
    public static boolean isReplicationFileSystem(UnManagedFileSystem unManagedFileSystem) {
        return isReplicationSource(unManagedFileSystem) || isReplicationTarget(unManagedFileSystem);
    }

    /*
     * Read the file policies from DB for given ids.
     * Return only replication policies.
     */
    private static List<FilePolicy> getReplicationPolicies(DbClient dbClient, List<String> policyUris) {
        List<FilePolicy> repPolicies = new ArrayList<FilePolicy>();
        List<FilePolicy> filePolicies = dbClient.queryObject(FilePolicy.class, URIUtil.uris(policyUris));
        for (FilePolicy filePolicy : filePolicies) {
            if (!filePolicy.getInactive() && FilePolicyType.file_replication.name().equalsIgnoreCase(filePolicy.getFilePolicyType())) {
                repPolicies.add(filePolicy);
            }
        }
        return repPolicies;
    }

    /*
     * Get replication policies at vpool level
     */
    private static List<FilePolicy> getvPoolLevelReplicationPolicies(DbClient dbClient, VirtualPool vPool) {
        List<FilePolicy> repPolicies = new ArrayList<FilePolicy>();
        List<String> filePolicies = new ArrayList<String>();

        // vPool policies
        if (vPool.getFilePolicies() != null && !vPool.getFilePolicies().isEmpty()) {
            filePolicies.addAll(vPool.getFilePolicies());
            // Get replication policies at vpool level
            repPolicies.addAll(getReplicationPolicies(dbClient, filePolicies));
        }
        return repPolicies;
    }

    /*
     * Get replication policies at project level
     */
    private static List<FilePolicy> getProjectLevelReplicationPolicies(DbClient dbClient, VirtualPool vPool, Project project) {
        List<FilePolicy> repPolicies = new ArrayList<FilePolicy>();
        List<String> filePolicies = new ArrayList<String>();

        // Project level policies
        if (project.getFilePolicies() != null && !project.getFilePolicies().isEmpty()) {
            filePolicies.addAll(project.getFilePolicies());
            // Get the replication policies at project level
            for (FilePolicy repPolicy : getReplicationPolicies(dbClient, filePolicies)) {
                // Filter the replication policies at project level
                // which are not applicable with the given vpool
                if (repPolicy != null && !NullColumnValueGetter.isNullURI(repPolicy.getFilePolicyVpool())
                        && repPolicy.getFilePolicyVpool().toString().equalsIgnoreCase(repPolicy.getId().toString())) {
                    repPolicies.add(repPolicy);
                }
            }
        }
        return repPolicies;
    }

    /*
     * Verifies the file system's replication path is same as given path
     * 
     */
    private static boolean isReplicationAtRightLevel(UnManagedFileSystem unManagedFileSystem, String reqPolicyPath) {

        StringSetMap unManagedFileSystemInformation = unManagedFileSystem
                .getFileSystemInformation();
        // Get the replication path from UMFS
        String umfsPolicyPath = PropertySetterUtil.extractValueFromStringSet(
                SupportedFileSystemInformation.POLICY_PATH.toString(),
                unManagedFileSystemInformation);

        // Add forward slash to both the path to make comparison easy!!
        if (umfsPolicyPath != null && !umfsPolicyPath.endsWith(FORWARD_SLASH)) {
            umfsPolicyPath = umfsPolicyPath + FORWARD_SLASH;
        }

        if (reqPolicyPath != null && !reqPolicyPath.endsWith(FORWARD_SLASH)) {
            reqPolicyPath = reqPolicyPath + FORWARD_SLASH;
        }

        if (reqPolicyPath != null && reqPolicyPath.equalsIgnoreCase(umfsPolicyPath)) {
            return true;
        }
        return false;
    }

    /*
     * Verifies the unmanaged file system's replication is at vpool level
     * 
     */
    private static boolean isUMFSReplicationAtvPoolLevel(UnManagedFileSystem unManagedFileSystem, String vPoolPath) {
        return isReplicationAtRightLevel(unManagedFileSystem, vPoolPath);
    }

    /*
     * Verifies the unmanaged file system's replication is at project level
     * 
     */
    private static boolean isUMFSReplicationAtProjectLevel(UnManagedFileSystem unManagedFileSystem, String projectPath) {
        return isReplicationAtRightLevel(unManagedFileSystem, projectPath);
    }

    /*
     * Verify the replication is at file system level
     */
    private static boolean isReplicationAtFsLevel(UnManagedFileSystem unManagedFileSystem) {
        StringSetMap unManagedFileSystemInformation = unManagedFileSystem
                .getFileSystemInformation();
        // Get the replication path from UMFS
        String fsPath = PropertySetterUtil.extractValueFromStringSet(
                SupportedFileSystemInformation.PATH.toString(),
                unManagedFileSystemInformation);
        return isReplicationAtRightLevel(unManagedFileSystem, fsPath);
    }

    /**
     * isValidFileSystemReplication method validate whether the replicated file system could be ingest to
     * given vpool and project
     * 
     * @param dbClient
     * @param unManagedFileSystem
     * @param vPool
     * @param project
     * @param vPoolDirPath
     * @param projectDirPath
     * @param message
     * @return
     */
    public static boolean isValidFileSystemReplication(DbClient dbClient, UnManagedFileSystem unManagedFileSystem, VirtualPool vPool,
            Project project, String vPoolDirPath, String projectDirPath, String fsPath, StringBuffer message) {
        // vPool is enabled with replication
        if (vPool.getFileReplicationSupported()) {
            // Is UMFS is replicated file system??
            if (!isReplicationFileSystem(unManagedFileSystem)) {
                message.append("File system ").append(fsPath).append(" is with replication, ")
                        .append("But ViPR vPool").append(vPool.getLabel()).append(" is not enabled with replication");
                _logger.info(message.toString());
                return false;
            }
            // Verify the file system is with vpool level policy??
            if (isUMFSReplicationAtvPoolLevel(unManagedFileSystem, vPoolDirPath)) {
                // Other level policies should not be present
                if (!getProjectLevelReplicationPolicies(dbClient, vPool, project).isEmpty()) {
                    message.append("UnManaged File system ").append(fsPath).append(" is with vpool level replication, ")
                            .append("But ViPR project has been assigned with replication policy");
                    _logger.info(message.toString());
                    return false;
                } else {
                    message.append("File system  ").append(fsPath).append(" is with vpool level replication ");
                    _logger.info(message.toString());
                    return true;
                }
            }

            // Verify the file system is with project level policy??
            if (isUMFSReplicationAtProjectLevel(unManagedFileSystem, vPoolDirPath)) {
                // Other level policies should not be present
                if (!getvPoolLevelReplicationPolicies(dbClient, vPool).isEmpty() || !vPool.getAllowFilePolicyAtProjectLevel()) {
                    message.append("File system  ").append(fsPath).append(" is with project level replication, ")
                            .append("But ViPR vPool has been assigned with replication policy ")
                            .append(" or vPool is not enabled for project level policies");
                    _logger.info(message.toString());
                    return false;
                } else {
                    message.append("File system ").append(fsPath).append(" is with project level replication ");
                    _logger.info(message.toString());
                    return true;
                }
            }

            // File system with replication at fs level??
            // is vPool enabled with policies at fs level??
            if (isReplicationAtFsLevel(unManagedFileSystem)) {
                if (!getvPoolLevelReplicationPolicies(dbClient, vPool).isEmpty()
                        || !getProjectLevelReplicationPolicies(dbClient, vPool, project).isEmpty()) {
                    message.append("File system ").append(fsPath).append(" is with fs level replication, ")
                            .append("But ViPR vPool or project has been assigned with replication policy ")
                            .append(" or vPool is not enabled for project level policies");
                    _logger.info(message.toString());
                    return false;
                } else if (vPool.getAllowFilePolicyAtFSLevel()) {
                    message.append("File system ").append(fsPath).append(" is with fs level replication ");
                    _logger.info(message.toString());
                    return true;
                } else {
                    message.append("The unmanaged file system  ").append(fsPath).append(" has replication at fs level, But the vPool ")
                            .append(vPool.getLabel())
                            .append(" does not support policies at fs level ");
                    _logger.info(message.toString());
                    return false;
                }
            }
            return false;
        } else {
            // Is UMFS is replicated file system??
            if (isReplicationFileSystem(unManagedFileSystem)) {
                message.append("File system  ").append(fsPath).append(" is with replication, ")
                        .append("But ViPR vPool").append(vPool.getLabel()).append(" is not enabled with replication");
                _logger.info(message.toString());
                return false;
            } else {

                _logger.info("Both file system {} and vpool {} does not support replication ", fsPath, vPool.getLabel());
                return true;
            }
        }
    }
}
