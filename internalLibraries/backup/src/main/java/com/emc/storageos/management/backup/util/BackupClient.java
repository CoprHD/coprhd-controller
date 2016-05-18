package com.emc.storageos.management.backup.util;

import com.emc.storageos.management.backup.ExternalServerType;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by yangg8 on 5/10/2016.
 */
public interface BackupClient {
    public OutputStream upload(String fileName, long offset) throws Exception;

    public List<String> listFiles(String prefix) throws Exception;

    public List<String> listAllFiles() throws Exception;

    public InputStream download(String BackupFileName) throws Exception;

    public void rename(String sourceFileName, String destFileName) throws Exception;

    public long getFileSize(String fileName) throws Exception;

    public String getUri();

}
