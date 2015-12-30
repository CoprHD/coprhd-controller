package com.emc.storageos.systemservices.impl.jobs.backupscheduler;


import com.emc.storageos.systemservices.impl.util.ProcessOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by brian on 12/30/15.
 */
public class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);
    private FtpClient client;
    private String remoteBackupFileName;

    public DownloadExecutor(SchedulerConfig cfg, String remoteBackupFileName) {
        client = new FtpClient(cfg, null);
        this.remoteBackupFileName = remoteBackupFileName;
    }

    @Override
    public void run() {
        try {
            download();
        }catch (IOException e) {
            log.error("Failed to download e=", e);
        }
    }

    private void download() throws IOException {
        log.info("download start");
        ZipInputStream zin = getDownloadStream();
    }

    private ZipInputStream getDownloadStream() throws IOException {
        log.info("lby get download stream");
        InputStream in = client.download(remoteBackupFileName);
        ZipInputStream zin = new ZipInputStream(in);

        log.info("lby in={} zin={}", in ,zin);
        log.info("lby available {}", zin.available());
        try {
            long size = client.getFileSize(remoteBackupFileName);
            log.info("lby size={}", size);
        }catch (Exception e) {
            log.info("lby e=",e);
        }

        while (zin.available() != 0) {
            ZipEntry zentry = zin.getNextEntry();
            log.info("lby file {} compressed size={} size={}", zentry.getName(), zentry.getCompressedSize(), zentry.getSize());
        }

        return zin;
    }
}
