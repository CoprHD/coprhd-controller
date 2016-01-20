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
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.commons.io.FileUtils;

import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus.Status;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.management.backup.BackupOps;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.SchedulerConfig;

public final class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);

    private FtpClient client;
    private String remoteBackupFileName;
    private BackupOps backupOps;
    private DownloadListener downloadListener;
    private boolean isGeo = false; // true if the backupset is from GEO env
    private volatile  boolean isCanceled = false;
    private File infoPropertiesFile;

    public DownloadExecutor(SchedulerConfig cfg, String backupZipFileName, BackupOps backupOps) {
        if (cfg.uploadUrl == null) {
            try {
                cfg.reload();
            }catch (Exception e) {
                log.error("Failed to reload cfg e=", e);
                throw new RuntimeException(e);
            }
        }

        client = new FtpClient(cfg.uploadUrl, cfg.uploadUserName, cfg.getExternalServerPassword());
        remoteBackupFileName = backupZipFileName;
        this.backupOps = backupOps;
    }

    public void registerListener() {
        try {
            log.info("Add download listener");
            downloadListener = new DownloadListener(Thread.currentThread());
            backupOps.addRestoreListener(downloadListener);
        }catch (Exception e) {
            log.error("Fail to add download listener e=", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
    }

    class DownloadListener implements NodeListener {
        private Thread downloadingThread;

        public DownloadListener(Thread t) {
            downloadingThread = t;
        }

        @Override
        public String getPath() {
            String prefix = backupOps.getBackupConfigPrefix();
            return prefix + "/" + remoteBackupFileName;
        }

        /**
         * called when user modify restore status, procedure or node status
         */
        @Override
        public void nodeChanged() {
            log.info("The restore status changed");
            onRestoreStatusChange();
        }

        private void onRestoreStatusChange() {
            BackupRestoreStatus status = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
            log.info("New restore status={}", status);
            Status s = status.getStatus();

            if (s == Status.DOWNLOAD_CANCELLED) {
                log.info("Stop downloading thread");
                isCanceled = true;
                downloadingThread.interrupt();
            }

            if (s.removeDownloadFiles()) {
                deleteDownloadedBackup();
            }

            if (s.removeListener()) {
                try {
                    log.info("Remove download listener");
                    backupOps.removeRestoreListener(downloadListener);
                }catch (Exception e) {
                    log.warn("Failed to remove download and restore listener");
                }
            }
        }

        /**
         * called when connection state changed.
         */
        @Override
        public void connectionStateChanged(State state) {
            log.info("Restore status connection state changed to {}", state);
            if (state.equals(State.CONNECTED)) {
                log.info("Curator (re)connected.");
                onRestoreStatusChange();
            }
        }
    }

    public synchronized void setDownloadStatus(String backupName, BackupRestoreStatus.Status status, long backupSize, long increasedSize,
                                               boolean increaseCompletedNodeNumber) {
        log.info("Set download status backupName={} status={} backupSize={} increasedSize={} increaseCompletedNodeNumber={}",
                new Object[] {backupName, status, backupSize, increasedSize, increaseCompletedNodeNumber});

        BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(backupName, false);
        if (status != null && status == Status.DOWNLOAD_CANCELLED ) {
            if (!s.getStatus().canBeCanceled()) {
                return;
            }
        }

        s.setBackupName(backupName);

        if (status != null) {
            s.setStatus(status);
        }

        if (backupSize > 0) {
            s.setBackupSize(backupSize);
        }

        if (increasedSize > 0) {
            long newSize = s.getDownoadSize() + increasedSize;
            s.setDownoadSize(newSize);
        }

        if (increaseCompletedNodeNumber) {
            s.increaseNodeCompleted();
        }

        backupOps.persistBackupRestoreStatus(s, false);
    }

    public void cancelDownload() {
        Map<String, String> map = backupOps.getCurrentBackupInfo();
        log.info("To cancel current download {}", map);
        String backupName = map.get(BackupConstants.CURRENT_DOWNLOADING_BACKUP_NAME_KEY);
        boolean isLocal = Boolean.parseBoolean(map.get(BackupConstants.CURRENT_DOWNLOADING_BACKUP_ISLOCAL_KEY));
        log.info("backupname={}, isLocal={}", backupName, isLocal);
        if (!isLocal) {
            setDownloadStatus(backupName, BackupRestoreStatus.Status.DOWNLOAD_CANCELLED, 0, 0, false);
            log.info("Persist the cancel flag into ZK");
        }
    }

    public void updateDownloadSize(long size) {
        log.info("Increase download size ={}", size);
        setDownloadStatus(remoteBackupFileName, null, 0, size, false);
    }

    @Override
    public void run() {
        InterProcessLock lock = null;
        try {
            lock = backupOps.getLock(BackupConstants.RESTORE_LOCK,
                    -1, TimeUnit.MILLISECONDS); // -1= no timeout

            registerListener();
            download();
        }catch (InterruptedException e) {
            log.info("The downloading thread has been interrupted");
            BackupRestoreStatus restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
            Status s = restoreStatus.getStatus();
            if (s.canBeCanceled()) {
                log.info("The downloading has been canceled");
                setDownloadStatus(remoteBackupFileName, Status.DOWNLOAD_CANCELLED, 0, 0, false);
            }
        }catch (Exception e) {
            log.info("isCanceled={}", isCanceled);
            Status s = Status.DOWNLOAD_FAILED;
            if (isCanceled) {
                s = Status.DOWNLOAD_CANCELLED;
                deleteDownloadedBackup();
            }

            setDownloadStatus(remoteBackupFileName, s, 0, 0, false);
            log.error("Failed to download e=", e);
        }finally {
            try {
                backupOps.releaseLock(lock);
            }catch (Exception e) {
                log.error("Failed to remove listener e=",e);
            }
        }
    }

    private void deleteDownloadedBackup() {
        File downloadedDir = backupOps.getDownloadDirectory(remoteBackupFileName);

        log.info("To remove downloaded {} exist={}", downloadedDir, downloadedDir.exists());
        try {
            FileUtils.deleteDirectory(downloadedDir);
        }catch (IOException ex) {
            log.error("Failed to remove {} e=", downloadedDir.getAbsolutePath(), ex);
        }
    }

    private void download() throws IOException, InterruptedException {
        log.info("download start");

        log.info("Persist current backup info {}", remoteBackupFileName);
        backupOps.persistCurrentBackupInfo(remoteBackupFileName, false);

        ZipInputStream zin = getDownloadStream();

        File backupFolder= backupOps.getDownloadDirectory(remoteBackupFileName);

        if (backupOps.isValidBackup(backupFolder)) {
            log.info("The backup {} for this node has already been downloaded", remoteBackupFileName);
            postDownload();

            return; //already downloaded, no need to download again
        }

        ZipEntry zentry = zin.getNextEntry();
        while (zentry != null) {
            if (isMyBackupFile(zentry)) {
                downloadMyBackupFile(backupFolder, zentry.getName(), zin);
            }
            zentry = zin.getNextEntry();
        }

        postDownload();

        try {
            zin.closeEntry();
            zin.close();
        }catch (IOException e) {
            log.debug("Failed to close the stream e", e);
            // it's a known issue to use curl
            //it's safe to ignore this exception here.
        }
    }

    private void postDownload() {
        BackupRestoreStatus restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        log.info("status={}", restoreStatus);

        if (!validBackup()) {
            log.error("Invalid backup");
            Status s = Status.DOWNLOAD_FAILED;
            s.setMessage("Invalid backup");
            restoreStatus.setStatus(s);
            backupOps.persistBackupRestoreStatus(restoreStatus, false);
            return;
        }

        Status s = null;
        if (restoreStatus.getStatus() == BackupRestoreStatus.Status.DOWNLOADING) {
            long nodeNumber = backupOps.getHosts().size();
            if (restoreStatus.getNodeCompleted() == nodeNumber -1 ) {
                s = Status.DOWNLOAD_SUCCESS;
            }
        }

        setDownloadStatus(remoteBackupFileName, s, 0, 0, true);

        if (s == Status.DOWNLOAD_SUCCESS || s == Status.DOWNLOAD_CANCELLED || s == Status.DOWNLOAD_FAILED ) {
            backupOps.clearCurrentBackupInfo();
        }
    }

    private boolean validBackup() {
        File downloadedDir = backupOps.getDownloadDirectory(remoteBackupFileName);
        return backupOps.isValidBackup(downloadedDir);
    }

    private boolean isMyBackupFile(ZipEntry backupEntry) throws UnknownHostException {
        String filename = backupEntry.getName();

        String localHostName = InetAddress.getLocalHost().getHostName();
        return filename.contains(localHostName) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private long downloadMyBackupFile(File downloadDir, String backupFileName, ZipInputStream zin) throws IOException {
        long downloadSize = 0;

        if (isGeo == false) {
            isGeo = backupOps.isGeoBackup(backupFileName);

            if (isGeo) {
                backupOps.setGeoFlag(remoteBackupFileName, false);
            }
        }

        File file = new File(downloadDir, backupFileName);
        if (!file.exists()) {
            log.info("To create the new file {}", file.getAbsolutePath());
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        if (backupFileName.contains(BackupConstants.BACKUP_INFO_SUFFIX)) {
            infoPropertiesFile = file;
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
            log.error("Failed to download {} from server e=", backupFileName, e);
            setDownloadStatus(remoteBackupFileName, BackupRestoreStatus.Status.DOWNLOAD_FAILED, 0, 0, false);
            throw e;
        }

        return downloadSize;
    }

    private ZipInputStream getDownloadStream() throws IOException {
        InputStream in = client.download(remoteBackupFileName);
        ZipInputStream zin = new ZipInputStream(in);

        return zin;
    }
}
