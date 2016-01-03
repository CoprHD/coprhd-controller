/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.management.backup.util.ProcessInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements upload protocol using FTPS
 */
public class FtpUploader extends Uploader {
    private static final Logger log = LoggerFactory.getLogger(FtpUploader.class);

    private final FtpClient ftpClient;

    public FtpUploader(SchedulerConfig cfg, BackupScheduler cli) {
        super(cfg, cli);
        ftpClient = new FtpClient(cfg.uploadUrl, cfg.uploadUserName, cfg.getUploadPassword());
    }

    private ProcessBuilder getBuilder() {
        log.info("lbyu uploadUrl={}", cfg.uploadUrl);
        return ftpClient.getBuilder();
        /*
        boolean isExplicit = startsWithIgnoreCase(this.cfg.uploadUrl, FTPS_URL_PREFIX);

        ProcessBuilder builder = new ProcessBuilder("curl", "-sSk", "-u", String.format("%s:%s",
                this.cfg.uploadUserName, this.cfg.getUploadPassword()));
        if (!isExplicit) {
            builder.command().add("--ftp-ssl");
        }

        return builder;
        */
    }

    public static boolean startsWithIgnoreCase(String str, String prefix) {
        log.info("lbyu str={} prefix={}", str, prefix);
        return FtpClient.startsWithIgnoreCase(str, prefix);

        // return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static boolean isSupported(String url) {
        return FtpClient.isSupported(url);
        // return startsWithIgnoreCase(url, BackupConstants.FTPS_URL_PREFIX) || startsWithIgnoreCase(url, BackupConstants.FTP_URL_PREFIX);
    }

    @Override
    public Long getFileSize(String fileName) throws Exception {
        return ftpClient.getFileSize(fileName);
        /*
        ProcessBuilder builder = getBuilder();

        builder.command().add("-I");
        builder.command().add(this.cfg.uploadUrl + fileName);

        Long length = null;

        log.info("lby ftp command={}", builder.command());

        try (ProcessRunner processor = new ProcessRunner(builder.start(), false)) {
            StringBuilder errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);

            for (String line : processor.enumLines(processor.getStdOut())) {
                if (line.startsWith(CONTENT_LENGTH_HEADER)) {
                    String lenStr = line.substring(CONTENT_LENGTH_HEADER.length() + 1);
                    length = Long.parseLong(lenStr);
                }
            }

            int exitCode = processor.join();
            if (exitCode != 0 && exitCode != FILE_DOES_NOT_EXIST) {
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }

        return length;
        */
    }

    @Override
    public OutputStream upload(String fileName, long offset) throws Exception {
        return ftpClient.upload(fileName, offset);
        /*
        ProcessBuilder builder = getBuilder();

        // We should send a "REST offset" command, but the earliest stage we can --quote it is before PASV/EPSV
        // (then "TYPE I", "STOR ..."), which does not comply to RFC959 that saying REST should be sent
        // just before STOR.
        // Here we assume the file on server is not changed after caller determined the offset - which should be
        // the size of the file on server, so we can just do an append.
        // We'll not do additional check to see if the file on server is really <offset> long right now, because
        // even so there is still a chance someone just appended to that file after our checking, it makes no
        // difference.
        if (offset > 0) {
            builder.command().add("-a");
        }

        builder.command().add("-T");
        builder.command().add("-");
        builder.command().add(this.cfg.uploadUrl + fileName);

        log.info("lby2 ftp command={}", builder.command());
        return new ProcessOutputStream(builder.start());
        */
    }

    @Override
    public List<String> listFiles(String prefix) throws Exception {
        /*
        if (prefix == null) {
            return null;
        }
        ProcessBuilder builder = getBuilder();
        builder.command().add("-l");
        builder.command().add(this.cfg.uploadUrl);

        log.info("lby3 ftp command={}", builder.command());
        List<String> fileList = new ArrayList<String>();
        try (ProcessRunner processor = new ProcessRunner(builder.start(), false)) {
            StringBuilder errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);

            for (String line : processor.enumLines(processor.getStdOut())) {
                if (line.startsWith(prefix)) {
                    fileList.add(line);
                }
            }

            int exitCode = processor.join();
            if (exitCode != 0) {
                log.error("List files on FTP {} failed, Exit code {}", this.cfg.uploadUrl, exitCode);
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }

        return fileList;
        */
        return ftpClient.listFiles(prefix);
    }

    @Override
    public void rename(String sourceFileName, String destFileName) throws Exception {
        /*
        ProcessBuilder builder = getBuilder();
        builder.command().add(this.cfg.uploadUrl);
        builder.command().add("-Q");
        builder.command().add("RNFR " + sourceFileName);
        builder.command().add("-Q");
        builder.command().add("RNTO " + destFileName);

        try (ProcessRunner processor = new ProcessRunner(builder.start(), false)) {
            StringBuilder errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);
            int exitCode = processor.join();
            if (exitCode != 0) {
                log.error("Rename files on FTP {} failed, Exit code {}", this.cfg.uploadUrl, exitCode);
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }
        */
        ftpClient.rename(sourceFileName, destFileName);
    }

    /*
    public InputStream download(String backupFileName) throws IOException {
        ProcessBuilder builder = getBuilder();
        String remoteBackupFile=cfg.uploadUrl+backupFileName;
        String localBackupFile="/data/"+backupFileName;
        builder.command().add(remoteBackupFile);
        // builder.command().add("-o");
        // builder.command().add(localBackupFile);

        log.info("lby cmd={}", builder.command());

        return new ProcessInputStream(builder.start());
    }
    */
}
