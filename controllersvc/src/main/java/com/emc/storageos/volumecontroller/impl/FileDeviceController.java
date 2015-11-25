/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NFSShareACL;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.file.CifsShareACLUpdateParams;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.model.file.ExportRules;
import com.emc.storageos.model.file.FileExportUpdateParams;
import com.emc.storageos.model.file.NfsACE;
import com.emc.storageos.model.file.NfsACLUpdateParams;
import com.emc.storageos.model.file.ShareACL;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileController;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileSMBShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.FileShareQuotaDirectory;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * Generic File Controller Implementation that does all of the database
 * operations and calls methods on the array specific implementations
 * 
 * @author burckb
 * 
 */
public class FileDeviceController implements FileController {

    private DbClient _dbClient;
    private static final String EVENT_SERVICE_TYPE = "file";
    private static final String EVENT_SERVICE_SOURCE = "FileController";
    private static final Logger _log = LoggerFactory.getLogger(FileDeviceController.class);
    private Map<String, FileStorageDevice> _devices;

    private RecordableEventManager _eventManager;
    private AuditLogManager _auditMgr;

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setDevices(Map<String, FileStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    public void setEventManager(RecordableEventManager eventManager) {
        _eventManager = eventManager;
    }

    public void setAuditLogManager(AuditLogManager auditManager) {
        _auditMgr = auditManager;
    }

    private FileStorageDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }

    /**
     * Create a nice event based on the File Share
     * 
     * @param fs FileShare for which the event is about
     * @param type Type of event such as VolumeCreated or VolumeDeleted
     * @param description Description for the event if needed
     */
    public static void recordFsEvent(DbClient dbClient, FileShare fs, String type, String description, String extensions) {
        if (fs == null) {
            _log.error("Invalid FileShare event");
            return;
        }
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);
        // TODO fix the bogus user ID once we have AuthZ working
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(fs, type, description,
                extensions, dbClient, EVENT_SERVICE_TYPE, RecordType.Event.name(), EVENT_SERVICE_SOURCE);

        try {
            eventManager.recordEvents(event);
        } catch (Exception th) {
            _log.error("Failed to record event. Event description: {}.", description, th);
        }
    }

    /**
     * Create a nice event based on the volume
     * 
     * @param snap Snapshot for which the event is about
     * @param fs FileShare from which the snapshot was created
     * @param type Type of event such as VolumeCreated or VolumeDeleted
     * @param description Description for the event if needed
     */
    public static void recordSnapshotEvent(DbClient dbClient, Snapshot snap, FileShare fs, String type,
            String description, String extensions) {
        if (snap == null || fs == null) {
            _log.error("Invalid Snapshot event");
            return;
        }
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);
        // TODO fix the bogus user ID once we have AuthZ working
        RecordableBourneEvent event = new RecordableBourneEvent(
                type,
                fs.getTenant().getURI(),
                URI.create("ViPR-User"),                                           // user ID TODO when AAA fixed
                fs.getProject().getURI(),
                fs.getVirtualPool(),
                EVENT_SERVICE_TYPE,
                snap.getId(),
                description,
                System.currentTimeMillis(),
                extensions,
                snap.getNativeGuid(),
                RecordType.Event.name(),
                EVENT_SERVICE_SOURCE,
                "",
                "");
        try {
            eventManager.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}.", description, ex);
        }
    }

    public static void recordQuotaDirectoryEvent(DbClient dbClient, QuotaDirectory quotaDir, FileShare fs, String type,
            String description, String extensions) {
        if (quotaDir == null || fs == null) {
            _log.error("Invalid quota directory event");
            return;
        }
        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);
        // TODO fix the bogus user ID once we have AuthZ working
        RecordableBourneEvent event = new RecordableBourneEvent(
                type,
                fs.getTenant().getURI(),
                URI.create("ViPR-User"),                                           // user ID TODO when AAA fixed
                fs.getProject().getURI(),
                fs.getVirtualPool(),
                EVENT_SERVICE_TYPE,
                quotaDir.getId(),
                description,
                System.currentTimeMillis(),
                extensions,
                quotaDir.getNativeGuid(),
                RecordType.Event.name(),
                EVENT_SERVICE_SOURCE,
                "",
                "");
        try {
            eventManager.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}.", description, ex);
        }
    }

    /**
     * Record audit log for file service
     * 
     * @param auditType Type of AuditLog
     * @param operationalStatus Status of operation
     * @param description Description for the AuditLog
     * @param descparams Description paramters
     */
    public static void auditFile(DbClient dbClient, OperationTypeEnum auditType,
            boolean operationalStatus,
            String description,
            Object... descparams) {
        AuditLogManager auditMgr = new AuditLogManager();
        auditMgr.setDbClient(dbClient);
        auditMgr.recordAuditLog(null, null,
                EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus ? AuditLogManager.AUDITLOG_SUCCESS : AuditLogManager.AUDITLOG_FAILURE,
                description,
                descparams);
    }

    @Override
    public void createFS(URI storage, URI pool, URI fs, String nativeId, String opId) throws ControllerException {
        FileObject fileObject = null;
        FileShare fsObj = null;
        StorageSystem storageObj = null;
        try {
            ControllerUtils.setThreadLocalLogData(fs, opId);
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            String[] params = { storage.toString(), pool.toString(), fs.toString() };
            _log.info("Create FS: {}, {}, {}", params);
            StoragePool poolObj = _dbClient.queryObject(StoragePool.class, pool);
            fsObj = _dbClient.queryObject(FileShare.class, fs);
            VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, fsObj.getVirtualPool());
            fileObject = fsObj;
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            args.addFileShare(fsObj);
            args.addStoragePool(poolObj);
            args.setVPool(vPool);
            args.setNativeDeviceFsId(nativeId);
            args.setOpId(opId);

            Project proj = _dbClient.queryObject(Project.class, fsObj.getProject());
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, fsObj.getTenant());
            setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
            args.setTenantOrg(tenant);
            args.setProject(proj);

            BiosCommandResult result = getDevice(storageObj.getSystemType()).doCreateFS(storageObj, args);
            if (!result.getCommandPending()) {
                fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
            }

            if (result.isCommandSuccess()) {
                fsObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, fsObj));
                fsObj.setInactive(false);
            } else if (!result.getCommandPending()) {
                fsObj.setInactive(true);
            }

            _dbClient.persistObject(fsObj);

            if (!result.getCommandPending()) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM, result.isCommandSuccess(), "", "", fsObj);
            }
        } catch (Exception e) {
            String[] params = { storage.toString(), pool.toString(), fs.toString(), e.getMessage() };
            _log.error("Unable to create file system: storage {}, pool {}, FS {}: {}", params);
            updateTaskStatus(opId, fileObject, e);
            if ((fsObj != null) && (storageObj != null)) {
                fsObj.setInactive(true);
                _dbClient.persistObject(fsObj);
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM, false, e.getMessage(), "", fsObj, storageObj);
            }
        }
    }

    @Override
    public void delete(URI storage, URI pool, URI uri, boolean forceDelete, String deleteType, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(uri, opId);
        StorageSystem storageObj = null;
        FileObject fileObject = null;
        FileShare fsObj = null;
        Snapshot snapshotObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            String[] params = { storage.toString(), uri.toString(), String.valueOf(forceDelete), deleteType };
            _log.info("Delete : storage : {}, URI : {}, forceDelete : {}, delete_type : {} ", params);
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            boolean isFile = false;
            args.setOpId(opId);
            if (URIUtil.isType(uri, FileShare.class)) {
                isFile = true;
                args.setForceDelete(forceDelete);
                fsObj = _dbClient.queryObject(FileShare.class, uri);
                setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
                fileObject = fsObj;
                args.addFileShare(fsObj);
                args.setFileOperation(isFile);
                BiosCommandResult result;

                if (FileControllerConstants.DeleteTypeEnum.VIPR_ONLY.toString().equalsIgnoreCase(deleteType) && !fsObj.getInactive()) {
                    result = BiosCommandResult.createSuccessfulResult();
                } else {
                    if (!fsObj.getInactive()) {
                        result = getDevice(storageObj.getSystemType()).doDeleteFS(storageObj, args);
                    } else {
                        result = BiosCommandResult.createSuccessfulResult();
                    }
                }
                if (result.getCommandPending()) {
                    return;
                }
                fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

                if (result.isCommandSuccess() && (FileControllerConstants.DeleteTypeEnum.FULL.toString().equalsIgnoreCase(deleteType))) {
                    fsObj.setInactive(true);
                    if (forceDelete) {
                        doDeleteSnapshotsFromDB(fsObj, true, null, args);  // Delete Snapshot and its references from DB
                        args.addQuotaDirectory(null);
                        doFSDeleteQuotaDirsFromDB(args);                   // Delete Quota Directory from DB
                        deleteShareACLsFromDB(args);                       // Delete CIFS Share ACLs from DB
                        doDeleteExportRulesFromDB(true, null, args);       // Delete Export Rules from DB
                    }
                    generateZeroStatisticsRecord(fsObj);
                }
                if (result.isCommandSuccess()
                        && (FileControllerConstants.DeleteTypeEnum.VIPR_ONLY.toString().equalsIgnoreCase(deleteType))) {

                    if ((snapshotsExistsOnFS(fsObj) || quotaDirectoriesExistsOnFS(fsObj))) {
                        boolean fsCheck = getDevice(storageObj.getSystemType()).doCheckFSExists(storageObj, args);

                        if (fsCheck) {
                            String errMsg = new String(
                                    "delete file system from DB failed due to either snapshots or QDs exist on FS " + fsObj.getLabel());
                            _log.error(errMsg);

                            final ServiceCoded serviceCoded = DeviceControllerException.errors.jobFailedOpMsg(
                                    OperationTypeEnum.DELETE_FILE_SYSTEM.toString(), errMsg);
                            result = BiosCommandResult.createErrorResult(serviceCoded);
                            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
                            recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM, result.isCommandSuccess(), "", "",
                                    fsObj, storageObj);
                            _dbClient.persistObject(fsObj);
                            return;

                        } else if (!fsCheck) {
                            doDeleteSnapshotsFromDB(fsObj, true, null, args);  // Delete Snapshot and its references from DB
                            args.addQuotaDirectory(null);
                            doFSDeleteQuotaDirsFromDB(args);
                        }
                    }

                    deleteShareACLsFromDB(args);
                    doDeleteExportRulesFromDB(true, null, args);
                    SMBShareMap cifsSharesMap = fsObj.getSMBFileShares();
                    if (cifsSharesMap != null && !cifsSharesMap.isEmpty()) {
                        cifsSharesMap.clear();
                    }
                    fsObj.setInactive(true);
                    generateZeroStatisticsRecord(fsObj);
                }
                _dbClient.persistObject(fsObj);
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM, result.isCommandSuccess(), "", "", fsObj,
                        storageObj);

            } else {
                snapshotObj = _dbClient.queryObject(Snapshot.class, uri);
                fileObject = snapshotObj;
                args.addSnapshot(snapshotObj);
                fsObj = _dbClient.queryObject(FileShare.class, snapshotObj.getParent());
                setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
                args.addFileShare(fsObj);
                args.setFileOperation(isFile);
                BiosCommandResult result = getDevice(storageObj.getSystemType()).doDeleteSnapshot(storageObj, args);
                if (result.getCommandPending()) {
                    return;
                }
                snapshotObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
                if (result.isCommandSuccess()) {
                    snapshotObj.setInactive(true);
                    // delete the corresponding export rules if available.
                    args.addSnapshot(snapshotObj);
                    doDeleteExportRulesFromDB(true, null, args);
                }
                _dbClient.persistObject(snapshotObj);
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SNAPSHOT, result.isCommandSuccess(), "", "",
                        snapshotObj, fsObj, storageObj);
            }
        } catch (Exception e) {
            String[] params = { storage.toString(), uri.toString(), String.valueOf(forceDelete),
                    e.getMessage().toString() };
            _log.error("Unable to delete file system or snapshot: storage {}, FS/snapshot {}, forceDelete {}: {}", params);
            updateTaskStatus(opId, fileObject, e);
            if (URIUtil.isType(uri, FileShare.class)) {
                if ((fsObj != null) && (storageObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM, false, e.getMessage(), "", fsObj,
                            storageObj);
                }
            } else {
                if ((fsObj != null) && (storageObj != null) && (snapshotObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SNAPSHOT, false, e.getMessage(), "", snapshotObj,
                            fsObj, storageObj);
                }
            }
        }
    }

    private List<QuotaDirectory> queryFileQuotaDirs(FileDeviceInputOutput args) {
        if (args.getFileOperation()) {
            FileShare fs = args.getFs();
            _log.info("Querying all quota directories Using FsId {}", fs.getId());
            try {
                ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                        .getQuotaDirectoryConstraint(fs.getId());
                List<QuotaDirectory> fsQuotaDirs = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient,
                        QuotaDirectory.class, containmentConstraint);
                return fsQuotaDirs;
            } catch (Exception e) {
                _log.error("Error while querying {}", e);
            }
        }
        return null;
    }

    private void doFSDeleteQuotaDirsFromDB(FileDeviceInputOutput args) throws Exception {
        List<QuotaDirectory> quotaDirs = queryFileQuotaDirs(args);
        if (quotaDirs != null && !quotaDirs.isEmpty()) {
            _log.info("Doing CRUD Operations on all DB QuotaDirectory for requested fs");
            for (QuotaDirectory dir : quotaDirs) {
                _log.info("Deleting quota dir from DB - Dir :{}", dir);
                dir.setInactive(true);
                _dbClient.persistObject(dir);
            }
        }
    }

    /**
     * Generate zero statistics record.
     * 
     * @param fsObj the file share object
     */
    private void generateZeroStatisticsRecord(FileShare fsObj) {
        try {
            Stat zeroStatRecord = new Stat();
            zeroStatRecord.setTimeInMillis(System.currentTimeMillis());
            zeroStatRecord.setTimeCollected(System.currentTimeMillis());
            zeroStatRecord.setServiceType(Constants._File);
            zeroStatRecord.setAllocatedCapacity(0);
            zeroStatRecord.setProvisionedCapacity(0);
            zeroStatRecord.setBandwidthIn(0);
            zeroStatRecord.setBandwidthOut(0);
            zeroStatRecord.setNativeGuid(fsObj.getNativeGuid());
            zeroStatRecord.setSnapshotCapacity(0);
            zeroStatRecord.setSnapshotCount(0);
            zeroStatRecord.setResourceId(fsObj.getId());
            zeroStatRecord.setVirtualPool(fsObj.getVirtualPool());
            zeroStatRecord.setProject(fsObj.getProject().getURI());
            zeroStatRecord.setTenant(fsObj.getTenant().getURI());
            _dbClient.insertTimeSeries(StatTimeSeries.class, zeroStatRecord);
        } catch (Exception e) {
            _log.error("Zero Stat Record Creation failed for FileShare : {}", fsObj.getId(), e);
        }
    }

    @Override
    public void export(URI storage, URI uri, List<FileShareExport> exports, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(uri, opId);
        FileObject fsObj = null;
        FileShare fs = null;
        Snapshot snapshotObj = null;
        StorageSystem storageObj = null;
        
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            boolean isFile = false;

            if (URIUtil.isType(uri, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, uri);
                fsObj = fs;
                args.addFSFileObject(fs);
                StoragePool pool = _dbClient.queryObject(StoragePool.class, fs.getPool());
                args.addStoragePool(pool);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
            } else {
                snapshotObj = _dbClient.queryObject(Snapshot.class, uri);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class, snapshotObj.getParent());
                args.addFileShare(fs);
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class, fs.getPool());
                args.addStoragePool(pool);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);
            _log.info("Export details...  ");
            List<FileExport> fileExports = new ArrayList<FileExport>();
            if (exports != null) {
                for (FileShareExport fileShareExport : exports) {
                    FileExport fExport = fileShareExport.getFileExport();
                    fExport.setMountPoint(fileShareExport.getMountPath());
                    _log.info("FileExport:clients:" + fExport.getClients() + ":portName:" + fExport.getStoragePortName()
                            + ":port:" + fExport.getStoragePort() + ":rootMapping:" + fExport.getRootUserMapping()
                            + ":permissions:" + fExport.getPermissions() + ":protocol:" + fExport.getProtocol()
                            + ":security:" + fExport.getSecurityType() + ":mountpoint:" + fExport.getMountPoint() + ":path:"
                            + fExport.getPath() + ":comments:" + fExport.getComments()
                            + ":subDirectory:" + fExport.getSubDirectory());
                    fileExports.add(fExport);
                }
            } else {
                _log.info("Exports are null");
            }

            BiosCommandResult result = getDevice(storageObj.getSystemType()).doExport(storageObj, args, fileExports);

            if (result.getCommandPending()) {
                return;
            }                                                         // Set Mount path info for the exports
            FSExportMap fsExports = fsObj.getFsExports();

            // Per New model get the rules and see if any rules that are already saved and available.
            List<FileExportRule> existingRules = queryFileExports(args);

            if (null != fsExports) {
                Iterator it = fsExports.keySet().iterator();
                while (it.hasNext()) {
                    String fsExpKey = (String) it.next();
                    FileExport fileExport = fsObj.getFsExports().get(fsExpKey);
                    if ((fileExport.getMountPath() != null) && (fileExport.getMountPath().length() > 0)) {
                        fileExport.setMountPoint(ExportUtils.getFileMountPoint(fileExport.getStoragePort(), fileExport.getMountPath()));
                    } else {
                        fileExport.setMountPoint(ExportUtils.getFileMountPoint(fileExport.getStoragePort(), fileExport.getPath()));
                    }
                    _log.info("FileExport mountpath set to {} {}", fsExpKey, fileExport.getMountPoint());

                    // Per New Model of Export Rules Lets create the rule and save it as FileExportRule.
                    if (result.isCommandSuccess()) {
                        FileExportRule newRule = getFileExportRule(fsObj.getId(), fileExport, args);
                        _log.debug("ExportRule Constucted per expotkey {}, {}", fsExpKey, newRule);
                        if (existingRules != null && existingRules.isEmpty()) {
                            newRule.setId(URIUtil.createId(FileExportRule.class));
                            _log.info("No Existing rules available for this FS Export and so creating the rule now {}", newRule);
                            _dbClient.createObject(newRule);
                        } else {
                            _log.debug("Checking for existing rule(s) available for this export...");
                            boolean isRuleFound = false;
                            for (FileExportRule rule : existingRules) {
                                _log.debug("Available Export Rule {} - Matching with New Rule {}", rule, newRule);
                                if (newRule.getFsExportIndex() != null && rule.getFsExportIndex().equals(newRule.getFsExportIndex())) {
                                    isRuleFound = true;
                                    _log.info("Match Found : Skipping this rule as already available {}", newRule);
                                    break;
                                }
                            }
                            if (!isRuleFound) {
                                _log.info("Creating new Export Rule {}", newRule);
                                newRule.setId(URIUtil.createId(FileExportRule.class));
                                _dbClient.createObject(newRule);
                                isRuleFound = false;
                            }
                        }
                    }
                }
            }
            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            OperationTypeEnum auditType = null;
            auditType = (isFile) ? OperationTypeEnum.EXPORT_FILE_SYSTEM : OperationTypeEnum.EXPORT_FILE_SNAPSHOT;

            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

            if (isFile) {
                recordFileDeviceOperation(_dbClient, auditType, result.isCommandSuccess(), eventMsg,
                        getExportClientExtensions(fileExports), fs, storageObj);
            } else {
                recordFileDeviceOperation(_dbClient, auditType, result.isCommandSuccess(), eventMsg,
                        getExportClientExtensions(fileExports), snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);

        } catch (Exception e) {
            String[] params = { storage.toString(), uri.toString(), e.getMessage() };
            _log.error("Unable to export file system or snapshot: storage {}, FS/snapshot URI {}: {}", params);
            for (FileShareExport fsExport : exports) {
                _log.error("{}", fsExport);
            }
            updateTaskStatus(opId, fsObj, e);
            if (URIUtil.isType(uri, FileShare.class)) {
                if ((fs != null) && (storageObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.EXPORT_FILE_SYSTEM, false, e.getMessage(), "", fs, storageObj);
                }
            } else {
                if ((fs != null) && (storageObj != null) && (snapshotObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.EXPORT_FILE_SNAPSHOT, false, e.getMessage(), "", snapshotObj,
                            fs, storageObj);
                }
            }
        }
    }

    /**
     * Gets the list of export clients to be recorded as an extension in the
     * event for FileSystem/snapshot export/unexport operations. The extension
     * is specified in the format:
     * 
     * ExtensionName=client1, client2, ..., client3
     * 
     * @param fExports The list of FileSystem export maps.
     * 
     * @return A string specifying export/unexport clients.
     */
    private String getExportClientExtensions(List<FileExport> fExports) {

        if (fExports == null) {
            return "";
        }

        StringBuilder strBuilder = new StringBuilder();

        int exportSize = fExports.size();

        if (exportSize > 0) {
            strBuilder.append(RecordableBourneEvent.FS_CLIENT_EXTENSION_NAME);
            strBuilder.append("=");

            for (int i = 0; i < exportSize; i++) {
                List<String> clients = fExports.get(i).getClients();
                for (int j = 0; j < clients.size(); j++) {
                    strBuilder.append(clients.get(j));
                    if (j < clients.size() - 1) {
                        strBuilder.append(",");
                    }
                }
                if (!clients.isEmpty() && (i < fExports.size() - 1)) {
                    strBuilder.append(",");
                }
            }
        }

        return strBuilder.toString();
    }

    @Override
    public void unexport(URI storage, URI fileUri, List<FileShareExport> exports, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(fileUri, opId);
        FileObject fsObj = null;
        FileShare fs = null;
        Snapshot snapshotObj = null;
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            boolean isFile = false;
            if (URIUtil.isType(fileUri, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fileUri);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                fsObj = fs;
                args.addFSFileObject(fs);
                StoragePool pool = _dbClient.queryObject(StoragePool.class, fs.getPool());
                args.addStoragePool(pool);
            } else {
                snapshotObj = _dbClient.queryObject(Snapshot.class, fileUri);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class, snapshotObj.getParent());
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                args.addFileShare(fs);
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class, fs.getPool());
                args.addStoragePool(pool);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);

            _log.info("Export details...  ");
            List<FileExport> fileExports = new ArrayList<FileExport>();
            List<FileExportRule> exportRules = new ArrayList<FileExportRule>();
            if (exports != null) {
                for (FileShareExport fileShareExport : exports) {
                    FileExport fileExport = fileShareExport.getFileExport();
                    fileExports.add(fileExport);
                    _log.info("FileExport:" + fileExport.getClients() + ":" + fileExport.getStoragePortName()
                            + ":" + fileExport.getStoragePort() + ":" + fileExport.getRootUserMapping()
                            + ":" + fileExport.getPermissions() + ":" + fileExport.getProtocol()
                            + ":" + fileExport.getSecurityType() + ":" + fileExport.getMountPoint()
                            + ":" + fileExport.getMountPath() + ":" + fileExport.getPath());
                    _log.info("FileShareExport: " + fileExport.getFileExportKey());

                    // Per New Model : Lets create the Export Rules, So these will not get missed.
                    FileExportRule rule = getFileExportRule(fileUri, fileExport, args);
                    exportRules.add(rule);

                }
            } else {
                _log.info("Exports are null");
            }
            BiosCommandResult result = getDevice(storageObj.getSystemType()).doUnexport(storageObj, args, fileExports);

            if (result.getCommandPending()) {
                return;
            }
            // Set status
            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            OperationTypeEnum auditType = (isFile) ? OperationTypeEnum.UNEXPORT_FILE_SYSTEM : OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT;

            if (result.isCommandSuccess()) {
                // Remove Export
                for (FileExport fileExport : fileExports) {
                    fsObj.getFsExports().remove(fileExport.getFileExportKey());
                    _log.info("FileShareExport removed : " + fileExport.getFileExportKey());
                }

                // Query Existing Export rule and if found set to delete.
                for (FileExportRule rule : exportRules) {

                    URIQueryResultList dbresult = new URIQueryResultList();

                    if (!args.getFileOperation() && rule.getSnapshotExportIndex() != null) {
                        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getSnapshotExportRuleConstraint(rule.getSnapshotExportIndex()), dbresult);
                    } else if (rule.getFsExportIndex() != null) {
                        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                .getFileExportRuleConstraint(rule.getFsExportIndex()), dbresult);
                    }

                    Iterator<URI> it = dbresult.iterator();
                    while (it.hasNext()) {
                        if (dbresult.iterator().hasNext()) {
                            rule = _dbClient.queryObject(FileExportRule.class, it.next());
                            if (rule != null && !rule.getInactive()) {
                                _log.info("Existing DB Model found {}", rule);
                                rule.setInactive(true);
                                _dbClient.persistObject(rule);
                                break;
                            }
                        }
                    }

                }

                FSExportMap exportsMap = fsObj.getFsExports();
                List<FileExport> fsExports = new ArrayList<FileExport>(exportsMap.values());
                if (isFile) {
                    recordFileDeviceOperation(_dbClient, auditType, result.isCommandSuccess(), eventMsg,
                            getExportClientExtensions(fsExports), fs, storageObj);
                } else {
                    recordFileDeviceOperation(_dbClient, auditType, result.isCommandSuccess(), eventMsg,
                            getExportClientExtensions(fsExports), snapshotObj, fs, storageObj);
                }
            }

            _dbClient.persistObject(fsObj);

        } catch (Exception e) {
            String[] params = { storage.toString(), fileUri.toString(), e.getMessage() };
            _log.error("Unable to unexport file system or snapshot: storage {}, FS/snapshot URI {}: {}", params);
            for (FileShareExport fsExport : exports) {
                _log.error("{}  ", fsExport);
            }
            updateTaskStatus(opId, fsObj, e);
            if (URIUtil.isType(fileUri, FileShare.class)) {
                if ((fs != null) && (storageObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.UNEXPORT_FILE_SYSTEM, false, e.getMessage(), "", fs, storageObj);
                }
            } else {
                if ((fs != null) && (storageObj != null) && (snapshotObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, false, e.getMessage(), "", snapshotObj,
                            fs, storageObj);
                }
            }

        }
    }

    @Override
    public void expandFS(URI storage, URI uri, long newFSsize, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(uri, opId);
        FileShare fs = null;
        try {
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            fs = _dbClient.queryObject(FileShare.class, uri);
            args.addFSFileObject(fs);
            StoragePool pool = _dbClient.queryObject(StoragePool.class, fs.getPool());
            args.addStoragePool(pool);
            args.setFileOperation(true);
            args.setNewFSCapacity(newFSsize);
            args.setOpId(opId);
            BiosCommandResult result = getDevice(storageObj.getSystemType()).doExpandFS(storageObj, args);
            if (result.getCommandPending()) {
                // async operation
                return;
            }
            if (result.isCommandSuccess()) {
                _log.info("FileSystem old capacity :" + args.getFsCapacity() + ":Expanded Size:" + args.getNewFSCapacity());
                args.setFsCapacity(args.getNewFSCapacity());
                _log.info("FileSystem new capacity :" + args.getFsCapacity());
            }
            // Set status
            fs.getOpStatus().updateTaskStatus(opId, result.toOperation());
            _dbClient.persistObject(fs);

            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            recordFileDeviceOperation(_dbClient, OperationTypeEnum.EXPAND_FILE_SYSTEM,
                    result.isCommandSuccess(), eventMsg, "", fs, String.valueOf(newFSsize));
        } catch (Exception e) {
            String[] params = { storage.toString(), uri.toString(), String.valueOf(newFSsize), e.getMessage() };
            _log.error("Unable to expand file system: storage {}, FS URI {}, size {}: {}", params);
            updateTaskStatus(opId, fs, e);
            if (fs != null) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.EXPAND_FILE_SYSTEM, false, e.getMessage(), "", fs,
                        String.valueOf(newFSsize));
            }
        }
    }

    @Override
    public void share(URI storage, URI uri, FileSMBShare smbShare, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(uri, opId);
        FileObject fileObject = null;
        StorageSystem storageObj = null;
        FileShare fsObj = null;
        Snapshot snapshotObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            _log.info(String.format(
                    "Create SMB share details --- name: %1$s, description: %2$s, permissionType: %3$s, permission: %4$s , maxUsers: %5$s",
                    smbShare.getName(), smbShare.getDescription(), smbShare.getPermissionType(),
                    smbShare.getPermission(), (smbShare.getMaxUsers() > 0) ? smbShare.getMaxUsers() : "unlimited"));
            _log.info("Path {}", smbShare.getPath());
            // get db object for smb share
            SMBFileShare smbFileShare = smbShare.getSMBFileShare();
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            args.setOpId(opId);
            
            if (URIUtil.isType(uri, FileShare.class)) {
                fsObj = _dbClient.queryObject(FileShare.class, uri);
                fileObject = fsObj;
                args.addFSFileObject(fsObj);
                args.setFileOperation(true);
                setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
                
                BiosCommandResult result = getDevice(storageObj.getSystemType()).doShare(storageObj, args, smbFileShare);

                if (result.getCommandPending()) {
                    return;
                }
                fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

                _dbClient.persistObject(fsObj);
                List<SMBFileShare> shares = new ArrayList<SMBFileShare>();
                shares.add(smbFileShare);

                if (result.isCommandSuccess()) {
                    _log.info("File share created successfully");
                    createDefaultACEForSMBShare(uri, smbShare, storageObj.getSystemType());
                }

                String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_SHARE, result.isCommandSuccess(),
                        eventMsg, getShareNameExtensions(shares), fsObj, smbShare);
            } else {
                snapshotObj = _dbClient.queryObject(Snapshot.class, uri);
                fileObject = snapshotObj;
                args.addSnapshotFileObject(snapshotObj);
                fsObj = _dbClient.queryObject(FileShare.class, snapshotObj.getParent());
                args.addFileShare(fsObj);
                args.setFileOperation(false);
                setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
                BiosCommandResult result = getDevice(storageObj.getSystemType()).doShare(storageObj, args, smbFileShare);

                if (result.getCommandPending()) {
                    return;
                }
                snapshotObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

                _dbClient.persistObject(snapshotObj);
                List<SMBFileShare> shares = new ArrayList<SMBFileShare>();
                shares.add(smbFileShare);

                if (result.isCommandSuccess()) {
                    _log.info("File snapshot share created successfully");
                    createDefaultACEForSMBShare(uri, smbShare, storageObj.getSystemType());
                }

                String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SNAPSHOT_SHARE, result.isCommandSuccess(),
                        eventMsg, getShareNameExtensions(shares), snapshotObj, fsObj, smbShare);
            }
        } catch (Exception e) {
            String[] params = { storage.toString(), uri.toString(), smbShare.getName(), e.getMessage() };
            _log.error("Unable to create file system or snapshot share: storage {}, FS/snapshot URI {}, SMB share {}: {}", params);
            updateTaskStatus(opId, fileObject, e);
            if (URIUtil.isType(uri, FileShare.class)) {
                if ((fsObj != null) && (storageObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_SHARE,
                            false, e.getMessage(), "", fsObj, smbShare, storageObj);
                }
            } else {
                if ((fsObj != null) && (storageObj != null) && (snapshotObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SNAPSHOT_SHARE,
                            false, e.getMessage(), "", snapshotObj, fsObj, smbShare, storageObj);
                }
            }
        }
    }


	@Override
    public void deleteShare(URI storage, URI uri, FileSMBShare smbShare, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(uri, opId);
        FileObject fileObject = null;
        StorageSystem storageObj = null;
        FileShare fsObj = null;
        Snapshot snapshotObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            _log.info(String.format(
                    "Delete SMB share details --- name: %1$s, description: %2$s, permissionType: %3$s, permission: %4$s , maxUsers: %5$s ",
                    smbShare.getName(), smbShare.getDescription(), smbShare.getPermissionType(),
                    smbShare.getPermission(), (smbShare.getMaxUsers() > 0) ? smbShare.getMaxUsers() : "unlimited"));
            // get db object for smb share
            SMBFileShare smbFileShare = smbShare.getSMBFileShare();

            FileDeviceInputOutput args = new FileDeviceInputOutput();
            args.setShareName(smbShare.getName());
            args.setOpId(opId);
            if (URIUtil.isType(uri, FileShare.class)) {
                fsObj = _dbClient.queryObject(FileShare.class, uri);
                setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
                fileObject = fsObj;
                args.addFSFileObject(fsObj);
                args.setFileOperation(true);
                BiosCommandResult result = getDevice(storageObj.getSystemType()).doDeleteShare(storageObj, args, smbFileShare);

                if (result.getCommandPending()) {
                    return;
                }
                fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

                _dbClient.persistObject(fsObj);

                String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
                List<SMBFileShare> shares = null;
                if (result.isCommandSuccess()) {
                    SMBShareMap shareMap = fsObj.getSMBFileShares();
                    shares = new ArrayList<SMBFileShare>(shareMap.values());
                    deleteShareACLsFromDB(args);
                } else {
                    shares = new ArrayList<SMBFileShare>();
                    shares.add(smbFileShare);
                }
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE, result.isCommandSuccess(),
                        eventMsg, getShareNameExtensions(shares), fsObj, smbShare);
            } else {
                snapshotObj = _dbClient.queryObject(Snapshot.class, uri);
                fileObject = snapshotObj;
                args.addSnapshotFileObject(snapshotObj);
                args.setFileOperation(false);
                BiosCommandResult result = getDevice(storageObj.getSystemType()).doDeleteShare(storageObj, args, smbFileShare);

                if (result.getCommandPending()) {
                    return;
                }
                snapshotObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

                _dbClient.persistObject(snapshotObj);
                fsObj = _dbClient.queryObject(FileShare.class, snapshotObj.getParent());
                setVirtualNASinArgs(fsObj.getVirtualNAS(), args);
                String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
                List<SMBFileShare> shares = null;
                if (result.isCommandSuccess()) {
                    SMBShareMap shareMap = snapshotObj.getSMBFileShares();
                    shares = new ArrayList<SMBFileShare>(shareMap.values());
                    deleteShareACLsFromDB(args);
                } else {
                    shares = new ArrayList<SMBFileShare>();
                    shares.add(smbFileShare);
                }
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE, result.isCommandSuccess(),
                        eventMsg, getShareNameExtensions(shares), snapshotObj, fsObj, smbShare);
            }
        } catch (Exception e) {
            String[] params = { storage.toString(), uri.toString(), smbShare.getName(), e.getMessage() };
            _log.error("Unable to delete file system or snapshot share: storage {}, FS/snapshot URI {}, SMB share {}: {}", params);
            updateTaskStatus(opId, fileObject, e);
            if (URIUtil.isType(uri, FileShare.class)) {
                if ((fsObj != null) && (storageObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE, false,
                            e.getMessage(), "", fsObj, smbShare, storageObj);
                }
            } else {
                if ((fsObj != null) && (storageObj != null) && (snapshotObj != null)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE, false,
                            e.getMessage(), "", snapshotObj, fsObj, smbShare, storageObj);
                }
            }
        }
    }

    private void deleteShareACLsFromDB(FileDeviceInputOutput args) {

        List<CifsShareACL> existingDBAclList = queryDBShareAcls(args);
        List<CifsShareACL> deleteAclList = new ArrayList<CifsShareACL>();

        _log.debug("Inside deleteShareACLsFromDB() to delete ACL of share {} from DB",
                args.getShareName());

        for (Iterator<CifsShareACL> iterator = existingDBAclList.iterator(); iterator.hasNext();) {

            CifsShareACL cifsShareACL = iterator.next();

            if (args.getShareName().equals(cifsShareACL.getShareName())) {
                cifsShareACL.setInactive(true);
                deleteAclList.add(cifsShareACL);
            }
        }

        if (!deleteAclList.isEmpty()) {
            _log.info("Deleting ACL of share {}", args.getShareName());
            _dbClient.persistObject(deleteAclList);
        }
    }

    /**
     * Gets the list of SMB share names to be recorded as an extension in the
     * event for filesystem/snapshot share/deleteShare operations. The extension
     * is specified in the format:
     * 
     * ExtensionName=name1, name2, ...,name3
     * 
     * @param smbFileShares The list of file system SMB shares.
     * 
     * @return extension names in the format as above
     */
    private String getShareNameExtensions(List<SMBFileShare> smbFileShares) {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(RecordableBourneEvent.FS_SHARE_EXTENSION_NAME);
        strBuilder.append("=");
        int i = smbFileShares.size();
        for (SMBFileShare fileShare : smbFileShares) {
            strBuilder.append(fileShare.getName());
            i--;
            if (i > 0) {
                strBuilder.append(",");
            }
        }
        return strBuilder.toString();
    }

    @Override
    public void modifyFS(URI storage, URI pool, URI fs, String opId) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void snapshotFS(URI storage, URI snapshot, URI fs, String task) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(fs, task);
        FileShare fsObj = null;
        Snapshot snapshotObj = null;
        try {
            String[] params = { storage.toString(), snapshot.toString(), fs.toString() };
            _log.info("Snapshot FS: storage : {}, snapshot : {}, fs : {}", params);
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            fsObj = _dbClient.queryObject(FileShare.class, fs);
            snapshotObj = _dbClient.queryObject(Snapshot.class, snapshot);
            args.addFileShare(fsObj);
            args.addSnapshot(snapshotObj);
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, fsObj.getPool());
            args.addStoragePool(storagePool);
            args.setOpId(task);
            BiosCommandResult result = getDevice(storageObj.getSystemType()).doSnapshotFS(storageObj, args);
            if (result.getCommandPending()) {
                return;
            }
            snapshotObj.setCreationTime(Calendar.getInstance());
            fsObj.getOpStatus().updateTaskStatus(task, result.toOperation());
            snapshotObj.getOpStatus().updateTaskStatus(task, result.toOperation());
            snapshotObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, snapshotObj));

            // Incase error, set the snapshot to inactive state.
            if (!result.isCommandSuccess()) {
                _log.error("Snapshot create command is not successfull, so making object to inactive");
                snapshotObj.setInactive(true);
            }
            _dbClient.persistObject(snapshotObj);
            _dbClient.persistObject(fsObj);

            fsObj = _dbClient.queryObject(FileShare.class, fs);
            _log.debug("After Snashot created and fs persisted, Task Stauts {} -- Operation Details : {}",
                    fsObj.getOpStatus().get(task).getStatus(), result.toOperation());

            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_SNAPSHOT, result.isCommandSuccess(),
                    eventMsg, "", snapshotObj, fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fs.toString(), snapshot.toString(), e.getMessage() };
            _log.error("Unable to create file system snapshot: storage {}, FS {}, snapshot {}: {}", params);
            updateTaskStatus(task, fsObj, e);
            updateTaskStatus(task, snapshotObj, e);
            if ((fsObj != null) && (snapshotObj != null)) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_SNAPSHOT, false, e.getMessage(), "", snapshotObj,
                        fsObj);
            }
        }
    }

    @Override
    public void restoreFS(URI storage, URI fs, URI snapshot, String opId)
            throws ControllerException {
        ControllerUtils.setThreadLocalLogData(fs, opId);
        StorageSystem storageObj = null;
        FileShare fsObj = null;
        Snapshot snapshotObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        Operation op = null;
        try {
            String[] params = { storage.toString(), snapshot.toString(), fs.toString() };
            _log.info("Restore FS: {} {} {}", params);

            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            fsObj = _dbClient.queryObject(FileShare.class, fs);
            snapshotObj = _dbClient.queryObject(Snapshot.class, snapshot);
            args.addFileShare(fsObj);
            args.addSnapshot(snapshotObj);
            args.setOpId(opId);

            BiosCommandResult result = getDevice(storageObj.getSystemType()).doRestoreFS(storageObj, args);
            if (result.getCommandPending()) {
                return;
            }
            op = result.toOperation();
            snapshotObj.getOpStatus().updateTaskStatus(opId, op);
            fsObj.getOpStatus().updateTaskStatus(opId, op);
            _dbClient.persistObject(fsObj);
            _dbClient.persistObject(snapshotObj);

            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            recordFileDeviceOperation(_dbClient, OperationTypeEnum.RESTORE_FILE_SNAPSHOT,
                    result.isCommandSuccess(), eventMsg, "", fsObj, snapshot);
        } catch (Exception e) {
            String[] params = { storage.toString(), fs.toString(), snapshot.toString(), e.getMessage() };
            _log.error("Unable to restore file system from snapshot: storage {}, FS {}, snapshot {}: {}", params);
            updateTaskStatus(opId, snapshotObj, e);
            if ((fsObj != null) && (snapshotObj != null)) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.RESTORE_FILE_SNAPSHOT, false, e.getMessage(), "", fsObj,
                        snapshotObj);
            }
        }

        if ((op != null) && (!(op.getStatus().equalsIgnoreCase(Operation.Status.error.name())))) {
            try {
                // Synchronize snapshot list in DB with the list on the device
                syncSnapshotList(storageObj, fsObj, args);
            } catch (Exception ex) {
                String[] params = { storage.toString(), fs.toString(), snapshot.toString() };
                _log.warn(
                        "Restore succeeded but failed to sync snapshot list in DB with list on the device: storage {}, FS {}, snapshot {}",
                        params);
            }
        }
    }

    private void syncSnapshotList(StorageSystem storageObj,
            FileShare fsObj, FileDeviceInputOutput args) {

        // Retrieve all snapshots from DB that belong to this file system
        URIQueryResultList results = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(fsObj.getId()), results);

        // Setup snapshot name-object map
        Map<String, Snapshot> snapshotsInDB = new ConcurrentHashMap<String, Snapshot>();
        while (results.iterator().hasNext()) {
            URI uri = results.iterator().next();
            Snapshot snapObj = _dbClient.queryObject(Snapshot.class, uri);
            snapshotsInDB.put(snapObj.getName(), snapObj);
        }

        // Retrieve list of valid snapshot names from the device
        List<String> snapshotsOnDevice = new ArrayList<String>();
        BiosCommandResult result = getDevice(storageObj.getSystemType()).getFSSnapshotList(storageObj, args, snapshotsOnDevice);

        Operation op = result.toOperation();
        if (!op.getStatus().equalsIgnoreCase(Operation.Status.ready.name())) {
            _log.warn("The list of snapshots could not be retrieved from the device {}.",
                    storageObj.getId());
        } else {

            // Iterate through the snapshots in the DB and if name not found in
            // the list returned by the device, mark snapshot in DB as inactive
            Set<String> snapshotNames = snapshotsInDB.keySet();
            for (String snapshotName : snapshotNames) {
                if (!snapshotsOnDevice.contains(snapshotName)) {
                    snapshotsInDB.get(snapshotName).setInactive(true);
                    _dbClient.persistObject(snapshotsInDB.get(snapshotName));
                }
            }

            // TODO: Iterate through the snapshot list from device and if a
            // snapshot is found on the device but not in the DB, add the
            // newly discovered snapshot to the DB.
        }

    }

    private void updateTaskStatus(String opId, DataObject fsObj, Exception e) {

        final ServiceCoded serviceCoded;
        if ((opId == null) || (fsObj == null)) {
            return;
        }
        if (e instanceof ServiceCoded) {
            serviceCoded = (ServiceCoded) e;
        } else {
            serviceCoded = DeviceControllerException.errors.jobFailed(e);
        }
        final BiosCommandResult result = BiosCommandResult.createErrorResult(serviceCoded);
        fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

        _dbClient.persistObject(fsObj);
        _log.debug("updateTaskStatus:afterUpdate:" + fsObj.getOpStatus().get(opId).toString());
    }

    /**
     * Creates a connection to monitor events generated by the storage
     * identified by the passed URI.
     * 
     * @param storage A database client URI that identifies the storage to be
     *            monitored.
     * 
     * @throws ControllerException When errors occur connecting the storage for
     *             event monitoring.
     */
    @Override
    public void connectStorage(URI storage) throws ControllerException {
        // Retrieve the storage device info from the database.
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.unableToConnectToStorageDeviceForMonitoringDbException(
                    storage.toString(), e);
        }

        // Verify non-null storage device returned from the database client.
        if (storageObj == null) {
            throw DeviceControllerException.exceptions.unableToConnectToStorageDeviceForMonitoringDbNullRef(
                    storage.toString());
        }

        // Get the file device reference for the type of file device managed
        // by the controller.
        FileStorageDevice storageDevice = getDevice(storageObj.getSystemType());
        if (storageDevice == null) {
            String devType = String.format("%1$s", storageDevice);
            throw DeviceControllerException.exceptions.unableToConnectToStorageDeviceForMonitoringNoDevice(
                    storage.toString(), devType);
        }

        storageDevice.doConnect(storageObj);

        _log.info("Adding to storage device to work pool: {}", storageObj.getId());

    }

    /**
     * Removes a connection that was previously established for monitoring
     * events from the storage identified by the passed URI.
     * 
     * @param storage A database client URI that identifies the storage to be
     *            disconnected.
     * 
     * @throws ControllerException When errors occur disconnecting the storage
     *             for event monitoring.
     */
    @Override
    public void disconnectStorage(URI storage) throws ControllerException {
        // Retrieve the storage device info from the database.
        StorageSystem storageObj = null;
        try {
            storageObj = _dbClient.queryObject(StorageSystem.class, storage);
        } catch (Exception e) {
            throw DeviceControllerException.exceptions.unableToDisconnectStorageDeviceMonitoringDbException(
                    storage.toString(), e);
        }

        // Verify non-null storage device returned from the database client.
        if (storageObj == null) {
            String msg = String.format("Failed disconnecting %1$s for monitoring. Database returned a null reference.",
                    storage);
            _log.error(msg);
            throw DeviceControllerException.exceptions.unableToDisconnectStorageDeviceMonitoringDbNullRef(
                    storage.toString());
        }

        // Get the file device reference for the type of file device managed
        // by the controller.
        FileStorageDevice storageDevice = getDevice(storageObj.getSystemType());
        if (storageDevice == null) {
            String devType = String.format("%1$s", getDevice(storageObj.getSystemType()));
            String msg = String.format("Failed disconnecting %1$s for monitoring. No device for type %2$s.",
                    storage, devType);
            _log.error(msg);
            throw DeviceControllerException.exceptions.unableToDisconnectStorageDeviceMonitoringNoDevice(
                    storage.toString(), devType);
        }

        storageDevice.doDisconnect(storageObj);

        _log.info("Removing storage device from work pool: {}", storageObj.getId());

    }

    /**
     * Fail the task
     * 
     * @param clazz
     * @param id
     * @param opId
     * @param serviceCoded
     */
    private void doFailTask(Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded) {
        try {
            _dbClient.error(clazz, id, opId, serviceCoded);
        } catch (DatabaseException ioe) {
            _log.error(ioe.getMessage());
        }
    }

    @Override
    public void discoverStorageSystem(AsyncTask[] tasks) throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void scanStorageProviders(AsyncTask[] tasks)
            throws ControllerException {
        // TODO Auto-generated method stub
    }

    public static void recordFileDeviceOperation(DbClient dbClient, OperationTypeEnum opType, boolean opStatus, String evDesc,
            String extensions, Object... extParam) {
        String evType;
        if (evDesc == null || evDesc.isEmpty()) {
            evDesc = opType.getDescription();
        }
        evType = opType.getEvType(opStatus);
        String opStage = AuditLogManager.AUDITOP_END;

        _log.info("opType: {} detail: {}", opType.toString(), evType + ':' + evDesc);

        FileShare fs = null;
        Snapshot snapshotObj = null;
        QuotaDirectory quotaDirObj = null;
        if (extParam[0] instanceof Snapshot) {
            snapshotObj = (Snapshot) extParam[0];
            fs = (FileShare) extParam[1];
            recordSnapshotEvent(dbClient, snapshotObj, fs, evType, evDesc, extensions);
        } else if (extParam[0] instanceof QuotaDirectory) {
            quotaDirObj = (QuotaDirectory) extParam[0];
            fs = (FileShare) extParam[1];
            recordQuotaDirectoryEvent(dbClient, quotaDirObj, fs, evType, evDesc, extensions);
        } else if (extParam[0] instanceof FileShare) {
            fs = (FileShare) extParam[0];
            recordFsEvent(dbClient, fs, evType, evDesc, extensions);
        } else {
            _log.error("unrecognized param list");
        }

        FileSMBShare smbShare = null;
        switch (opType) {
            case CREATE_FILE_SYSTEM:
                auditFile(dbClient, opType, opStatus, opStage,
                        fs.getLabel(), fs.getVirtualArray().toString(),
                        (fs.getProject() != null) ? fs.getProject().toString() : null);
                break;
            case DELETE_FILE_SYSTEM:
                auditFile(dbClient, opType, opStatus, opStage,
                        fs.getId().toString(), ((StorageSystem) extParam[1]).getId().toString());
                break;
            case DELETE_FILE_SNAPSHOT:
                auditFile(dbClient, opType, opStatus, opStage,
                        snapshotObj.getId().toString(), ((StorageSystem) extParam[2]).getId().toString());
                break;
            case EXPORT_FILE_SYSTEM:
            case UPDATE_EXPORT_RULES_FILE_SYSTEM:
            case UPDATE_FILE_SYSTEM_SHARE_ACL:
            case UNEXPORT_FILE_SYSTEM:
                auditFile(dbClient, opType, opStatus, opStage,
                        fs.getId().toString(), ((StorageSystem) extParam[1]).getId().toString(), extensions);
                break;

            case EXPAND_FILE_SYSTEM:
                auditFile(dbClient, opType, opStatus, opStage,
                        fs.getId().toString(), extParam[1]);
                break;

            case CREATE_FILE_SYSTEM_SHARE:
            case DELETE_FILE_SYSTEM_SHARE:
                smbShare = (FileSMBShare) extParam[1];
                auditFile(dbClient, opType, opStatus, opStage,
                        smbShare.getName(), smbShare.getPermissionType(), smbShare.getPermission(),
                        smbShare.getMaxUsers(), smbShare.getDescription(), fs.getId().toString());
                break;

            case RESTORE_FILE_SNAPSHOT:
                URI snapshot = (URI) extParam[1];
                auditFile(dbClient, opType, opStatus, opStage,
                        snapshot, fs.getId().toString());
                break;

            case CREATE_FILE_SYSTEM_SNAPSHOT:
                auditFile(dbClient, opType, opStatus, opStage,
                        snapshotObj.getLabel(), snapshotObj.getId(), fs.getId().toString());
                break;

            case EXPORT_FILE_SNAPSHOT:
            case UPDATE_EXPORT_RULES_FILE_SNAPSHOT:
            case UPDATE_FILE_SNAPSHOT_SHARE_ACL:
            case UNEXPORT_FILE_SNAPSHOT:
                auditFile(dbClient, opType, opStatus, opStage,
                        snapshotObj.getId().toString(), ((StorageSystem) extParam[2]).getId().toString(), extensions);
                break;

            case CREATE_FILE_SNAPSHOT_SHARE:
            case DELETE_FILE_SNAPSHOT_SHARE:
                smbShare = (FileSMBShare) extParam[2];
                auditFile(dbClient, opType, opStatus, opStage,
                        smbShare.getName(), smbShare.getPermissionType(), smbShare.getPermission(),
                        smbShare.getMaxUsers(), smbShare.getDescription(), snapshotObj.getId().toString());
                break;

            case CREATE_FILE_SYSTEM_QUOTA_DIR:
            case DELETE_FILE_SYSTEM_QUOTA_DIR:
            case UPDATE_FILE_SYSTEM_QUOTA_DIR:
                auditFile(dbClient, opType, opStatus, opStage,
                        quotaDirObj.getLabel(), quotaDirObj.getId(), fs.getId().toString());
                break;

            case DELETE_FILE_SYSTEM_SHARE_ACL:
                auditFile(dbClient, opType, opStatus, opStage,
                        fs.getId().toString(), ((StorageSystem) extParam[1]).getId().toString(), extensions);
                break;
            case DELETE_FILE_SNAPSHOT_SHARE_ACL:
                auditFile(dbClient, opType, opStatus, opStage,
                        snapshotObj.getId().toString(), ((StorageSystem) extParam[2]).getId().toString(), extensions);
                break;

            default:
                _log.error("unrecognized fileshare operation type");
        }
    }

    @Override
    public void startMonitoring(AsyncTask task, Type deviceType)
            throws ControllerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void createQuotaDirectory(URI storage, FileShareQuotaDirectory quotaDir, URI fs, String task) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(fs, task);
        FileShare fsObj = null;
        QuotaDirectory quotaDirObj = null;
        try {
            String[] params = { storage.toString(), fs.toString(), quotaDir.toString() };
            _log.info("FileDeviceController::createQtree: create QuotaDirectory: storage : {}, quotaDir : {}, fs : {}", params);

            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            fsObj = _dbClient.queryObject(FileShare.class, fs);

            URI qtreeURI = quotaDir.getId();
            quotaDirObj = _dbClient.queryObject(QuotaDirectory.class, qtreeURI);
            FileDeviceInputOutput args = new FileDeviceInputOutput();

            // Set up args
            args.addFileShare(fsObj);
            args.addQuotaDirectory(quotaDirObj);
            args.setOpId(task);

            FileStorageDevice nasDevice = getDevice(storageObj.getSystemType());
            BiosCommandResult result = nasDevice.doCreateQuotaDirectory(storageObj, args, quotaDirObj);
            if (result.getCommandPending()) {
                return;
            }
            fsObj.getOpStatus().updateTaskStatus(task, result.toOperation());
            quotaDirObj.getOpStatus().updateTaskStatus(task, result.toOperation());

            String fsName = fsObj.getName();
            quotaDirObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, quotaDirObj, fsName));

            // In case of an error, set the quotaDir to an 'inactive' state.
            if (!result.isCommandSuccess()) {
                quotaDirObj.setInactive(true);
                _log.error("FileDeviceController::createQtree: QuotaDirectory create command is not successfull");
            }

            _dbClient.persistObject(quotaDirObj);
            _dbClient.persistObject(fsObj);

            fsObj = _dbClient.queryObject(FileShare.class, fs);
            _log.debug(
                    "FileDeviceController::createQtree: After QuotaDirectory created and fs persisted, Task Stauts {} -- Operation Details : {}",
                    fsObj.getOpStatus().get(task).getStatus(), result.toOperation());

            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_QUOTA_DIR, result.isCommandSuccess(),
                    eventMsg, "", quotaDirObj, fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fs.toString(), quotaDir.toString(), e.getMessage() };
            _log.error("FileDeviceController::createQtree: Unable to create file system quotaDir: storage {}, FS {}, snapshot {}: {}",
                    params);
            updateTaskStatus(task, fsObj, e);
            updateTaskStatus(task, quotaDirObj, e);
            if ((fsObj != null) && (quotaDirObj != null)) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_QUOTA_DIR, false, e.getMessage(), "",
                        quotaDirObj, fsObj);
            }
        }
    }

    @Override
    public void updateQuotaDirectory(URI storage, FileShareQuotaDirectory quotaDir, URI fs, String task) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(fs, task);
        FileShare fsObj = null;
        QuotaDirectory quotaDirObj = null;
        try {
            String[] params = { storage.toString(), fs.toString(), quotaDir.toString() };
            _log.info("FileDeviceController::updateQtree:  storage : {}, fs : {}, quotaDir : {}", params);

            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            fsObj = _dbClient.queryObject(FileShare.class, fs);

            URI qtreeURI = quotaDir.getId();
            quotaDirObj = _dbClient.queryObject(QuotaDirectory.class, qtreeURI);
            FileDeviceInputOutput args = new FileDeviceInputOutput();

            // Set up args
            args.addFileShare(fsObj);
            args.addQuotaDirectory(quotaDirObj);
            args.setOpId(task);

            FileStorageDevice nasDevice = getDevice(storageObj.getSystemType());
            BiosCommandResult result = nasDevice.doUpdateQuotaDirectory(storageObj, args, quotaDirObj);
            if (result.getCommandPending()) {
                return;
            }
            fsObj.getOpStatus().updateTaskStatus(task, result.toOperation());
            quotaDirObj.getOpStatus().updateTaskStatus(task, result.toOperation());

            String fsName = fsObj.getName();
            quotaDirObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, quotaDirObj, fsName));

            if (!result.isCommandSuccess()) {
                _log.error("FileDeviceController::updateQtree: QuotaDirectory update command is not successfull");
            }
            // save the task status into db
            _dbClient.persistObject(quotaDirObj);
            _dbClient.persistObject(fsObj);

            fsObj = _dbClient.queryObject(FileShare.class, fs);
            _log.debug(
                    "FileDeviceController::updateQtree: After QuotaDirectory updated and fs persisted, Task Stauts {} -- Operation Details : {}",
                    fsObj.getOpStatus().get(task).getStatus(), result.toOperation());
            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            recordFileDeviceOperation(_dbClient, OperationTypeEnum.UPDATE_FILE_SYSTEM_QUOTA_DIR, result.isCommandSuccess(),
                    eventMsg, "", quotaDirObj, fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fs.toString(), quotaDir.toString(), e.getMessage() };
            _log.error("FileDeviceController::updateQtree: Unable to update file system quotaDir: storage {}, FS {}, snapshot {}: {}",
                    params);
            updateTaskStatus(task, fsObj, e);
            updateTaskStatus(task, quotaDirObj, e);
            if ((fsObj != null) && (quotaDirObj != null)) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.CREATE_FILE_SYSTEM_QUOTA_DIR, false, e.getMessage(), "",
                        quotaDirObj, fsObj);
            }
        }
    }

    @Override
    public void deleteQuotaDirectory(URI storage, URI quotaDir, URI fs, String task) throws ControllerException {
        FileShare fsObj = null;
        QuotaDirectory quotaDirObj = null;
        try {
            String[] params = { storage.toString(), quotaDir.toString(), fs.toString() };
            _log.info("FileDeviceController::deleteQuotaDirectory: storage : {}, quotadir : {}, fs : {}", params);

            StorageSystem storageObj = _dbClient.queryObject(StorageSystem.class, storage);
            fsObj = _dbClient.queryObject(FileShare.class, fs);
            quotaDirObj = _dbClient.queryObject(QuotaDirectory.class, quotaDir);
            FileDeviceInputOutput args = new FileDeviceInputOutput();
            String quotaName = quotaDirObj.getName();
            args.addFSFileObject(fsObj);

            FSExportMap fsExportMap = fsObj.getFsExports();
            boolean isExported = false;
            // delete export
            if (fsExportMap != null && !fsExportMap.isEmpty()) {
                // check the quota directory is exported
                for (FileExport fileExport : fsExportMap.values()) {
                    if (quotaName.equals(fileExport.getSubDirectory()) &&
                            fileExport.getPath().endsWith(quotaName)) {
                        isExported = true;
                        _log.info("Delete the nfs sub directory export path {} and key {}",
                                fileExport.getPath(), fileExport.getFileExportKey());
                    }
                }
                if (true == isExported) {
                    // delete the export of quota directory
                    this.deleteExportRules(storage, fs, false, quotaName, task);
                    fsObj = _dbClient.queryObject(FileShare.class, fs);
                }
            }

            // delete fileshare of quota directory
            SMBShareMap smbShareMap = fsObj.getSMBFileShares();
            if (smbShareMap != null && !smbShareMap.isEmpty()) {
                FileSMBShare fileSMBShare = null;
                List<FileSMBShare> fileSMBShares = new ArrayList<FileSMBShare>();
                for (SMBFileShare smbFileShare : smbShareMap.values()) {
                    // check for quotaname in native fs path
                    if (true == (smbFileShare.getPath().endsWith(quotaName))) {
                        fileSMBShare = new FileSMBShare(smbFileShare);
                        _log.info("Delete the cifs sub directory path of quota directory {}",
                                smbFileShare.getPath());
                        fileSMBShares.add(fileSMBShare);
                    }
                }
                if (fileSMBShares != null && !fileSMBShares.isEmpty()) { // delete shares
                    for (FileSMBShare tempFileSMBShare : fileSMBShares) {
                        this.deleteShare(storage, fs, tempFileSMBShare, task);
                        _log.info("Delete SMB Share Name{} for quota ", tempFileSMBShare.getName());
                    }
                }
            }

            fsObj = _dbClient.queryObject(FileShare.class, fs);
            // Set up args
            args.addFSFileObject(fsObj);

            args.addQuotaDirectory(quotaDirObj);
            args.setOpId(task);

            FileStorageDevice nasDevice = getDevice(storageObj.getSystemType());
            BiosCommandResult result = nasDevice.doDeleteQuotaDirectory(storageObj, args);
            if (result.getCommandPending()) {
                return;
            }
            fsObj.getOpStatus().updateTaskStatus(task, result.toOperation());
            quotaDirObj.getOpStatus().updateTaskStatus(task, result.toOperation());

            String fsName = fsObj.getName();
            quotaDirObj.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(_dbClient, quotaDirObj, fsName));

            if (!result.isCommandSuccess()) {
                _log.error("FileDeviceController::deleteQuotaDirectory: command is not successfull");
            }
            // save the task status in db
            _dbClient.persistObject(quotaDirObj);
            _dbClient.persistObject(fsObj);

            fsObj = _dbClient.queryObject(FileShare.class, fs);
            _log.debug(
                    "FileDeviceController::deleteQuotaDirectory: After deleteQuotaDirectory created and fs persisted, Task Stauts {} -- Operation Details : {}",
                    fsObj.getOpStatus().get(task).getStatus(), result.toOperation());
            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM_QUOTA_DIR, result.isCommandSuccess(),
                    eventMsg, "", quotaDirObj, fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fs.toString(), quotaDir.toString(), e.getMessage() };
            _log.error(
                    "FileDeviceController::deleteQuotaDirectory: Unable to create file system quota dir: storage {}, FS {}, quotadir {}: {}",
                    params);
            updateTaskStatus(task, fsObj, e);
            updateTaskStatus(task, quotaDirObj, e);
            if ((fsObj != null) && (quotaDirObj != null)) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM_QUOTA_DIR, false, e.getMessage(), "",
                        quotaDirObj, fsObj);
            }
        }
    }

    @Override
    public void updateExportRules(URI storage, URI fsURI, FileExportUpdateParams param, String opId) throws ControllerException {
        ControllerUtils.setThreadLocalLogData(fsURI, opId);
        FileObject fsObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        FileShare fs = null;
        Snapshot snapshotObj = null;
        boolean isFile = false;

        try {

            StorageSystem storageObj = _dbClient.queryObject(
                    StorageSystem.class, storage);

            args.setSubDirectory(param.getSubDir());
            args.setAllExportRules(param);

            _log.info("Controller Recieved FileExportUpdateParams {}", param);

            // File
            if (URIUtil.isType(fsURI, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fsURI);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                fsObj = fs;
                args.addFSFileObject(fs);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);

            } else {
                // Snapshot
                snapshotObj = _dbClient.queryObject(Snapshot.class, fsURI);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class,
                        snapshotObj.getParent());
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                args.addFileShare(fs);
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);

            // Query & Pass all Existing Exports
            args.setExistingDBExportRules(queryExports(args));

            // Do the Operation on device.
            BiosCommandResult result = getDevice(storageObj.getSystemType())
                    .updateExportRules(storageObj, args);

            if (result.isCommandSuccess()) {
                // Update Database
                doCRUDExports(param, fs, args);

                // Delete the Export Map, if there are no exports.
                if ((args.getFileObjExports() != null) && (queryExports(args).isEmpty())) {
                    args.getFileObjExports().clear();
                    _dbClient.persistObject(args.getFileObj());
                }

            }

            if (result.getCommandPending()) {
                return;
            }
            // Audit & Update the task status
            OperationTypeEnum auditType = null;
            auditType = (isFile) ? OperationTypeEnum.EXPORT_FILE_SYSTEM
                    : OperationTypeEnum.EXPORT_FILE_SNAPSHOT;

            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

            // Monitoring - Event Processing
            String eventMsg = result.isCommandSuccess() ? "" : result
                    .getMessage();

            if (isFile) {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        getExportNewClientExtensions(param.retrieveAllExports()),
                        fs, storageObj);
            } else {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        getExportNewClientExtensions(param.retrieveAllExports()),
                        snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fsURI.toString() };
            _log.error("Unable to export file system or snapshot: storage {}, FS/snapshot URI {}", params, e);
            _log.error("{}, {} ", e.getMessage(), e);
            updateTaskStatus(opId, fsObj, e);
        }
    }

    private FileExportRule getAvailableExportRule(FileExportRule exportRule, FileDeviceInputOutput args)
            throws URISyntaxException {

        String exportIndex = exportRule.getFsExportIndex();
        if (!args.getFileOperation()) {
            exportIndex = exportRule.getSnapshotExportIndex();
        }

        _log.info("Retriving DB Model using its index {}", exportIndex);
        FileExportRule rule = null;
        URIQueryResultList result = new URIQueryResultList();

        if (!args.getFileOperation()) {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotExportRuleConstraint(exportIndex), result);
        } else {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileExportRuleConstraint(exportIndex), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                rule = _dbClient.queryObject(FileExportRule.class, it.next());
                if (rule != null && !rule.getInactive()) {
                    _log.info("Existing DB Model found {}", rule);
                    break;
                }
            }
        }

        return rule;

    }

    private List<ExportRule> queryExports(FileDeviceInputOutput args) {
        List<ExportRule> rules = null;

        try {
            ContainmentConstraint containmentConstraint;

            if (args.getFileOperation()) {
                FileShare fs = args.getFs();
                _log.info("Querying all ExportRules Using FsId {}", fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            } else {
                URI snapshotId = args.getSnapshotId();
                _log.info("Querying all ExportRules Using Snapshot Id {}", snapshotId);
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotExportRulesConstraint(snapshotId);
            }

            List<FileExportRule> fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileExportRule.class,
                    containmentConstraint);

            rules = new ArrayList<>();

            for (FileExportRule fileExportRule : fileExportRules) {
                ExportRule rule = new ExportRule();
                getExportRule(fileExportRule, rule);
                rules.add(rule);
            }
        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return rules;

    }

    private List<FileExportRule> queryFileExports(FileDeviceInputOutput args) {
        List<FileExportRule> fileExportRules = null;
        try {
            ContainmentConstraint containmentConstraint;

            if (args.getFileOperation()) {
                FileShare fs = args.getFs();
                _log.info("Querying all ExportRules Using FsId {}", fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            } else {
                URI snapshotId = args.getSnapshotId();
                _log.info("Querying all ExportRules Using Snapshot Id {}", snapshotId);
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotExportRulesConstraint(snapshotId);
            }

            fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, FileExportRule.class,
                    containmentConstraint);

        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }

        return fileExportRules;

    }

    /*
     * private List<FileExportRule> queryFSDBExports(FileShare fs)
     * {
     * List<URI> exportIds = _dbClient.queryByConstraint(ContainmentConstraint.
     * Factory.getFileExportRulesConstraint(fs.getId()));
     * return _dbClient.queryObject(FileExportRule.class, exportIds);
     * 
     * }
     */
    private void getExportRule(FileExportRule orig, ExportRule dest) {

        dest.setFsID(orig.getFileSystemId());
        dest.setSnapShotID(orig.getSnapshotId());
        dest.setExportPath(orig.getExportPath());
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        dest.setReadOnlyHosts(orig.getReadOnlyHosts());
        dest.setReadWriteHosts(orig.getReadWriteHosts());
        dest.setRootHosts(orig.getRootHosts());
        dest.setMountPoint(orig.getMountPoint());
        dest.setDeviceExportId(orig.getDeviceExportId());

        _log.info("Original FileExportRule {}", orig.toString());
        _log.info("Destination ExportRule {}", dest.toString());
    }

    private void copyPropertiesToSave(FileExportRule dest, ExportRule orig,
            FileShare fs, FileDeviceInputOutput args) {

        _log.info("Origin {}", orig.toString());

        String exportPath = args.getExportPath();
        // This export path is the one that is figured out at the device.
        // Make sure you set the path on args object while doing the operation. Check for <Device>FileStorageDeviceXXX.java
        dest.setExportPath(exportPath);
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        if (orig.getReadOnlyHosts() != null && !orig.getReadOnlyHosts().isEmpty()) {
            dest.setReadOnlyHosts(new StringSet(orig.getReadOnlyHosts()));
            _log.info("Read Only Hosts {}", dest.getReadOnlyHosts());
        }
        if (orig.getReadWriteHosts() != null && !orig.getReadWriteHosts().isEmpty()) {
            dest.setReadWriteHosts(new StringSet(orig.getReadWriteHosts()));
            _log.info("Read Write Hosts {}", dest.getReadWriteHosts());
        }
        if (orig.getRootHosts() != null && !orig.getRootHosts().isEmpty()) {
            dest.setRootHosts(new StringSet(orig.getRootHosts()));
            _log.info("Root hosts {}", dest.getRootHosts());
        }

        // Set this always at the end -- Thats how the model is defined.
        if (!args.getFileOperation()) {
            dest.setSnapshotId(args.getSnapshotId());
        } else {
            dest.setFileSystemId(fs.getId());
        }

        // Figure out Storage Port Network id to build the mount point.
        StoragePort storagePort = _dbClient.queryObject(StoragePort.class, fs.getStoragePort());
        String mountPoint = ExportUtils.getFileMountPoint(storagePort.getPortNetworkId(), exportPath);
        dest.setMountPoint(mountPoint);

        // dest.calculateExportRuleIndex();
        if ((orig.getDeviceExportId() != null) && (!orig.getDeviceExportId().isEmpty())) {
            dest.setDeviceExportId(orig.getDeviceExportId());
        } else if ((args.getObjIdOnDevice() != null) && (!args.getObjIdOnDevice().isEmpty())) {
            dest.setDeviceExportId(args.getObjIdOnDevice());
        }
        // _log.info("New File Export Rule Object {}", dest);
        _log.info("Dest After {}", dest.toString());
    }

    private void doDeleteExportRulesFromDB(boolean allDirs, String subDir, FileDeviceInputOutput args) throws Exception {

        // Query All Export Rules Specific to a File System.
        // queryExports
        List<FileExportRule> exports = queryFileExports(args);

        if (exports != null && !exports.isEmpty()) {
            if (allDirs) {
                // ALl EXPORTS
                _log.info("Doing CRUD Operations on all DB FileExportRules for requested fs");
                for (FileExportRule rule : exports) {
                    _log.info("Deleting export rule from DB having path {} - Rule :{}", rule.getExportPath(), rule);
                    rule.setInactive(true);
                    _dbClient.persistObject(rule);
                }
            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _log.info("Doing CRUD Operations on DB FileExportRules Specific to SubDirectory {}", subDir);
                for (FileExportRule rule : exports) {
                    if (rule.getExportPath().endsWith("/" + subDir)) {
                        _log.info("Deleting Subdiretcory export rule from DB having path {} - Rule :{}", rule.getExportPath(), rule);
                        rule.setInactive(true);
                        _dbClient.persistObject(rule);
                    }
                }
            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                for (FileExportRule rule : exports) {
                    if (args.getFileOperation() && rule.getExportPath().equalsIgnoreCase(args.getFsPath())) {
                        _log.info("Deleting export rule from DB having path {} - Rule :{}", rule.getExportPath(), rule);
                        rule.setInactive(true);
                        _dbClient.persistObject(rule);
                    } else if (args.getFileOperation() == false && rule.getExportPath().equalsIgnoreCase(args.getSnapshotPath())) {
                        _log.info("Deleting snapshot export rule from DB having path {} - Rule :{}", rule.getExportPath(), rule);
                        rule.setInactive(true);
                        _dbClient.persistObject(rule);
                    }
                }
            }
        }
    }

    private void doDeleteSnapshotsFromDB(FileShare fs, boolean allDirs, String subDir, FileDeviceInputOutput args) throws Exception {

        _log.info(" Setting Snapshots to InActive with Force Delete ");
        URIQueryResultList snapIDList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(fs.getId()), snapIDList);
        _log.info("getSnapshots: FS {}: {} ", fs.getId().toString(), snapIDList.toString());
        List<Snapshot> snapList = _dbClient.queryObject(Snapshot.class, snapIDList);

        args.setFileOperation(false);// Set this as snapshot operation to delete only snapshots.
        if (snapList != null && !snapList.isEmpty()) {
            for (Snapshot snapshot : snapList) {
                _log.info("Marking Snapshot as InActive Snapshot Id {} Fs Id : {}", snapshot.getId(), snapshot.getParent());
                snapshot.setInactive(true);
                args.addSnapshot(snapshot);
                doDeleteExportRulesFromDB(true, null, args);
                deleteShareACLsFromDB(args);
                _dbClient.persistObject(snapshot);
            }
        }
        args.setFileOperation(true);       // restoring back
    }

    private void doCRUDExports(FileExportUpdateParams param, FileShare fs, FileDeviceInputOutput args) throws Exception {
        try {
            // create new exports
            ExportRules exportRules = param.getExportRulesToAdd();
            List<ExportRule> rules;
            if (exportRules != null) {
                rules = exportRules.getExportRules();
                if (rules != null && !rules.isEmpty()) {
                    for (ExportRule exportRule : rules) {
                        FileExportRule rule = new FileExportRule();
                        rule.setId(URIUtil.createId(FileExportRule.class));
                        copyPropertiesToSave(rule, exportRule, fs, args);
                        _log.info("Storing New DB Export Rule {}", rule);
                        _dbClient.createObject(rule);
                    }
                }
            }

            // Modify Existing Exports
            exportRules = param.getExportRulesToModify();
            if (exportRules != null) {
                rules = exportRules.getExportRules();
                if (rules != null && !rules.isEmpty()) {
                    for (ExportRule exportRule : rules) {
                        FileExportRule rule = new FileExportRule();
                        // Copy the properties to build the index id to query DB for existing Export Rule
                        copyPropertiesToSave(rule, exportRule, fs, args);
                        rule = getAvailableExportRule(rule, args);

                        // Remove the existing and create the new one.
                        // Don't Update the existing one as persist object will create a new StringSet rather
                        // it updates the existing one with new information and upon keeping/appending to old one.
                        rule.setInactive(true);
                        _log.info("Removing Existing DB Export Rule {}", rule);
                        _dbClient.persistObject(rule);

                        FileExportRule newRule = new FileExportRule();
                        newRule.setId(URIUtil.createId(FileExportRule.class));
                        // Now, Copy the properties again into the rule came out of DB, before updating.
                        copyPropertiesToSave(newRule, exportRule, fs, args);
                        _log.info("Storing New DB Export Rule {}", newRule);
                        _dbClient.createObject(newRule);

                    }
                }
            }

            // Delete Existing Exports
            exportRules = param.getExportRulesToDelete();
            if (exportRules != null) {
                rules = exportRules.getExportRules();
                if (rules != null && !rules.isEmpty()) {
                    for (ExportRule exportRule : rules) {
                        FileExportRule rule = new FileExportRule();
                        copyPropertiesToSave(rule, exportRule, fs, args);
                        rule = getAvailableExportRule(rule, args);
                        rule.setInactive(true);
                        _log.info("Marking DB Export Rule Inactive {}", rule);
                        _dbClient.persistObject(rule);
                    }
                }
            }
        } catch (Exception e) {
            _log.info("Error While executing CRUD Operations {}", e);
        }
    }

    /**
     * Gets the list of export clients to be recorded as an extension in the
     * event for FileSystem/snapshot export/unexport operations. The extension
     * is specified in the format:
     * 
     * ExtensionName=client1, client2, ..., client3
     * 
     * @param fExports The list of FileSystem export maps.
     * 
     * @return A string specifying export/unexport clients.
     */
    private String getExportNewClientExtensions(List<ExportRule> fExports) {

        if (fExports == null || fExports.isEmpty()) {
            return "";
        }

        StringBuilder strBuilder = new StringBuilder();

        int exportSize = fExports.size();
        if (exportSize > 0) {
            strBuilder.append(RecordableBourneEvent.FS_CLIENT_EXTENSION_NAME);
            strBuilder.append("=");
            // List<String> clients = new ArrayList<String>();

            List<String> ro = new ArrayList<String>();
            List<String> rw = new ArrayList<String>();
            List<String> root = new ArrayList<String>();

            for (int i = 0; i < exportSize; i++) {

                if (fExports.get(i) != null && fExports.get(i).getReadOnlyHosts() != null) {
                    ro.addAll(fExports.get(i).getReadOnlyHosts());
                    // clients.addAll(fExports.get(i).getReadOnlyHosts());
                }
                if (fExports.get(i) != null && fExports.get(i).getReadWriteHosts() != null) {
                    rw.addAll(fExports.get(i).getReadWriteHosts());
                    // clients.addAll(fExports.get(i).getReadWriteHosts());
                }
                if (fExports.get(i) != null && fExports.get(i).getRootHosts() != null) {
                    root.addAll(fExports.get(i).getRootHosts());
                    // clients.addAll(fExports.get(i).getRootHosts());
                }
            }
            StringBuilder allROhosts = new StringBuilder("ReadOnly Hosts : [");
            for (String roClient : ro) {
                allROhosts.append(roClient).append(",");
            }
            allROhosts.append(" ]");

            StringBuilder allRWhosts = new StringBuilder("ReadWrite Hosts : [");
            for (String rwClient : rw) {
                allRWhosts.append(rwClient).append(",");
            }
            allRWhosts.append(" ]");

            StringBuilder allROOThosts = new StringBuilder("Root Hosts : [");
            for (String rootClient : root) {
                allROOThosts.append(rootClient).append(",");
            }
            allROOThosts.append(" ]");
            strBuilder.append(allROhosts).append(allRWhosts).append(allROOThosts);
        }

        return strBuilder.toString();
    }

    private String getNewACLExtensions(List<ShareACL> acls) {

        if (acls == null || acls.isEmpty()) {
            return "";
        }

        StringBuilder strBuilder = new StringBuilder();

        int aclSize = acls.size();
        if (aclSize > 0) {
            strBuilder.append(RecordableBourneEvent.FS_ACL_EXTENSION_NAME);
            strBuilder.append("=");
            strBuilder.append(acls);

        }

        return strBuilder.toString();
    }

    @Override
    public void deleteExportRules(URI storage, URI fileUri, boolean allDirs,
            String subDir, String opId) throws ControllerException {

        ControllerUtils.setThreadLocalLogData(fileUri, opId);
        FileObject fsObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        FileShare fs = null;
        Snapshot snapshotObj = null;
        StorageSystem storageObj = null;
        boolean isFile = false;

        try {

            storageObj = _dbClient.queryObject(
                    StorageSystem.class, storage);

            // File
            if (URIUtil.isType(fileUri, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fileUri);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                fsObj = fs;
                args.addFSFileObject(fs);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
                args.setAllDir(allDirs);
                args.setSubDirectory(subDir);

            } else {
                // Snapshot
                snapshotObj = _dbClient.queryObject(Snapshot.class, fileUri);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class,
                        snapshotObj.getParent());
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                args.addFileShare(fs);
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
                args.setAllDir(true);
                args.setSubDirectory(null);
            }
            args.setFileOperation(isFile);
            args.setOpId(opId);

            List<ExportRule> existingExportRules = queryExports(args);
            args.setExistingDBExportRules(existingExportRules);
            // Do the Operation on device.

            _log.info("Delete Export Rules : request received for {}, with allDirs : {}, subDir : {}", new Object[] { fs.getId(), allDirs,
                    subDir });

            BiosCommandResult result = getDevice(storageObj.getSystemType())
                    .deleteExportRules(storageObj, args);
            if (result.isCommandSuccess()) {
                // Update Database
                doDeleteExportRulesFromDB(allDirs, subDir, args);
                doDeleteExportsFromFSObjMap(allDirs, subDir, args);
            }
            // Audit & Update the task status
            String eventMsg = result.isCommandSuccess() ? "" : result.getMessage();
            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
            if (isFile) {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.UNEXPORT_FILE_SYSTEM, result.isCommandSuccess(), eventMsg, "", fs,
                        storageObj);
            } else {
                recordFileDeviceOperation(_dbClient, OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, result.isCommandSuccess(), eventMsg, "",
                        snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);

        } catch (Exception e) {
            String[] params = { storage.toString(), fileUri.toString() };
            _log.error("Unable to export file system or snapshot: storage {}, FS/snapshot URI {}", params);
            if ((fsObj != null) && (storageObj != null)) {
                if (URIUtil.isType(fileUri, FileShare.class)) {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.UNEXPORT_FILE_SYSTEM, false, e.getMessage(), "", fs, storageObj);
                } else {
                    recordFileDeviceOperation(_dbClient, OperationTypeEnum.UNEXPORT_FILE_SNAPSHOT, false, e.getMessage(), "", snapshotObj,
                            fs, storageObj);
                }
            }
            updateTaskStatus(opId, fsObj, e);
        }
    }

    private void doDeleteExportsFromFSObjMap(boolean allDirs, String subDir, FileDeviceInputOutput args) throws Exception {

        // Query All Export Rules Specific to a File System.
        // Update the FS ExportMap
        // Remove Export

        FileObject fsObj = null;
        if (args.getFileOperation()) {
            fsObj = args.getFs();
        } else {
            fsObj = args.getFileSnapshot();
        }

        FSExportMap map = args.getFileObjExports();
        if ((map == null) || (map.isEmpty())) {
            return;
        }

        Collection<FileExport> fileExports = map.values();
        List<String> filexportKeys = new ArrayList<>();
        if (allDirs) {
            // ALl EXPORTS
            // to avoid current exception, prepare seperate delete list
            for (FileExport fileExport : fileExports) {
                filexportKeys.add(fileExport.getFileExportKey());
            }
            // then remove keys
            _log.info("Removing all exports from the export map");
            for (String filexportKey : filexportKeys) {
                fsObj.getFsExports().remove(filexportKey);
                _log.info("FileShareExport removed : " + filexportKey);
            }

            _dbClient.persistObject(fsObj);
        } else if (subDir != null && !subDir.isEmpty()) {
            _log.info("Removing FileExport Specific to SubDirectory from Export Map {}", subDir);
            // Filter for a specific Sub Directory export
            // to avoid current exception, prepare seperate delete list
            for (FileExport fileExport : fileExports) {
                if (fileExport.getSubDirectory().equalsIgnoreCase(subDir)) {
                    filexportKeys.add(fileExport.getFileExportKey());
                }
            }
            for (String filexportKey : filexportKeys) {
                fsObj.getFsExports().remove(filexportKey);
                _log.info("FileShareExport removed : " + filexportKey);
            }
            _dbClient.persistObject(fsObj);
        } else {
            // Filter for No SUBDIR - main export rules with no sub dirs
            _log.info("Removing All FileExports other than SubDirectory exports from Export Map");
            // to avoid current exception, prepare seperate delete list
            for (FileExport fileExport : fileExports) {
                if (fileExport.getSubDirectory() == null
                        || (fileExport.getSubDirectory() != null && fileExport.getSubDirectory().isEmpty())) {
                    filexportKeys.add(fileExport.getFileExportKey());
                }
            }
            // remove the filesystem keys
            for (String filexportKey : filexportKeys) {
                fsObj.getFsExports().remove(filexportKey);
                _log.info("FileShareExport removed : " + filexportKey);
            }
            _dbClient.persistObject(fsObj);
        }

    }

    private FileExportRule getFileExportRule(URI uri, FileExport fileExport, FileDeviceInputOutput args) {

        FileExportRule rule = new FileExportRule();
        rule.setAnon(fileExport.getRootUserMapping());
        rule.setExportPath(fileExport.getPath());
        if (!args.getFileOperation()) {
            rule.setSnapshotId(uri);

        } else {
            rule.setFileSystemId(uri);
        }
        rule.setSecFlavor(fileExport.getSecurityType());

        if (fileExport.getPermissions().equals(FileShareExport.Permissions.ro.name())
                && fileExport.getClients() != null && !fileExport.getClients().isEmpty()) {
            rule.setReadOnlyHosts(new StringSet(fileExport.getClients()));
        }
        if (fileExport.getPermissions().equals(FileShareExport.Permissions.rw.name())
                && fileExport.getClients() != null && !fileExport.getClients().isEmpty()) {
            rule.setReadWriteHosts(new StringSet(fileExport.getClients()));
        }
        if (fileExport.getPermissions().equals(FileShareExport.Permissions.root.name())
                && fileExport.getClients() != null && !fileExport.getClients().isEmpty()) {
            rule.setRootHosts(new StringSet(fileExport.getClients()));
        }
        rule.setMountPoint(fileExport.getMountPoint());
        // _log.info("Generating FileExportRule IsilonId ? {}", fileExport.getIsilonId());
        if (fileExport.getIsilonId() != null) {
            rule.setDeviceExportId(fileExport.getIsilonId());
        }
        if (fileExport.getNativeId() != null) {
            rule.setDeviceExportId(fileExport.getNativeId());
        }
        return rule;
    }

    @Override
    public void updateShareACLs(URI storage, URI fsURI, String shareName,
            CifsShareACLUpdateParams param,
            String opId) throws ControllerException {

        ControllerUtils.setThreadLocalLogData(fsURI, opId);
        FileObject fsObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        FileShare fs = null;
        Snapshot snapshotObj = null;
        boolean isFile = false;

        _log.info("Controller recieved request to update ACL of share {}: cifsShareACLUpdateParams {}",
                shareName, param);

        try {

            StorageSystem storageObj = _dbClient.queryObject(
                    StorageSystem.class, storage);

            args.setAllShareAcls(param);
            args.setShareName(shareName);

            // File
            if (URIUtil.isType(fsURI, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fsURI);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                fsObj = fs;
                args.addFSFileObject(fs);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);

            } else {
                // Snapshot
                snapshotObj = _dbClient.queryObject(Snapshot.class, fsURI);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class,
                        snapshotObj.getParent());
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                args.addFileShare(fs);
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);

            // Query for existing ACLs
            args.setExistingShareAcls(queryExistingShareAcls(args));

            // Do the Operation on device.
            BiosCommandResult result = getDevice(storageObj.getSystemType())
                    .updateShareACLs(storageObj, args);

            if (result.isCommandSuccess()) {
                // Update database
                updateShareACLsInDB(param, fs, args);
            }

            if (result.getCommandPending()) {
                return;
            }
            // Audit & Update the task status
            OperationTypeEnum auditType = null;
            auditType = (isFile) ? OperationTypeEnum.UPDATE_FILE_SYSTEM_SHARE_ACL
                    : OperationTypeEnum.UPDATE_FILE_SNAPSHOT_SHARE_ACL;

            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

            // Monitoring - Event Processing
            String eventMsg = result.isCommandSuccess() ? "" : result
                    .getMessage();

            if (isFile) {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        getNewACLExtensions(param.retrieveAllACLs()),
                        fs, storageObj);
            } else {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        getNewACLExtensions(param.retrieveAllACLs()),
                        snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);
        } catch (Exception e) {
            String[] logParams = { storage.toString(), fsURI.toString() };
            _log.error("Unable to update share ACL for file system or snapshot: storage {}, FS/snapshot URI {}",
                    logParams, e);
            _log.error("{}, {} ", e.getMessage(), e);
            updateTaskStatus(opId, fsObj, e);
        }

    }

    private void updateShareACLsInDB(CifsShareACLUpdateParams param,
            FileShare fs, FileDeviceInputOutput args) {

        try {
            // Create new Acls
            ShareACLs shareAcls = param.getAclsToAdd();
            List<ShareACL> shareAclList = null;
            if (shareAcls != null) {
                shareAclList = shareAcls.getShareACLs();
                if (shareAclList != null && !shareAclList.isEmpty()) {
                    for (ShareACL acl : shareAclList) {
                        CifsShareACL dbShareAcl = new CifsShareACL();
                        dbShareAcl.setId(URIUtil.createId(CifsShareACL.class));
                        copyPropertiesToSave(acl, dbShareAcl, fs, args);
                        _log.info("Storing new acl in DB: {}", dbShareAcl);
                        _dbClient.createObject(dbShareAcl);
                    }
                }
            }

            // Modify existing acls
            shareAcls = param.getAclsToModify();
            if (shareAcls != null) {
                shareAclList = shareAcls.getShareACLs();
                if (shareAclList != null && !shareAclList.isEmpty()) {
                    for (ShareACL acl : shareAclList) {
                        CifsShareACL dbShareAcl = new CifsShareACL();

                        copyPropertiesToSave(acl, dbShareAcl, fs, args);
                        CifsShareACL dbShareAclTemp = getExistingShareAclFromDB(dbShareAcl, args);
                        dbShareAcl.setId(dbShareAclTemp.getId());
                        _log.info("Updating acl in DB: {}", dbShareAcl);
                        _dbClient.persistObject(dbShareAcl);

                    }
                }
            }

            // Delete existing acls
            shareAcls = param.getAclsToDelete();
            if (shareAcls != null) {
                shareAclList = shareAcls.getShareACLs();
                if (shareAclList != null && !shareAclList.isEmpty()) {
                    for (ShareACL acl : shareAclList) {
                        CifsShareACL dbShareAcl = new CifsShareACL();
                        copyPropertiesToSave(acl, dbShareAcl, fs, args);
                        CifsShareACL dbShareAclTemp = getExistingShareAclFromDB(dbShareAcl, args);
                        dbShareAcl.setId(dbShareAclTemp.getId());
                        dbShareAcl.setInactive(true);
                        _log.info("Marking acl inactive in DB: {}", dbShareAcl);
                        _dbClient.persistObject(dbShareAcl);
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Error While executing CRUD Operations {}", e);
        }

    }

    private CifsShareACL getExistingShareAclFromDB(CifsShareACL dbShareAcl,
            FileDeviceInputOutput args) {

        CifsShareACL acl = null;
        String index = null;
        URIQueryResultList result = new URIQueryResultList();
        if (args.getFileOperation()) {
            index = dbShareAcl.getFileSystemShareACLIndex();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileSystemShareACLConstraint(index), result);
        } else {
            index = dbShareAcl.getSnapshotShareACLIndex();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotShareACLConstraint(index), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                acl = _dbClient.queryObject(CifsShareACL.class, it.next());
                if (acl != null && !acl.getInactive()) {
                    _log.info("Existing ACE found in DB: {}", acl);
                    return acl;
                }
            }
        }

        return null;
    }

    private void copyPropertiesToSave(ShareACL acl, CifsShareACL dbShareAcl,
            FileShare fs, FileDeviceInputOutput args) {

        dbShareAcl.setShareName(args.getShareName());

        if (acl.getGroup() != null) {
            dbShareAcl.setGroup(acl.getGroup());
        }

        if (acl.getUser() != null) {
            dbShareAcl.setUser(acl.getUser());
        }

        if (acl.getPermission() != null) {
            dbShareAcl.setPermission(acl.getPermission());
        }

        if (acl.getDomain() != null) {
            dbShareAcl.setDomain(acl.getDomain());
        }

        if (args.getFileOperation()) {
            dbShareAcl.setFileSystemId(fs.getId());
        } else {
            dbShareAcl.setSnapshotId(args.getSnapshotId());
        }

    }

    /**
     * Create the DB object from the NfsACE object
     * 
     * @param ace given NfsACE object
     * @param dbShareAcl DB object need to be formed
     * @param fs FileShare object
     * @param args FileDeviceInputOutput object
     */
    private void copyToPersistNfsACL(NfsACE ace, NFSShareACL dbShareAcl,
            FileShare fs, FileDeviceInputOutput args) {

        if (args.getFileSystemPath() != null) {
            String path = args.getFileSystemPath();
            if (args.getSubDirectory() != null && !args.getSubDirectory().isEmpty()) {
                path = path + "/" + args.getSubDirectory();
            }
            dbShareAcl.setFileSystemPath(path);
        }

        if (ace.getUser() != null) {
            dbShareAcl.setUser(ace.getUser());
        }
        if (ace.getType() != null) {
            dbShareAcl.setType(ace.getType());
        }

        if (ace.getDomain() != null) {
            dbShareAcl.setDomain(ace.getDomain());
        }

        if (args.getFileOperation()) {
            dbShareAcl.setFileSystemId(fs.getId());
        } else {
            dbShareAcl.setSnapshotId(args.getSnapshotId());
        }

        if (ace.getPermissions() != null) {
            dbShareAcl.setPermissions(ace.getPermissions());
        }
        if (ace.getPermissionType() != null) {
            dbShareAcl.setPermissionType(ace.getPermissionType());
        }

    }

    /**
     * Get the DB object to modify it
     * 
     * @param dbShareAcl the DB object which need to be searched
     * @param isFile it is file or snapshot operation
     * @return
     */
    private NFSShareACL getExistingNfsAclFromDB(NFSShareACL dbShareAcl,
            boolean isFile) {

        NFSShareACL acl = null;
        String index = null;
        URIQueryResultList result = new URIQueryResultList();
        if (isFile) {
            index = dbShareAcl.getFileSystemNfsACLIndex();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileSystemNfsACLConstraint(index), result);
        } else {
            index = dbShareAcl.getSnapshotNfsACLIndex();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotNfsACLConstraint(index), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            acl = _dbClient.queryObject(NFSShareACL.class, it.next());
            if (acl != null && !acl.getInactive()) {
                _log.info("Existing ACE found in DB: {}", acl);
                return acl;
            }
        }

        return null;
    }

    private List<CifsShareACL> queryDBShareAcls(FileDeviceInputOutput args) {
        List<CifsShareACL> acls = new ArrayList<CifsShareACL>();
        try {

            ContainmentConstraint containmentConstraint = null;

            if (args.getFileOperation()) {
                FileShare fs = args.getFs();
                _log.info("Querying DB for Share ACLs of share {} of filesystemId {} ",
                        args.getShareName(), fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileCifsShareAclsConstraint(fs.getId());

            } else {
                URI snapshotId = args.getSnapshotId();
                _log.info("Querying DB for Share ACLs of share {} of snapshotId {} ",
                        args.getShareName(), snapshotId);
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotCifsShareAclsConstraint(snapshotId);
            }

            List<CifsShareACL> shareAclList = CustomQueryUtility.queryActiveResourcesByConstraint(
                    _dbClient, CifsShareACL.class, containmentConstraint);

            Iterator<CifsShareACL> shareAclIter = shareAclList.iterator();
            while (shareAclIter.hasNext()) {

                CifsShareACL shareAcl = shareAclIter.next();
                if (args.getShareName().equals(shareAcl.getShareName())) {
                    acls.add(shareAcl);
                }
            }
        } catch (Exception e) {
            _log.error("Error while querying DB for ACL(s) of a share {}", e);
        }

        return acls;
    }

    private boolean snapshotsExistsOnFS(FileShare fs) {

        URIQueryResultList snapIDList = new URIQueryResultList();

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getFileshareSnapshotConstraint(fs.getId()), snapIDList);

        _log.info("getSnapshots: FS {}: {} ", fs.getId().toString(),
                snapIDList.toString());
        List<Snapshot> snapList = _dbClient.queryObject(
                Snapshot.class, snapIDList);

        if (snapList != null) {
            _log.info(" No of Snapshots on FS {} ", snapList.size());
            for (Snapshot snapshot : snapList) {
                if (!snapshot.getInactive())
                    return true;
            }
        }

        return false;
    }

    private boolean quotaDirectoriesExistsOnFS(FileShare fs) {
        _log.info(" Setting Snapshots to InActive with Force Delete ");

        URIQueryResultList qdIDList = new URIQueryResultList();

        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getQuotaDirectoryConstraint(fs.getId()), qdIDList);

        _log.info("getQuotaDirectories : FS {}: {} ", fs.getId().toString(),
                qdIDList.toString());
        List<QuotaDirectory> qdList = _dbClient.queryObject(
                QuotaDirectory.class, qdIDList);

        if (qdList != null) {
            for (QuotaDirectory qd : qdList) {
                if (!qd.getInactive())
                    return true;
            }
        }

        return false;
    }

    private List<ShareACL> queryExistingShareAcls(FileDeviceInputOutput args) {

        _log.info("Querying for Share ACLs of share {}", args.getShareName());
        List<ShareACL> acls = new ArrayList<ShareACL>();

        try {
            List<CifsShareACL> dbShareAclList = queryDBShareAcls(args);
            Iterator<CifsShareACL> dbShareAclIter = dbShareAclList.iterator();
            while (dbShareAclIter.hasNext()) {

                CifsShareACL dbShareAcl = dbShareAclIter.next();
                ShareACL shareAcl = new ShareACL();
                shareAcl.setDomain(dbShareAcl.getDomain());
                if (args.getFileOperation()) {
                    shareAcl.setFileSystemId(dbShareAcl.getFileSystemId());
                } else {
                    shareAcl.setSnapshotId(dbShareAcl.getSnapshotId());
                }
                shareAcl.setGroup(dbShareAcl.getGroup());
                shareAcl.setPermission(dbShareAcl.getPermission());
                shareAcl.setShareName(dbShareAcl.getShareName());
                shareAcl.setUser(dbShareAcl.getUser());

                acls.add(shareAcl);
            }

        } catch (Exception e) {
            _log.error("Error while querying ACL(s) of a share {}", e);
        }

        return acls;
    }

    @Override
    public void deleteShareACLs(URI storage, URI fsURI, String shareName,
            String opId) throws InternalException {

        ControllerUtils.setThreadLocalLogData(fsURI, opId);
        FileObject fsObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        FileShare fs = null;
        Snapshot snapshotObj = null;
        boolean isFile = false;

        _log.info("Controller recieved request to delete share ACL for share {}",
                shareName);

        try {

            StorageSystem storageObj = _dbClient.queryObject(
                    StorageSystem.class, storage);

            args.setShareName(shareName);

            // File
            if (URIUtil.isType(fsURI, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fsURI);
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                fsObj = fs;
                args.addFSFileObject(fs);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);

            } else {
                // Snapshot
                snapshotObj = _dbClient.queryObject(Snapshot.class, fsURI);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class,
                        snapshotObj.getParent());
                setVirtualNASinArgs(fs.getVirtualNAS(), args);
                args.addFileShare(fs);
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);
            // query and setExistingShare ACL
            args.setExistingShareAcls(queryExistingShareAcls(args));

            // Do the Operation on device.
            BiosCommandResult result = getDevice(storageObj.getSystemType())
                    .deleteShareACLs(storageObj, args);

            if (result.isCommandSuccess()) {
                // Update database
                deleteShareACLsFromDB(args);
            }

            if (result.getCommandPending()) {
                return;
            }
            // Audit & Update the task status
            OperationTypeEnum auditType = null;
            auditType = (isFile) ? OperationTypeEnum.DELETE_FILE_SYSTEM_SHARE_ACL
                    : OperationTypeEnum.DELETE_FILE_SNAPSHOT_SHARE_ACL;

            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

            // Monitoring - Event Processing
            String eventMsg = result.isCommandSuccess() ? "" : result
                    .getMessage();

            if (isFile) {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        shareName,
                        fs, storageObj);
            } else {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        shareName,
                        snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);
        } catch (Exception e) {
            String[] logParams = { storage.toString(), fsURI.toString() };
            _log.error("Unable to delete share ACL for file system or snapshot: storage {}, FS/snapshot URI {}",
                    logParams, e);
            _log.error("{}, {} ", e.getMessage(), e);
            updateTaskStatus(opId, fsObj, e);
        }

    }

    /**
     * Creates default share ACE for fileshares on VNXe, VXFile and Datadomain
     * 
     * @param id URI of filesystem or snapshot
     * @param share
     * @param storageType
     */
    private void createDefaultACEForSMBShare(URI id, FileSMBShare fileShare,
            String storageType) {

        StorageSystem.Type storageSystemType = Enum.valueOf(
                StorageSystem.Type.class, storageType);

        switch (storageSystemType) {
            case vnxe:
            case vnxfile:
            case datadomain:
                SMBFileShare share = fileShare.getSMBFileShare();
                CifsShareACL ace = new CifsShareACL();
                ace.setUser(FileControllerConstants.CIFS_SHARE_USER_EVERYONE);
                String permission = null;
                switch (share.getPermission()) {
                    case "read":
                        permission = FileControllerConstants.CIFS_SHARE_PERMISSION_READ;
                        break;
                    case "change":
                        permission = FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE;
                        break;
                    case "full":
                        permission = FileControllerConstants.CIFS_SHARE_PERMISSION_FULLCONTROL;
                        break;
                }
                ace.setPermission(permission);
                ace.setId(URIUtil.createId(CifsShareACL.class));
                ace.setShareName(share.getName());
                if (URIUtil.isType(id, FileShare.class)) {
                    ace.setFileSystemId(id);
                } else {
                    ace.setSnapshotId(id);
                }

                _log.info("Creating default ACE for the share: {}", ace);
                _dbClient.createObject(ace);
                break;

            default:
                break;
        }

    }

    @Override
    public void updateNFSAcl(URI storage, URI fsURI, NfsACLUpdateParams param, String opId) throws InternalException {
        ControllerUtils.setThreadLocalLogData(fsURI, opId);
        FileObject fsObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        FileShare fs = null;
        Snapshot snapshotObj = null;
        boolean isFile = false;

        try {

            StorageSystem storageObj = _dbClient.queryObject(
                    StorageSystem.class, storage);

            args.setSubDirectory(param.getSubDir());
            args.setAllNfsAcls(param);

            _log.info("Controller Recieved NfsACLUpdateParams {}", param);

            // File
            if (URIUtil.isType(fsURI, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fsURI);
                fsObj = fs;
                args.addFSFileObject(fs);
                args.setFileSystemPath(fs.getPath());
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);

            } else {
                // Snapshot
                snapshotObj = _dbClient.queryObject(Snapshot.class, fsURI);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class,
                        snapshotObj.getParent());
                args.addFileShare(fs);
                args.setFileSystemPath(fs.getPath());
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);

            // Do the Operation on device.
            BiosCommandResult result = getDevice(storageObj.getSystemType())
                    .updateNfsACLs(storageObj, args);

            if (result.isCommandSuccess()) {
                // Update Database
                updateNFSACLsInDB(param, fs, args);
            }

            if (result.getCommandPending()) {
                return;
            }
            // Audit & Update the task status
            OperationTypeEnum auditType = null;
            auditType = (isFile) ? OperationTypeEnum.UPDATE_FILE_SYSTEM_NFS_ACL
                    : OperationTypeEnum.UPDATE_FILE_SNAPSHOT_NFS_ACL;

            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

            // Monitoring - Event Processing
            String eventMsg = result.isCommandSuccess() ? "" : result
                    .getMessage();

            if (isFile) {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        args.getFileSystemPath(),
                        fs, storageObj);
            } else {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        args.getFileSystemPath(),
                        snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fsURI.toString() };
            _log.error("Unable to set ACL on  file system or snapshot: storage {}, FS/snapshot URI {}", params, e);
            _log.error("{}, {} ", e.getMessage(), e);
            updateTaskStatus(opId, fsObj, e);
        }
    }

    /**
     * Update the DB object, this method need to be called after the success of
     * back end command
     * 
     * @param param object of NfsACLUpdateParams
     * @param fs FileShare object
     * @param args FileDeviceInputOutput object
     */
    private void updateNFSACLsInDB(NfsACLUpdateParams param,
            FileShare fs, FileDeviceInputOutput args) {

        try {
            // Create new Acls
            List<NfsACE> aceAdd = param.getAcesToAdd();

            if (aceAdd != null && !aceAdd.isEmpty()) {
                for (NfsACE ace : aceAdd) {
                    NFSShareACL dbNfsAcl = new NFSShareACL();
                    dbNfsAcl.setId(URIUtil.createId(NFSShareACL.class));
                    copyToPersistNfsACL(ace, dbNfsAcl, fs, args);
                    _log.info("Storing new acl in DB: {}", dbNfsAcl);
                    _dbClient.createObject(dbNfsAcl);
                }
            }

            // Modify existing acls
            List<NfsACE> aceModify = param.getAcesToModify();

            if (aceModify != null && !aceModify.isEmpty()) {
                for (NfsACE ace : aceModify) {
                    NFSShareACL dbNfsAcl = new NFSShareACL();
                    copyToPersistNfsACL(ace, dbNfsAcl, fs, args);
                    NFSShareACL dbNfsAclTemp = getExistingNfsAclFromDB(dbNfsAcl, args.getFileOperation());
                    if (dbNfsAclTemp != null ) {
                    	dbNfsAcl.setId(dbNfsAclTemp.getId());
                        _log.info("Modifying acl in DB: {}", dbNfsAcl);
                        _dbClient.persistObject(dbNfsAcl);
                    } 
                }
            }

            List<NfsACE> aceDelete = param.getAcesToDelete();

            if (aceDelete != null && !aceDelete.isEmpty()) {
                for (NfsACE ace : aceDelete) {
                    NFSShareACL dbNfsAcl = new NFSShareACL();
                    copyToPersistNfsACL(ace, dbNfsAcl, fs, args);
                    NFSShareACL dbNfsAclTemp = getExistingNfsAclFromDB(dbNfsAcl, args.getFileOperation());
                    if ( dbNfsAclTemp != null) {
                    	dbNfsAcl.setId(dbNfsAclTemp.getId());
                        dbNfsAcl.setInactive(true);
                        _log.info("Marking acl inactive in DB: {}", dbNfsAcl);
                        _dbClient.persistObject(dbNfsAcl);
                    }                  
                }
            }
        }

        catch (Exception e) {
            _log.error("Error While executing CRUD Operations {}", e);
        }
    }

    @Override
    public void deleteNFSAcls(URI storage, URI fsURI, String subDir, String opId) throws InternalException {
        ControllerUtils.setThreadLocalLogData(fsURI, opId);
        FileObject fsObj = null;
        FileDeviceInputOutput args = new FileDeviceInputOutput();
        FileShare fs = null;
        Snapshot snapshotObj = null;
        boolean isFile = false;

        try {

            StorageSystem storageObj = _dbClient.queryObject(
                    StorageSystem.class, storage);

            args.setSubDirectory(subDir);

            _log.info("FileDeviceController::deleteNFSAcls Recieved Nfs ACL DELETE Operation ");

            // File
            if (URIUtil.isType(fsURI, FileShare.class)) {
                isFile = true;
                fs = _dbClient.queryObject(FileShare.class, fsURI);
                fsObj = fs;
                args.addFSFileObject(fs);
                args.setFileSystemPath(fs.getPath());
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);

            } else {
                // Snapshot
                snapshotObj = _dbClient.queryObject(Snapshot.class, fsURI);
                fsObj = snapshotObj;
                fs = _dbClient.queryObject(FileShare.class,
                        snapshotObj.getParent());
                args.addFileShare(fs);
                args.setFileSystemPath(fs.getPath());
                args.addSnapshotFileObject(snapshotObj);
                StoragePool pool = _dbClient.queryObject(StoragePool.class,
                        fs.getPool());
                args.addStoragePool(pool);
            }

            args.setFileOperation(isFile);
            args.setOpId(opId);

            List<NfsACE> aceDeleteList = new ArrayList<NfsACE>();
            List<NFSShareACL> dbNfsAclTemp = queryAllNfsACLInDB(fs, subDir, args);
            makeNfsAceFromDB(aceDeleteList, dbNfsAclTemp);
            args.setNfsAclsToDelete(aceDeleteList);

            // Do the Operation on device.
            BiosCommandResult result = getDevice(storageObj.getSystemType())
                    .deleteNfsACLs(storageObj, args);

            if (result.isCommandSuccess()) {
                // Update Database
            	if ( !dbNfsAclTemp.isEmpty()) {
            		for (NFSShareACL nfsShareACL : dbNfsAclTemp) {
                        nfsShareACL.setInactive(true);
                    }
            		 _dbClient.persistObject(dbNfsAclTemp);
            	}
            }

            if (result.getCommandPending()) {
                return;
            }
            // Audit & Update the task status
            OperationTypeEnum auditType = null;
            auditType = (isFile) ? OperationTypeEnum.DELETE_FILE_SYSTEM_NFS_ACL
                    : OperationTypeEnum.DELETE_FILE_SNAPSHOT_NFS_ACL;

            fsObj.getOpStatus().updateTaskStatus(opId, result.toOperation());

            // Monitoring - Event Processing
            String eventMsg = result.isCommandSuccess() ? "" : result
                    .getMessage();

            if (isFile) {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        args.getFileSystemPath(),
                        fs, storageObj);
            } else {
                recordFileDeviceOperation(_dbClient,
                        auditType,
                        result.isCommandSuccess(),
                        eventMsg,
                        args.getFileSystemPath(),
                        snapshotObj, fs, storageObj);
            }
            _dbClient.persistObject(fsObj);
        } catch (Exception e) {
            String[] params = { storage.toString(), fsURI.toString() };
            _log.error("Unable to Delete  ACL on  file system or snapshot: storage {}, FS/snapshot URI {}", params, e);
            _log.error("{}, {} ", e.getMessage(), e);
            updateTaskStatus(opId, fsObj, e);
        }
    }

    /**
     * To get all the ACLs associated with the a FileShare
     * 
     * @param fs File Share
     * @param subDir Sub directory
     * @return List of NFS ACL present in DB.
     */
    private List<NFSShareACL> queryAllNfsACLInDB(FileShare fs, String subDir, FileDeviceInputOutput args) {
        List<NFSShareACL> nfsShareAcl = null;
        _log.info("Querying all Nfs File System ACL Using FsId {}", fs.getId());
        try {
        	
        	ContainmentConstraint containmentConstraint = null;

        	if ( args.getFileOperation() ) {
        		containmentConstraint = ContainmentConstraint.Factory
        				.getFileNfsAclsConstraint(fs.getId());
        		
        	} else {
        		containmentConstraint = ContainmentConstraint.Factory
        				.getSnapshotNfsAclsConstraint(args.getSnapshotId());
        	}
            
            nfsShareAcl = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, NFSShareACL.class,
                    containmentConstraint);

        } catch (Exception e) {
            _log.error("Error while querying {}", e);
        }
        if (subDir != null && !subDir.isEmpty()) {

            // Filter for a specific Sub Directory export
            // fs path + subdir path is same as acl filesystem path
            String absoluteSubdir = fs.getPath() + "/" + subDir;
            for (NFSShareACL nfsAcl : nfsShareAcl) {
                if (!nfsAcl.getFileSystemPath().equals(absoluteSubdir)) {
                    // list of the ace
                    nfsShareAcl.remove(nfsAcl);

                }
            }
        }
        return nfsShareAcl;
    }

    /**
     * Convert list of NfsACE to list of DB object for ACL
     * 
     * @param nfsAcls list of the NfsACE object
     * @param dbNfsAclTemp converted DB object List
     */
    private void makeNfsAceFromDB(List<NfsACE> nfsAcls, List<NFSShareACL> dbNfsAclTemp) {

        for (NFSShareACL nfsShareACL : dbNfsAclTemp) {
        	
            NfsACE nfsAce = new NfsACE();

            String permission = nfsShareACL.getPermissions();
            if (permission != null && !permission.isEmpty()) {
                nfsAce.setPermissions(permission);
            }

            String domain = nfsShareACL.getDomain();
            if (domain != null && !domain.isEmpty()) {
                nfsAce.setDomain(domain);
            }
                   
            String permissionType = nfsShareACL.getPermissionType();
            nfsAce.setPermissionType(FileControllerConstants.NFS_FILE_PERMISSION_TYPE_ALLOW);
            if (permissionType != null && !permissionType.isEmpty()) {
                nfsAce.setPermissionType(permissionType);
            } 
            
            String type = nfsShareACL.getType();
            if (type != null && !type.isEmpty()) {
                nfsAce.setType(type);
            }
            
            String user = nfsShareACL.getUser();
            if (user != null && !user.isEmpty()) {
                nfsAce.setUser(user);
            }
            
            nfsAcls.add(nfsAce);
        }
    }

    /**
     * Set is the vNAS entity in args only if vNASURI is not null
     * @param vNASURI the URI of VirtualNAS
     * @param args instance of FileDeviceInputOutput
     */
    private void setVirtualNASinArgs(URI vNASURI, FileDeviceInputOutput args) {
    	
        if(vNASURI != null) {
            VirtualNAS vNAS = _dbClient.queryObject(VirtualNAS.class, vNASURI);
            args.setvNAS(vNAS);
        }
	}

}
