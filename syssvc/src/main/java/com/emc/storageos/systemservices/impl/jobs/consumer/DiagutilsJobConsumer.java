/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.consumer;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DiagutilJobStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.management.backup.util.BackupClient;
import com.emc.storageos.management.backup.util.CifsClient;
import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.resource.DataCollectionService;
import com.emc.storageos.systemservices.impl.resource.LogService;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import com.emc.vipr.model.sys.diagutil.DiagutilsJob;
import com.emc.vipr.model.sys.diagutil.LogParam;
import com.emc.vipr.model.sys.diagutil.UploadParam;
import com.emc.vipr.model.sys.diagutil.UploadParam.*;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.media.jfxmedia.Media;
import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.*;

public class DiagutilsJobConsumer extends DistributedQueueConsumer<DiagutilsJob> {
    private static final Logger log = LoggerFactory.getLogger(DiagutilsJobConsumer.class);
    @Autowired
    private LogService logService;
    private static final String _OPT_DIR = "/opt/storageos/bin/";
    private static final String _DIAGUTIL = "diagutils";
    private static final String _DIAGUTIL_CMD = _OPT_DIR + "diagutils";
    private static final String _DIAGUTIL_GUI = "-gui";
    private static final String _DIAGUTIL_OUTPUT = "-output_dir";
    private static final String _DIAGUTIL_PRECHECK = "-pre_check";
    private static final String _DIAGUTIL_ARCHIVE = "-archive";
    public static final String _DIAGUTIL_COLLECT_DIR = "/data/diagutils-data/";
    private static final long COMMAND_TIMEOUT = 60 * 60 * 1000;

    private DataCollectionService dataCollectionService;
    private CoordinatorClientExt coordinatorClientExt;

    public void setCoordinatorClientExt(CoordinatorClientExt coordinatorClientExt) {
        this.coordinatorClientExt = coordinatorClientExt;
    }

    @Override
    public void consumeItem(DiagutilsJob diagutilsJob, DistributedQueueItemProcessedCallback callback) throws Exception {
        try {
            List<String> options = diagutilsJob.getOptions();
            DiagutilJobStatus jobStatus = coordinatorClientExt.getCoordinatorClient().queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class);
            if (jobStatus == null) {
                log.info("jobStatus is null,quit consumer");
                return;
            }
            log.info("jobStatus in zk is {}",jobStatus);
            String startTime = jobStatus.getStartTime();
            log.info("diagutilsJob startTime is {}", startTime);
            String subOutputDir = _DIAGUTIL + "-" + startTime;
            String dataFiledir = _DIAGUTIL_COLLECT_DIR + subOutputDir;
            //pre-check
            jobStatus.setStatus(DiagutilStatus.PRECHECK_IN_PROGRESS);
            jobStatus.setDescription(DiagutilStatusDesc.PRECHECK_IN_PROGRESS);
            if (!updateJobInfoIfNotCancel(jobStatus)) { //job cancelled
                return;
            }
            FileUtils.deleteQuietly(new File(_DIAGUTIL_COLLECT_DIR)); //clean up left data in collection dir

            final String[] precheckCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_PRECHECK};
            log.info("Executing cmd {}", Arrays.toString(precheckCmd));
            Exec.Result result = Exec.sudo(COMMAND_TIMEOUT, precheckCmd);
            if (!result.exitedNormally() || result.getExitValue() != 0) {
                log.error("Executing precheck error {},stdOutput: {}, stdError:{}",result.getExitValue(),result.getStdOutput(),result.getStdError());
                jobStatus.setStatus(DiagutilStatus.PRECHECK_ERROR);
                jobStatus.setDescription(DiagutilStatusDesc.DISK_FULL);
                updateJobInfoIfNotCancel(jobStatus);
                return;
            }
            log.info("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());
/*            jobStatus.setStatus(DiagutilStatus.PRECHECK_SUCCESS);
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                return;
            }*/
            //collect data other than logs
            jobStatus.setStatus(DiagutilStatus.COLLECTING_IN_PROGRESS);
            jobStatus.setNodeId(coordinatorClientExt.getMyNodeId());
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                return;
            }
            for (String option : options) {

                DiagutilStatusDesc collectDesc = DiagutilStatusDesc.valueOf("collecting_" + option);
                jobStatus.setDescription(collectDesc);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    return;
                }
                log.info("Collecting {}...", option);
                String[] collectCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, "-" + option};
                log.info("Executing cmd {}", Arrays.toString(collectCmd));
                result = Exec.sudo(COMMAND_TIMEOUT, collectCmd);
                if (!result.exitedNormally() || result.getExitValue() != 0) {
                    log.error("Collecting {} error {}", option, result.getExitValue());
                    log.error("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());
                    jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                    //to be modifed...
                    jobStatus.setDescription(DiagutilStatusDesc.COLLECTING_HEALTH_FAILURE);
                    updateJobInfoIfNotCancel(jobStatus);
                    return;
                }
                log.info("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());
                /*else {
                log.info("Collecting {} done", option);
                jobStatus.setStatus(DiagutilStatus.COLLECTING_SUCCESS);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    //cancelled
                }
            }*/
            }


            //collect logs
            if (diagutilsJob.getLogEnable()) {
                String myId = coordinatorClientExt.getMyNodeId();
                log.info("Collecting logs...");
                jobStatus.setDescription(DiagutilStatusDesc.collecting_logs);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    //cancelled
                    return;
                }
                try {
                    LogParam logParam = diagutilsJob.getLogParam();
                    List<String> nodeIds = logParam.getNodeIds();
                    List<String > logNames = logParam.getLogNames();
                    if (logNames != null) {
                        for (String nodeId : nodeIds) {
                            for(String logName : logNames) {
                                String logPath = String.format("%s/logs/%s_%s_%s.log", dataFiledir, logName, nodeId, nodeId);
                                writeLogs(nodeId, nodeId, logName, logParam, logPath);
                            }

                        }
                        //to be modified to saved in each service file,and split
                        //    ClientResponse response = SysClientFactory.getSysClient(coordinatorClientExt.getNodeEndpoint(myId)).get(SysClientFactory.URI_LOGS, ClientResponse.class, MediaType.APPLICATION_XML);

                    }
                } catch (Exception e) {
                    jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                    jobStatus.setDescription(DiagutilStatusDesc.COLLECTING_LOGS_FAILURE);
                    log.info("Collecting logs error {},quit the job", e);
                    updateJobInfoIfNotCancel(jobStatus);
                    return;
                }
            }

            //archive
            jobStatus.setDescription(DiagutilStatusDesc.COLLECTING_ARCHIVE);
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                //cancelled
                return;
            }
            final String[] archiveCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, _DIAGUTIL_ARCHIVE};
            log.info("Executing cmd {}", Arrays.toString(archiveCmd));
            result = Exec.sudo(COMMAND_TIMEOUT, archiveCmd);
            if (!result.exitedNormally() || result.getExitValue() != 0) {
                log.error("Collecting failed at archive error {}", result.getExitValue());
                log.error("stdOutput: {}, stdError:{}",result.getStdOutput(),result.getStdError());
                jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                updateJobInfoIfNotCancel(jobStatus);
                return;
            }
            log.info("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());

            //record output file location and collect success.
            //String dataFiledir = _DIAGUTIL_COLLECT_DIR + subOutputDir +".zip";

            jobStatus.setLocation(dataFiledir);
            jobStatus.setStatus(DiagutilStatus.COLLECTING_SUCCESS);
            jobStatus.setDescription(DiagutilStatusDesc.COLLECT_COMPLETE);
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                //cancelled
                return;
            }
            //upload
            UploadParam uploadParam = diagutilsJob.getUploadParam();
            if (uploadParam == null) {
                log.info("uploadParam is null,quit collect job consumer");
                return;
            }
            BackupClient uploadClient = null;

            if (UploadParam.UploadType.download != uploadParam.getUploadType()) {
                String uri = uploadParam.getUploadFtpParam().getFtp();
                String user = uploadParam.getUploadFtpParam().getUser();
                String passwd = uploadParam.getUploadFtpParam().getPassword();
/*                switch (uploadParam.getUploadType()) {
                    case ftp:
                        uploadClient = new FtpClient(uri, user, passwd);
                        break;
                    case sftp:
                        uploadClient = new FtpClient(uri, user, passwd);
                }*/
                try ( OutputStream os = uploadClient.upload(subOutputDir + ".zip", 0);
                      FileInputStream fis = new FileInputStream(dataFiledir + ".zip");) {
                    int n = 0;
                    byte [] buffer = new byte[102400];
                    while ((n = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, n);
                    }
                } catch (Exception e ) {
                    log.error("Upload got exception {}", e);
                    jobStatus.setStatus(DiagutilStatus.UPLOADING_ERROR);
                    jobStatus.setDescription(DiagutilStatusDesc.UPLOAD_FAILURE);
                    updateJobInfoIfNotCancel(jobStatus);
                    return ;
                }

                jobStatus.setStatus(DiagutilStatus.COMPLETE);
                jobStatus.setDescription(DiagutilStatusDesc.UPLOAD_COMPLETE);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    //cancelled
                    return;
                }
            }

        }finally {
            callback.itemProcessed();
        }
    }

    private void collectLogs(String outputDir) {

    }


    private boolean updateJobInfoIfNotCancel(DiagutilJobStatus jobstatus) throws Exception{
        CoordinatorClient coordinatorClient = coordinatorClientExt.getCoordinatorClient();
        InterProcessLock lock = coordinatorClient.getLock(DataCollectionService.DIAGUTIL_JOB_LOCK);
        lock.acquire();
        log.info("acquired {} lock",DataCollectionService.DIAGUTIL_JOB_LOCK);
        if (null == coordinatorClient.queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class)) {
            log.info("diagutilsJobStatus is null,job has been canceled,quit consumer");
            lock.release();
            //callback.itemProcessed();
            return false;
        }
        log.info("Updating DiagutilJobStatus to {}",jobstatus);
        coordinatorClient.persistRuntimeState(Constants.DIAGUTIL_JOB_STATUS, jobstatus);
        lock.release();
        log.info("released {} lock",DataCollectionService.DIAGUTIL_JOB_LOCK);
        return true;

    }

    private void writeLogs(String nodeId, String nodeName, String logName, LogParam logParam, String destLogPath) {
        List<String> nodeIds = new ArrayList<String>();
        nodeIds.add(nodeId);
        List<String> nodeNames = new ArrayList<>();
        nodeNames.add(nodeName);
        List<String> logNames = new ArrayList<>();
        logNames.add(logName);
        InputStream is;
        try {
            is = (InputStream) logService.getLogs(nodeIds, nodeNames, logNames, logParam.getSeverity(), logParam.getStartTimeStr(),
                    logParam.getEndTimeStr(), logParam.getMsgRegex(), logParam.getMaxCount(), false).getEntity();
            File file = new File(destLogPath);
            FileUtils.copyInputStreamToFile(is, file);
        }catch (Exception e ) {
            log.error("get logs error {}",e);
        }
    }



}
