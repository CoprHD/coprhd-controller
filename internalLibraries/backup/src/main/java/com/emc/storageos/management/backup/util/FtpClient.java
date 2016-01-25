/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.management.backup.BackupConstants;

public class FtpClient {
    private static final Logger log = LoggerFactory.getLogger(FtpClient.class);

    private final String uri;
    private final String username;
    private final String password;

    public FtpClient(String uri, String username, String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    public ProcessBuilder getBuilder() {
        boolean isExplicit = startsWithIgnoreCase(uri, BackupConstants.FTPS_URL_PREFIX);

        ProcessBuilder builder = new ProcessBuilder("curl", "-sSk", "-u", String.format("%s:%s",
                username, password));
        if (!isExplicit) {
            builder.command().add("--ftp-ssl");
        }

        return builder;
    }

    private static boolean startsWithIgnoreCase(String str, String prefix) {
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public static boolean isSupported(String url) {
        return startsWithIgnoreCase(url, BackupConstants.FTPS_URL_PREFIX) ||
                startsWithIgnoreCase(url, BackupConstants.FTP_URL_PREFIX);
    }

    public long getFileSize(String fileName) throws IOException, InterruptedException {
        ProcessBuilder builder = getBuilder();

        builder.command().add("-I");
        builder.command().add(uri + fileName);

        log.info("command={}", builder.command());

        long length = 0;

        try (ProcessRunner processor = new ProcessRunner(builder.start(), false)) {
            StringBuilder errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);

            for (String line : processor.enumLines(processor.getStdOut())) {
                if (line.startsWith(BackupConstants.CONTENT_LENGTH_HEADER)) {
                    String lenStr = line.substring(BackupConstants.CONTENT_LENGTH_HEADER.length() + 1);
                    length = Long.parseLong(lenStr);
                }
            }

            int exitCode = processor.join();
            if (exitCode != 0 && exitCode != BackupConstants.FILE_DOES_NOT_EXIST) {
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }

        return length;
    }

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
        builder.command().add(uri + fileName);

         log.info("command={}", builder.command());
        return new ProcessOutputStream(builder.start());
    }

    public List<String> listFiles(String prefix) throws Exception {
        ProcessBuilder builder = getBuilder();
        builder.command().add("-l");
        builder.command().add(uri);

        log.info("cmd={}", builder.command());

        List<String> fileList = new ArrayList<String>();
        try (ProcessRunner processor = new ProcessRunner(builder.start(), false)) {
            StringBuilder errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);

            for (String line : processor.enumLines(processor.getStdOut())) {
                log.info("File name: {}", line);
                if (!line.endsWith(BackupConstants.COMPRESS_SUFFIX)) {
                    continue;
                }
                if (prefix == null || line.startsWith(prefix)) {
                    fileList.add(line);
                    log.info("Listing {}", line);
                }
            }

            int exitCode = processor.join();
            if (exitCode != 0) {
                log.error("List files on FTP {} failed, Exit code {}", uri, exitCode);
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }

        return fileList;
    }

    public List<String> listAllFiles() throws Exception {
        return listFiles(null);
    }

     public void rename(String sourceFileName, String destFileName) throws Exception {
        ProcessBuilder builder = getBuilder();
        builder.command().add(uri);
        builder.command().add("-Q");
        builder.command().add("RNFR " + sourceFileName);
        builder.command().add("-Q");
        builder.command().add("RNTO " + destFileName);

        try (ProcessRunner processor = new ProcessRunner(builder.start(), false)) {
            StringBuilder errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);
            int exitCode = processor.join();
            if (exitCode != 0) {
                log.error("Rename files on FTP {} failed, Exit code {}", uri, exitCode);
                throw new IOException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }
    }

     public InputStream download(String backupFileName) throws IOException {
        ProcessBuilder builder = getBuilder();
        String remoteBackupFile = uri + backupFileName;
        builder.command().add(remoteBackupFile);

        return new ProcessInputStream(builder.start());
    }
}
