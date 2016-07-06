/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import com.emc.storageos.management.backup.BackupConstants;
import jcifs.smb.*;
import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements of smb protocol  using JCIFS
 */
public class CifsClient implements BackupClient{
    private static final Logger log = LoggerFactory.getLogger(CifsClient.class);
    private final String uri;
    private NtlmPasswordAuthentication auth;

    public CifsClient(String uri, String domain, String username, String password) {
        this.uri = uri + "/";
        if ( domain != null && !domain.equals("")){
            auth = new NtlmPasswordAuthentication(domain,username,password);
        }else {
            auth = new NtlmPasswordAuthentication(username + ":" + password);
        }
    }

    public OutputStream upload(String fileName, long offset) throws Exception {
        SmbFile uploadFile = getSmbFileHandler(fileName);
        return new BufferedOutputStream(new SmbFileOutputStream(uploadFile));
    }

    public List<String> listFiles(String prefix) throws Exception {
        List<String> fileList = new ArrayList<String>();
        SmbFile smbDir = getSmbFileHandler("");
        String[] files = smbDir.list();
        for (String file : files) {
            if (!file.endsWith(BackupConstants.COMPRESS_SUFFIX)) {
                continue;
            }
            if (prefix == null || file.startsWith(prefix)) {
                fileList.add(file);
            }
        }
        log.info("Listing {}",fileList);
        return fileList;
    }

    public List<String> listAllFiles() throws Exception {
        return listFiles(null);
    }

    public InputStream download(String backupFileName) throws MalformedURLException, SmbException, UnknownHostException {
        SmbFile remoteBackupFile = getSmbFileHandler(backupFileName);
        return new BufferedInputStream(new SmbFileInputStream(remoteBackupFile));
    }

    public void rename (String sourceFileName ,String destFileName) throws Exception {
        SmbFile sourceFile = getSmbFileHandler(sourceFileName);
        SmbFile destFile = getSmbFileHandler(destFileName);
        try {
            sourceFile.renameTo(destFile);
        }catch (SmbException e) {
            log.warn("failed to rename file from {} to {}",sourceFileName,destFileName);
            throw e;
        }
    }

    public long getFileSize(String fileName) throws Exception{
        SmbFile smbFile = getSmbFileHandler(fileName);
        long len = 0;
        try {
            len = smbFile.length();
        }catch ( SmbException e ){
            log.warn("failed to get {} size,return lenth 0",fileName);
        }
        return len;
    }

    public static boolean isSupported (String url) {
        return url.regionMatches(true, 0, BackupConstants.SMB_URL_PREFIX, 0, BackupConstants.SMB_URL_PREFIX.length());
    }

    public String getUri() {
        return uri;
    }

    public void validate() throws AuthenticationException,ConnectException{
        try {
            getSmbFileHandler("").length();
        }catch (SmbAuthException e){
            log.info("SmbAuthException when test external server :{}",e);
            throw new AuthenticationException(e.getMessage());
        }catch (Exception e ) {
            log.info("Exception when test external server :{}",e);
            throw new ConnectException(e.getMessage());
        }
    }

    private SmbFile getSmbFileHandler(String fileName) throws MalformedURLException{
        StringBuilder sb = new StringBuilder(uri);
        sb.append(fileName);
        return new SmbFile(sb.toString(),auth);
    }
}
