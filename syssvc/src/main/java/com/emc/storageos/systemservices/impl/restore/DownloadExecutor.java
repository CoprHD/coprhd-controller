/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.restore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.ws.rs.core.MediaType;


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
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.exceptions.SysClientException;

public final class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);

    private FtpClient client;
    private String remoteBackupFileName;
    private BackupOps backupOps;
    private DownloadListener downloadListener;
    private URI endpoint;
    private boolean fromRemoteServer = false;
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
        fromRemoteServer = true;
    }

    public DownloadExecutor(String backupFileName, BackupOps backupOps, URI endpoint) {
        remoteBackupFileName = backupFileName;
        this.backupOps = backupOps;
        this.endpoint = endpoint;
        fromRemoteServer = false;
    }

    private void registerListener() {
        try {
            log.info("Add download backup listener");
            downloadListener = new DownloadListener(Thread.currentThread());
            backupOps.addRestoreListener(downloadListener);
        }catch (Exception e) {
            log.error("Fail to add download backup listener e=", e);
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
                    log.info("Remove pullBackupFilesFromRemoteServer listener");
                    backupOps.removeRestoreListener(downloadListener);
                }catch (Exception e) {
                    log.warn("Failed to remove pullBackupFilesFromRemoteServer and restore listener");
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
        log.info("Increase pullBackupFilesFromRemoteServer size ={}", size);
        backupOps.updateDownloadSize(remoteBackupFileName, size);
    }

    @Override
    public void run() {
        InterProcessLock lock = null;

        try {
            registerListener();

            backupOps.registerDownloader();

            if (fromRemoteServer == false ) {
                pullFromInternalNode();
                return;
            }

            lock = backupOps.getLock(BackupConstants.RESTORE_LOCK,
                    -1, TimeUnit.MILLISECONDS); // -1= no timeout


            BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
            log.info("lbyf current status={}", s);
            if (s.isNotSuccess() || s.getStatus() == Status.DOWNLOAD_CANCELLED) {
                log.info("Download failed or canceled to pullBackupFilesFromRemoteServer {}, no need to start it on this node", remoteBackupFileName);
                return;
            }

            pullBackupFilesFromRemoteServer();
            postDownload();
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

            backupOps.setRestoreStatus(remoteBackupFileName, s, false);

            if (fromRemoteServer) {
                log.error("Failed to pull backup file from remote server e=", e);
            }else {
                log.error("Failed to pull backup file from other node e=", e);
            }
        }finally {
            try {
                backupOps.unregisterDownloader();
            }catch (Exception e) {
                log.error("Failed to remove listener e=",e);
            }

            log.info("To release lock {}", BackupConstants.RESTORE_LOCK);
            backupOps.releaseLock(lock);
        }
    }

    private void pullFromInternalNode() throws Exception {
        BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        List<String> backupFilenames = s.getBackupFileNames();

        log.info("lbyf endpoint={}", endpoint);

        for (String filename : backupFilenames) {
            if (isMyBackupFile(filename)) {
                pullFileFromNode(endpoint, filename);
            }
        }
        backupOps.setRestoreStatus(remoteBackupFileName, null, true);
        updateRestoreStatus();
    }

    private void pullFileFromNode(URI endpoint, String filename) throws IOException {
        log.info("lbyf pull file {} from {}", filename, endpoint);
        File downloadDir = backupOps.getDownloadDirectory(remoteBackupFileName);

        try {
            String uri = SysClientFactory.URI_NODE_PULL_BACKUP_FILE+ "?backupname=" + remoteBackupFileName + "&filename="+filename;
            final InputStream in = SysClientFactory.getSysClient(endpoint)
                    .get(new URI(uri),
                            InputStream.class, MediaType.APPLICATION_OCTET_STREAM);

            log.info("lbyf Starting background download.");

            byte[] buffer = new byte[BackupConstants.DOWNLOAD_BUFFER_SIZE];
            int length = 0;
            long size = 0;

            File file = new File(downloadDir, filename);
            if (!file.exists()) {
                log.info("To create the new file {}", file.getAbsolutePath());
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                while ((length = in.read(buffer)) > 0) {
                    size += length;
                    out.write(buffer, 0, length);
                }
                backupOps.updateDownloadSize(remoteBackupFileName, size);
            }catch(IOException e) {
                String errMsg = String.format("Failed to download backupfile %s", filename);
                log.error("{} e=", errMsg, e);
                BackupRestoreStatus.Status s =  Status.DOWNLOAD_FAILED;
                s.setMessage(errMsg);
                throw e;
            }
        } catch (URISyntaxException e) {
            log.error("Internal error occurred while prepareing get image URI: {}", e);
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

    private void pullBackupFilesFromRemoteServer() throws IOException, InterruptedException {
        log.info("pull backup files from remote server start");

        log.info("Persist current backup info {}", remoteBackupFileName);
        backupOps.persistCurrentBackupInfo(remoteBackupFileName, false);

        ZipInputStream zin = getDownloadStream();
        BufferedInputStream in = new BufferedInputStream(zin);

        File backupFolder= backupOps.getDownloadDirectory(remoteBackupFileName);

        try {
            backupOps.checkBackup(backupFolder);
            log.info("The backup {} for this node has already been downloaded", remoteBackupFileName);
            // postDownload();
            return; //already downloaded, no need to pullBackupFilesFromRemoteServer again
        } catch (Exception e) {
            // no backup or invalid backup, so pullBackupFilesFromRemoteServer it again
        }

        byte[] buf = new byte[BackupConstants.DOWNLOAD_BUFFER_SIZE];
        ZipEntry zentry = zin.getNextEntry();
        while (zentry != null) {
            //if (isMyBackupFile(zentry)) {
                //pullBackupFileFromRemoteServer(backupFolder, zentry.getName(), zin);
                log.info("lbyf download bacup file {} from remote server", zentry.getName());
                pullBackupFileFromRemoteServer(backupFolder, zentry.getName(), in, buf);
            // }
            zentry = zin.getNextEntry();
        }

        try {
            zin.closeEntry();
            in.close();
            // zin.close();
        }catch (IOException e) {
            log.debug("Failed to close the stream e", e);
            // it's a known issue to use curl
            //it's safe to ignore this exception here.
        }

        backupOps.setRestoreStatus(remoteBackupFileName, null, true);
        // postDownload();
    }

    private void postDownload() {
        BackupRestoreStatus restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        log.info("lbyf postdownload status={}", restoreStatus);

        if (backupOps.shouldHaveBackupData()) {
            try {
                // Map<URI, List<File>> filesMap = validBackup();
                List<String> filenames = validBackup();
                backupOps.setBackupFileNames(remoteBackupFileName, filenames);

                if (fromRemoteServer) {
                    notifyOtherNodes(remoteBackupFileName);
                }
            }catch (Exception e) {
                log.error("Invalid backup e=", e);
                Status s = Status.DOWNLOAD_FAILED;
                s.setMessage(e.getMessage());
                backupOps.setRestoreStatus(remoteBackupFileName, s, false);
                return;
            }
        }

        updateRestoreStatus();
    }

    private void updateRestoreStatus() {
        BackupRestoreStatus restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        Status s = restoreStatus.getStatus();
        log.info("lbyt restorestatus={}", restoreStatus);
        if ( s == Status.DOWNLOADING) {
            long nodeNumber = backupOps.getHosts().size();
            log.info("lbyt nodeNumber={}", nodeNumber);
            if (restoreStatus.getNodeCompleted() == nodeNumber ) {
                s = Status.DOWNLOAD_SUCCESS;
                log.info("lbyb set restore status to {}",s);
            }
        }

        backupOps.setRestoreStatus(remoteBackupFileName, s, false);

        if (s == Status.DOWNLOAD_SUCCESS || s == Status.DOWNLOAD_CANCELLED || s == Status.DOWNLOAD_FAILED ) {
            backupOps.clearCurrentBackupInfo();
        }
    }

    private List<String> validBackup() throws Exception {
        File downloadedDir = backupOps.getDownloadDirectory(remoteBackupFileName);
        backupOps.checkBackup(downloadedDir);
        return backupOps.getBackupFileNames(downloadedDir);
    }

    private void notifyOtherNodes(String backupName) {
        URI endpoint = null;
        String pushUri = null;
        try {
            List<URI> uris = backupOps.getOtherNodes();
            URI myURI = backupOps.getMyURI();
            for (URI uri : uris) {
                endpoint = uri;
                log.info("Notify {}", endpoint);
                pushUri= SysClientFactory.URI_NODE_BACKUPS_PULL+ "?backupname=" + remoteBackupFileName + "&endpoint="+myURI;
                SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(endpoint);
                sysClient.post(new URI(pushUri), null, null);
            }
        }catch (Exception e) {
            String errMsg = String.format("Failed to send %s to %s", pushUri, endpoint);
            log.error(errMsg);
            log.error("lby e=",e);
            BackupRestoreStatus.Status s = BackupRestoreStatus.Status.DOWNLOAD_FAILED;
            s.setMessage(errMsg);
            backupOps.setRestoreStatus(backupName, s, false);
            throw SysClientException.syssvcExceptions.pullBackupFailed(backupName, errMsg);
        }
    }


    private boolean isMyBackupFile(ZipEntry backupEntry) throws UnknownHostException {
        String filename = backupEntry.getName();

        String localHostName = InetAddress.getLocalHost().getHostName();
        return filename.contains(localHostName) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private boolean isMyBackupFile(String filename) throws UnknownHostException {
        String localHostName = InetAddress.getLocalHost().getHostName();
        return filename.contains(localHostName) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private long pullBackupFileFromRemoteServer(File downloadDir, String backupFileName,
                                                BufferedInputStream in, byte[] buffer) throws IOException {
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

        int length;

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
                downloadSize += length;
                updateDownloadSize(length);
            }
        } catch(IOException e) {
            log.error("Failed to pullBackupFilesFromRemoteServer {} from server e=", backupFileName, e);
            BackupRestoreStatus.Status s = BackupRestoreStatus.Status.DOWNLOAD_FAILED;
            s.setMessage(e.getMessage());
            backupOps.setRestoreStatus(remoteBackupFileName, s, true);
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
