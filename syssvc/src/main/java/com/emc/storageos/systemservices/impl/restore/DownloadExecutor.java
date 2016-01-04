/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.restore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.management.backup.BackupOps;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.SchedulerConfig;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.util.FtpClient;

public class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);

    private FtpClient client;
    private String remoteBackupFileName;
    private BackupOps backupOps;
    private boolean notifyOthers;
    private String localHostName;
    private BackupRestoreStatus restoreStatus;
    DownloadListener listener = new DownloadListener();

    public DownloadExecutor(SchedulerConfig cfg, String backupZipFileName, BackupOps backupOps, boolean notifyOthers) {
        if (cfg.uploadUrl == null) {
            try {
                cfg.reload();
            }catch (Exception e) {
                log.error("Failed to reload cfg e=", e);
                throw new RuntimeException(e);
            }
        }

        client = new FtpClient(cfg.uploadUrl, cfg.uploadUserName, cfg.getUploadPassword());
        remoteBackupFileName = backupZipFileName;
        this.backupOps = backupOps;
        this.notifyOthers = notifyOthers;
    }

    public void registerListener() {
        try {
            backupOps.addRestoreListener(listener);
        }catch (Exception e) {
            log.error("Fail to add node listener for restore status config znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
        log.info("lbym1");
    }

    class DownloadListener implements NodeListener {
        @Override
        public String getPath() {
            String path = String.format("/config/%s/%s/%s",
                    BackupConstants.BACKUP_RESTORE_STATUS, remoteBackupFileName,
                    Constants.GLOBAL_ID);
            log.info("lbym config path={}", path);
            return path;
        }

        /**
         * called when user modify restore status, procedure or node status from ipreconfig point of view
         */
        @Override
        public void nodeChanged() {
            log.info("lbym The restore status changed");
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("lbym Restore status connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected. Waking up the ip reconfig procedure...");
            }
        }
    }

    public void setDownloadStatus(String backupName, BackupRestoreStatus.Status status, long backupSize, long downloadSize) {
        log.info("lbymm set download status backupName={} status={} backupSize={} downloadSize={}",
                new Object[] {backupName, status, backupSize, downloadSize});
        restoreStatus = backupOps.queryBackupUploadStatus(backupName);
        restoreStatus.setBackupName(backupName);
        log.info("lbymm1");
        restoreStatus.setStatus(status);

        if (backupSize > 0) {
            restoreStatus.setBackupSize(backupSize);
        }

        if (downloadSize > 0) {
            restoreStatus.setDownoadSize(downloadSize);
        }

        backupOps.persistBackupRestoreStatus(restoreStatus);
    }

    public void updateDownloadSize(long size) {
        log.info("lbymm increase download increase ={}", size);
        restoreStatus = backupOps.queryBackupUploadStatus(remoteBackupFileName);
        log.info("lbymm1");

        long newSize = restoreStatus.getDownoadSize() + size;
        restoreStatus.setDownoadSize(newSize);
        log.info("lbyn new status={}", restoreStatus);
        backupOps.persistBackupRestoreStatus(restoreStatus);

    }

    @Override
    public void run() {
        try {
            localHostName = InetAddress.getLocalHost().getHostName();
            download();
            notifyOtherNodes();
        }catch (Exception e) {
            setDownloadStatus(remoteBackupFileName, BackupRestoreStatus.Status.DOWNLOAD_FAILED, 0, 0);
            log.error("Failed to download e=", e);
        }finally {
            try {
                backupOps.removeRestoreListener(listener);
            }catch (Exception e) {
                log.error("Failed to remvoe listener e=",e);
            }
        }
    }

    private void download() throws IOException, InterruptedException {
        log.info("download start");
        ZipInputStream zin = getDownloadStream();

        if (notifyOthers) {
            // This is the first node to download backup files
            try {
                long size = client.getFileSize(remoteBackupFileName);
                log.info("lby size={}", size);

                setDownloadStatus(remoteBackupFileName, BackupRestoreStatus.Status.DOWNLOADING, size, 0);
            } catch (IOException | InterruptedException e) {
                log.error("Failed to get zip file size e=", e);
                throw e;
            }
        }else {
            setDownloadStatus(remoteBackupFileName, BackupRestoreStatus.Status.DOWNLOADING, 0, 0);
        }

        int dotIndex=remoteBackupFileName.lastIndexOf(".");
        String backupFolder=remoteBackupFileName.substring(0, dotIndex);
        log.info("lbyx backupFolder={}", backupFolder);

        String localHostName = InetAddress.getLocalHost().getHostName();
        log.info("lby local hostname={}", localHostName);

        long downloadSize = 0;
        ZipEntry zentry = zin.getNextEntry();
        while (zentry != null) {
            log.info("lbyy file {}", zentry.getName());
            if (belongsTo(zentry.getName(), localHostName)) {
                log.info("lbyy to download {}", backupFolder+"/"+zentry.getName());
                downloadSize += downloadMyBackupFile(backupFolder+"/"+zentry.getName(), zin);
            }
            zentry = zin.getNextEntry();
        }

        // fix the download size
        postDownload(BackupRestoreStatus.Status.DOWNLOAD_SUCCESS);

        try {
            zin.closeEntry();
            zin.close();
        }catch (IOException e) {
            log.info("lbyu exception will close ignored e=", e);
        }
    }

    private void postDownload(BackupRestoreStatus.Status status) {
        restoreStatus = backupOps.queryBackupUploadStatus(remoteBackupFileName);
        log.info("lbynn restoreStatus={}", restoreStatus);
        restoreStatus.increaseNodeCompleted();
        int completedNodes = restoreStatus.getNodeCompleted();
        log.info("lbynn restoreStatus={} completedNodes={}", restoreStatus, completedNodes);

        if (restoreStatus.getStatus() == BackupRestoreStatus.Status.DOWNLOADING) {
            long nodeNumber = backupOps.getHosts().size();
            log.info("lbynn nodeNumber={}", nodeNumber);
            if (completedNodes == nodeNumber) {
                restoreStatus.setStatus(BackupRestoreStatus.Status.DOWNLOAD_SUCCESS);
            }
        }

        log.info("lbynn after restoreStatus={}", restoreStatus);
        backupOps.persistBackupRestoreStatus(restoreStatus);
    }

    private boolean belongsTo(String filename, String hostname) {
        log.info("lbyuu filename={} zk suffix={}", filename, BackupConstants.BACKUP_ZK_FILE_SUFFIX);
        return filename.contains(hostname) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private long downloadMyBackupFile(String backupFileName, ZipInputStream zin) throws IOException {
        long downloadSize = 0;
        File file = new File(backupOps.getBackupDir(), backupFileName);
        log.info("lbyu file={}", file.getAbsoluteFile());
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        byte[] buf = new byte[BackupConstants.DOWNLOAD_BUFFER_SIZE];
        int length;

        try (FileOutputStream out = new FileOutputStream(file)) {
            while ((length = zin.read(buf)) > 0) {
                out.write(buf, 0, length);
                downloadSize += length;
                updateDownloadSize(length);
            }
        } catch(IOException e) {
            log.error("lbyn Failed to download {} from server", backupFileName);
            setDownloadStatus(remoteBackupFileName,
                    BackupRestoreStatus.Status.DOWNLOAD_FAILED, 0, 0);
            throw e;
        }

        return downloadSize;
    }

    private void notifyOtherNodes() {
        log.info("lbyu notify other nodes");
        if (notifyOthers == false) {
            return;
        }

        URI pushUri = SysClientFactory.URI_NODE_BACKUPS_PUSH;
        log.info("lbyx pushUrl={}", pushUri);

        List<Service> sysSvcs = backupOps.getAllSysSvc();
        for (Service svc : sysSvcs) {
            URI endpoint = svc.getEndpoint();
            log.info("lbyy notify {} hostname={}", endpoint, svc.getNodeName());

            if (localHostName.equals(svc.getNodeName())) {
                log.info("lbym local host skip");
            }

            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(endpoint);
            sysClient.post(pushUri, null, remoteBackupFileName);
        }
    }

    private ZipInputStream getDownloadStream() throws IOException {
        log.info("lbyu get download stream backupfile={}", remoteBackupFileName);

        InputStream in = client.download(remoteBackupFileName);
        ZipInputStream zin = new ZipInputStream(in);
        log.info("lby in={} zin={}", in ,zin);

        return zin;
    }
}
