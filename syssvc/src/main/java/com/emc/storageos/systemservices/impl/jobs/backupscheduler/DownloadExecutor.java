package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.emc.storageos.management.backup.util.FtpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.file.StreamDataBodyPart;
import com.sun.jersey.multipart.impl.MultiPartWriter;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.management.backup.BackupConstants;

/**
 * Created by brian on 12/30/15.
 */
public class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);
    private FtpClient client;
    private String remoteBackupFileName;
    private List<Service> sysSvcs;

    public DownloadExecutor(SchedulerConfig cfg, String remoteBackupFileName, List<Service> svcs) {
        if (cfg.uploadUrl == null) {
            try {
                cfg.reload();
            }catch (Exception e) {
                log.error("lbyu failed to reload cfg e=", e);
                throw new RuntimeException(e);
            }
        }

        log.info("lbyu cfg={} url={}", cfg, cfg.uploadUrl);
        client = new FtpClient(cfg.uploadUrl, cfg.uploadUserName, cfg.getUploadPassword());
        this.remoteBackupFileName = remoteBackupFileName;
        sysSvcs = svcs;
    }

    @Override
    public void run() {
        try {
            download();
            notifyOtherNodes();
        }catch (Exception e) {
            log.error("Failed to download e=", e);
        }
    }

    private void download() throws IOException {
        log.info("download start");
        ZipInputStream zin = getDownloadStream();

        log.info("lby1 available {}", zin.available());
        try {
            long size = client.getFileSize(remoteBackupFileName);
            log.info("lby size={}", size);
        }catch (Exception e) {
            log.info("lby e=",e);
        }

        int dotIndex=remoteBackupFileName.lastIndexOf(".");
        String backupFolder=remoteBackupFileName.substring(0,dotIndex);
        log.info("lbyx backupFolder={}", backupFolder);

        String localHostName = InetAddress.getLocalHost().getHostName();
        log.info("lby local hostname={}", localHostName);

        ZipEntry zentry = zin.getNextEntry();
        // while (zin.available() != 0) {
        while (zentry != null) {
            log.info("lbyy file {}", zentry.getName());
            if (belongsTo(zentry, localHostName)) {
                log.info("lbyy to download {}", backupFolder+"/"+zentry.getName());
                downloadMyBackupFile(backupFolder+"/"+zentry.getName(), zin);
            }
            zentry = zin.getNextEntry();
        }

        try {
            zin.closeEntry();
            zin.close();
        }catch (IOException e) {
            log.info("lbyu exception will close ignored e=",e);
        }
    }

    private boolean belongsTo(ZipEntry zipEntry, String hostname) {
        String filename = zipEntry.getName();

        log.info("lbyuu filename={} zk suffix={}", filename, BackupConstants.BACKUP_ZK_FILE_SUFFIX);
        return filename.contains(hostname) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private void downloadMyBackupFile(String backupFileName, ZipInputStream zin) throws IOException {
        File file = new File("/data/backup", backupFileName);
        log.info("lbyu file={}", file.getAbsoluteFile());
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        byte[] buf = new byte[1024*4];
        int length;
        try (FileOutputStream out = new FileOutputStream(file)) {
            while ((length = zin.read(buf)) > 0) {
                out.write(buf, 0, length);
            }
        }
    }

    private void notifyOtherNodes() {
        log.info("lbyu notify other nodes");
        if (sysSvcs == null) {
            return;
        }

        URI pushUri = SysClientFactory.URI_NODE_BACKUPS_PUSH;
        log.info("lbyx pusUrl={}", pushUri);

        for (Service svc : sysSvcs) {
            URI endpoint = svc.getEndpoint();
            log.info("lbyy notify {}", endpoint);
            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(endpoint);
            sysClient.post(pushUri, null, remoteBackupFileName);
        }
    }

    private void sendFile(String backupFileName, ZipInputStream zin, URI endpoint) {
        URI postUri = SysClientFactory.URI_NODE_BACKUPS_PUSH;

        //sendFile(backupFolder+"/"+zentry.getName(), zin, svc.getEndpoint());
        SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(endpoint);

        /*
        InputStreamEntity body = new InputStreamEntity(zin);
        body.setContentType("application/octet-stream");
        body.setChunked(true);
        */

        log.info("lbyx posturl={}", postUri);
        try {
            StreamDataBodyPart body = new StreamDataBodyPart(backupFileName, zin);
            body.setFilename(backupFileName);
            FormDataMultiPart body1 = new FormDataMultiPart();
            body1.bodyPart(body);
            //File file= sysClient.post(postUri, File.class, backupFileName);
            //sysClient.post(postUri, File.class, body1);
            //sysClient.post(postUri, null, body1);
            /*
            log.info("lbyx file={}, canwrite={}", file, file.canWrite());
            FileOutputStream out = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(out);
            writer.write("abc");
            writer.close();
            */
            ClientConfig cc = new DefaultClientConfig();
            cc.getClasses().add(MultiPartWriter.class);
            Client client = Client.create(cc);
            log.info("send {} to {} success", backupFileName, endpoint);
        }catch (Exception e) {
            log.error("lby failed {}", e);
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
