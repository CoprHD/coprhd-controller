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
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import com.emc.vipr.model.sys.diagutil.DiagutilsJob;
import com.emc.vipr.model.sys.diagutil.UploadParam;
import com.emc.vipr.model.sys.diagutil.UploadParam.*;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.media.jfxmedia.Media;
import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class DiagutilsJobConsumer extends DistributedQueueConsumer<DiagutilsJob> {
    private static final Logger log = LoggerFactory.getLogger(DiagutilsJobConsumer.class);
    private static final String _OPT_DIR = "/opt/storageos/bin/";
    private static final String _DIAGUTIL = _OPT_DIR + "diagutils";
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

            //pre-check
            jobStatus.setStatus(DiagutilStatus.PRECHECK_IN_PROGRESS);
            if (!updateJobInfoIfNotCancel(jobStatus)) { //job cancelled
                return;
            }
            FileUtils.deleteQuietly(new File(_DIAGUTIL_COLLECT_DIR)); //clean up left data in collection dir

            final String[] precheckCmd = {_DIAGUTIL, _DIAGUTIL_GUI, _DIAGUTIL_PRECHECK};
            log.info("Executing cmd {}", Arrays.toString(precheckCmd));
            Exec.Result result = Exec.sudo(COMMAND_TIMEOUT, precheckCmd);
            if (!result.exitedNormally() || result.getExitValue() != 0) {
                log.error("Executing precheck error {},stdOutput: {}, stdError:{}",result.getExitValue(),result.getStdOutput(),result.getStdError());
                jobStatus.setStatus(DiagutilStatus.PRECHECK_ERROR);
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
            if (!updateJobInfoIfNotCancel(jobStatus)) {
                return;
            }
            for (String option : options) {
                jobStatus.setDescription(DiagutilStatusDesc.COLLECTING_DB);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    return;
                }
                log.info("Collecting {}...", option);
                String[] collectCmd = {_DIAGUTIL, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, "-" + option};
                log.info("Executing cmd {}", Arrays.toString(collectCmd));
                result = Exec.sudo(COMMAND_TIMEOUT, collectCmd);
                if (!result.exitedNormally() || result.getExitValue() != 0) {
                    jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
                    log.error("Collecting {} error {}", option, result.getExitValue());
                    log.error("stdOutput: {}, stdError:{}", result.getStdOutput(),result.getStdError());
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
                jobStatus.setDescription(DiagutilStatusDesc.COLLECTING_LOGS);
                if (!updateJobInfoIfNotCancel(jobStatus)) {
                    //cancelled
                    return;
                }
                try {
                    //to be modified to saved in each service file,and split
                    ClientResponse response = SysClientFactory.getSysClient(coordinatorClientExt.getNodeEndpoint(myId)).get(SysClientFactory.URI_LOGS, ClientResponse.class, MediaType.APPLICATION_XML);
                    File file = new File(_DIAGUTIL_COLLECT_DIR + subOutputDir + "/logs");
                    FileUtils.copyInputStreamToFile(response.getEntityInputStream(), file);
                } catch (Exception e) {
                    jobStatus.setStatus(DiagutilStatus.COLLECTING_ERROR);
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
            final String[] archiveCmd = {_DIAGUTIL, _DIAGUTIL_GUI, _DIAGUTIL_OUTPUT, subOutputDir, _DIAGUTIL_ARCHIVE};
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
            String dataFiledir = _DIAGUTIL_COLLECT_DIR + subOutputDir;

            jobStatus.setLocation(dataFiledir);
            jobStatus.setStatus(DiagutilStatus.COLLECTING_SUCCESS);
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

            if (!UploadParam.UploadType.download.equals(uploadParam.getUploadType())) {
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
                    updateJobInfoIfNotCancel(jobStatus);
                    return ;
                }

                jobStatus.setStatus(DiagutilStatus.COMPLETE);
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
        log.info("Updating DiagutilJobStatus to {}",jobstatus);
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
        coordinatorClient.persistRuntimeState(Constants.DIAGUTIL_JOB_STATUS, jobstatus);
        lock.release();
        log.info("released {} lock",DataCollectionService.DIAGUTIL_JOB_LOCK);
        return true;

    }



}
