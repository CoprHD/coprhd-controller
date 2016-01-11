/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.restore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.commons.io.FileUtils;

import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.common.Service;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus.Status;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.management.backup.BackupOps;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.SchedulerConfig;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;

public final class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);

    private FtpClient client;
    private String remoteBackupFileName;
    private BackupOps backupOps;
    private boolean notifyOthers;
    private String localHostName;
    private BackupRestoreStatus restoreStatus;
    private DownloadListener listener = new DownloadListener();

    public static DownloadExecutor create(SchedulerConfig cfg, String backupZipFileName, BackupOps backupOps, boolean notifyOthers) {

        DownloadExecutor downloader = new DownloadExecutor(cfg, backupZipFileName, backupOps, notifyOthers);
        downloader.registerListener();

        return downloader;
    }

    private DownloadExecutor(SchedulerConfig cfg, String backupZipFileName, BackupOps backupOps, boolean notifyOthers) {
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
            log.info("lby add restore listener");
            backupOps.addRestoreListener(listener);
        }catch (Exception e) {
            log.error("Fail to add node listener for restore status config znode", e);
            throw APIException.internalServerErrors.addListenerFailed();
        }
    }

    class DownloadListener implements NodeListener {
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
            log.info("Restore status={}", status);
            Status s = status.getStatus();

            if (s == Status.DOWNLOAD_FAILED || s == Status.RESTORE_CANCELLED ) {
                //Remove downloaded backup data
                File downloadedDir = backupOps.getDownloadDirectory(remoteBackupFileName);
                log.info("To remove downloaded {}", downloadedDir);
                try {
                    FileUtils.deleteDirectory(downloadedDir);
                }catch (IOException e) {
                    log.error("Failed to remove {} e=", downloadedDir.getAbsolutePath(), e );
                }
            }

            if (s == Status.DOWNLOAD_FAILED || s == Status.RESTORE_CANCELLED || s == Status.DOWNLOAD_SUCCESS) {
                try {
                    log.info("lby remove restore listener");
                    backupOps.removeRestoreListener(listener);
                }catch (Exception e) {
                    log.warn("Failed to remove download listener");
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

    public void setDownloadStatus(String backupName, BackupRestoreStatus.Status status, long backupSize, long downloadSize) {
        log.info("Set download status backupName={} status={} backupSize={} downloadSize={}",
                new Object[] {backupName, status, backupSize, downloadSize});
        restoreStatus = backupOps.queryBackupRestoreStatus(backupName, false);
        restoreStatus.setBackupName(backupName);
        restoreStatus.setStatus(status);

        if (backupSize > 0) {
            restoreStatus.setBackupSize(backupSize);
        }

        if (downloadSize > 0) {
            restoreStatus.setDownoadSize(downloadSize);
        }

        backupOps.persistBackupRestoreStatus(restoreStatus, false);
    }

    public void updateDownloadSize(long size) {
        log.info("Increase download size ={}", size);
        restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);

        long newSize = restoreStatus.getDownoadSize() + size;
        restoreStatus.setDownoadSize(newSize);
        backupOps.persistBackupRestoreStatus(restoreStatus, false);
    }

    @Override
    public void run() {
        InterProcessLock lock = null;
        try {
            lock = backupOps.getLock(BackupConstants.RESTORE_LOCK,
                    -1, TimeUnit.MILLISECONDS); // -1= no timeout

            localHostName = InetAddress.getLocalHost().getHostName();
            download();
            notifyOtherNodes();
        }catch (Exception e) {
            setDownloadStatus(remoteBackupFileName, BackupRestoreStatus.Status.DOWNLOAD_FAILED, 0, 0);
            log.error("Failed to download e=", e);
        }finally {
            try {
                backupOps.releaseLock(lock);
            }catch (Exception e) {
                log.error("Failed to remove listener e=",e);
            }
        }
    }

    private void download() throws IOException, InterruptedException {
        log.info("download start");
        ZipInputStream zin = getDownloadStream();

        File backupFolder= backupOps.getDownloadDirectory(remoteBackupFileName);

        if (hasDownloaded(backupFolder)) {
            log.info("The backup {} for this node has already been downloaded", remoteBackupFileName);
            return; //already downloaded, no need to download again
        }

        if (notifyOthers) {
            // This is the first node to download backup files
            long size = client.getFileSize(remoteBackupFileName);

            setDownloadStatus(remoteBackupFileName, BackupRestoreStatus.Status.DOWNLOADING, size, 0);
        }

        ZipEntry zentry = zin.getNextEntry();
        while (zentry != null) {
            if (isMyBackupFile(zentry)) {
                downloadMyBackupFile(backupFolder, zentry.getName(), zin);
            }
            zentry = zin.getNextEntry();
        }

        // fix the download size
        postDownload(BackupRestoreStatus.Status.DOWNLOAD_SUCCESS);

        try {
            zin.closeEntry();
            zin.close();
        }catch (IOException e) {
            log.debug("Failed to close the stream e", e);
            // it's a known issue to use curl
            //it's safe to ignore this exception here.
        }
    }

    private boolean hasDownloaded(File backupFolder) {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".zip");
            }
        };

        File[] zipFiles = backupFolder.listFiles(filter);

        if (zipFiles == null) {
            return false;
        }

        for (File zipFile : zipFiles) {
            if (backupOps.checkMD5(zipFile) == false) {
                log.info("MD5 of {} does not match its md5 file", zipFile.getAbsolutePath());
                return false;
            }
        }

        return true;
    }

    private void postDownload(BackupRestoreStatus.Status status) {
        restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        restoreStatus.increaseNodeCompleted();
        int completedNodes = restoreStatus.getNodeCompleted();

        if (restoreStatus.getStatus() == BackupRestoreStatus.Status.DOWNLOADING) {
            long nodeNumber = backupOps.getHosts().size();
            if (completedNodes == nodeNumber) {
                restoreStatus.setStatus(BackupRestoreStatus.Status.DOWNLOAD_SUCCESS);
            }
        }

        backupOps.persistBackupRestoreStatus(restoreStatus, false);
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

        File file = new File(downloadDir, backupFileName);
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
            log.error("Failed to download {} from server", backupFileName);
            setDownloadStatus(remoteBackupFileName,
                    BackupRestoreStatus.Status.DOWNLOAD_FAILED, 0, 0);
            throw e;
        }

        return downloadSize;
    }

    private void notifyOtherNodes() {
        log.info("Notify other nodes");
        if (notifyOthers == false) {
            return;
        }

        URI pushUri = SysClientFactory.URI_NODE_BACKUPS_PUSH;

        List<Service> sysSvcs = backupOps.getAllSysSvc();
        for (Service svc : sysSvcs) {
            URI endpoint = svc.getEndpoint();
            log.info("Notify {} hostname={}", endpoint, svc.getNodeName());

            if (localHostName.equals(svc.getNodeName())) {
                continue;
            }

            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(endpoint);
            sysClient.post(pushUri, null, remoteBackupFileName);
        }
    }

    private ZipInputStream getDownloadStream() throws IOException {
        InputStream in = client.download(remoteBackupFileName);
        ZipInputStream zin = new ZipInputStream(in);

        return zin;
    }
}
