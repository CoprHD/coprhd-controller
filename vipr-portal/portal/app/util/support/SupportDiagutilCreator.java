/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DiagutilJobStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import org.apache.commons.io.IOUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import play.Logger;
import play.jobs.Job;
import plugin.StorageOsPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SupportDiagutilCreator {
    private ViPRSystemClient client;
    private String nodeId;
    private String fileName;

    public SupportDiagutilCreator(ViPRSystemClient client, String nodeId, String fileName) {
        this.client = client;
        this.nodeId = nodeId;
        this.fileName = fileName;
    }

    public void writeTo(OutputStream out) {
        updataDiagutilStatusByCoordinator(DiagutilStatus.DOWNLOADING_IN_PROGRESS, DiagutilStatusDesc.downloading_in_progress);
        InputStream in = client.diagutil().getAsStream(nodeId, fileName);
        try {
            IOUtils.copy(in, out);
            updataDiagutilStatusByCoordinator(DiagutilStatus.COMPLETE, DiagutilStatusDesc.downloading_complete);
        }catch (IOException e) {
            updataDiagutilStatusByCoordinator(DiagutilStatus.DOWNLOAD_ERROR, DiagutilStatusDesc.downloading_failure);
        }finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    public CreateSupportDiagutilJob createJob(OutputStream out) {
        return new CreateSupportDiagutilJob(out, this);
    }

    private void updataDiagutilStatusByCoordinator (DiagutilStatus status, DiagutilStatusDesc desc) {
        try {
            if (StorageOsPlugin.isEnabled()) {
                CoordinatorClient coordinatorClient = StorageOsPlugin.getInstance().getCoordinatorClient();
                InterProcessLock lock = coordinatorClient.getLock(Constants.DIAGUTIL_JOB_LOCK);
                lock.acquire();
                DiagutilJobStatus jobStatus = coordinatorClient.queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class);
                jobStatus.setStatus(status);
                jobStatus.setDescription(desc);
                coordinatorClient.persistRuntimeState(Constants.DIAGUTIL_JOB_STATUS, jobStatus);
                lock.release();
            }
        }catch (Exception e ) {
            Logger.error("Persist zk status error", e);
        }
    }


    public static class CreateSupportDiagutilJob extends Job {
        private OutputStream out;
        private SupportDiagutilCreator supportDiagutilCreator;

        public CreateSupportDiagutilJob(OutputStream out, SupportDiagutilCreator supportDiagutilCreator) {
            this.out = out;
            this.supportDiagutilCreator = supportDiagutilCreator;
        }

        @Override
        public void doJob() throws Exception {
            supportDiagutilCreator.writeTo(out);
        }
    }
}
