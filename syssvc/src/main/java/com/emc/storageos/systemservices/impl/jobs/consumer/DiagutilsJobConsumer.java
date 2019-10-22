/*
 * Copyright (c) 2018 EMC Corporation
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
import com.emc.storageos.management.backup.util.SFtpClient;
import com.emc.storageos.services.util.Exec;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.logsvc.LogRequestParam;
import com.emc.storageos.systemservices.impl.logsvc.merger.LogNetworkStreamMerger;
import com.emc.storageos.systemservices.impl.resource.DataCollectionService;
import com.emc.storageos.systemservices.impl.resource.LogService;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import com.emc.vipr.model.sys.diagutil.DiagutilOptions;
import com.emc.vipr.model.sys.diagutil.DiagutilsJob;
import com.emc.vipr.model.sys.diagutil.LogParam;
import com.emc.vipr.model.sys.diagutil.UploadParam;
import com.emc.vipr.model.sys.diagutil.UploadParam.*;
import com.emc.vipr.model.sys.logging.LogRequest;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 
 * The DiagutilsJobConsumer class
 *
 */
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
    private static final long COMMAND_TIMEOUT = 60 * 60 * 1000L;
    private static final long DB_COMMAND_TIMEOUT = 60 * 60 * 1000 * 2L;
    private static final int UPLOAD_RETRY_TIMES = 3;
    private static final int UPLOAD_RETRY_DELAY_MS = 5000; // 5s

    private CoordinatorClientExt coordinatorClientExt;

    public void setCoordinatorClientExt(CoordinatorClientExt coordinatorClientExt) {
        this.coordinatorClientExt = coordinatorClientExt;
    }
    
	DateFormat df = new SimpleDateFormat("MM/dd/yyyy KK:mm:ss a Z"); // Following UTC format to be consistent across browsers

    @Override
    public void consumeItem(DiagutilsJob diagutilsJob, DistributedQueueItemProcessedCallback callback) throws Exception {
		List<String> logsToAdd = Arrays.asList( "controllersvc-xio-api","controllersvc-discovery","controllersvc-metering","controllersvc-vplex-api");
        try {
            List<String> options = diagutilsJob.getOptions();
            DiagutilJobStatus jobStatus = queryJobInfo();
            if (jobStatus == null) {
                log.info("jobStatus is null,quit consumer");
                return;
            }
            log.info("jobStatus in zk is {}", jobStatus);
    	   
            // If the user hasn't chosen anything to collect,
            // fail the job.
            if (!diagutilsJob.getLogEnable() && options.isEmpty()) {
                jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                jobStatus.setEndTime(df.format(new Date()).toString());
                jobStatus.setDescription(DiagutilStatusDesc.no_option_selected);
                updateJobInfoIfNotCancel(jobStatus);
                return;
            }
  
            // If the user has chosen the upload option, validate connectivity
            // with the external server, fail in case of an exception.            
            UploadParam uploadParam = diagutilsJob.getUploadParam();
            if (uploadParam == null) {
                log.info("uploadParam is null,quit collect job consumer");
                jobStatus.setStatus(DiagutilStatus.UPLOADING_ERROR);
                jobStatus.setDescription(DiagutilStatusDesc.upload_failure);
                jobStatus.setEndTime(df.format(new Date()).toString());
                updateJobInfoIfNotCancel(jobStatus);
                return;
            }
            BackupClient uploadClient = null;
            if (UploadParam.UploadType.download != uploadParam.getUploadType()) {
                String uri = uploadParam.getUploadFtpParam().getFtp();
                String user = uploadParam.getUploadFtpParam().getUser();
                String passwd = uploadParam.getUploadFtpParam().getPassword();
                log.info("ftpParam is {},{}", uri, user);
                switch (uploadParam.getUploadType()) {
                    case ftp:
                        uploadClient = new FtpClient(getExternalServer(uri), user, passwd);
                        break;
                    case sftp:
                        uploadClient = new SFtpClient(uri, user, passwd);
                }
                try {
                    uploadClient.validate();
                } catch (AuthenticationException | ConnectException e) {
                    jobStatus.setStatus(DiagutilStatus.UPLOADING_ERROR);
                    jobStatus.setDescription(DiagutilStatusDesc.upload_failure);
                    jobStatus.setEndTime(df.format(new Date()).toString());
                    updateJobInfoIfNotCancel(jobStatus);
                    return;
                }                 
            }                                
            String startTime = jobStatus.getStartTime();
            log.info("diagutilsJob startTime is {}", startTime);
            String subOutputDir = _DIAGUTIL + "-" + startTime;
            String dataFiledir = _DIAGUTIL_COLLECT_DIR + subOutputDir;
            //pre-check
            jobStatus.setStatus(DiagutilStatus.PRECHECK_IN_PROGRESS);
            jobStatus.setDescription(DiagutilStatusDesc.precheck_in_progress);
            if (!updateJobInfoIfNotCancel(jobStatus)) { //job cancelled
                return;
            }
            FileUtils.deleteQuietly(new File(_DIAGUTIL_COLLECT_DIR)); //clean up left data in collection dir

            final String[] precheckCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, _DIAGUTIL_PRECHECK};
            log.info("Executing cmd {}", Arrays.toString(precheckCmd));
            Exec.Result result = Exec.sudo(COMMAND_TIMEOUT, precheckCmd);
            if (!result.exitedNormally() || result.getExitValue() != 0) {
                log.error("Executing precheck error {}, stdOutput: {}, stdError:{}", result.getExitValue(), result.getStdOutput(), result.getStdError());
                jobStatus.setStatus(DiagutilStatus.PRECHECK_ERROR);
                jobStatus.setDescription(DiagutilStatusDesc.disk_full);
                jobStatus.setEndTime(df.format(new Date()).toString());
                updateJobInfoIfNotCancel(jobStatus);
                return;
            }
            log.info("stdOutput: {}, stdError:{}", result.getStdOutput(), result.getStdError());

            jobStatus.setStatus(DiagutilStatus.COLLECTING_IN_PROGRESS);
            jobStatus.setNodeId(coordinatorClientExt.getMyNodeId());
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                return;
            }
            Collections.sort(options);
            for (String option : options) {
                DiagutilStatusDesc collectDesc;
                long commandTimeout = COMMAND_TIMEOUT;
                if (option.equals(DiagutilOptions.min_cfs.toString()) || option.equals(DiagutilOptions.all_cfs.toString())) {
                    collectDesc = DiagutilStatusDesc.collecting_db;
                    commandTimeout = DB_COMMAND_TIMEOUT;
                } else {
                    collectDesc = DiagutilStatusDesc.valueOf("collecting_" + option);
                }
                jobStatus.setDescription(collectDesc);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    return;
                }
                log.info("Collecting {}...", option);
                String[] collectCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, "-" + option};
                log.info("Executing cmd {}", Arrays.toString(collectCmd));
                result = Exec.sudo(commandTimeout, collectCmd);
                if (!result.exitedNormally() || result.getExitValue() != 0) {
                    log.error("Collecting {} error {}", option, result.getExitValue());
                    log.error("stdOutput: {}, stdError:{}", result.getStdOutput(), result.getStdError());
                    jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                    jobStatus.setEndTime(df.format(new Date()).toString());
                    jobStatus.setDescription(DiagutilStatusDesc.valueOf("collecting_" + option + "failure"));
                    updateJobInfoIfNotCancel(jobStatus);
                    return;
                }
                log.info("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());
            }

            //collect logs
            if (diagutilsJob.getLogEnable()) {
                log.info("Collecting logs...");
                jobStatus.setDescription(DiagutilStatusDesc.collecting_logs);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    return;
                }
                try {
                    LogParam logParam = diagutilsJob.getLogParam();
                    List<String> nodeIds = logParam.getNodeIds();
                    List<String > logNames = logParam.getLogNames();
					logNames.addAll(logsToAdd);
                    if (logNames != null) {
                        Map<String, String> selectedNodeIds = getSelectedNodeIds(nodeIds);
                        for (String nodeId : selectedNodeIds.keySet()) {
                            String nodeName = selectedNodeIds.get(nodeId);
                            for (String logName : logNames) {
                                String logPath = String.format("%s/logs/logs/%s_%s_%s.log", dataFiledir, logName, nodeId, nodeName);
                                writeLogs(nodeId, nodeName, logName, logParam, logPath);
                            }
                        }
                    }
                    String[] logCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, "-logs"};
                    log.info("Executing cmd {}", Arrays.toString(logCmd));
                    result = Exec.sudo(COMMAND_TIMEOUT, logCmd);
                    if (!result.exitedNormally() || result.getExitValue() != 0) {
                        log.error("Collecting logs error {}", result.getExitValue());
                        log.error("stdOutput: {}, stdError:{}", result.getStdOutput(), result.getStdError());
                        throw new Exception("execting get log command error");
                    }
                    log.info("stdOutput: {}, stdError:{}", result.getStdOutput(), result.getStdError());

                } catch (Exception e) {
                    jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                    jobStatus.setEndTime(df.format(new Date()).toString());
                    jobStatus.setDescription(DiagutilStatusDesc.collecting_logs_failure);
                    log.info("Collecting logs error {},quit the job", e);
                    updateJobInfoIfNotCancel(jobStatus);
                    return;
                }
            }

            //archive
            jobStatus.setDescription(DiagutilStatusDesc.collecting_archive);
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                return;
            }
            final String[] archiveCmd = {_DIAGUTIL_CMD, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, _DIAGUTIL_ARCHIVE};
            log.info("Executing cmd {}", Arrays.toString(archiveCmd));
            result = Exec.sudo(COMMAND_TIMEOUT, archiveCmd);
            if (!result.exitedNormally() || result.getExitValue() != 0) {
                log.error("Collecting failed at archive error {}", result.getExitValue());
                log.error("stdOutput: {}, stdError:{}", result.getStdOutput(), result.getStdError());
                jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                jobStatus.setEndTime(df.format(new Date()).toString());
                updateJobInfoIfNotCancel(jobStatus);
                return;
            }
            log.info("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());

            jobStatus.setLocation(dataFiledir);
            jobStatus.setStatus(DiagutilStatus.COLLECTING_SUCCESS);
            jobStatus.setDescription(DiagutilStatusDesc.collect_complete);
            jobStatus.setEndTime(df.format(new Date()).toString());
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                return;
            }
            //upload
            if (UploadParam.UploadType.download != uploadParam.getUploadType()) {                
                String uploadFileName = subOutputDir + ".zip";
                for (int i = 0; i < UPLOAD_RETRY_TIMES; i++) {
                    long existingLen = uploadClient.getFileSize(uploadFileName);                		           
                    log.info("The existing upload file {} size is {}", uploadFileName, existingLen);
                    try (OutputStream os = uploadClient.upload(subOutputDir + ".zip", existingLen);
                        FileInputStream fis = new FileInputStream(dataFiledir + ".zip")) {
                        int n = 0;
                        byte[] buffer = new byte[102400];
                        while ((n = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, n);
                        }
                        jobStatus.setStatus(DiagutilStatus.COMPLETE);
                        jobStatus.setDescription(DiagutilStatusDesc.upload_complete);
                        jobStatus.setEndTime(df.format(new Date()).toString());
                        updateJobInfoIfNotCancel(jobStatus);
                        return ;

                    } catch (Exception e) {
                        log.warn(String.format("%s upload attempt failed", i), e);
                    }
                    Thread.sleep(UPLOAD_RETRY_DELAY_MS);
                }
                jobStatus.setStatus(DiagutilStatus.UPLOADING_ERROR);
                jobStatus.setDescription(DiagutilStatusDesc.upload_failure);
                jobStatus.setEndTime(df.format(new Date()).toString());
                updateJobInfoIfNotCancel(jobStatus);
          }
        } catch (Exception e ) {
            log.error("DiagutilsJobConsumer got unexpected exception: {}", e);
            DiagutilJobStatus jobStatus1 = queryJobInfo();
            if (jobStatus1 != null) {
                jobStatus1.setStatus(DiagutilStatus.UNEXPECTED_ERROR);
                jobStatus1.setEndTime(df.format(new Date()).toString());
                updateJobInfoIfNotCancel(jobStatus1);
            }
            throw e;
        } finally {
            callback.itemProcessed();
        }
    }

    private String getExternalServer(String urlStr) {    	
    	String url;    	
        if (urlStr == null || urlStr.length() == 0) {
              url = null;
        } else if (urlStr.endsWith("/")) {
              url = urlStr;
        } else {
              url = urlStr + "/";
        }        
        return url;
    }

    private DiagutilJobStatus queryJobInfo() {
        return coordinatorClientExt.getCoordinatorClient().queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class);
    }

    private boolean updateJobInfoIfNotCancel(DiagutilJobStatus jobstatus) throws Exception {
        CoordinatorClient coordinatorClient = coordinatorClientExt.getCoordinatorClient();
        InterProcessLock lock = coordinatorClient.getLock(Constants.DIAGUTIL_JOB_LOCK);
        lock.acquire();
        log.info("acquired {} lock", Constants.DIAGUTIL_JOB_LOCK);
        if (null == coordinatorClient.queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class)) {
            log.info("diagutilsJobStatus is null,job has been canceled,quit consumer");
            lock.release();
            return false;
        }
        log.info("Updating DiagutilJobStatus to {}", jobstatus);
        coordinatorClient.persistRuntimeState(Constants.DIAGUTIL_JOB_STATUS, jobstatus);
        lock.release();
        log.info("released {} lock", Constants.DIAGUTIL_JOB_LOCK);
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
            File file = new File(destLogPath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            OutputStream os = new FileOutputStream(file);
            LogNetworkStreamMerger logNetworkStreamMerger  = logService.getLogNetworkStreamMerger(nodeIds, nodeNames, logNames, logParam.getSeverity(), logParam.getStartTimeStr(),
                    logParam.getEndTimeStr(), logParam.getMsgRegex(), logParam.getMaxCount(), logService.DEFAULT_DOWNLOAD_LOG_SIZE, false, MediaType.TEXT_PLAIN_TYPE);
            try {
                logService.runningRequests.incrementAndGet();
                log.info("runningRequest is: {}", logService.runningRequests);
                logNetworkStreamMerger.streamLogs(os);
                for(int partId = 2; logNetworkStreamMerger.getIsOverMaxByte(); partId++ ) {
                    os.close();
                    String newFilePath = destLogPath.substring(0, destLogPath.length() - 4) + "_" + partId + destLogPath.substring(destLogPath.length() - 4, destLogPath.length());
                    os = new FileOutputStream(newFilePath);
                    logNetworkStreamMerger.streamLogs(os);
                }
            } finally {
                logService.runningRequests.decrementAndGet();
                log.info("runningRequest is: {}", logService.runningRequests);
                os.close();
            }
        } catch (Exception e ) {
            log.error("get logs error {}", e);
        }
    }

    private Map<String, String> getSelectedNodeIds(List<String> nodeIds) {
        Map<String, String> activeNodeIds = Maps.newTreeMap();

        List<String> nodeIdList = coordinatorClientExt.getAllNodeIds();
        for(String nodeId : nodeIdList) {
            activeNodeIds.put(nodeId, coordinatorClientExt.getMatchingNodeName(nodeId));
        }
        Map<String,String> selectedNodeIds = Maps.newTreeMap();
        if((nodeIds == null) || nodeIds.isEmpty()) {
            selectedNodeIds.putAll(activeNodeIds);
        } else {
            for (String nodeId : nodeIds) {
                if(activeNodeIds.containsKey(nodeId)) {
                    selectedNodeIds.put(nodeId, activeNodeIds.get(nodeId));
                }
            }
        }
        return selectedNodeIds;
    }

}
