/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.management.backup.util;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yangg8 on 4/28/2016.
 */
public class CifsClient {
    private static final Logger log = LoggerFactory.getLogger(CifsClient.class);
    private final String uri;
    private final String username;
    private final String password;
    private NtlmPasswordAuthentication auth;

    public CifsClient(String uri, String username, String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        auth = new NtlmPasswordAuthentication(username + ":" + password);

    }

    public OutputStream upload(String fileName, long offset) throws Exception {
        SmbFile uploadFile = new SmbFile(uri + fileName, auth);
        return new SmbFileOutputStream(uploadFile);
    }

    public List<String> listFiles(String prefix) throws Exception {
        List<String> fileList = new ArrayList<String>();
        SmbFile smbDir = new SmbFile(uri, auth);
        String[] files = smbDir.list();
        for (String file : files) {
            if (prefix == null || file.startsWith(prefix)) {
                fileList.add(file);
                log.info("Listing {}", file);
            }
        }
        return fileList;
    }

    public List<String> listAllFiles() throws Exception {
        return listFiles(null);
    }

    public InputStream download(String BackupFileName) throws Exception {
        SmbFile remoteBackupFile = new SmbFile(uri + BackupFileName, auth);
        return new SmbFileInputStream(remoteBackupFile);
    }

    public static Boolean isSupported (String url) {
        return true;
    }

    private NtlmPasswordAuthentication getNtlmPasswordAuthentication() {
        return new NtlmPasswordAuthentication(username + ":" + password);

    }




}
