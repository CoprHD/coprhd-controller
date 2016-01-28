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

    private void registerListener() {
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
            Status s = status.getStatus();

            if (s == Status.DOWNLOADING) {
                return; // downloaded size is changed
            }

            log.info("New restore status={}", status);
            if (s == Status.DOWNLOAD_CANCELLED) {
                log.info("Stop downloading thread");
                isCanceled = true;
                downloadingThread.interrupt();
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

    private void updateDownloadSize(long size) {
        log.info("Increase download size ={}", size);
        backupOps.setRestoreStatus(remoteBackupFileName, null, 0, size, false, false, false);
    }

    @Override
    public void run() {
        InterProcessLock lock = null;
        try {
            lock = backupOps.getLock(BackupConstants.RESTORE_LOCK,
                    -1, TimeUnit.MILLISECONDS); // -1= no timeout

            backupOps.setDownloadOwner();

            BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
            if (s.isNotSuccess() || s.getStatus() == Status.DOWNLOAD_CANCELLED) {
                log.info("Already failed to download {}, no need to start it on this node", remoteBackupFileName);
                return;
            }

            registerListener();
            download();
        }catch (InterruptedException e) {
            log.info("The downloading thread has been interrupted");
        }catch (Exception e) {
            log.info("isCanceled={}", isCanceled);

            Status s = Status.DOWNLOAD_FAILED;
            s.setMessage(e.getMessage());

            if (isCanceled) {
                s = Status.DOWNLOAD_CANCELLED;
                deleteDownloadedBackup();
            }

            backupOps.setRestoreStatus(remoteBackupFileName, s, 0, 0, false, false, true);

            log.error("Failed to download e=", e);
        }finally {
            try {
                backupOps.deleteDownloadOwner();
            }catch (Exception e) {
                log.error("Failed to remove listener e=",e);
            }

            log.info("To release lock {}", BackupConstants.RESTORE_LOCK);
            backupOps.releaseLock(lock);
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

        try {
            backupOps.checkBackup(backupFolder);
            log.info("The backup {} for this node has already been downloaded", remoteBackupFileName);
            postDownload();
            return; //already downloaded, no need to download again
        } catch (Exception e) {
            // no backup or invalid backup, so download it again
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

        if (backupOps.shouldHaveBackupData()) {
            try {
                validBackup();
            }catch (Exception e) {
                log.error("Invalid backup");
                Status s = Status.DOWNLOAD_FAILED;
                s.setMessage(e.getMessage());
                backupOps.setRestoreStatus(remoteBackupFileName, s, 0, 0, true, false, true);
                return;
            }
        }

        Status s = null;
        if (restoreStatus.getStatus() == BackupRestoreStatus.Status.DOWNLOADING) {
            long nodeNumber = backupOps.getHosts().size();
            if (restoreStatus.getNodeCompleted() == nodeNumber -1 ) {
                s = Status.DOWNLOAD_SUCCESS;
            }
        }

        backupOps.setRestoreStatus(remoteBackupFileName, s, 0, 0, true, false, true);

        if (s == Status.DOWNLOAD_SUCCESS || s == Status.DOWNLOAD_CANCELLED || s == Status.DOWNLOAD_FAILED ) {
            backupOps.clearCurrentBackupInfo();
        }
    }

    private void validBackup() throws Exception {
        File downloadedDir = backupOps.getDownloadDirectory(remoteBackupFileName);
        backupOps.checkBackup(downloadedDir);
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
            BackupRestoreStatus.Status s = BackupRestoreStatus.Status.DOWNLOAD_FAILED;
            s.setMessage(e.getMessage());
            backupOps.setRestoreStatus(remoteBackupFileName, s, 0, 0, false, false, true);
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
