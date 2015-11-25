/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.IOException;
import java.io.OutputStream;

import com.emc.storageos.systemservices.impl.util.ProcessOutputStream;
import com.emc.storageos.systemservices.impl.util.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements upload protocol using FTPS
 */
public class FtpsUploader extends Uploader {
    private static final Logger log = LoggerFactory.getLogger(FtpsUploader.class);

    private final static String CONTENT_LENGTH_HEADER = "Content-Length:";
    private final static String FTPS_URL_PREFIX = "ftps://";
    private final static String FTP_URL_PREFIX = "ftp://";
    private final static int FILE_DOES_NOT_EXIST = 19;

    public FtpsUploader(SchedulerConfig cfg, BackupScheduler cli) {
        super(cfg, cli);
    }

    private ProcessBuilder getBuilder() {
        boolean isExplicit = startsWithIgnoreCase(this.cfg.uploadUrl, FTPS_URL_PREFIX);

        ProcessBuilder builder = new ProcessBuilder("curl", "-sSk", "-u", String.format("%s:%s",
                this.cfg.uploadUserName, this.cfg.getUploadPassword()));
        if (!isExplicit) {
            builder.command().add("--ftp-ssl");
        }

        return builder;
    }

    public static boolean startsWithIgnoreCase(String str, String prefix) {
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static boolean isSupported(String url) {
        return startsWithIgnoreCase(url, FTPS_URL_PREFIX) || startsWithIgnoreCase(url, FTP_URL_PREFIX);
    }

    @Override
    public Long getFileSize(String fileName) throws Exception {
        ProcessBuilder builder = getBuilder();

        builder.command().add("-I");
        builder.command().add(this.cfg.uploadUrl + fileName);

        Long length = null;

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
    }

    @Override
    public OutputStream upload(String fileName, long offset) throws Exception {
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

        return new ProcessOutputStream(builder.start());
    }
}