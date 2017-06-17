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
import com.emc.storageos.db.client.constraint.ContainmentPrefixConstraint;
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
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Joiner;

public class FileSystemIngestionUtil {
    private static Logger _logger = LoggerFactory.getLogger(FileSystemIngestionUtil.class);
    public static final String UNMANAGEDFILESYSTEM = "UNMANAGEDFILESYSTEM";
    public static final String FILESYSTEM = "FILESYSTEM";

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

    public static boolean checkForDuplicateFSName(DbClient _dbClient, URI project, String fsName, List<FileShare> filesystems) {
        List<FileShare> objectList = new ArrayList<>();
        objectList = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileShare.class,
                ContainmentPrefixConstraint.Factory.getFullMatchConstraint(FileShare.class, "project",
                        project, fsName));
        if (objectList != null && !objectList.isEmpty()) {
            return true;
        }
        for (FileShare fs : filesystems) {
            if (fs.getLabel().equals(fsName) || fs.getName().equals(fsName)) {
                return true;
            }
        }
        return false;
    }
}
