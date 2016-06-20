/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.restore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.ws.rs.core.MediaType;


import com.emc.storageos.management.backup.ExternalServerType;
import com.emc.storageos.management.backup.exceptions.BackupException;
import com.emc.storageos.management.backup.util.CifsClient;
import com.emc.storageos.management.backup.util.BackupClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus;
import com.emc.vipr.model.sys.backup.BackupRestoreStatus.Status;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.management.backup.BackupOps;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;

public final class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);

    private BackupClient client;
    private String remoteBackupFileName;
    private BackupOps backupOps;
    private DownloadListener downloadListener;
    private URI endpoint;
    private boolean fromRemoteServer = false;
    private boolean isGeo = false; // true if the backupset is from GEO env
    private volatile  boolean isCanceled = false;

    public DownloadExecutor(BackupClient client, String backupZipFileName, BackupOps backupOps) {

        this.client = client;
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
                    log.info("Remove pull listener");
                    backupOps.removeRestoreListener(downloadListener);
                }catch (Exception e) {
                    log.warn("Failed to remove pull listener");
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

    @Override
    public void run() {
        try {
            BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
            if (s.isNotSuccess() || s.getStatus() == Status.DOWNLOAD_CANCELLED) {
                log.info("Download {} has been failed or canceled, no need to start it on this node", remoteBackupFileName);
                return;
            }

            registerListener();

            backupOps.registerDownloader();

            if (!fromRemoteServer) {
                pullFromInternalNode();
                return;
            }

            pullBackupFilesFromRemoteServer();
            postDownload();
            backupOps.setRestoreStatus(remoteBackupFileName, false, null, null, true, true);
        }catch (InterruptedException e) {
            log.info("The downloading thread has been interrupted");
        }catch (Exception e) {
            log.info("isCanceled={}", isCanceled);

            if (!isCanceled) {
                if (fromRemoteServer) {
                    log.error("Failed to pull backup file from remote server e=", e);
                }else {
                    log.error("Failed to pull backup file from other node e=", e);
                }
                backupOps.setRestoreStatus(remoteBackupFileName, false, Status.DOWNLOAD_FAILED, e.getMessage(), false, true);
            }
        }finally {
            try {
                backupOps.unregisterDownloader();
            }catch (Exception ex) {
                if (!isCanceled) {
                    log.error("Failed to remove listener e=", ex);
                }
            }
        }
    }

    private void pullFromInternalNode() throws Exception {
        log.info("Pull from internal node");
        BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        List<String> backupFilenames = s.getBackupFileNames();

        for (String filename : backupFilenames) {
            if (isMyBackupFile(filename)) {
                pullFileFromNode(endpoint, filename);
            }
        }

        backupOps.setRestoreStatus(remoteBackupFileName, false, null, null, true, true);
    }

    private void pullFileFromNode(URI endpoint, String filename) throws IOException {
        log.info("Pull the file {} from {}", filename, endpoint);
        File downloadDir = backupOps.getDownloadDirectory(remoteBackupFileName);

        try {
            String uri = SysClientFactory.URI_NODE_PULL_BACKUP_FILE+ "?backupname=" + URLEncoder.encode(remoteBackupFileName,"UTF8")
                    + "&filename="+URLEncoder.encode(filename,"UTF8");
            final InputStream in = SysClientFactory.getSysClient(endpoint)
                                                   .get(new URI(uri), InputStream.class, MediaType.APPLICATION_OCTET_STREAM);

            byte[] buffer = new byte[BackupConstants.DOWNLOAD_BUFFER_SIZE];
            persistBackupFile(downloadDir, filename, new BufferedInputStream(in), buffer, true, true);
        } catch (URISyntaxException e) {
            log.error("Internal error occurred while prepareing get image URI: {}", e);
        }
    }

    private void pullBackupFilesFromRemoteServer() throws Exception {
        log.info("pull backup files in {} from remote server start", remoteBackupFileName);

        backupOps.persistCurrentBackupInfo(remoteBackupFileName, false);

        File backupFolder= backupOps.getDownloadDirectory(remoteBackupFileName);

        try {
            backupOps.checkBackup(backupFolder, false);
            long size = backupOps.getSizeToDownload(remoteBackupFileName);
            backupOps.updateDownloadedSize(remoteBackupFileName, size, false);
            log.info("The backup {} for this node has already been downloaded", remoteBackupFileName);
            return; //no need to download again
        } catch (Exception e) {
            // no backup or invalid backup, so download it again
        }

        byte[] buf = new byte[BackupConstants.DOWNLOAD_BUFFER_SIZE];
        InputStream in = client.download(remoteBackupFileName);


        //Step1: download the zip file
        File zipFile = null;
        try (BufferedInputStream bin = new BufferedInputStream(in)) {
            zipFile = pullBackupFileFromRemoteServer(backupFolder, remoteBackupFileName, bin, buf);
        }

        //Step2: extract the zip file
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile));  BufferedInputStream bzin = new BufferedInputStream(zin);) {
            ZipEntry zentry = zin.getNextEntry();
            while (zentry != null) {
                String filename = zentry.getName();
                log.info("Extract backup file {}", filename);
                persistBackupFile(backupFolder, filename, bzin, buf, false, false);

                if (!isGeo) {
                    isGeo = backupOps.isGeoBackup(filename);

                    if (isGeo) {
                        backupOps.setGeoFlag(remoteBackupFileName, false);
                    }
                }

                zentry = zin.getNextEntry();
            }
            zin.closeEntry();
        }

        //Step3: delete the downloaded zip file
        zipFile.delete();
    }

    private void postDownload() {
        BackupRestoreStatus restoreStatus = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);
        log.info("In postDownload status={}", restoreStatus);

        try {
            File downloadedDir = backupOps.getDownloadDirectory(remoteBackupFileName);
            backupOps.checkBackup(downloadedDir, false);

            // persist the names of data files into the ZK
            List<String> filenames = backupOps.getBackupFileNames(downloadedDir);

            backupOps.setBackupFileNames(remoteBackupFileName, filenames);

            // since all files has been downloaded/unzipped to current node,
            // calculate the size to download on each node
            // on current node, since all files have been downloaded
            // set the size-to-download = downloaded-size

            // calculate the size to be downloaded on other nodes
            Map<String, Long> downloadSize = backupOps.getInternalDownloadSize(remoteBackupFileName);

            BackupRestoreStatus s = backupOps.queryBackupRestoreStatus(remoteBackupFileName, false);

            Map<String, Long> sizeToDownload = s.getSizeToDownload();
            sizeToDownload.putAll(downloadSize);
            s.setSizeToDownload(sizeToDownload);

            log.info("sizeToDownload={} downloadedSize={}", sizeToDownload, s.getDownloadedSize());

            backupOps.persistBackupRestoreStatus(s, false, true);

            notifyOtherNodes(remoteBackupFileName);
        }catch (Exception e) {
            log.error("Invalid backup e=", e);
            Status s = Status.DOWNLOAD_FAILED;
            backupOps.setRestoreStatus(remoteBackupFileName, false, Status.DOWNLOAD_FAILED, e.getMessage(), false, true);
        }
    }

    private void notifyOtherNodes(String backupName) {
        URI node= null;
        String pushUri = null;
        try {
            List<URI> uris = backupOps.getOtherNodes();
            URI myURI = backupOps.getMyURI();
            for (URI uri : uris) {
                node = uri;
                log.info("Notify {}", node);
                pushUri= SysClientFactory.URI_NODE_BACKUPS_PULL+ "?backupname=" + URLEncoder.encode(remoteBackupFileName,"UTF8") + "&endpoint="+myURI;
                SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(node);
                sysClient.post(new URI(pushUri), null, null);
            }
        }catch (Exception e) {
            String errMsg = String.format("Failed to send %s to %s", pushUri, node);
            backupOps.setRestoreStatus(backupName, false, Status.DOWNLOAD_FAILED, e.getMessage(), false, true);
            throw BackupException.fatals.pullBackupFailed(backupName, errMsg);
        }
    }

    private boolean isMyBackupFile(String filename) throws UnknownHostException {
        String localHostName = InetAddress.getLocalHost().getHostName();
        return filename.contains(localHostName) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private File pullBackupFileFromRemoteServer(File downloadDir, String backupFileName,
                                                BufferedInputStream in, byte[] buffer) throws IOException {
        return persistBackupFile(downloadDir, backupFileName, in, buffer, true, false);
    }

    private File persistBackupFile(File downloadDir, String backupFileName, BufferedInputStream in, byte[] buffer, boolean updateDownloadedSize, boolean doLock) throws IOException {
        File file = new File(downloadDir, backupFileName);

        if (!file.exists()) {
            log.info("To create the new file {}", file.getAbsolutePath());
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        // Skip downloaded part
        long skip = file.length();
        log.info("To skip={} bytes", skip);
        in.skip(file.length());

        backupOps.updateDownloadedSize(remoteBackupFileName, skip, doLock);

        int length;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file, true))) {
            while (true) {
                length = in.read(buffer);

                if (length <=0) {
                    break; //reach the end
                }

                out.write(buffer, 0, length);
                if (updateDownloadedSize) {
                    backupOps.updateDownloadedSize(remoteBackupFileName, length, doLock);
                }
            }
        } catch(IOException e) {
            log.error("Failed to download file {} e=", backupFileName, e);
            backupOps.setRestoreStatus(remoteBackupFileName, false, Status.DOWNLOAD_FAILED, e.getMessage(), true, doLock);
            throw e;
        }

        return file;
    }
}
