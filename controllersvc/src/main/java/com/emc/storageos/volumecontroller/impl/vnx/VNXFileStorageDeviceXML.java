/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnx;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SMBFileShare;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.FileSystemConstants;
import com.emc.storageos.vnx.xmlapi.VNXException;
import com.emc.storageos.vnx.xmlapi.VNXFileExport;
import com.emc.storageos.vnx.xmlapi.VNXFileSshApi;
import com.emc.storageos.vnx.xmlapi.VNXFileSystem;
import com.emc.storageos.vnx.xmlapi.VNXQuotaTree;
import com.emc.storageos.vnx.xmlapi.VNXSnapshot;
import com.emc.storageos.vnx.xmlapi.XMLApiResult;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileDeviceInputOutput;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.plugins.provisioning.VNXFileCommApi;

/*
 * Suppressing these warnings as fix will be made in future release.
 */
@SuppressWarnings({ "findbugs:RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "findbugs:NP_NULL_ON_SOME_PATH" })
public class VNXFileStorageDeviceXML implements FileStorageDevice {

    private static final Logger _log = LoggerFactory.getLogger(VNXFileStorageDeviceXML.class);

    private static final String provision_context_file_name = "controller-vnxfile-prov.xml";

    private static final String VNXCOMM_API = "vnxcommapi";

    private static final String VNXCOMM_ERR_MSG = "VNXFile Provisioning context didn't get loaded properly";

    private int _controllerID = 4002;

    private DbClient _dbClient;

    private static final String ERROR = "error";
    private static final String READY = "ready";

    private static int BYTESPERMB = 1048576;

    public VNXFileStorageDeviceXML() {
        this._controllerID = 4002;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    /**
     * Loading context every time for each operation doesn't look right.
     * 
     * TODO As of v1, adding this code, this definitely had to be changed.
     * 
     * @return
     */
    private VNXFileCommApi loadVNXFileCommunicationAPIs(ApplicationContext context) {
        VNXFileCommApi vnxComm = null;
        try {

            vnxComm = (VNXFileCommApi) context.getBean(VNXCOMM_API);
            vnxComm.setDbClient(_dbClient);

        } catch (Exception e) {
            throw new DeviceControllerException(
                    "VNXFile Provisioning context didn't get loaded properly.Terminating File System Provisioning operations.");
        }
        return vnxComm;

    }

    private ApplicationContext loadContext() {
        ApplicationContext context = null;
        try {

            context = new ClassPathXmlApplicationContext(provision_context_file_name);
        } catch (Exception e) {
            throw new DeviceControllerException(
                    "VNXFile Provisioning context didn't get loaded properly.Terminating File System Provisioning operations.", e);
        }
        return context;
    }

    private void clearContext(ApplicationContext context) {
        if (null != context) {
            ((ClassPathXmlApplicationContext) context).close();
        }

    }

    @Override
    public BiosCommandResult doCreateFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {

        Map<String, String> autoExtendAtts = getAutoExtendAttrs(args);
        Long fsSize = args.getFsCapacity() / BYTESPERMB;
        if (fsSize < 1) {
            // Invalid size throw an error
            String errMsg = "doCreateFS failed : FileSystem size in bytes is not valid " + args.getFsCapacity();
            _log.error(errMsg);
            return BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToCreateFileSystem(errMsg));
        }
        _log.info("FileSystem size translation : {} : {} ", args.getFsCapacity(), fsSize);

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            result = vnxComm.createFileSystem(storage,
                    args.getFsName(),
                    args.getPoolName(),           // This will be used for CLI create FS
                    "1",
                    fsSize,
                    args.getThinProvision(),
                    args.getNativeDeviceFsId(),
                    autoExtendAtts);

            if (result.isCommandSuccess()) {
                VNXFileSystem vnxFS = (VNXFileSystem) result.getObject();
                args.setFsNativeId(String.valueOf(vnxFS.getFsId()));
                String path = "/" + args.getFsName();
                // Set path & mountpath
                args.setFsMountPath(path);
                args.setFsPath(path);
            }
        } catch (VNXException e) {
            throw DeviceControllerException.exceptions.unableToCreateFileSystem(e.getMessage(Locale.getDefault()));
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToCreateFileSystem(result.getMessage()));
        }
        return cmdResult;
    }

    public Map<String, String> getAutoExtendAttrs(FileDeviceInputOutput args) {

        Map<String, String> autoAtts = new HashMap<String, String>();

        if (args.getFsExtensions() == null) {
            args.initFsExtensions();
        }

        String wormValue = "";
        if (args.getFsExtensions().containsKey(VNXFileCommApi.WORM_ATTRIBUTE)) {
            wormValue = args.getFsExtensions().get(VNXFileCommApi.WORM_ATTRIBUTE);
        } else {
            wormValue = args.getPoolExtensions().get(VNXFileCommApi.WORM_ATTRIBUTE);
        }
        wormValue = (wormValue != null) ? wormValue : VNXFileCommApi.WORM_DEF;
        args.getFsExtensions().put(VNXFileCommApi.WORM_ATTRIBUTE, wormValue);

        String autoExtendValue = "";
        if (args.getFsExtensions().containsKey(VNXFileCommApi.AUTO_EXTEND_ENABLED_ATTRIBUTE)) {
            autoExtendValue = args.getFsExtensions().get(VNXFileCommApi.AUTO_EXTEND_ENABLED_ATTRIBUTE);
        } else {
            autoExtendValue = args.getPoolExtensions().get(VNXFileCommApi.AUTO_EXTEND_ENABLED_ATTRIBUTE);
        }
        autoExtendValue = (autoExtendValue != null) ? autoExtendValue : VNXFileCommApi.AUTO_EXTEND_ENABLED_DEF;
        args.getFsExtensions().put(VNXFileCommApi.AUTO_EXTEND_ENABLED_ATTRIBUTE, autoExtendValue);

        String autoExtendHWM = args.getFsExtensions().get(VNXFileCommApi.AUTO_EXTEND_HWM_ATTRIBUTE);
        String autoExtendMaxSize = args.getFsExtensions().get(VNXFileCommApi.AUTO_EXTEND_MAX_SIZE_ATTRIBUTE);
        if (Boolean.valueOf(autoExtendValue).booleanValue() == true) {
            _log.debug("AutoExtend is true");
            autoExtendHWM = (autoExtendHWM != null) ? autoExtendHWM : args.getPoolExtensions().get(VNXFileCommApi.AUTO_EXTEND_HWM_DEF);
            autoExtendHWM = (autoExtendHWM != null) ? autoExtendHWM : VNXFileCommApi.AUTO_EXTEND_HWM_DEF;
            args.getFsExtensions().put(VNXFileCommApi.AUTO_EXTEND_HWM_ATTRIBUTE, autoExtendHWM);
            autoExtendMaxSize = (autoExtendMaxSize != null) ? autoExtendMaxSize : args.getPoolExtensions().get(
                    VNXFileCommApi.AUTO_EXTEND_MAX_SIZE_ATTRIBUTE);
            autoExtendMaxSize = (autoExtendMaxSize != null) ? autoExtendMaxSize : String.valueOf(args.getFsCapacity() * 1.1);
            args.getFsExtensions().put(VNXFileCommApi.AUTO_EXTEND_MAX_SIZE_ATTRIBUTE, autoExtendMaxSize);

        } else {
            _log.debug("AutoExtend is false");
        }

        String fileSystemType = "";
        if (args.getFsExtensions().containsKey(VNXFileCommApi.FILE_SYSTEM_TYPE_ATTRIBUTE)) {
            fileSystemType = args.getFsExtensions().get(VNXFileCommApi.FILE_SYSTEM_TYPE_ATTRIBUTE);
        } else {
            fileSystemType = args.getPoolExtensions().get(VNXFileCommApi.FILE_SYSTEM_TYPE_ATTRIBUTE);
        }
        fileSystemType = (fileSystemType != null) ? fileSystemType : VNXFileCommApi.FILE_SYSTEM_TYPE_DEF;
        args.getFsExtensions().put(VNXFileCommApi.FILE_SYSTEM_TYPE_ATTRIBUTE, fileSystemType);

        String thinProvisioned = "";
        if (args.getThinProvision() != null) {
            thinProvisioned = String.valueOf(args.getThinProvision());
        } else {
            thinProvisioned = args.getPoolExtensions().get(VNXFileCommApi.THIN_PROVISIONED_ATTRIBUTE);
        }
        thinProvisioned = (thinProvisioned != null) ? thinProvisioned : VNXFileCommApi.THIN_PROVISIONED_DEF;
        args.getFsExtensions().put(VNXFileCommApi.THIN_PROVISIONED_ATTRIBUTE, thinProvisioned);

        autoAtts.put(VNXFileCommApi.FILE_SYSTEM_TYPE_ATTRIBUTE, args.getFsExtensions().get(VNXFileCommApi.FILE_SYSTEM_TYPE_ATTRIBUTE));
        autoAtts.put(VNXFileCommApi.THIN_PROVISIONED_ATTRIBUTE, args.getFsExtensions().get(VNXFileCommApi.THIN_PROVISIONED_ATTRIBUTE));
        autoAtts.put(VNXFileCommApi.WORM_ATTRIBUTE, args.getFsExtensions().get(VNXFileCommApi.WORM_ATTRIBUTE));
        autoAtts.put(VNXFileCommApi.AUTO_EXTEND_ENABLED_ATTRIBUTE,
                args.getFsExtensions().get(VNXFileCommApi.AUTO_EXTEND_ENABLED_ATTRIBUTE));
        autoAtts.put(VNXFileCommApi.AUTO_EXTEND_HWM_ATTRIBUTE, args.getFsExtensions().get(VNXFileCommApi.AUTO_EXTEND_HWM_ATTRIBUTE));
        autoAtts.put(VNXFileCommApi.AUTO_EXTEND_MAX_SIZE_ATTRIBUTE,
                args.getFsExtensions().get(VNXFileCommApi.AUTO_EXTEND_MAX_SIZE_ATTRIBUTE));

        return autoAtts;
    }

    @Override
    public BiosCommandResult doDeleteFS(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        _log.info("doDeleteFS: fs id = {} Force Delete : {}", args.getFsNativeId(), args.getForceDelete());
        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            _log.info("DBClient :" + vnxComm.getDbClient());
            result = vnxComm.deleteFileSystem(storage, args.getFsNativeId(), args.getFsName(), args.getForceDelete(), args.getFs());

        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToDeleteFileSystem(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public boolean doCheckFSExists(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        _log.info("checking file system existence on array: ", args.getFsName());
        boolean isFSExists = true;
        try {
            ApplicationContext context = null;
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            isFSExists = vnxComm.checkFileSystemExists(storage, args.getFsNativeId(), args.getFsName());
        } catch (VNXException e) {
            _log.error("Querying FS existence failed");
        }
        return isFSExists;
    }

    @Override
    public BiosCommandResult updateExportRules(StorageSystem storage,
            FileDeviceInputOutput args)
            throws ControllerException {
        XMLApiResult result = null;
        ApplicationContext context = null;

        // Requested Export Rules
        List<ExportRule> exportAdd = args.getExportRulesToAdd();
        List<ExportRule> exportDelete = args.getExportRulesToDelete();
        List<ExportRule> exportModify = args.getExportRulesToModify();

        // To be processed export rules
        List<ExportRule> exportsToRemove = new ArrayList<>();
        List<ExportRule> exportsToAdd = new ArrayList<>();

        String exportPath;
        String subDir = args.getSubDirectory();

        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null && subDir.length() > 0) {
                exportPath = args.getSnapshotPath() + "/" + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null && subDir.length() > 0) {
                exportPath = args.getFs().getPath() + "/" + subDir;
            }
        }
        _log.info("exportPath : {}", exportPath);
        args.setExportPath(exportPath);

        // ALL EXPORTS
        List<ExportRule> existingDBExportRule = args.getExistingDBExportRules();
        List<ExportRule> exportsToprocess = new ArrayList<>();
        for (ExportRule rule : existingDBExportRule) {
            if (rule.getExportPath().equalsIgnoreCase(exportPath)) {
                exportsToprocess.add(rule);
            }
        }
        _log.info("Number of existng Rules found {}", exportsToprocess.size());

        // Handle Modified export Rules and add rules
        // If there are no Export rules and add is allowed
        if (!exportsToprocess.isEmpty() || (exportAdd != null && !exportAdd.isEmpty())) {
            for (ExportRule existingRule : exportsToprocess) {
                for (ExportRule modifiedrule : exportModify) {
                    if (modifiedrule.getSecFlavor().equals(
                            existingRule.getSecFlavor())) {
                        _log.info("Modifying Export Rule from {}, To {}",
                                existingRule, modifiedrule);
                        // use a separate list to avoid concurrent modifications for now.
                        exportsToRemove.add(existingRule);
                        exportsToAdd.add(modifiedrule);
                    }
                }
            }

            // Handle Add export Rules
            if (exportAdd != null && !exportAdd.isEmpty()) {
                for (ExportRule newExport : exportAdd) {
                    _log.info("Adding Export Rule {}", newExport);
                    exportsToAdd.add(newExport);
                }
            }

            // Handle Delete export Rules
            if (exportDelete != null && !exportDelete.isEmpty()) {
                for (ExportRule existingRule : exportsToprocess) {
                    for (ExportRule oldExport : exportDelete) {
                        if (oldExport.getSecFlavor().equals(
                                existingRule.getSecFlavor())) {
                            _log.info("Deleting Export Rule {}", existingRule);
                            exportsToRemove.add(existingRule);
                        }
                    }
                }
            }
            // No of exports found to remove from the list
            _log.info("No of exports found to remove from the existing exports list {}", exportsToRemove.size());
            exportsToprocess.removeAll(exportsToRemove);
            _log.info("No of exports found to add to the existing exports list {}", exportsToAdd.size());
            exportsToprocess.addAll(exportsToAdd);

            // Figure out mounted or not
            SMBShareMap shares = args.getFs().getSMBFileShares();
            boolean isMounted = true;
            if (exportsToprocess.isEmpty() &&
                    (shares == null || (shares != null && shares.isEmpty()))) {
                isMounted = false;
            }
            // Mounting is only necessary for FileSystem and not snapshot for the first time export
            if (!args.getFileOperation()) {
                isMounted = false;
            }

            // To be compatible with existing export creating an empty list
            List<String> newPaths = new ArrayList<String>();
            newPaths.add(exportPath);

            try {
                context = loadContext();
                VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
                if (null == vnxComm) {
                    throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
                }
                // Get DataMover Name and whether it is virtual
                StorageHADomain dm = this.getDataMover(args.getFs());
                if (dm == null) {
                    Exception e = new Exception("VNX File Export Failed Data Mover not found");
                    throw VNXException.exceptions.createExportFailed("VNX File Export Failed Data Mover not found", e);
                }

                List<VNXFileExport> exportList = new ArrayList<VNXFileExport>();

                for (ExportRule rule : exportsToprocess) {

                    VNXFileExport vnxExp = null;
                    // update the comment
                    String comments = rule.getComments();
                    String protocol = "nfs";
                    if (rule.getReadOnlyHosts() != null && !rule.getReadOnlyHosts().isEmpty()) {
                        vnxExp = new VNXFileExport(new ArrayList<String>(rule.getReadOnlyHosts()),
                                dm.getName(), exportPath,
                                rule.getSecFlavor(), "ro",
                                rule.getAnon(), protocol,
                                args.getFs().getStoragePort().toString(), subDir, comments);

                        exportList.add(vnxExp);
                    }
                    if (rule.getReadWriteHosts() != null && !rule.getReadWriteHosts().isEmpty()) {
                        vnxExp = new VNXFileExport(new ArrayList<String>(rule.getReadWriteHosts()),
                                dm.getName(), exportPath,
                                rule.getSecFlavor(), "rw",
                                rule.getAnon(), protocol,
                                args.getFs().getStoragePort().toString(), subDir, comments);

                        exportList.add(vnxExp);
                    }
                    if (rule.getRootHosts() != null && !rule.getRootHosts().isEmpty()) {
                        vnxExp = new VNXFileExport(new ArrayList<String>(rule.getRootHosts()),
                                dm.getName(), exportPath,
                                rule.getSecFlavor(), "root",
                                rule.getAnon(), protocol,
                                args.getFs().getStoragePort().toString(), subDir, comments);
                        exportList.add(vnxExp);
                    }

                }

                // When all the export rules removed, add one rule manually to meet the requirments of
                // existing VNXComm API. This is required to read the subsequent information down the line.
                if ((exportList != null && exportList.isEmpty()) && (exportsToRemove != null && !exportsToRemove.isEmpty())) {
                    _log.info("Requested to remove all export rules");
                    VNXFileExport vnxExp = new VNXFileExport(new ArrayList<String>(),
                            dm.getName(), exportPath,
                            "", "root", "", "",
                            args.getFs().getStoragePort().toString(), subDir, "");

                    exportList.add(vnxExp);
                }

                // List<VNXFileExport> vnxExports = getVNXFileExports(newExpList);
                if (args.getFileOperation()) {
                    // Perform FileSystem export
                    result = vnxComm.doExport(storage, dm, exportList, newPaths, args.getFileObj(), args.getFsNativeId(), isMounted);

                } else {
                    // perform Snapshot export
                    result = vnxComm.doExport(storage, dm, exportList, newPaths, args.getFileObj(), args.getSnapNativeId(), isMounted);
                }

                if (result.isCommandSuccess()) {
                    _log.info("updateExportRules result.isCommandSuccess true");
                }
            } catch (VNXException e) {
                throw VNXException.exceptions.createExportFailed("VNX File Export Failed", e);
            } finally {
                clearContext(context);
            }
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToUpdateExport(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doExport(StorageSystem storage, FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {

        boolean firstExport = false;
        if (args.getFileObjExports() == null || args.getFileObjExports().isEmpty()) {
            args.initFileObjExports();
        }

        // mount the FileSystem
        firstExport = !(args.isFileShareMounted());
        if (firstExport) {
            _log.debug("First export: no existing file exports");
        }
        // Mounting is only necessary for FileSystem and not snapshot for the first time export
        if (!args.getFileOperation()) {
            firstExport = false;
        }

        // Create a list of the new exports.
        FSExportMap newExpList = new FSExportMap();
        FSExportMap curExpList = args.getFileObjExports();
        FileExport curExport = null;
        FileExport newExport = null;
        List<String> newPaths = new ArrayList<String>();

        Iterator<String> it = curExpList.keySet().iterator();
        while (it.hasNext()) {
            curExport = curExpList.get(it.next());
            newExport = new FileExport(curExport.getClients(), curExport.getStoragePortName(),
                    ExportUtils.getFileMountPoint(curExport.getStoragePort(), curExport.getPath()),
                    curExport.getSecurityType(), curExport.getPermissions(), curExport.getRootUserMapping(),
                    curExport.getProtocol(), curExport.getStoragePort(), curExport.getPath(),
                    curExport.getMountPath(), curExport.getSubDirectory(), curExport.getComments());

            _log.info("FileExport key : {} ", newExport.getFileExportKey());
            newExpList.put(newExport.getFileExportKey(), newExport);
        }

        for (FileExport exp : exportList) {
            newExport = new FileExport(exp.getClients(), exp.getStoragePortName(),
                    ExportUtils.getFileMountPoint(exp.getStoragePort(), exp.getPath()),
                    exp.getSecurityType(), exp.getPermissions(), exp.getRootUserMapping(), exp.getProtocol(),
                    exp.getStoragePort(), exp.getPath(), exp.getMountPath(), exp.getSubDirectory(), exp.getComments());

            _log.info("FileExport key : {} ", newExport.getFileExportKey());
            newExpList.put(newExport.getFileExportKey(), newExport);
            if (!newPaths.contains(newExport.getPath())) {
                newPaths.add(newExport.getPath());
            }
        }

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            // Get DataMover Name and whether it is virtual
            StorageHADomain dm = this.getDataMover(args.getFs());
            if (dm == null) {
                Exception e = new Exception("VNX File Export Failed Data Mover not found");
                throw VNXException.exceptions.createExportFailed("VNX File Export Failed Data Mover not found", e);
            }

            List<VNXFileExport> vnxExports = getVNXFileExports(newExpList);
            if (args.getFileOperation()) {
                // Perform FileSystem export
                result = vnxComm.doExport(storage, dm, vnxExports, newPaths, args.getFileObj(), args.getFsNativeId(), firstExport);

            } else {
                // perform Snapshot export
                result = vnxComm.doExport(storage, dm, vnxExports, newPaths, args.getFileObj(), args.getSnapNativeId(), firstExport);
            }

            if (result.isCommandSuccess()) {
                curExpList.putAll(newExpList);
            }
        } catch (VNXException e) {
            throw VNXException.exceptions.createExportFailed("VNX File Export Failed", e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToExportFileSystem(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doUnexport(StorageSystem storage, FileDeviceInputOutput args, List<FileExport> exportList)
            throws ControllerException {
        _log.info("doUnExport " + args.getOperationType());
        _log.info("Call FileShare UnExport");

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            for (int expCount = 0; expCount < exportList.size(); expCount++) {
                List<String> endPoints = new ArrayList<String>();
                FileExport export = exportList.get(expCount);
                String exportEntryKey = FileExport.exportLookupKey(export.getProtocol(),
                        export.getSecurityType(), export.getPermissions(), export.getRootUserMapping(),
                        export.getPath());
                FileExport fileExport = args.getFileObjExports().get(exportEntryKey);
                if (fileExport != null) {
                    endPoints.addAll(fileExport.getClients());
                }
                export.setClients(endPoints);
                _log.info("FileExport:" + export.getClients() + ":" + export.getStoragePortName()
                        + ":" + export.getStoragePort() + ":" + export.getRootUserMapping()
                        + ":" + export.getPermissions() + ":" + export.getProtocol()
                        + ":" + export.getSecurityType() + ":" + export.getMountPoint()
                        + ":" + export.getPath());

            }

            List<VNXFileExport> vnxExps = getVNXFileExports(exportList);
            result = vnxComm.doUnexport(storage, vnxExps.get(0), args, false);
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToUnexportFileSystem(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doExpandFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        _log.info("Call FileSystem Expand");

        // For the request to succeed to expand, the system must be mounted.
        // Below snippet helps to verify whether FS Mounted or not.
        // Note : Since, Every Export will mount the FileSystem at first, we
        // should do cross check as below whether file system is already mounted or not
        // using FSExportMap and SMBShareMap

        // If FileShare mounted then Mount is not required

        boolean isMountRequired = !(args.isFileShareMounted());

        _log.info("Mount required or not, to expand requested FileSystem {}", isMountRequired);

        Long newFsExpandSize = args.getNewFSCapacity();

        if (args.getNewFSCapacity() % BYTESPERMB == 0) {
            newFsExpandSize = newFsExpandSize / BYTESPERMB;
        } else {
            newFsExpandSize = newFsExpandSize / BYTESPERMB + 1;
        }

        _log.info("FileSystem new size translation : {} : {}", args.getNewFSCapacity(), args.getFsCapacity());

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            result = vnxComm.expandFS(storage, args.getFsName(), newFsExpandSize, isMountRequired, args.getThinProvision());
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToExpandFileSystem(result.getMessage()));
        }

        return cmdResult;
    }

    @Override
    public BiosCommandResult doShare(StorageSystem storage, FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        _log.info("Call FileShare doShare");

        boolean firstExport = false;
        if (args.getFileObjShares() == null || args.getFileObjShares().isEmpty()) {
            args.initFileObjShares();
        }

        firstExport = !(args.isFileShareMounted());
        if (firstExport) {
            _log.debug("First export: no existing file obj shares");
        }

        if (!args.getFileOperation()) {
            firstExport = false;
        }

        String portName = smbFileShare.getPortGroup();
        String path = smbFileShare.getPath();
        if (path == null || path.length() == 0) {
            if (args.getFileOperation()) {
                path = args.getFsMountPath();
            } else {
                path = args.getSnapshotMountPath();
            }
        }

        _log.debug("Data Mover: {}", portName);
        _log.debug("Mount path: {}", path);

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            List<String> clients = new ArrayList<String>();
            VNXFileExport fileExport = new VNXFileExport(clients,
                    portName,
                    path,
                    "",                // no security type
                    smbFileShare.getPermission(),
                    "",                // root user mapping n/a for CIFS
                    VNXFileSshApi.VNX_CIFS,
                    "",          // Port information is never used for for CIFS or NFS exports.
                    "",           // SUB DIR
                    ""); // Comments -- TODO

            fileExport.setExportName(smbFileShare.getName());
            fileExport.setComment(smbFileShare.getDescription());
            fileExport.setMaxUsers(Integer.toString(smbFileShare.getMaxUsers()));

            List<VNXFileExport> vnxExports = new ArrayList<VNXFileExport>();
            vnxExports.add(fileExport);

            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }

            // Get DataMover
            StorageHADomain dm = this.getDataMover(args.getFs());
            if (dm == null) {
                Exception e = new Exception("VNX File Share creation Failed Data Mover not found");
                throw VNXException.exceptions.createExportFailed("VNX File Share creation Failed Data Mover not found", e);
            }

            List<String> paths = new ArrayList<String>();
            paths.add(path);

            if (args.getFileOperation()) {
                // Perform FileSystem export
                result = vnxComm.doExport(storage, dm, vnxExports, paths, args.getFileObj(), args.getFsNativeId(), firstExport);

            } else {
                // perform Snapshot export
                result = vnxComm.doExport(storage, dm, vnxExports, paths, args.getFileObj(), args.getSnapNativeId(), firstExport);
            }

            if ((result != null) && (result.isCommandSuccess())) {
                // Set MountPoint
                smbFileShare.setMountPoint(fileExport.getNetBios(), smbFileShare.getStoragePortNetworkId(),
                        smbFileShare.getStoragePortName(), smbFileShare.getName());
                args.getFileObjShares().put(smbFileShare.getName(), smbFileShare);
            }

        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } catch (NumberFormatException e) {
            // Placeholder until real handling is determined.
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToCreateFileShare(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doDeleteShare(StorageSystem storage, FileDeviceInputOutput args, SMBFileShare smbFileShare)
            throws ControllerException {
        _log.info("Call FileShare doDeleteShare");

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            StorageHADomain dm = null;
            String mountPoint = null;

            if (args.getFileOperation()) {
                mountPoint = args.getFs().getMountPath();
                // Get DataMover
                dm = this.getDataMover(args.getFs());
                if (dm == null) {
                    Exception e = new Exception("VNX File Share creation Failed Data Mover not found");
                    throw VNXException.exceptions.createExportFailed("VNX File Delete Share Failed Data Mover not found", e);
                }
            } else {
                // Get DataMover
                URI snapshotId = args.getSnapshotId();
                Snapshot snapshot = _dbClient.queryObject(Snapshot.class, snapshotId);
                FileShare fileshare = _dbClient.queryObject(FileShare.class, snapshot.getParent().getURI());
                mountPoint = fileshare.getMountPath();
                dm = this.getDataMover(fileshare);
                if (dm == null) {
                    Exception e = new Exception("VNX File Share creation Failed Data Mover not found");
                    throw VNXException.exceptions.createExportFailed("VNX File Delete Share Failed Data Mover not found", e);
                }
            }

            result = vnxComm.doDeleteShare(storage, dm, smbFileShare.getName(), mountPoint, false, args);

            args.getFileObjShares().remove(smbFileShare.getName());
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToDeleteFileShare(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doDeleteShares(StorageSystem storage, FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(false);
        result.setCommandStatus(Operation.Status.error.name());
        result.setMessage("SMB sharing is not supported.");
        return result;
    }

    @Override
    public BiosCommandResult doModifyFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BiosCommandResult doSnapshotFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        // generate checkpoint baseline name
        args.setSnaphotCheckPointBaseline(args.getSnapshotName() + "_baseline");
        args.setSnapshotMountPath("/" + args.getSnapshotName());
        _log.info("FileShare, Snapshot {} {}", args.getFsUUID(), args.getSnapshotId());
        _log.info("FSName: {}", args.getFsName());
        _log.info("SnapShotName: {}", args.getSnapshotName());

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            FileShare fileShare = args.getFs();
            result = vnxComm.createSnapshot(storage, args.getFsName(), args.getSnapshotName(), fileShare);
            _log.info("createSnapshot call result : {}", result.isCommandSuccess());
            if (result.isCommandSuccess()) {
                VNXSnapshot vnxSnap = (VNXSnapshot) result.getObject();
                args.setSnapNativeId(String.valueOf(vnxSnap.getId()));
                String path = "/" + args.getSnapshotName();
                // Set path & mountpath
                args.setSnapshotMountPath(path);
                args.setSnapshotPath(path);
            }
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }
        _log.info("Status of the result {}", (result != null) ? result.isCommandSuccess() : result);

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToCreateFileSnapshot(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doRestoreFS(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        _log.info("FileShare, Snapshot {} {}", args.getFsUUID(), args.getSnapshotId());
        _log.info("FSName: {}", args.getFsName());
        _log.info("SnapShotName: {}", args.getSnapshotName());
        int snapNativeId = 0;
        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            result = vnxComm.doRestoreSnapshot(storage, args.getFsName(), args.getSnapNativeId(), args.getSnapshotName());
            _log.info("restoreSnapshot call result : {}", result.isCommandSuccess());

        } catch (NumberFormatException ne) {
            // Call failed
            result = new XMLApiResult();
            result.setCommandFailed();
            result.setMessage("Not valid snapshot Id " + args.getSnapNativeId());
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }
        _log.info("restoreSnapshot call result : {}", result.isCommandSuccess());

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToRestoreFileSystem(result.getMessage()));
        }
        return cmdResult;
    }

    // Get FS snapshot list from the array
    @Override
    public BiosCommandResult getFSSnapshotList(StorageSystem storage,
            FileDeviceInputOutput args, List<String> snapshots)
            throws ControllerException {

        // TODO: Implement method
        String op = "getFSSnapshotList";
        String devType = storage.getSystemType();
        BiosCommandResult result = BiosCommandResult
                .createErrorResult(DeviceControllerException.errors.unsupportedOperationOnDevType(op, devType));

        return result;
    }

    private List<VNXFileExport> getVNXFileExports(FSExportMap existingExps) {

        List<VNXFileExport> vnxExports = new ArrayList<VNXFileExport>();
        if (existingExps != null && !existingExps.isEmpty()) {
            for (FileExport cur : existingExps.values()) {
                _log.debug("Added export sec, perm {} {}", cur.getSecurityType(), cur.getPermissions());
                _log.debug("             anon,path {} {}", cur.getRootUserMapping(), cur.getPath());

                VNXFileExport vnxExp = new VNXFileExport(cur.getClients(),
                        cur.getStoragePortName(), cur.getPath(),
                        cur.getSecurityType(), cur.getPermissions(),
                        cur.getRootUserMapping(), cur.getProtocol(),
                        cur.getStoragePort(), cur.getSubDirectory(), cur.getComments());
                vnxExports.add(vnxExp);
            }
        }

        return vnxExports;
    }

    private List<VNXFileExport> getVNXFileExports(List<FileExport> exports) {

        List<VNXFileExport> vnxExports = new ArrayList<VNXFileExport>();
        for (FileExport exp : exports) {
            _log.debug("Added export sec, perm {} {}", exp.getSecurityType(), exp.getPermissions());
            _log.debug("             anon,path {} {}", exp.getRootUserMapping(), exp.getPath());

            VNXFileExport vnxExp = new VNXFileExport(exp.getClients(), exp.getStoragePortName(), exp.getPath(), exp.getSecurityType(),
                    exp.getPermissions(), exp.getRootUserMapping(), exp.getProtocol(), exp.getStoragePort(), exp.getSubDirectory(),
                    exp.getComments());

            vnxExports.add(vnxExp);
        }

        return vnxExports;
    }

    @Override
    public BiosCommandResult doDeleteSnapshot(StorageSystem storage, FileDeviceInputOutput args)
            throws ControllerException {
        // generate checkpoint baseline name
        _log.info("FileShare, Snapshot {} {}", args.getFsUUID(), args.getSnapshotId());
        _log.info("FSName: {}", args.getFsName());
        _log.info("SnapShotName: {}", args.getSnapshotName());
        _log.info("SnapShotBaseline: {}", args.getSnapshotCheckPointBaseline());

        XMLApiResult result = null;
        ApplicationContext context = null;
        try {
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            result = vnxComm.doDeleteSnapshot(storage, args.getSnapNativeId(), args.getSnapshotName(), true);
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToDeleteFileSnapshot(result.getMessage()));
        }
        return cmdResult;
    }

    @Override
    public void doConnect(StorageSystem storage) {

    }

    @Override
    public void doDisconnect(StorageSystem storage) {
        // FIX ME

    }

    @Override
    public BiosCommandResult getPhysicalInventory(StorageSystem storage) {
        BiosCommandResult result = new BiosCommandResult();
        result.setCommandSuccess(true);
        return result;
    }

    private StorageHADomain getDataMover(FileShare fileShare) {
        StoragePort port = _dbClient.queryObject(StoragePort.class, fileShare.getStoragePort());
        StorageHADomain dm = null;
        if (port != null) {
            dm = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
        }
        return dm;
    }

    @Override
    public BiosCommandResult deleteExportRules(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {

        BiosCommandResult biosResult = new BiosCommandResult();
        biosResult.setCommandSuccess(true);
        biosResult.setCommandStatus(Operation.Status.ready.name());

        List<ExportRule> allExports = args.getExistingDBExportRules();
        String subDir = args.getSubDirectory();
        boolean allDirs = args.isAllDir();

        String exportPath;
        String subDirExportPath = "";

        if (!args.getFileOperation()) {
            exportPath = args.getSnapshotPath();
            if (subDir != null && subDir.length() > 0) {
                subDirExportPath = args.getSnapshotPath() + "/" + subDir;
            }

        } else {
            exportPath = args.getFs().getPath();
            if (subDir != null && subDir.length() > 0) {
                subDirExportPath = args.getFs().getPath() + "/" + subDir;
            }
        }

        Set<String> allPaths = new HashSet<String>();
        try {

            if (allDirs) {
                // ALL EXPORTS
                _log.info("Deleting all exports specific to filesystem at device and rules from DB including sub dirs rules and exports");
                for (ExportRule rule : allExports) {
                    allPaths.add(rule.getExportPath());
                }

            } else if (subDir != null && !subDir.isEmpty()) {
                // Filter for a specific Sub Directory export
                _log.info(
                        "Deleting all subdir exports rules at ViPR and sub directory export at device {}",
                        subDir);
                for (ExportRule rule : allExports) {
                    if (rule.getExportPath().endsWith("/" + subDir)) {
                        allPaths.add(subDirExportPath);
                        break;
                    }
                }
            } else {
                // Filter for No SUBDIR - main export rules with no sub dirs
                _log.info("Deleting all export rules  from DB and export at device not included sub dirs");
                allPaths.add(exportPath);
            }
            _log.info("Number of Exports to Delete : {}", allPaths.size());
            Map<String, Boolean> operationResult = new HashMap<String, Boolean>();

            XMLApiResult result = null;
            ApplicationContext context = null;
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }

            for (String exportPathToDelete : allPaths) {
                _log.info("Deleting Export Path {}", exportPathToDelete);
                try {
                    result = vnxComm.doDeleteExport(storage, exportPathToDelete, args, false);
                    if (result.isCommandSuccess()) {
                        _log.info("Export Deleted : {}", exportPathToDelete);
                        operationResult.put(exportPathToDelete, true);
                    } else {
                        _log.error("Error deleting Export : {}", exportPathToDelete);
                        operationResult.put(exportPathToDelete, false);
                        biosResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToUnexportFileSystem(result
                                .getMessage()));
                    }
                } catch (Exception e) {
                    _log.error("Error deleting Export : {}", exportPathToDelete);
                    operationResult.put(exportPathToDelete, false);
                    biosResult = BiosCommandResult
                            .createErrorResult(DeviceControllerErrors.vnx.unableToUnexportFileSystem(result.getMessage()));
                }
            }

        } catch (VNXException e) {
            _log.error("Exception:" + e.getMessage());
            throw new DeviceControllerException(
                    "Exception while performing export for {0} ",
                    new Object[] { args.getFsId() });
        }

        _log.info("VNXFileStorageDevice deleteExportRules {} - complete", args.getFsId());

        return biosResult;

    }

    @Override
    public BiosCommandResult doCreateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory quotaDir) throws ControllerException {

        BiosCommandResult result = new BiosCommandResult();
        ApplicationContext context = null;
        XMLApiResult apiResult = null;
        try {
            _log.info("VNXFileStorageDeviceXML doCreateQuotaDirectory - start");

            String fsName = args.getFsName();
            String quotaTreetreeName = args.getQuotaDirectoryName();
            Boolean oplocks = quotaDir.getOpLock();
            String securityStyle = quotaDir.getSecurityStyle();
            Long size = quotaDir.getSize();

            if (null == fsName) {
                _log.error("VNXFileStorageDeviceXML::doCreateQuotaDirectory failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.vnx.unableToCreateQuotaDir();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == quotaTreetreeName) {
                _log.error("VNXFileStorageDeviceXML::doCreateQuotaDirectory failed:  Quota Tree name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.vnx.unableToCreateQuotaDir();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            _log.info("FSName: {}", args.getFsName());
            _log.info("Quota tree name: {}", args.getQuotaDirectoryName());

            boolean isMountRequired = !(args.isFileShareMounted());
            _log.info("Mount required or not, to create quota dir requested {}", isMountRequired);

            // Load the context
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            // quota directory create/update takes size in MB as similar to FS create.
            Long sizeMBs = size / BYTESPERMB;
            apiResult = vnxComm.createQuotaDirectory(storage, args.getFsName(), quotaTreetreeName, securityStyle, sizeMBs, oplocks,
                    isMountRequired);
            _log.info("createQuotaDirectory call result : {}", apiResult.isCommandSuccess());
            if (apiResult.isCommandSuccess()) {
                VNXQuotaTree quotaTree = (VNXQuotaTree) apiResult.getObject();
                args.getQuotaDirectory().setNativeId(String.valueOf(quotaTree.getId()));
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }
        _log.info("Status of the result {}", (result != null) ? result.isCommandSuccess() : result);

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToCreateQuotaDir());
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doDeleteQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ApplicationContext context = null;
        XMLApiResult apiResult = null;
        try {
            _log.info("VNXFileStorageDeviceXML doDeleteQuotaDirectory - start");

            String fsName = args.getFsName();
            String quotaTreetreeName = args.getQuotaDirectoryName();
            QuotaDirectory quotaDir = args.getQuotaDirectory();

            if (null == fsName) {
                _log.error("VNXFileStorageDeviceXML::doDeleteQuotaDirectory failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.vnx.unableToDeleteQuotaDir();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == quotaTreetreeName) {
                _log.error("VNXFileStorageDeviceXML::doDeleteQuotaDirectory failed:  Quota Tree name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.vnx.unableToDeleteQuotaDir();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            _log.info("FSName: {}", args.getFsName());
            _log.info("Quota tree name: {}", args.getQuotaDirectoryName());

            boolean isMountRequired = !(args.isFileShareMounted());

            _log.info("Mount required or not, to delete quota dir requested {}", isMountRequired);

            // Load the context
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }

            // we can't delete quota, if the filsystem is not mount, We should changes quota cmd
            apiResult = vnxComm.deleteQuotaDirectory(storage, args.getFsName(), quotaTreetreeName, true, isMountRequired);
            if (apiResult.isCommandSuccess()) {
                result = BiosCommandResult.createSuccessfulResult();
            }
            _log.info("doDeleteQuotaDirectory call result : {}", apiResult.isCommandSuccess());
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToDeleteQuotaDir());
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult doUpdateQuotaDirectory(StorageSystem storage,
            FileDeviceInputOutput args, QuotaDirectory quotaDir) throws ControllerException {
        BiosCommandResult result = new BiosCommandResult();
        ApplicationContext context = null;
        XMLApiResult apiResult = null;
        try {
            _log.info("VNXFileStorageDeviceXML doUpdateQuotaDirectory - start");

            String fslName = args.getFsName();
            String quotaTreetreeName = args.getQuotaDirectoryName();
            Boolean oplocks = quotaDir.getOpLock();
            String securityStyle = quotaDir.getSecurityStyle();
            Long size = quotaDir.getSize();

            if (null == fslName) {
                _log.error("VNXFileStorageDeviceXML::doUpdateQuotaDirectory failed:  Filesystem name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.vnx.unableToUpdateQuotaDir();
                serviceError.setMessage(FileSystemConstants.FS_ERR_FS_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            if (null == quotaTreetreeName) {
                _log.error("VNXFileStorageDeviceXML::doUpdateQuotaDirectory failed:  Quota Tree name is either missing or empty");
                ServiceError serviceError = DeviceControllerErrors.vnx.unableToUpdateQuotaDir();
                serviceError.setMessage(FileSystemConstants.FS_ERR_QUOTADIR_NAME_MISSING_OR_EMPTY);
                result = BiosCommandResult.createErrorResult(serviceError);
                return result;
            }

            _log.info("FSName: {}", args.getFsName());
            _log.info("Quota tree name: {}", args.getQuotaDirectoryName());

            boolean isMountRequired = !(args.isFileShareMounted());
            _log.info("Mount required or not, to update quota dir requested {}", isMountRequired);

            // Load the context
            context = loadContext();
            VNXFileCommApi vnxComm = loadVNXFileCommunicationAPIs(context);
            if (null == vnxComm) {
                throw VNXException.exceptions.communicationFailed(VNXCOMM_ERR_MSG);
            }
            // quota directory create/update takes size in MB as similar to FS create.
            Long sizeMBs = size / BYTESPERMB;

            apiResult = vnxComm.modifyQuotaDirectory(storage, args.getFsName(), quotaTreetreeName, securityStyle, sizeMBs, oplocks,
                    isMountRequired);
            _log.info("doUpdateQuotaDirectory call result : {}", apiResult.isCommandSuccess());
            if (apiResult.isCommandSuccess()) {
                VNXQuotaTree quotaTree = (VNXQuotaTree) apiResult.getObject();
                args.getQuotaDirectory().setNativeId(String.valueOf(quotaTree.getId()));
                result = BiosCommandResult.createSuccessfulResult();
            }
        } catch (VNXException e) {
            throw new DeviceControllerException(e);
        } finally {
            clearContext(context);
        }

        BiosCommandResult cmdResult = null;
        if (result.isCommandSuccess()) {
            cmdResult = BiosCommandResult.createSuccessfulResult();
        } else {
            cmdResult = BiosCommandResult.createErrorResult(DeviceControllerErrors.vnx.unableToUpdateQuotaDir());
        }
        return cmdResult;
    }

    @Override
    public BiosCommandResult updateShareACLs(StorageSystem storage,
            FileDeviceInputOutput args) {

        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnx.operationNotSupported());

    }

    @Override
    public BiosCommandResult deleteShareACLs(StorageSystem storageObj,
            FileDeviceInputOutput args) {

        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnx.operationNotSupported());
    }

    @Override
    public BiosCommandResult updateNfsACLs(StorageSystem storage, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnx.operationNotSupported());
    }

    @Override
    public BiosCommandResult deleteNfsACLs(StorageSystem storageObj, FileDeviceInputOutput args) {
        return BiosCommandResult.createErrorResult(
                DeviceControllerErrors.vnx.operationNotSupported());
    }
}
