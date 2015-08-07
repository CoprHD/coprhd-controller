/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.model.file.ExportRule;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeNfsShare;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;

public class VNXeModifyExportJob extends VNXeJob {
    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeModifyExportJob.class);
    private boolean isFile;
    private boolean isDeleteRule;
    private ExportRule rule;
    private String exportPath;
    private FileShareExport exportInfo;
    private String shareName;

    public VNXeModifyExportJob(String jobId, URI storageSystemUri,
            TaskCompleter taskCompleter, ExportRule rule, FileShareExport exportInfo, String exportPath, boolean isFile,
            boolean isDeleteRule, String shareName) {

        super(jobId, storageSystemUri, taskCompleter, "updateExportRules");

        this.isFile = isFile;
        this.isDeleteRule = isDeleteRule;
        this.rule = rule;
        this.exportPath = exportPath;
        this.exportInfo = exportInfo;
        this.shareName = shareName;
    }

    /**
     * Called to update the job status when the export file system job completes.
     * 
     * @param jobContext The job context.
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {

        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }

            VNXeApiClient vnxeApiClient = getVNXeClient(jobContext);

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));

            FileShare fsObj = null;
            Snapshot snapObj = null;
            URI objId = getTaskCompleter().getId();
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, getStorageSystemUri());
            if (_status == JobStatus.SUCCESS) {
                _isSuccess = true;
                FileExport newExport = null;
                if (exportInfo != null) {
                    newExport = exportInfo.getFileExport();
                }
                VNXeNfsShare nfsShare = null;
                if (isFile) {
                    fsObj = dbClient.queryObject(FileShare.class, objId);
                    nfsShare = vnxeApiClient.getNfsShareById(rule.getDeviceExportId());
                    // nfsShare = vnxeApiClient.findNfsShare(fsObj.getNativeId(), shareName);
                    updateExportRules(vnxeApiClient, dbClient, fsObj, nfsShare);
                    if (newExport != null) {
                        updateFSExport(fsObj, dbClient, newExport);
                    }
                } else {
                    snapObj = dbClient.queryObject(Snapshot.class, objId);
                    fsObj = dbClient.queryObject(FileShare.class, snapObj.getParent().getURI());
                    nfsShare = vnxeApiClient.findSnapNfsShare(snapObj.getNativeId(), shareName);
                    updateExportRules(vnxeApiClient, dbClient, fsObj, nfsShare);
                    if (newExport != null) {
                        updateSnapshotExport(snapObj, dbClient, newExport);
                    }
                }
            } else if (_status == JobStatus.FAILED) {
                // cleanupFSExport(fsObj, dbClient);
                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to update export rules: %s", opId, objId.toString()));
            }
            _logger.info(logMsgBuilder.toString());
            if (isFile) {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SYSTEM,
                        _isSuccess, logMsgBuilder.toString(), "", fsObj, storageObj);
            } else {
                FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.UPDATE_EXPORT_RULES_FILE_SNAPSHOT,
                        _isSuccess, logMsgBuilder.toString(), "", snapObj, fsObj, storageObj);
            }
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for VNXeModifyExportJob", e);
            setErrorStatus("Encountered an internal error during update export rules job status processing : " + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }

    /**
     * update FileShare after exported in VNXe
     * 
     * @param fsOjb fileShare object in vipr
     * @param dbClient DbClient
     * @param vnxeApiClient VNXeApiClient
     */
    private void updateExportRules(VNXeApiClient vnxeApiClient, DbClient dbClient, FileShare fileObj, VNXeNfsShare nfsShare) {
        _logger.info("updating file export. ");
        try {
            // Modify Existing Exports
            FileExportRule newRule = new FileExportRule();
            URI snapshotId = null;
            if (!isFile) {
                snapshotId = getTaskCompleter().getId();
            }
            // Copy the properties to build the index id to query DB for existing Export Rule
            copyPropertiesToSave(newRule, rule, fileObj, dbClient, snapshotId);
            newRule = getAvailableExportRule(newRule, dbClient);

            // Remove the existing and create the new one.
            // Don't Update the existing one as persist object will create a new StringSet rather
            // it updates the existing one with new information and upon keeping/appending to old one.
            if (newRule != null) {
                newRule.setInactive(true);
                _logger.info("Removing Existing DB Export Rule {}", rule);
                dbClient.persistObject(newRule);
            }

            if (!isDeleteRule) {
                newRule = new FileExportRule();
                newRule.setId(URIUtil.createId(FileExportRule.class));

                if (nfsShare != null) {
                    if (nfsShare.getReadOnlyHosts() != null) {
                        Set<String> hosts = new HashSet<String>();
                        for (VNXeBase hostId : nfsShare.getReadOnlyHosts()) {
                            hosts.add(vnxeApiClient.getHostById(hostId.getId()).getName());
                        }
                        rule.setReadOnlyHosts(hosts);
                    }
                    if (nfsShare.getReadWriteHosts() != null) {
                        Set<String> hosts = new HashSet<String>();
                        for (VNXeBase hostId : nfsShare.getReadWriteHosts()) {
                            hosts.add(vnxeApiClient.getHostById(hostId.getId()).getName());
                        }
                        rule.setReadWriteHosts(hosts);
                    }
                    if (nfsShare.getRootAccessHosts() != null) {
                        Set<String> hosts = new HashSet<String>();
                        for (VNXeBase hostId : nfsShare.getRootAccessHosts()) {
                            hosts.add(vnxeApiClient.getHostById(hostId.getId()).getName());
                        }
                        rule.setRootHosts(hosts);
                    }
                }
                // Now, Copy the properties again into the rule came out of DB, before updating.
                copyPropertiesToSave(newRule, rule, fileObj, dbClient, snapshotId);

                _logger.info("Storing New DB Export Rule {}", newRule);
                dbClient.createObject(newRule);
            }

        } catch (Exception e) {
            _logger.info("Error While executing CRUD Operations {}", e);
        }
    }

    private void updateFSExport(FileShare fsObj, DbClient dbClient, FileExport newExport) {
        _logger.info("updating file export. ");
        FSExportMap exports = fsObj.getFsExports();
        if (exports == null) {
            exports = new FSExportMap();
        }

        FileExport exportToBeUpdated = exports.get(newExport.getFileExportKey());
        if (exportToBeUpdated != null) {
            List<String> clients = new ArrayList<String>();
            if (rule.getReadOnlyHosts() != null) {
                clients.addAll(rule.getReadOnlyHosts());
            }
            if (rule.getReadWriteHosts() != null) {
                clients.addAll(rule.getReadWriteHosts());
            }
            if (rule.getRootHosts() != null) {
                clients.addAll(rule.getRootHosts());
            }
            exportToBeUpdated.setClients(clients);
            exports.put(newExport.getFileExportKey(), exportToBeUpdated);
            fsObj.setFsExports(exports);
            dbClient.persistObject(fsObj);
        }
    }

    private void updateSnapshotExport(Snapshot snapObj, DbClient dbClient, FileExport newExport) {
        _logger.info("updating file export. ");
        FSExportMap exports = snapObj.getFsExports();
        if (exports == null) {
            exports = new FSExportMap();
        }

        FileExport exportToBeUpdated = exports.get(newExport.getFileExportKey());
        if (exportToBeUpdated != null) {
            List<String> clients = new ArrayList<String>();
            if (rule.getReadOnlyHosts() != null) {
                clients.addAll(rule.getReadOnlyHosts());
            }
            if (rule.getReadWriteHosts() != null) {
                clients.addAll(rule.getReadWriteHosts());
            }
            if (rule.getRootHosts() != null) {
                clients.addAll(rule.getRootHosts());
            }
            exportToBeUpdated.setClients(clients);
            exports.put(newExport.getFileExportKey(), exportToBeUpdated);
            snapObj.setFsExports(exports);
            dbClient.persistObject(snapObj);
        }
    }

    private FileExportRule getAvailableExportRule(FileExportRule exportRule, DbClient dbClient)
            throws URISyntaxException {

        String exportIndex = exportRule.getFsExportIndex();
        if (!isFile) {
            exportIndex = exportRule.getSnapshotExportIndex();
        }

        _logger.info("Retriving DB Model using its index {}", exportIndex);
        FileExportRule rule = null;
        URIQueryResultList result = new URIQueryResultList();

        if (!isFile) {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getSnapshotExportRuleConstraint(exportIndex), result);
        } else {
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getFileExportRuleConstraint(exportIndex), result);
        }

        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            if (result.iterator().hasNext()) {
                rule = dbClient.queryObject(FileExportRule.class, it.next());
                if (rule != null && !rule.getInactive()) {
                    _logger.info("Existing DB Model found {}", rule);
                    break;
                }
            }
        }

        return rule;

    }

    private void copyPropertiesToSave(FileExportRule dest, ExportRule orig,
            FileShare fs, DbClient dbClient, URI snapshotId) {

        _logger.info("Origin {}", orig.toString());

        // This export path is the one that is figured out at the device.
        // Make sure you set the path on args object while doing the operation. Check for <Device>FileStorageDeviceXXX.java
        dest.setExportPath(exportPath);
        dest.setSecFlavor(orig.getSecFlavor());
        dest.setAnon(orig.getAnon());
        if (orig.getReadOnlyHosts() != null && !orig.getReadOnlyHosts().isEmpty()) {
            dest.setReadOnlyHosts(new StringSet(orig.getReadOnlyHosts()));
            _logger.info("Read Only Hosts {}", dest.getReadOnlyHosts());
        }
        if (orig.getReadWriteHosts() != null && !orig.getReadWriteHosts().isEmpty()) {
            dest.setReadWriteHosts(new StringSet(orig.getReadWriteHosts()));
            _logger.info("Read Write Hosts {}", dest.getReadWriteHosts());
        }
        if (orig.getRootHosts() != null && !orig.getRootHosts().isEmpty()) {
            dest.setRootHosts(new StringSet(orig.getRootHosts()));
            _logger.info("Root hosts {}", dest.getRootHosts());
        }

        // Set this always at the end -- Thats how the model is defined.
        if (!isFile) {
            dest.setSnapshotId(snapshotId);
        } else {
            dest.setFileSystemId(fs.getId());
        }

        // Figure out Storage Port Network id to build the mount point.
        StoragePort storagePort = dbClient.queryObject(StoragePort.class, fs.getStoragePort());
        String mountPoint = ExportUtils.getFileMountPoint(storagePort.getPortNetworkId(), exportPath);
        dest.setMountPoint(mountPoint);

        dest.setDeviceExportId(orig.getDeviceExportId());
        _logger.info("Dest After {}", dest.toString());
    }
}
