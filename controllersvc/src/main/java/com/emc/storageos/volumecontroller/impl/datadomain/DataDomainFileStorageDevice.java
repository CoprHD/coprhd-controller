/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.datadomain;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.datadomain.restapi.DataDomainApiConstants;
import com.emc.storageos.datadomain.restapi.DataDomainClient;
import com.emc.storageos.datadomain.restapi.DataDomainClientFactory;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainResourceNotFoundException;
import com.emc.storageos.datadomain.restapi.model.DDExportClient;
import com.emc.storageos.datadomain.restapi.model.DDExportClientModify;
import com.emc.storageos.datadomain.restapi.model.DDExportInfo;
import com.emc.storageos.datadomain.restapi.model.DDExportInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDExportList;
import com.emc.storageos.datadomain.restapi.model.DDMCInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfo;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDServiceStatus;
import com.emc.storageos.datadomain.restapi.model.DDShareInfo;
import com.emc.storageos.datadomain.restapi.model.DDShareInfoDetail;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * DataDomain specific file controller implementation.
 */
public class DataDomainFileStorageDevice implements FileStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(DataDomainFileStorageDevice.class);

    DataDomainClientFactory _factory;
    DbClient _dbClient;

    /**
     * Set DataDomain API factory
     * 
     * @param factory
     */
    public void setDataDomainFactory(DataDomainClientFactory factory) {
        _factory = factory;
    }

    /**
     * Set DBClient
     * 
     * @param dbClient
     */
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Get DataDomain device represented by the StorageDevice
     * 
     * @param device StorageDevice object
     * @return DataDomainClient object
     * @throws com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException
     */
    private DataDomainClient getDataDomainClient(StorageSystem device) throws DataDomainApiException {
        URI providerId = device.getActiveProviderURI();
        StorageProvider provider = null;
        if (providerId != null) {
            provider = _dbClient.queryObject(StorageProvider.class, providerId);
        }

        DataDomainClient ddClient = null;
        if (provider != null) {
            ddClient = (DataDomainClient) _factory.getRESTClient(
                    DataDomainApiConstants.newDataDomainBaseURI(
                            device.getSmisProviderIP(),
                            device.getSmisPortNumber()),
                    provider.getUserName(),
                    provider.getPassword());
        }
        return ddClient;
    }

    private void ddDeleteExports(DataDomainClient ddClient,
            String storagePoolId,
            FSExportMap currentExports,
            List<FileExport> exportsToDelete) {
        if ((currentExports != null && (exportsToDelete != null) && (!exportsToDelete.isEmpty()))) {
            for (FileExport fileExport : exportsToDelete) {
                String key = fileExport.getFileExportKey();
                String ddExportId = null;
                FileExport fExport = currentExports.get(key);
                if (fExport != null) {
                    ddExportId = fExport.getNativeId();
                }
                if (ddExportId != null) {
                    DDExportInfoDetail ddExport = ddClient.getExport(storagePoolId, ddExportId);
                    if (ddExport.getPathStatus() == DataDomainApiConstants.PATH_EXISTS) {
                        DDServiceStatus ddSvcStatus = ddClient.deleteExport(storagePoolId, ddExportId);
                    }

                }
            }
        }
    }

    private void doAddDeleteClients(DataDomainClient ddClient, String storagePoolId,
            String exportId, List<ExportRule> rulesToModify, boolean delete) throws DataDomainApiException {
        if ((rulesToModify != null) && (!rulesToModify.isEmpty())) {
            // Build list of endpoints for rules being modified (added or deleted)
            List<DDExportClient> ddExportClients = new ArrayList<>();
            for (ExportRule ruleToModify : rulesToModify) {
                List<DDExportClient> ddExpClients = ddBuildExportClientList(ruleToModify);
                if (ddExpClients != null) {
                    ddExportClients.addAll(ddExpClients);
                }
            }
            // Build list of clients to be modified on the array
            List<DDExportClientModify> modifyClients = new ArrayList<>();
            for (DDExportClient ddExportClient : ddExportClients) {
                DDExportClientModify modifyClient = new DDExportClientModify(
                        ddExportClient.getName(), ddExportClient.getOptions(), delete);
                modifyClients.add(modifyClient);
            }
            // Modify clients on the array
            if (!modifyClients.isEmpty()) {
                DDExportInfo ddExportInfo = ddClient.modifyExport(storagePoolId,
                        exportId, modifyClients);

                if (ddExportInfo.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                    DDExportInfoDetail exportDetail = ddClient.getExport(storagePoolId, exportId);
                    if (delete) {
                        throw DataDomainApiException.exceptions.failedToDeleteExportClients(
                                exportDetail.getPath());
                    } else {
                        throw DataDomainApiException.exceptions.failedToAddExportClients(
                                exportDetail.getPath());
                    }
                }
            }
        }
    }

    private void doCreateExports(DataDomainClient ddClient, String storagePoolId,
            String exportPath, List<ExportRule> rulesToCreate) throws DataDomainApiException {
        if ((rulesToCreate != null) && (!rulesToCreate.isEmpty())) {
            // Build list of endpoints for rules being modified (added or deleted)
            List<DDExportClient> ddExportClients = new ArrayList<>();
            for (ExportRule ruleToCreate : rulesToCreate) {
                List<DDExportClient> ddExpClients = ddBuildExportClientList(ruleToCreate);
                if (ddExpClients != null) {
                    ddExportClients.addAll(ddExpClients);
                }
            }
            // Create export on the array
            if (!ddExportClients.isEmpty()) {
                DDExportInfo ddExportInfo = ddClient.createExport(storagePoolId,
                        exportPath, ddExportClients);
                if (ddExportInfo.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                    throw DataDomainApiException.exceptions.failedToCreateExport(
                            exportPath);
                }
            }
        }
    }

    private void doDeleteFsExport(DataDomainClient ddClient, String storagePoolId,
            String ddExportId) throws DataDomainApiException {
        if (ddExportId != null) {
            DDServiceStatus ddSvcStatus = ddClient.deleteExport(
                    storagePoolId, ddExportId);
            if (ddSvcStatus.getCode() != DataDomainApiConstants.SVC_CODE_SUCCESS) {
                StringBuilder message = new StringBuilder(ddSvcStatus.getCode());
                message.append(": " + ddSvcStatus.getDetails());
                throw DataDomainApiException.exceptions.failedToDeleteExport(
                        message.toString());
            }
        }
    }

    private void doDeleteExports(DataDomainClient ddClient, String storagePoolId,
            List<String> exportIdsToDelete) {
        if ((exportIdsToDelete != null) && (!exportIdsToDelete.isEmpty())) {
            for (String exportId : exportIdsToDelete) {
                DDServiceStatus ddSvcStatus = ddClient.deleteExport(
                        storagePoolId, exportId);
                if (ddSvcStatus.getCode() != DataDomainApiConstants.SVC_CODE_SUCCESS) {
                    StringBuilder message = new StringBuilder(ddSvcStatus.getCode());
                    message.append(": " + ddSvcStatus.getDetails());
                    throw DataDomainApiException.exceptions.failedToDeleteExport(
                            message.toString());
                }
            }
        }
    }

    private void ddDeleteShares(DataDomainClient ddClient,
            String storagePoolId,
            SMBShareMap currentShares,
            List<SMBFileShare> sharesToDelete) {
        if ((currentShares != null && (sharesToDelete != null))) {
            for (SMBFileShare fileShare : sharesToDelete) {
                String key = fileShare.getName();
                String ddShareId = null;
                SMBFileShare fShare = currentShares.get(key);
                if (fShare != null) {
                    ddShareId = fShare.getNativeId();
                }
                if (ddShareId != null) {
                    DDServiceStatus ddSvcStatus = ddClient.deleteShare(storagePoolId, ddShareId);
                    if (ddSvcStatus.getCode() == DataDomainApiConstants.SVC_CODE_SUCCESS) {
                        currentShares.remove(key);
                    }
                }
            }
        }
    }

    private List<DDExportClient> ddBuildCreateExportClientList(
            FileExport fileExport) {
        List<DDExportClient> ddExportClients = new ArrayList<>();
        String options = ddSetExportOptions(fileExport);
        List<String> addClients = fileExport.getClients();
        for (String client : addClients) {
            ddExportClients.add(new DDExportClient(client, options));
        }
        return (ddExportClients);
    }

    private List<DDExportClient> ddBuildExportClientList(
            ExportRule exportRule) {
        List<DDExportClient> ddExportClients = new ArrayList<>();
        Set<String> roHosts = exportRule.getReadOnlyHosts();
        Set<String> rwHosts = exportRule.getReadWriteHosts();
        Set<String> rootHosts = exportRule.getRootHosts();

        // Set options common to all permission types
        StringBuilder commonOptions = new StringBuilder(ddSetExportOptions(exportRule));

        _log.info("ddBuildExportClientList commonOptions {} for exportRule {}",
                commonOptions, exportRule.toString());
        // Read only exports
        if (roHosts != null) {
            for (String host : roHosts) {
                StringBuilder roOptions = new StringBuilder(commonOptions);
                roOptions.append(DataDomainApiConstants.PERMISSION_RO);
                ddExportClients.add(new DDExportClient(host, roOptions.toString()));
            }
        }

        // Read write exports
        if (rwHosts != null) {
            for (String host : rwHosts) {
                StringBuilder rwOptions = new StringBuilder(commonOptions);
                rwOptions.append(DataDomainApiConstants.PERMISSION_RW);
                ddExportClients.add(new DDExportClient(host, rwOptions.toString()));
            }
        }

        // Root exports
        if (rootHosts != null) {
            for (String host : rootHosts) {
                StringBuilder rootOptions = new StringBuilder(commonOptions);
                rootOptions.append(DataDomainApiConstants.PERMISSION_RW);
                ddExportClients.add(new DDExportClient(host, rootOptions.toString()));
            }
        }

        for (DDExportClient ddClient : ddExportClients) {
            _log.info("DDExportClient : {}", ddClient.toString());
        }

        return (ddExportClients);
    }

    private List<DDExportClientModify> ddBuildModifyExportClientList(
            FSExportMap exportMap, FileExport fileExport) {
        List<DDExportClientModify> ddExportClients = new ArrayList<>();
        String options = ddSetExportOptions(fileExport);

        // Add clients from the new export
        List<String> addClients = fileExport.getClients();

        // Remove clients not in the new export
        List<String> removeClients = new ArrayList<>();
        List<String> oldClients = exportMap.get(fileExport.getFileExportKey()).getClients();
        for (String oldClient : oldClients) {
            if (!addClients.contains(oldClient)) {
                removeClients.add(oldClient);
            }
        }

        // Add new clients
        for (String client : addClients) {
            ddExportClients.add(new DDExportClientModify(client, options, false));
        }
        // Remove old clients no more in the list
        for (String client : removeClients) {
            ddExportClients.add(new DDExportClientModify(client, options, true));
        }

        return (ddExportClients);
    }

    private void ddSetNewExportProperties(FileExport fileExport,
            List<DDExportClient> ddExportClients, DDExportInfo ddExportInfo) {
        fileExport.setNativeId(ddExportInfo.getId());
        fileExport.setMountPath(ddExportInfo.getPath());
        List<String> clients = new ArrayList<>();
        for (DDExportClient ddExportClient : ddExportClients) {
            clients.add(ddExportClient.getName());
        }

        // Note: We store the fileExport permissions as available in the original
        // FileExport object. Only Clients, Protocol and Security Types are updated.
        fileExport.setClients(clients);
        fileExport.setProtocol(DataDomainApiConstants.NFS_PROTOCOL);
        String securityType = fileExport.getSecurityType();
        if (securityType == null) {
            fileExport.setSecurityType(DataDomainApiConstants.DEFAULT_SECURITY);
        }
    }

    private void ddSetExistingExportProperties(FileExport existingExport,
            FileExport fExport, DDExportInfo ddExportInfo) {
        List<String> CurrClients = fExport.getClients();
        existingExport.setClients(CurrClients);

        // While storing in the ViPR DB, we need to store the unmanipulated permissions
        // from FileExport object
        existingExport.setPermissions(fExport.getPermissions());
        existingExport.setProtocol(DataDomainApiConstants.NFS_PROTOCOL);
        existingExport.setRootUserMapping(fExport.getRootUserMapping());
        String securityType = fExport.getSecurityType();
        if (securityType == null) {
            existingExport.setSecurityType(DataDomainApiConstants.DEFAULT_SECURITY);
        }
        existingExport.setStoragePort(fExport.getStoragePort());
        existingExport.setStoragePortName(fExport.getStoragePortName());
    }

    private String ddSetExportOptions(FileExport fileExport) {
        StringBuilder options = new StringBuilder();
        // Security type
        options.append(DataDomainApiConstants.SECURITY_TYPE_OPTION +
                fileExport.getSecurityType() + " ");

        // Set permission, "ro" by default
        options.append(ddSetPermissions(fileExport.getPermissions()) + " ");

        // Root mapping
        options.append(ddSetRootMappingOption(fileExport.getRootUserMapping()) + " ");

        // For now map all UIDs and GIDs to anonymous
        options.append(DataDomainApiConstants.ALL_SQUASH + " ");

        // For now assume connections from port below 1024 are allowed
        options.append(DataDomainApiConstants.SECURE + " ");

        // For now assume default anonymous UID and GID are used.
        // These will be set by the device.

        return options.toString();
    }

    private String ddSetExportOptions(ExportRule exportRule) {
        StringBuilder options = new StringBuilder();
        // Permissions will be specified somewhere else individually for each
        // client, based on the list (ro, rw, root) a client belongs to

        // Security type
        options.append(DataDomainApiConstants.SECURITY_TYPE_OPTION +
                exportRule.getSecFlavor() + " ");

        // Root mapping
        options.append(ddSetRootMappingOption(exportRule.getAnon()) + " ");

        // For now map all UIDs and GIDs to anonymous
        options.append(DataDomainApiConstants.ALL_SQUASH + " ");

        // For now assume connections from port below 1024 are allowed
        options.append(DataDomainApiConstants.SECURE + " ");

        // For now assume default anonymous UID and GID are used.
        // These will be set by the device.

        return options.toString();
    }

    private String ddSetPermissions(String permissions) {
        if (permissions.equalsIgnoreCase(FileShareExport.Permissions.rw.toString())) {
            return (FileShareExport.Permissions.rw.toString());
        } else if (permissions.equalsIgnoreCase(FileShareExport.Permissions.root.toString())) {
            return (FileShareExport.Permissions.rw.toString());
        } else {
            // Default permission on DataDomain
            return (FileShareExport.Permissions.ro.toString());
        }
    }

    private String ddSetRootMappingOption(String rootMapping) {
        if (rootMapping.equalsIgnoreCase(DataDomainApiConstants.ANONYMOUS.toString())) {
            return (DataDomainApiConstants.ROOT_SQUASH);
        } else {
            // Default setting for now
            return (DataDomainApiConstants.NO_ROOT_SQUASH);
        }
    }

    private String ddGetRootMappingOption(String rootOption) {
        if (rootOption.equalsIgnoreCase(DataDomainApiConstants.NO_ROOT_SQUASH)) {
            return (DataDomainApiConstants.ROOT);
        } else {
            // Default setting for now
            return (DataDomainApiConstants.ANONYMOUS);
        }
    }

    private void ddInitCreateModifyFileExportLists(
            FSExportMap currExpMap,
            List<FileExport> exportList,
            List<FileExport> modifyFileExports,
            List<FileExport> createFileExports) {
        Set<String> currExpKeys = currExpMap.keySet();
        for (FileExport fileExport : exportList) {
            String newExpKey = fileExport.getFileExportKey();
            if (currExpKeys.contains(newExpKey)) {
                // Existing export, add to modify list
                modifyFileExports.add(fileExport);
            } else {
                // New export, add to create list
                createFileExports.add(fileExport);
            }
        }
    }

    private void ddCreateExports(DataDomainClient ddClient, String storagePoolId,
            FSExportMap exportMap, List<FileExport> createFileExports)
            throws DataDomainApiException {
        for (FileExport fileExport : createFileExports) {
            // Build export map for export create
            String exportName;
            if (!fileExport.getPath().startsWith(DataDomainApiConstants.FS_PATH_BASE)) {
                exportName = DataDomainApiConstants.FS_PATH_BASE + fileExport.getPath();
                fileExport.setPath(exportName);
            } else {
                exportName = fileExport.getPath();
            }
            List<DDExportClient> ddExportClients = ddBuildCreateExportClientList(fileExport);
            for (DDExportClient ddExpClient : ddExportClients) {
                _log.info("DD Export Client {}", ddExpClient.toString());
            }
            DDExportInfo ddExportInfo = ddClient.createExport(storagePoolId,
                    exportName, ddExportClients);

            if (ddExportInfo.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                DDServiceStatus ddSvcStatus = ddClient.deleteExport(
                        storagePoolId, ddExportInfo.getId());
                throw DataDomainApiException.exceptions.failedExportPathDoesNotExist(exportName);
            } else {
                ddSetNewExportProperties(fileExport, ddExportClients, ddExportInfo);
                String exportKey = fileExport.getFileExportKey();
                exportMap.put(exportKey, fileExport);
            }
        }
    }

    private void ddModifyExports(DataDomainClient ddClient, String storagePoolId,
            FSExportMap exportMap, List<FileExport> modifyFileExports) {
        for (FileExport fileExport : modifyFileExports) {
            fileExport.setNativeId(exportMap.get(fileExport.getFileExportKey()).getNativeId());
            List<DDExportClientModify> ddExportClients = ddBuildModifyExportClientList(exportMap, fileExport);
            DDExportInfoDetail ddExport = ddClient.getExport(storagePoolId,
                    fileExport.getNativeId());
            if (ddExport.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                DDServiceStatus ddSvcStatus = ddClient.deleteExport(
                        storagePoolId, ddExport.getId());
                throw DataDomainApiException.exceptions.failedExportPathDoesNotExist(ddExport.getPath());
            } else {
                DDExportInfo ddExportInfo = ddClient.modifyExport(storagePoolId,
                        fileExport.getNativeId(), ddExportClients);
                FileExport existingExport = exportMap.get(fileExport.getFileExportKey());
                ddSetExistingExportProperties(existingExport, fileExport, ddExportInfo);
            }
        }
    }

    private void ddCreateShare(DataDomainClient ddClient, String storagePoolId,
            SMBFileShare smbFileShare, String sharePath) throws DataDomainApiException {
        String shareName = smbFileShare.getName();
        int maxUsers = smbFileShare.getMaxUsers();
        String desc = smbFileShare.getDescription();
        String permissionType = smbFileShare.getPermissionType();
        String permission = smbFileShare.getPermission();
        DDShareInfo ddShareInfo = ddClient.createShare(storagePoolId,
                shareName, sharePath, maxUsers, desc, permissionType, permission);
        if (ddShareInfo.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
            DDServiceStatus ddSvcStatus = ddClient.deleteShare(storagePoolId, ddShareInfo.getId());
            throw DataDomainApiException.exceptions.failedSharePathDoesNotExist(sharePath);
        }
        smbFileShare.setNativeId(ddShareInfo.getId());
    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        _log.info("DataDomainFileStorageDevice doCreateFS {} with name {} - start",
                args.getFsId(), args.getFsName());
        // TODO
        try {
            _log.info("DataDomainFileStorageDevice doCreateFS {} with name {} - start",
                    args.getFsId(), args.getFsName());
            DataDomainClient ddclient = getDataDomainClient(storage);
            if (ddclient == null) {
                _log.error("doCreateFS failed, provider unreachable");
                String op = "FS create";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }
            // Update path and mountPath
            // TODO: try to mount export
            String path = args.getFsName();
            String mountPath;
            if (!path.startsWith(DataDomainApiConstants.FS_PATH_BASE)) {
                mountPath = DataDomainApiConstants.FS_PATH_BASE + path;
            } else {
                mountPath = path;
            }
            _log.info("Mount path to mount the DataDomain File System {}", mountPath);
            args.setFsMountPath(mountPath);
            args.setFsPath(mountPath);

            // Create MTree
            // Data Domain expects capacity in Bytes
            Long mtreeCapacity = args.getFsCapacity();
            // TODO: Following two values are hard-coded for now, until they are implemented in UI
            Boolean enableRetention = false;
            String retentionMode = "compliance";
            DDMTreeInfo ddMtreeInfo = ddclient.createMTree(args.getStoragePool().getNativeId(),
                    mountPath, mtreeCapacity, enableRetention, retentionMode);
            args.setFsNativeId(ddMtreeInfo.getId());
            String serialNumber = storage.getSerialNumber();
            if (serialNumber == null) {
                serialNumber = storage.getModel();
            }
            String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    storage.getSystemType(),
                    serialNumber.toUpperCase(),
                    ddMtreeInfo.getId());
            args.setFsNativeGuid(fsNativeGuid);
            args.setNewFSCapacity(args.getFsCapacity());

            if (args.getFsExtensions() == null) {
                args.initFsExtensions();
            }
            args.getFsExtensions().put(DataDomainApiConstants.TOTAL_PHYSICAL_CAPACITY, String.valueOf(args.getFsCapacity()));

            _log.info("DataDomainFileStorageDevice doCreateFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doCreateFS failed, device error...attempting to delete FS to rollback.", e);
            // rollback this operation to prevent partial result of file share create
            BiosCommandResult rollbackResult = doDeleteFS(storage, args);
            if (rollbackResult.isCommandSuccess()) {
                _log.info("DataDomainFileStorageDevice doCreateFS {} - rollback completed.", args.getFsId());
            } else {
                _log.error("DataDomainFileStorageDevice doCreateFS {} - rollback failed,  message: {} .", args.getFsId(),
                        rollbackResult.getMessage());
            }
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {

        try {
            _log.info("DataDomainFileStorageDevice doDeleteFS {} - start",
                    args.getFsId());
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doDeleteFS failed, provider unreachable");
                String op = "FS delete";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }

            URI storagePoolId = args.getFs().getPool();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolId);

            // Delete the exports for this file system
            FSExportMap exportMap = args.getFsExports();
            List<FileExport> exportMapvalues = null;
            if (exportMap != null) {
                exportMapvalues = new ArrayList<>(exportMap.values());
            }
            if ((exportMap != null) && (exportMapvalues != null)) {
                try {
                    ddDeleteExports(ddClient, storagePool.getNativeId(), exportMap,
                            exportMapvalues);
                } catch (DataDomainApiException dde) {
                    _log.error("Unable to delete exports for the FS: ", dde);
                }
            }

            // Delete the SMB shares for this file system
            SMBShareMap shareMap = args.getFsShares();
            List<SMBFileShare> shareMapValues = null;
            if (shareMap != null) {
                shareMapValues = new ArrayList<>(shareMap.values());
            }
            if ((shareMap != null) && (shareMapValues != null)) {
                try {
                    ddDeleteShares(ddClient, storagePool.getNativeId(), shareMap,
                            shareMapValues);
                } catch (DataDomainApiException dde) {
                    _log.error("Unable to delete cifs shares for the FS: ", dde);
                }
            }

            // Delete mtree on the DD array
            DDServiceStatus ddSvcStatus = ddClient.deleteMTree(
                    storagePool.getNativeId(), args.getFs().getNativeId());
            _log.info("DataDomainFileStorageDevice doDeleteFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doDeleteFS failed, device error", e);
            return BiosCommandResult.createErrorResult(e);

        } catch (DataDomainResourceNotFoundException e) {
            _log.error("doDeleteFS failed, Mtree not found.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public boolean doCheckFSExists(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        _log.info("checking file system existence on array: ", args.getFsName());
        boolean isMtreeExists = true;
        try {
            DataDomainClient ddClient = getDataDomainClient(storage);
            URI storagePoolId = args.getFs().getPool();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolId);
            DDMTreeInfoDetail mtreeInfo = ddClient.getMTree(storagePool.getNativeId(), args.getFs().getNativeId());
            if (mtreeInfo != null && (mtreeInfo.id.equals(args.getFsNativeId()))) {
                isMtreeExists = true;
            }
        } catch (DataDomainResourceNotFoundException e) {
            _log.info("Mtree not found.", e);
            isMtreeExists = false;
        }
        return isMtreeExists;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        try {
            _log.info("DataDomainFileStorageDevice doExpandFS {} - start", args.getFsId());
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doExpandFS failed, provider unreachable");
                String op = "FS expand";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }
            Long newSize = args.getNewFSCapacity();
            Long currSize;
            if ((args.getFsCapacity() != null) && (args.getFsCapacity() > 0)) {
                currSize = args.getFsCapacity();
            } else {
                ServiceError serviceError = DeviceControllerErrors.datadomain.doFailedToGetCurrSize();
                return BiosCommandResult.createErrorResult(serviceError);
            }

            if (currSize >= newSize) {
                ServiceError serviceError = DeviceControllerErrors.datadomain.doShrinkFSFailed(currSize, newSize);
                return BiosCommandResult.createErrorResult(serviceError);
            }

            // Modify mtree
            // Data Domain expects capacity in Bytes
            DDMTreeInfo ddMtreeInfo = ddClient.expandMTree(
                    args.getStoragePool().getNativeId(), args.getFs().getNativeId(),
                    newSize);

            if (args.getFsExtensions() == null) {
                args.initFsExtensions();
            }
            args.getFsExtensions().put(DataDomainApiConstants.TOTAL_PHYSICAL_CAPACITY, String.valueOf(newSize));

            _log.info("DataDomainFileStorageDevice doExpandFS {} - complete", args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doExpandFS failed, device error.", e);
            return BiosCommandResult.createErrorResult(e);
        } catch (Exception e) {
            _log.error("doExpandFS failed.", e);
            ServiceError serviceError = DeviceControllerErrors.datadomain.doExpandFSFailed(e.getMessage());
            return BiosCommandResult.createErrorResult(serviceError);
        }
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage, FileDeviceInputOutput args,
            List<FileExport> exportList) throws ControllerException {

        _log.info("DataDomainFileStorageDevice doExport {} - start", args.getFileObjId());
        // Snapshot Export operation is not supported by Data Domain.
        if (args.getFileOperation() == false) {
            return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.doCreateSnapshotExportFailed());
        }

        if ((exportList == null) || (exportList.isEmpty())) {
            return BiosCommandResult.createSuccessfulResult();
        }

        try {
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doExport failed, provider unreachable");
                String op = "FS export";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }

            if ((args.getFsExports() == null) || (args.getFsExports().isEmpty())) {
                // Initialize exports map
                args.initFileObjExports();
            }

            // Go through the list of new exports and add each to create list
            // if not in the list of existing exports or to the modify list if
            // contained in the existing list
            FSExportMap currExpMap = args.getFsExports();
            List<FileExport> modifyFileExports = new ArrayList<>();
            List<FileExport> createFileExports = new ArrayList<>();
            ddInitCreateModifyFileExportLists(currExpMap, exportList,
                    modifyFileExports, createFileExports);

            // Create new exports and add to file export map
            ddCreateExports(ddClient, args.getStoragePool().getNativeId(),
                    args.getFsExports(), createFileExports);

            // Modify existing exports
            ddModifyExports(ddClient, args.getStoragePool().getNativeId(),
                    args.getFsExports(), modifyFileExports);

            _log.info("DataDomainFileStorageDevice doExport {} - complete", args.getFileObjId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doExport failed, device error.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage,
            FileDeviceInputOutput args,
            List<FileExport> exportList) throws ControllerException {
        try {
            _log.info("DataDomainFileStorageDevice doUnexport {} - start",
                    args.getFsId());
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doUnexport failed, provider unreachable");
                String op = "FS unexport";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }
            URI storagePoolId = args.getFs().getPool();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolId);
            FSExportMap currentExports = args.getFsExports();
            ddDeleteExports(ddClient, storagePool.getNativeId(), currentExports,
                    exportList);
            _log.info("DataDomainFileStorageDevice doUnexport {} - complete",
                    args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doUnexport failed, device error.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doShare(StorageSystem storage, FileDeviceInputOutput args,
            SMBFileShare smbFileShare) throws ControllerException {
        try {
            _log.info("DataDomainFileStorageDevice doShare() - start");
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doShare failed, provider unreachable");
                String op = "FS share create";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }

            // Check if this is a new share or update of the existing share
            SMBShareMap smbShareMap = args.getFileObjShares();
            SMBFileShare existingShare = (smbShareMap == null) ? null : smbShareMap.get(smbFileShare.getName());

            String shareId;
            DDShareInfo ddShareInfo;
            // Cannot send empty description, send the share name in that case
            if (smbFileShare.getDescription() == null || smbFileShare.getDescription().isEmpty()) {
                _log.debug("SMB Share creation was called with empty description and setting name as desc");
                smbFileShare.setDescription(smbFileShare.getName());
            }
            if (existingShare != null) {
                shareId = existingShare.getNativeId();
                // modify share
                URI storagePoolId = args.getFs().getPool();
                StoragePool storagePool = _dbClient.queryObject(
                        StoragePool.class, storagePoolId);
                DDShareInfoDetail ddShareInfoDetail = ddClient.getShare(storagePool.getNativeId(), shareId);

                if (ddShareInfoDetail.getPathStatus() == 0) {
                    DDServiceStatus ddSvcStatus = ddClient.deleteShare(storagePool.getNativeId(), shareId);
                    throw DataDomainApiException.exceptions.failedSharePathDoesNotExist(ddShareInfoDetail.getPath());
                }
                ddShareInfo = ddClient.modifyShare(
                        storagePool.getNativeId(),
                        shareId, smbFileShare.getDescription());

            } else {
                // new share
                URI storagePoolId = args.getFs().getPool();
                StoragePool storagePool = _dbClient.queryObject(
                        StoragePool.class, storagePoolId);
                ddCreateShare(ddClient, storagePool.getNativeId(),
                        smbFileShare, smbFileShare.getPath());
            }

            // init file share map
            if (args.getFileObjShares() == null) {
                args.initFileObjShares();
            }
            args.getFileObjShares().put(smbFileShare.getName(), smbFileShare);

            _log.info("DataDomainFileStorageDevice doShare() - complete");
            // Set MountPoint
            smbFileShare.setMountPoint(smbFileShare.getStoragePortNetworkId(), smbFileShare.getStoragePortName(),
                    smbFileShare.getName());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doShare failed, device error.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage,
            FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        try {
            _log.info("DataDomainFileStorageDevice doDeleteShare: {} - start");
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doDeleteShare failed, provider unreachable");
                String op = "FS share delete";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }
            URI storagePoolId = args.getFs().getPool();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolId);
            SMBShareMap currentShares = args.getFileObjShares();
            List<SMBFileShare> sharesToDelete = new ArrayList<SMBFileShare>();
            sharesToDelete.add(smbFileShare);
            ddDeleteShares(ddClient, storagePool.getNativeId(),
                    currentShares, sharesToDelete);
            _log.info("DataDomainFileStorageDevice doDeleteShare {} - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doDeleteShare failed, device error.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {

        try {
            _log.info("DataDomainFileStorageDevice doDeleteShares: {} - start");
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doDeleteShares failed, provider unreachable");
                String op = "FS shares delete";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }
            URI storagePoolId = args.getFs().getPool();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolId);
            SMBShareMap currentShares = args.getFileObjShares();
            List<SMBFileShare> sharesToDelete = new ArrayList<SMBFileShare>();
            sharesToDelete.addAll(currentShares.values());
            ddDeleteShares(ddClient, storagePool.getNativeId(),
                    currentShares, sharesToDelete);
            _log.info("DataDomainFileStorageDevice doDeleteShare {} - complete");
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException e) {
            _log.error("doDeleteShare failed, device error.", e);
            return BiosCommandResult.createErrorResult(e);
        }
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        // TODO
        return null;
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        String message = "Snapshot creation on Data Domain not supported yet, create operation failed";
        _log.error(message);
        ServiceError serviceError = DeviceControllerErrors.datadomain.operationNotSupported();
        serviceError.setMessage(message);
        serviceError.setRetryable(false);
        return BiosCommandResult.createErrorResult(serviceError);

        // TODO Following lines may be uncommented once Data domain provides snapshot APIs
        // try {
        // _log.info("DataDomainFileStorageDevice doSnapshotFS {} {} - start",
        // args.getSnapshotId(), args.getSnapshotName());
        // DataDomainClient ddClient = getDataDomainClient(storage);
        // // To Do - add timestamp for uniqueness
        // DDSnapshotCreate snapshotCreate = new DDSnapshotCreate(
        // args.getSnapshotName(), args.getFsMountPath());
        // // TODO: Following will be uncommented when DD API becomes available.
        // DDSnapshot snapshot = ddClient.createSnapshot(
        // args.getStoragePool().getNativeId(), snapshotCreate);
        // if (args.getSnapshotExtensions() == null) {
        // args.initSnapshotExtensions();
        // }
        // // TODO: These are placeholders until DD APIs are implemented
        // String id = "fictitiousId";
        // String name = "fictitiousName";
        // String path = "fictitiousPath";
        // DDSnapshot snapshot = new DDSnapshot(id, name, path);
        // // Remove lines above
        //
        // args.setSnapNativeId(snapshot.getId());
        // // For now Data Domain does not support snapshot export
        // args.setSnapshotMountPath(snapshot.getPath());
        // args.setSnapshotPath(snapshot.getPath());
        // _log.info("DataDomainFileStorageDevice doSnapshotFS {} - complete",
        // args.getSnapshotId());
        // return BiosCommandResult.createSuccessfulResult();
        // } catch (DataDomainApiException e) {
        // _log.error("doSnapshotFS failed, device error. ", e);
        // return BiosCommandResult.createErrorResult(e);
        // }
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        // TODO To be implemented once Data Domain provides snapshot APIs
        return BiosCommandResult.createSuccessfulResult();
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage,
            FileDeviceInputOutput args)
            throws ControllerException {

        String message = "Data Domain snapshots not supported yet, delete operation failed";
        _log.error(message);
        ServiceError serviceError = DeviceControllerErrors.datadomain.operationNotSupported();
        serviceError.setMessage(message);
        serviceError.setRetryable(false);
        return BiosCommandResult.createErrorResult(serviceError);

        // TODO Uncomment the following lines once Data Domain provides snapshot APIs
        // if (args.getFsName() == null) {
        // return BiosCommandResult.createErrorResult(
        // DeviceControllerErrors.datadomain.
        // doDeleteSnapshotFailedNoFSName());
        // }
        //
        // if (args.getSnapshotName() == null) {
        // return BiosCommandResult.createErrorResult(
        // DeviceControllerErrors.datadomain.
        // doDeleteSnapshotFailedNoSnapName());
        // }
        // try{
        // _log.info("DataDomainFileStorageDevice doDeleteSnapshot {} - start",
        // args.getSnapshotId());
        // DataDomainClient ddClient = getDataDomainClient(storage);
        //
        // // Data Domain does not support exports or shares on snapshots.
        // // Just delete snapshot
        // String ddSnapshotId = args.getSnapNativeId();
        // URI storagePoolId = args.getFs().getPool();
        // StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
        // storagePoolId);
        // // TODO: Following will be uncommented when DD API becomes available.
        // DDServiceStatus svcStatus = ddClient.deleteSnapshot(
        // storagePool.getNativeId(), ddSnapshotId);
        // _log.info("DataDomainFileStorageDevice doDeleteSnapshot {} - complete",
        // args.getSnapshotId());
        // return BiosCommandResult.createSuccessfulResult();
        // } catch (DataDomainApiException e) {
        // _log.error("doDeleteSnapshot failed.", e);
        // return BiosCommandResult.createErrorResult(e);
        // }
    }

    // Get FS snapshot list from the array
    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput args, List<String> snapshots)
            throws ControllerException {
        // TODO To be implemented once Data Domain provides snapshot APIs
        String message = "Data Domain snapshots not supported yet, get list operation failed";
        _log.error(message);
        ServiceError serviceError = DeviceControllerErrors.datadomain.operationNotSupported();
        serviceError.setMessage(message);
        serviceError.setRetryable(false);
        return BiosCommandResult.createErrorResult(serviceError);
    }

    @Override
    public void doConnect(StorageSystem storage) {
        try {
            _log.info("doConnect {} - start", storage.getId());
            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("doConnect failed, provider unreachable");
                String sys = storage.getLabel() + "(" + storage.getIpAddress()
                        + ")";
                throw DataDomainApiException.exceptions.connectStorageFailed(sys);
            }
            DDMCInfoDetail ddInfo = ddClient.getManagementSystemInfo();
            String msg = String.format("doConnect %1$s - complete", ddInfo);
            _log.info(msg);
        } catch (DataDomainApiException e) {
            _log.error("doConnect failed.", e);
            throw DeviceControllerException.exceptions.connectStorageFailed(e);
        }
    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        // not much to do here ... just reply success
    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        // TODO
        return null;
    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qd) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ServiceError serviceError = DeviceControllerErrors.datadomain.operationNotSupported();
        result = BiosCommandResult.createErrorResult(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ServiceError serviceError = DeviceControllerErrors.datadomain.operationNotSupported();
        result = BiosCommandResult.createErrorResult(serviceError);
        return result;
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory qd) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ServiceError serviceError = DeviceControllerErrors.datadomain.operationNotSupported();
        result = BiosCommandResult.createErrorResult(serviceError);
        return result;
    }

    private boolean repeatedClients(List<String> clients,
            List<ExportRule> exportRules) {
        if ((exportRules != null) && (!exportRules.isEmpty())) {
            for (ExportRule expRule : exportRules) {
                if (expRule.getReadOnlyHosts() != null) {
                    for (String client : expRule.getReadOnlyHosts()) {
                        if (clients.contains(client)) {
                            return true;
                        } else {
                            clients.add(client);
                        }
                    }
                }
                if (expRule.getReadWriteHosts() != null) {
                    for (String client : expRule.getReadWriteHosts()) {
                        if (clients.contains(client)) {
                            return true;
                        } else {
                            clients.add(client);
                        }
                    }
                }
                if (expRule.getRootHosts() != null) {
                    for (String client : expRule.getRootHosts()) {
                        if (clients.contains(client)) {
                            return true;
                        } else {
                            clients.add(client);
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean addingExistingClient(List<ExportRule> existingExports,
            String exportPath, List<ExportRule> exportAdd) {
        List<String> clients = new ArrayList<>();
        if ((existingExports != null) && (!existingExports.isEmpty()) &&
                (exportAdd != null) && (!exportAdd.isEmpty())) {
            for (ExportRule exportRule : existingExports) {
                if (exportRule.getExportPath().equals(exportPath)) {
                    if (exportRule.getReadOnlyHosts() != null) {
                        clients.addAll(exportRule.getReadOnlyHosts());
                    }
                    if (exportRule.getReadWriteHosts() != null) {
                        clients.addAll(exportRule.getReadWriteHosts());
                    }
                    if (exportRule.getRootHosts() != null) {
                        clients.addAll(exportRule.getRootHosts());
                    }
                }
            }
            if (repeatedClients(clients, exportAdd)) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatedClientsInRequest(List<ExportRule> exportAdd,
            List<ExportRule> exportDelete, List<ExportRule> exportModify) {
        List<String> clients = new ArrayList<>();
        if (repeatedClients(clients, exportAdd)) {
            return true;
        }
        if (repeatedClients(clients, exportDelete)) {
            return true;
        }
        if (repeatedClients(clients, exportModify)) {
            return true;
        }
        return false;
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {

        // Requested Export Rules
        List<ExportRule> exportAdd = args.getExportRulesToAdd();
        List<ExportRule> exportDelete = args.getExportRulesToDelete();
        List<ExportRule> exportModify = args.getExportRulesToModify();

        // Export path
        String exportPath;
        String subDir = args.getSubDirectory();

        StringBuilder path = new StringBuilder();
        if (!args.getFileOperation()) {
            path.append(args.getSnapshotPath());
        } else {
            path.append(args.getFs().getPath());
        }
        if ((subDir != null) && (subDir.length() > 0)) {
            path.append("/");
            path.append(subDir);
        }
        exportPath = path.toString();

        // Data Domain attaches a prefix to every file system path
        path = new StringBuilder();
        if (!exportPath.startsWith(DataDomainApiConstants.FS_PATH_BASE)) {
            path.append(DataDomainApiConstants.FS_PATH_BASE);
        }
        path.append(exportPath);
        _log.info("exportPath : {}", path);
        args.setExportPath(path.toString());

        // Check to ensure clients are not repeated
        if (repeatedClientsInRequest(exportAdd, exportDelete, exportModify)) {
            ServiceError serviceError = DeviceControllerErrors.datadomain
                    .exportUpdateFailedRepeatedClients();
            return BiosCommandResult.createErrorResult(serviceError);
        }

        // Ensure that the clients being added don't already exist in
        // another export rule
        List<ExportRule> existingExports = args.getExistingDBExportRules();
        if (addingExistingClient(existingExports, path.toString(), exportAdd)) {
            ServiceError serviceError = DeviceControllerErrors.datadomain
                    .exportUpdateFailedAddingExistingClient();
            return BiosCommandResult.createErrorResult(serviceError);
        }

        // To be processed export rules
        List<ExportRule> exportsToRemove = new ArrayList<>();
        List<ExportRule> exportsToAdd = new ArrayList<>();

        if (existingExports != null && !existingExports.isEmpty()) {
            args.setObjIdOnDevice(existingExports.get(0).getDeviceExportId());
            _log.info("Number of existng Rules found {}", existingExports.size());

        }

        // Create/Modify/Delete exports on the array
        DataDomainClient ddClient = getDataDomainClient(storage);
        if (ddClient == null) {
            _log.error("updateExportRules failed, provider unreachable");
            String op = "Update export rules";
            return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
        }
        URI storagePoolId = args.getFs().getPool();
        StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                storagePoolId);

        // Process modify list
        // If all existing export rules are being modified, all corresponding
        // end points must be deleted first then recreated. This requires
        // deleting the export on the array.
        // If not all existing export rules are being modified, end points
        // may be deleted selectively without deleting the export.
        if ((exportModify != null) && (!exportModify.isEmpty())) {
            // Are all existing rules being modified?
            int existingRulesToModify = 0;
            int numExistingRules = 0;
            for (ExportRule modifyRule : exportModify) {
                String securityFlavor = modifyRule.getSecFlavor();
                for (ExportRule existingRule : existingExports) {
                    if (existingRule.getExportPath().equals(path.toString())) {
                        if (existingRule.getSecFlavor().equals(securityFlavor)) {
                            existingRulesToModify++;
                        }
                        numExistingRules++;
                    }
                }
            }

            if (existingRulesToModify == numExistingRules) {
                // All rules are being modified, delete the existing export on the array
                String deviceExportId = null;
                for (ExportRule existingRule : existingExports) {
                    if (existingRule.getExportPath().equals(path.toString())) {
                        deviceExportId = existingRule.getDeviceExportId();
                        break;
                    }
                }
                try {
                    doDeleteFsExport(ddClient, storagePool.getNativeId(), deviceExportId);
                } catch (DataDomainApiException dde) {
                    _log.error("Export update failed, device error.", dde);
                    return BiosCommandResult.createErrorResult(dde);
                }
                // Export rules to create
                for (ExportRule modifyRule : exportModify) {
                    exportsToAdd.add(modifyRule);
                }
                doCreateExports(ddClient, storagePool.getNativeId(), path.toString(),
                        exportsToAdd);
                exportsToAdd.clear();
            } else {
                // Not all are being modified, modify existing export by deleting
                // end points selectively
                for (ExportRule modifyRule : exportModify) {
                    String securityFlavor = modifyRule.getSecFlavor();
                    ExportRule matchingRule = null;
                    for (ExportRule existingRule : existingExports) {
                        if (existingRule.getSecFlavor().equals(securityFlavor)) {
                            matchingRule = existingRule;
                            break;
                        }
                    }
                    // Since the list has been validated already, we can safely assume we will
                    // always find a matching existing rule.
                    modifyRule.setExportPath(args.getExportPath());
                    modifyRule.setFsID(args.getFsId());
                    exportsToRemove.add(matchingRule);
                    exportsToAdd.add(modifyRule);
                }
            }
        }

        // Process add list
        if ((exportAdd != null) && (!exportAdd.isEmpty())) {
            for (ExportRule newExport : exportAdd) {
                _log.info("Adding Export Rule {}", newExport);
                if (args.getFileObjExports() != null) {
                    Collection<FileExport> expList = args.getFileObjExports().values();
                    Iterator<FileExport> it = expList.iterator();
                    FileExport exp = null;

                    while (it.hasNext()) {
                        FileExport export = it.next();
                        if (export.getPath().equalsIgnoreCase(path.toString())) {
                            exp = export;
                        }
                    }
                    // set the device export id with export id.
                    if (exp != null) {
                        if (exp.getIsilonId() != null) {
                            newExport.setDeviceExportId(exp.getIsilonId());
                        }
                        if (exp.getNativeId() != null) {
                            newExport.setDeviceExportId(exp.getNativeId());
                        }
                    }
                }
                newExport.setExportPath(args.getExportPath());
                newExport.setFsID(args.getFsId());
                exportsToAdd.add(newExport);
            }
            // If there are no existing rules, create export.
            // otherwise, update the exports.
            if (existingExports == null || existingExports.isEmpty()) {
                doCreateExports(ddClient, storagePool.getNativeId(), path.toString(),
                        exportsToAdd);
                exportsToAdd.clear();
            }

        }

        // Process delete list
        // If all existing rules are being deleted, simply delete the FS export.
        // If not all rules are being deleted, update export by deleting end points
        // selectively, without disrupting unaffected end points.
        if ((exportDelete != null) && (!exportDelete.isEmpty())) {
            // Are all existing rules being deleted?
            int existingRulesToDelete = 0;
            for (ExportRule deleteRule : exportDelete) {
                String securityFlavor = deleteRule.getSecFlavor();
                for (ExportRule existingRule : existingExports) {
                    if (existingRule.getSecFlavor().equals(securityFlavor)) {
                        existingRulesToDelete++;
                    }
                }
            }

            if (existingRulesToDelete == existingExports.size()) {
                // All rules are being deleted, delete the existing export on the array
                String deviceExportId = existingExports.get(0).getDeviceExportId();
                try {
                    doDeleteFsExport(ddClient, storagePool.getNativeId(), deviceExportId);
                } catch (DataDomainApiException dde) {
                    _log.error("Export update failed, device error.", dde);
                    return BiosCommandResult.createErrorResult(dde);
                }
            } else {
                // Not all rules are being deleted, modify existing export by deleting
                // end points selectively
                for (ExportRule deleteRule : exportDelete) {
                    String securityFlavor = deleteRule.getSecFlavor();
                    ExportRule matchingRule = null;
                    for (ExportRule existingRule : existingExports) {
                        if (existingRule.getSecFlavor().equals(securityFlavor)) {
                            matchingRule = existingRule;
                            break;
                        }
                    }
                    // Since the list has been validated already, we can safely assume we will
                    // always find a matching existing rule.
                    exportsToRemove.add(matchingRule);
                }
            }
        }

        _log.info("No of exports to be removed from the existing list {}", exportsToRemove.size());
        _log.info("No of exports to be added to the existing list {}", exportsToAdd.size());

        // Delete clients selectively
        try {
            String deviceExportId = null;
            for (ExportRule existingRule : existingExports) {
                if (existingRule.getExportPath().equals(path.toString())) {
                    deviceExportId = existingRule.getDeviceExportId();
                    break;
                }
            }
            boolean deleteClients = true;
            doAddDeleteClients(ddClient, storagePool.getNativeId(), deviceExportId,
                    exportsToRemove, deleteClients);
        } catch (DataDomainApiException dde) {
            _log.error("Export update failed, device error.", dde);
            return BiosCommandResult.createErrorResult(dde);
        }

        // Create exports
        try {
            String deviceExportId = null;
            for (ExportRule existingRule : existingExports) {
                if (existingRule.getExportPath().equals(path.toString())) {
                    deviceExportId = existingRule.getDeviceExportId();
                    break;
                }
            }
            boolean deleteClients = false;
            doAddDeleteClients(ddClient, storagePool.getNativeId(), deviceExportId,
                    exportsToAdd, deleteClients);
        } catch (DataDomainApiException dde) {
            _log.error("Export update failed, device error.", dde);
            return BiosCommandResult.createErrorResult(dde);
        }

        _log.info("DataDomainFileStorageDevice updateFSExportRules {} - complete",
                args.getFsId());
        return BiosCommandResult.createSuccessfulResult();

    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) {
        try {

            // TODO - These lines may be removed once DD snapshot APIs become available.
            if (!args.getFileOperation()) {
                // Snapshot Export operation is not supported by Data Domain.
                ServiceError serviceError = DeviceControllerErrors.datadomain
                        .operationNotSupported();
                serviceError.setMessage("Data Domain does not support snapshot export");
                return BiosCommandResult.createErrorResult(serviceError);
            }

            _log.info("DataDomainFileStorageDevice deleteExportRules - start");

            FileShare fs = args.getFs();

            // List of existing export rules
            List<ExportRule> existingExports = args.getExistingDBExportRules();
            if ((existingExports == null) || (existingExports.isEmpty())) {
                _log.info(
                        "Export rule delete, file system {} does not have an existing export to delete",
                        args.getFsId());
                return BiosCommandResult.createSuccessfulResult();
            }

            _log.info("Number of existng Rules found {}", existingExports.size());

            // Build export path, adding sub-directory if not null and non-empty
            String exportPath;
            String subDir = args.getSubDirectory();

            StringBuilder expPath = new StringBuilder();
            if (!args.getFileOperation()) {
                expPath.append(args.getSnapshotPath());
            } else {
                expPath.append(args.getFs().getPath());
            }
            if ((subDir != null) && (subDir.length() > 0)) {
                expPath.append("/");
                expPath.append(subDir);
            }
            exportPath = expPath.toString();

            // Data Domain attaches a prefix to every file system path
            expPath = new StringBuilder();
            if (!exportPath.startsWith(DataDomainApiConstants.FS_PATH_BASE)) {
                expPath.append(DataDomainApiConstants.FS_PATH_BASE);
            }
            expPath.append(exportPath);
            _log.info("exportPath : {}", expPath);
            args.setExportPath(expPath.toString());

            for (ExportRule expRule : existingExports) {
                if (expRule.getExportPath().equals(expPath.toString())) {
                    args.setObjIdOnDevice(expRule.getDeviceExportId());
                    break;
                }
            }

            // Do we need to delete all subdirectories as well?
            boolean allDirs = args.isAllDir();

            // List of IDs of exports to delete
            List<String> exportIdsToDelete = new ArrayList<>();
            exportIdsToDelete.add(args.getObjIdOnDevice());

            DataDomainClient ddClient = getDataDomainClient(storage);
            if (ddClient == null) {
                _log.error("deleteExportRules failed, provider unreachable");
                String op = "Delete export rules";
                return BiosCommandResult.createErrorResult(DeviceControllerErrors.datadomain.operationFailedProviderInaccessible(op));
            }
            URI storagePoolId = args.getFs().getPool();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolId);

            if (allDirs) {
                // Add to the list of IDs of exports to delete
                buildListOfIdsToDelete(ddClient, storagePool.getNativeId(), expPath.toString(), exportIdsToDelete);
            }

            doDeleteExports(ddClient, storagePool.getNativeId(), exportIdsToDelete);

            _log.info("DataDomainFileStorageDevice deleteExportRules {} - complete",
                    args.getFsId());
            return BiosCommandResult.createSuccessfulResult();
        } catch (DataDomainApiException dde) {
            _log.error("Export update failed, device error:", dde);
            return BiosCommandResult.createErrorResult(dde);
        }
    }

    private void buildListOfIdsToDelete(DataDomainClient ddClient, String system, String exportPath,
            List<String> exportIdsToDelete) {
        // Get the list of all exports from the array, select those for deletion that
        // have a path that starts with exportPath.
        DDExportList exportList = ddClient.getExports(system);
        if ((exportList != null) && (exportList.getExports() != null) && (!exportList.getExports().isEmpty())) {
            for (DDExportInfo exportInfo : exportList.getExports()) {
                if ((exportInfo.getPath().startsWith(exportPath)) &&
                        (!exportIdsToDelete.contains(exportInfo.getId()))) {
                    exportIdsToDelete.add(exportInfo.getId());
                }
            }
        }
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.datadomain.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storageObj,
            FileDeviceInputOutput args) {

        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.datadomain.operationNotSupported());
    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.datadomain.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.datadomain.operationNotSupported());
    }

}
