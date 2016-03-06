/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlRootElement(name = "restore_status")
public class BackupRestoreStatus {
    private static final Logger log = LoggerFactory.getLogger(BackupRestoreStatus.class);
    private static final String KEY_BACKUP_NAME = "backupName";
    private static final String KEY_PULL_SIZE = "pullSize";
    private static final String KEY_DOWNLOADED_SIZE = "downloadedSize";
    private static final String KEY_STATUS = "status";
    private static final String KEY_DETAILS_MSG = "details";
    private static final String KEY_NODE_COMPLETED= "nodeCompleted";
    private static final String KEY_FILE_NAMES= "filenames";
    private static final String FILE_NAME_SEPARATOR= ":";
    private static final String KEY_VALUE_SEPARATOR= "=";
    private static final String MAP_ENTRY_SEPARATOR= ":";
    private static final String KEY_IS_GEO_ENV= "isGeo";

    private String backupName;

    //key=hostname e.g. vipr1, vipr2 ...
    //value= the number of bytes to be downloaded on this node
    private Map<String, Long> sizeToDownload = new HashMap();

    //key=hostname e.g. vipr1, vipr2 ...
    //value= the number of bytes to has been downloaded on this node
    private Map<String, Long> downloadedSize = new HashMap();

    private Status status = Status.READY;
    private String details = "";
    private int nodeCompleted = 0;
    private boolean isGeo = false;
    private List<String> backupFileNames = new ArrayList();

    public BackupRestoreStatus() {
    }

    @XmlElement(name = "backup_name")
    public String getBackupName() {
        return this.backupName;
    }

    public void setBackupName(String backupName) {
        this.backupName = backupName;
    }

    @XmlElement (name = "status")
    public Status getStatus() {
        return status;
    }

    @XmlElement (name = "details")
    public String getDetails() {
        return details;
    }

    public void setStatusWithDetails(Status s, String details) {
        status = s;
        this.details = details != null ? details : s.getMessage();
    }

    public int getNodeCompleted() {
        return nodeCompleted;
    }

    public void increaseNodeCompleted() {
        nodeCompleted++;
    }

    public List<String> getBackupFileNames() {
        return backupFileNames;
    }

    public void setBackupFileNames(List<String> backupFileNames) {
        this.backupFileNames = backupFileNames;
    }

    @XmlElement (name = "is_geo")
    public boolean isGeo() {
        return isGeo;
    }

    public void setIsGeo(boolean isGeo) {
        this.isGeo = isGeo;
    }


    /**
     * The status of uploading backup set
     */
    @XmlType(name = "restore_Status")
    public enum Status {
        READY (true, false, false, "Ready"),
        DOWNLOADING (true, false, false, "Downloading"),
        DOWNLOAD_SUCCESS (false, false, true, "Download success"),
        DOWNLOAD_FAILED (false, true, true, "Download failed"),
        DOWNLOAD_CANCELLED (false, true, true, "Download Canceled"),
        RESTORE_FAILED (false, false, false, "Restore failed"),
        RESTORE_SUCCESS (false, false ,false, "Restore success");

        private boolean cancellable = false;
        private boolean removeDownloadedFiles = false;
        private boolean removeListener = false;
        private String message = "";

        Status(boolean cancellable, boolean removeFiles, boolean removeListener, String msg) {
            this.cancellable = cancellable;
            this.removeDownloadedFiles = removeFiles;
            this.removeListener = removeListener;
            message = msg;
        }

        public boolean canBeCanceled() {
            return cancellable;
        }

        public boolean removeListener() {

            return removeListener;
        }

        public String getMessage () {
            return message;
        }
    }

    @XmlElement(name = "downloaded_size")
    public Map<String, Long> getDownoadedSize() {
        return downloadedSize;
    }

    public void increaseDownloadedSize(String node, long size) {
        Long s = downloadedSize.get(node);
        if (s == null) {
            s = (long)0;
        }

        s += size;
        downloadedSize.put(node, s);
    }

    public void setDownloadedSize(String node, long size) {
        downloadedSize.put(node, size);
    }

    @XmlElement(name = "size_to_download")
    public Map<String, Long> getSizeToDownload() {
        return sizeToDownload;
    }

    public void setSizeToDownload(Map<String, Long> sizes) {
        sizeToDownload = sizes;
    }

    public boolean isNotSuccess() {
        return status == Status.DOWNLOAD_FAILED || status == Status.RESTORE_FAILED;
    }

    //convert to Map to persist to ZK
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap();
        map.put(KEY_BACKUP_NAME, backupName);

        String str = toString(sizeToDownload);
        log.info("To persist whole size to download={}", str);
        map.put(KEY_PULL_SIZE, str);

        str = toString(downloadedSize);
        log.info("To persist downloaded size={}", str);
        map.put(KEY_DOWNLOADED_SIZE, str);

        map.put(KEY_STATUS, status.name());
        map.put(KEY_DETAILS_MSG, details);
        map.put(KEY_NODE_COMPLETED, Integer.toString(nodeCompleted));
        map.put(KEY_IS_GEO_ENV, Boolean.toString(isGeo));

        String names = String.join(FILE_NAME_SEPARATOR, backupFileNames);
        map.put(KEY_FILE_NAMES, names);

        return map;
    }

    private String toString(Map<String, Long> map) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Long> s : map.entrySet()) {
            builder.append(MAP_ENTRY_SEPARATOR);
            builder.append(s.getKey());
            builder.append(KEY_VALUE_SEPARATOR);
            builder.append(s.getValue());
        }

        return builder.toString();
    }

    private Map<String, Long> toMap(String s) {
        Map<String, Long> map = new HashMap();

        if (s.isEmpty()) {
            return map;
        }

        String str = s.substring(1); //skip leading MAP_ENTRY_SEPARATOR
        String[] entries = str.split(MAP_ENTRY_SEPARATOR);

        for (String entry : entries) {
            String[] e = entry.split(KEY_VALUE_SEPARATOR);
            map.put(e[0], Long.parseLong(e[1]));
        }

        return map;
    }

    public BackupRestoreStatus(Map<String, String> configs) {
        update(configs);
    }

    private void update(Map<String, String> configs) {
        for (Map.Entry<String, String> config: configs.entrySet()) {
            String key = config.getKey();
            String value = config.getValue();
            switch (key) {
                case KEY_BACKUP_NAME:
                    backupName = value;
                    break;
                case KEY_PULL_SIZE:
                    log.info("get whole size from zk={}", value);
                    sizeToDownload = toMap(value);
                    break;
                case KEY_DOWNLOADED_SIZE:
                    log.info("get downloaded size={}", value);
                    downloadedSize = toMap(value);
                    break;
                case KEY_STATUS:
                    status = Status.valueOf(value);
                    break;
                case KEY_DETAILS_MSG:
                    details = value;
                    break;
                case KEY_NODE_COMPLETED:
                    nodeCompleted = Integer.parseInt(value);
                    break;
                case KEY_FILE_NAMES:
                    String[] names = value.split(FILE_NAME_SEPARATOR);

                    backupFileNames = Arrays.asList(names);
                    break;
                case KEY_IS_GEO_ENV:
                    isGeo = Boolean.valueOf(value);
                    break;
            }
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("BackupName:")
          .append(backupName)
          .append(", size to download:")
          .append(sizeToDownload)
          .append(", downloaded size:")
          .append(downloadedSize)
          .append(", filesDownloaded:")
          .append(backupFileNames)
          .append(", nodesCompleted:")
          .append(nodeCompleted)
          .append(", isGeo:")
          .append(isGeo)
          .append(", status:")
          .append(status)
          .append(", details:")
          .append(details);

        return sb.toString();
    }
}
