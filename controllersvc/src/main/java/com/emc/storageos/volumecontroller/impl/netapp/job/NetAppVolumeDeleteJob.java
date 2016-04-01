package com.emc.storageos.volumecontroller.impl.netapp.job;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsShareACL;
import com.emc.storageos.db.client.model.FileExportRule;
import com.emc.storageos.db.client.model.FileObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.SchedulePolicy;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StatTimeSeries;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.netapp.NetappApiFactory;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.FileDeviceController;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

public class NetAppVolumeDeleteJob extends Job implements Serializable {

    private static final Logger _logger = LoggerFactory.getLogger(NetAppVolumeDeleteJob.class);
    private static final long ERROR_TRACKING_LIMIT = 60 * 1000; // tracking limit for transient errors. set for 2 hours

    private String _jobName;
    private URI _storageSystemUri;
    private TaskCompleter _taskCompleter;
    private List<String> _jobIds = new ArrayList<String>();
    private boolean forceDelete;

    private long _error_tracking_time = 0L;
    private JobStatus _status = JobStatus.IN_PROGRESS;

    private JobPollResult _pollResult = new JobPollResult();
    private String _errorDescription = null;

    public NetAppVolumeDeleteJob(String jobId, URI storageSystemUri, boolean forcedelete, TaskCompleter taskCompleter) {
        this._storageSystemUri = storageSystemUri;
        this._taskCompleter = taskCompleter;
        this._jobName = "deleteFileSystemJob";
        this.forceDelete = forcedelete;
        this._jobIds.add(jobId);
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        String currentJob = _jobIds.get(0);
        try {
            NetAppApi netAppApi = getNetappApi(jobContext);
            if (netAppApi == null) {
                String errorMessage = "No NetApp API found for: " + _storageSystemUri;
                processTransientError(currentJob, trackingPeriodInMillis, errorMessage, null);
            } else {
                _pollResult.setJobName(_jobName);
                _pollResult.setJobId(_taskCompleter.getOpId());

                boolean offline = netAppApi.isVolumeOffline(currentJob);
                if (offline) {
                    netAppApi.destroyFS(currentJob);
                    _status = JobStatus.SUCCESS;
                    _pollResult.setJobPercentComplete(100);
                    _logger.info("Volume deleted successfully: {}", currentJob);
                } else {
                    _logger.info("Polling to delete Netapp volume: {}", currentJob);
                    _status = JobStatus.IN_PROGRESS;
                }

            }
        } catch (Exception e) {
            processTransientError(currentJob, trackingPeriodInMillis, e.getMessage(), e);
        } finally {
            try {
                updateStatus(jobContext);
            } catch (Exception e) {
                setErrorStatus(e.getMessage());
                _logger.error("Problem while trying to update status", e);
            }
        }
        _pollResult.setJobStatus(_status);

        return _pollResult;
    }

    @Override
    public TaskCompleter getTaskCompleter() {
        return _taskCompleter;
    }

    public void updateStatus(JobContext jobContext) throws Exception {

        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format("Updating status of job %s to %s", opId, _status.name()));

            URI fsId = getTaskCompleter().getId();
            FileShare fsObj = dbClient.queryObject(FileShare.class, fsId);
            StorageSystem storageObj = dbClient.queryObject(StorageSystem.class, _storageSystemUri);

            if (_status == JobStatus.SUCCESS && fsObj != null) {
                deleteFSFromDB(dbClient, fsObj, forceDelete);

                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s succeeded to delete file system: %s", opId, fsId.toString()));

            } else if (_status == JobStatus.FAILED && fsObj != null) {

                logMsgBuilder.append("\n");
                logMsgBuilder.append(String.format(
                        "Task %s failed to delete file system: %s", opId, fsId.toString()));

            } else {
                logMsgBuilder.append(String.format("The file system: %s is not found anymore", fsId));
            }
            _logger.info(logMsgBuilder.toString());
            FileDeviceController.recordFileDeviceOperation(dbClient, OperationTypeEnum.DELETE_FILE_SYSTEM, _status == JobStatus.SUCCESS,
                    "", "", fsObj,
                    storageObj);
        } catch (Exception e) {
            _logger.error("Caught an exception while trying to updateStatus for NetAppVolumeDeleteJob", e);
            setErrorStatus("Encountered an internal error during file system delete job status processing : " + e.getMessage());
        } finally {
            if (_status == JobStatus.SUCCESS) {
                _taskCompleter.ready(dbClient);
            } else if (_status == JobStatus.FAILED || _status == JobStatus.FATAL_ERROR) {
                ServiceError error = DeviceControllerErrors.netapp.jobFailed(_errorDescription);
                _taskCompleter.error(jobContext.getDbClient(), error);
            }
        }
    }

    public void setErrorStatus(String errorDescription) {
        _status = JobStatus.FATAL_ERROR;
        _errorDescription = errorDescription;
    }

    public void setErrorTrackingTime(long trackingTime) {
        _error_tracking_time = trackingTime;
    }

    /**
     * Get Isilon API client
     * 
     * @param jobContext
     * @return
     */
    public NetAppApi getNetappApi(JobContext jobContext) {
        StorageSystem device = jobContext.getDbClient().queryObject(StorageSystem.class, _storageSystemUri);
        NetappApiFactory factory = jobContext.getNetAppApiFactory();
        if (factory != null) {
            NetAppApi netAppApi = factory.getClient(device.getIpAddress(), device.getPortNumber(),
                    device.getUsername(), device.getPassword(), true, null);

            return netAppApi;
        }
        return null;
    }

    private void processTransientError(String jobId, long trackingInterval, String errorMessage, Exception ex) {
        _status = JobStatus.ERROR;
        _errorDescription = errorMessage;
        if (ex != null) {
            _logger.error(String.format("Error while processing SnapMirror Job - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status), ex);
        } else {
            _logger.error(String.format("Error while processing SnapMirror - Name: %s, ID: %s, Desc: %s Status: %s",
                    _jobName, jobId, _errorDescription, _status));
        }

        // Check if job tracking limit was reached. Set status to FAILED in such a case.
        setErrorTrackingTime(_error_tracking_time + trackingInterval);
        _logger.info(String.format("Tracking time of SnapMirror in transient error status - %s, Name: %s, ID: %s. Status %s .",
                _error_tracking_time, _jobName, jobId, _status));
        if (_error_tracking_time > ERROR_TRACKING_LIMIT) {
            _status = JobStatus.FATAL_ERROR;
            _logger.error(String.format("Reached tracking time limit for SnapMirror - Name: %s, ID: %s. Set status to %s .",
                    _jobName, jobId, _status));
        }
    }

    /**
     * Generate zero statistics record.
     * 
     * @param fsObj the file share object
     */
    private void generateZeroStatisticsRecord(DbClient dbClient, FileShare fsObj) {
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
            dbClient.insertTimeSeries(StatTimeSeries.class, zeroStatRecord);
        } catch (Exception e) {
            _logger.error("Zero Stat Record Creation failed for FileShare : {}", fsObj.getId(), e);
        }
    }

    private void doDeleteSnapshotsFromDB(DbClient dbClient, FileShare fs) {

        _logger.info(" Setting Snapshots to InActive with Force Delete ");
        URIQueryResultList snapIDList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getFileshareSnapshotConstraint(fs.getId()), snapIDList);
        _logger.info("getSnapshots: FS {}: {} ", fs.getId().toString(), snapIDList.toString());
        List<Snapshot> snapList = dbClient.queryObject(Snapshot.class, snapIDList);
        if (snapList != null && !snapList.isEmpty()) {
            for (Snapshot snapshot : snapList) {
                _logger.info("Marking Snapshot as InActive Snapshot Id {} Fs Id : {}", snapshot.getId(), snapshot.getParent());
                snapshot.setInactive(true);
                doDeleteExportRulesFromDB(dbClient, snapshot, false);
                deleteShareACLsFromDB(dbClient, snapshot, false);
                dbClient.updateObject(snapshot);
            }
        }
    }

    private List<QuotaDirectory> queryFileQuotaDirs(DbClient dbClient, FileShare fs) {
        _logger.info("Querying all quota directories Using FsId {}", fs.getId());
        try {
            ContainmentConstraint containmentConstraint = ContainmentConstraint.Factory
                    .getQuotaDirectoryConstraint(fs.getId());
            List<QuotaDirectory> fsQuotaDirs = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient,
                    QuotaDirectory.class, containmentConstraint);
            return fsQuotaDirs;
        } catch (Exception e) {
            _logger.error("Error while querying {}", e);
        }
        return null;
    }

    private void doFSDeleteQuotaDirsFromDB(DbClient dbClient, FileShare fs) {
        List<QuotaDirectory> quotaDirs = queryFileQuotaDirs(dbClient, fs);
        if (quotaDirs != null && !quotaDirs.isEmpty()) {
            _logger.info("Doing CRUD Operations on all DB QuotaDirectory for requested fs");
            for (QuotaDirectory dir : quotaDirs) {
                _logger.info("Deleting quota dir from DB - Dir :{}", dir);
                dir.setInactive(true);
                dbClient.updateObject(dir);
            }
        }
    }

    private void deleteShareACLsFromDB(DbClient dbClient, FileObject fs, boolean fileOperation) {
        List<CifsShareACL> acls = new ArrayList<CifsShareACL>();
        try {
            ContainmentConstraint containmentConstraint = null;
            if (fileOperation) {
                _logger.info("Querying DB for Share ACLs of filesystemId {} ",
                        fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileCifsShareAclsConstraint(fs.getId());

            } else {
                _logger.info("Querying DB for Share ACLs of share {} of snapshotId {} ",
                        fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotCifsShareAclsConstraint(fs.getId());
            }

            List<CifsShareACL> shareAclList = CustomQueryUtility.queryActiveResourcesByConstraint(
                    dbClient, CifsShareACL.class, containmentConstraint);

            Iterator<CifsShareACL> shareAclIter = shareAclList.iterator();
            while (shareAclIter.hasNext()) {
                CifsShareACL shareAcl = shareAclIter.next();
                shareAcl.setInactive(true);
                acls.add(shareAcl);
            }

            if (!shareAclList.isEmpty()) {
                dbClient.updateObject(shareAclList);
            }
        } catch (Exception e) {
            _logger.error("Error while querying DB for ACL(s) of a share {}", e);
        }
    }

    private void doDeleteExportRulesFromDB(DbClient dbClient, FileObject fs, boolean fileOperation) {
        List<FileExportRule> fileExportRules = null;
        try {
            ContainmentConstraint containmentConstraint;

            if (fileOperation) {
                _logger.info("Querying all ExportRules Using fileSystem Id {}", fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getFileExportRulesConstraint(fs.getId());
            } else {
                _logger.info("Querying all ExportRules Using Snapshot Id {}", fs.getId());
                containmentConstraint = ContainmentConstraint.Factory.getSnapshotExportRulesConstraint(fs.getId());
            }
            fileExportRules = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, FileExportRule.class,
                    containmentConstraint);

            if (fileExportRules != null && !fileExportRules.isEmpty()) {
                // ALl EXPORTS
                _logger.info("Doing CRUD Operations on all DB FileExportRules for requested fs");
                for (FileExportRule rule : fileExportRules) {
                    _logger.info("Deleting export rule from DB having path {} - Rule :{}", rule.getExportPath(), rule);
                    rule.setInactive(true);
                    dbClient.updateObject(rule);
                }
            }
        } catch (Exception e) {
            _logger.error("Error while querying {}", e);
        }
    }

    private void doDeletePolicyReferenceFromDB(DbClient dbClient, FileShare fs) {

        if (fs.getFilePolicies() != null && !fs.getFilePolicies().isEmpty()) {
            _logger.info("Removing policy reference for file system  " + fs.getName());
            for (String policy : fs.getFilePolicies()) {
                SchedulePolicy fp = dbClient.queryObject(SchedulePolicy.class, URI.create(policy));
                StringSet fsURIs = fp.getAssignedResources();
                fsURIs.remove(fs.getId().toString());
                fp.setAssignedResources(fsURIs);
                dbClient.updateObject(fp);

            }
        }

    }

    private void deleteFSFromDB(DbClient dbClient, FileShare fsObj, boolean forceDelete) {
        if (forceDelete) {
            doDeleteSnapshotsFromDB(dbClient, fsObj);  // Delete Snapshot and its references from DB
            doFSDeleteQuotaDirsFromDB(dbClient, fsObj);                   // Delete Quota Directory from DB
            deleteShareACLsFromDB(dbClient, fsObj, true);                       // Delete CIFS Share ACLs from DB
            doDeleteExportRulesFromDB(dbClient, fsObj, true);       // Delete Export Rules from DB
            doDeletePolicyReferenceFromDB(dbClient, fsObj);              // Remove FileShare Reference from Schedule Policy
        }

        fsObj.setInactive(true);
        generateZeroStatisticsRecord(dbClient, fsObj);
        dbClient.updateObject(fsObj);
    }

}
