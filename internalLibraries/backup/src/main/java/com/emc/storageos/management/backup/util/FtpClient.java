/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import java.net.ConnectException;
import java.net.URI;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.management.backup.BackupConstants;

public class FtpClient implements BackupClient {
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

    public static boolean isSupported(String url) {
        return startsWithIgnoreCase(url, BackupConstants.FTPS_URL_PREFIX) ||
                startsWithIgnoreCase(url, BackupConstants.FTP_URL_PREFIX);
    }

    private static boolean startsWithIgnoreCase(String str, String prefix) {
        return str.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public long getFileSize(String fileName) throws IOException, InterruptedException {
        ProcessBuilder builder = getBuilder();

        builder.command().add("-I");
        builder.command().add(uri + fileName);

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

        return new ProcessOutputStream(builder.start());
    }

    public List<String> listFiles(String prefix) throws Exception {
        ProcessBuilder builder = getBuilder();
        builder.command().add("-l");
        builder.command().add(uri);

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
        if (uri == null) {
            throw new IllegalStateException("uri is null");
        }
        URI serverUri = new URI(uri);
        String endpoint = serverUri.getScheme() + "://" + serverUri.getAuthority();
        String path = serverUri.getPath();
        String sourceName = (new File(path, sourceFileName)).toString().substring(1);
        String destName = (new File(path, destFileName)).toString().substring(1);

        ProcessBuilder builder = getBuilder();
        builder.command().add(endpoint);
        builder.command().add("-Q");
        builder.command().add("RNFR " + sourceName);
        builder.command().add("-Q");
        builder.command().add("RNTO " + destName);
        log.info("cmd={}", hidePassword(builder.command()));


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

    public String getUri() {
        return this.uri;
    }

    public void validate() throws AuthenticationException, ConnectException {
        ProcessBuilder builder = getBuilder();
        builder.command().add("-I");
        builder.command().add("--connect-timeout");
        builder.command().add("30");
        builder.command().add(uri);
        int exitCode;
        StringBuilder errText;
        try {
            ProcessRunner processor = new ProcessRunner(builder.start(), false);
            errText = new StringBuilder();
            processor.captureAllTextInBackground(processor.getStdErr(), errText);
            exitCode = processor.join();
        } catch (Exception e) {
            throw new ConnectException(e.getMessage());
        }
        if (exitCode != 0) {
            if (exitCode == 67) {
                throw new AuthenticationException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            } else {
                throw new ConnectException(errText.length() > 0 ? errText.toString() : Integer.toString(exitCode));
            }
        }

    }

    // just show the first letter of password
    private String hidePassword(List<String> command) {
        String credential = command.get(3);
        return command.toString().replace(credential, credential.substring(0, (credential.indexOf(":") + 2)) + "***");
    }
}
