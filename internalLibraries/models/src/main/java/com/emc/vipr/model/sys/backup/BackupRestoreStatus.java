/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

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

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlElement (name = "details")
    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public void setStatusWithDetails(Status s, String details) {
        status = s;
        this.details = details != null ? details : s.getMessage();
    }

    @XmlTransient
    public int getNodeCompleted() {
        return nodeCompleted;
    }

    public void increaseNodeCompleted() {
        nodeCompleted++;
    }

    @XmlElementWrapper(name = "backup_file_names")
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

    public void setGeo(boolean isGeo) {
        this.isGeo = isGeo;
    }

    @XmlElementWrapper(name = "downloaded_size")
    public Map<String, Long> getDownloadedSize() {
        return downloadedSize;
    }

    public void setDownloadedSize(Map<String, Long> downloadedSize) {
        this.downloadedSize = downloadedSize;
    }

    public void increaseDownloadedSize(String node, long size) {
        long s = downloadedSize.get(node) + size;
        downloadedSize.put(node, s);
    }

    @XmlElementWrapper(name = "size_to_download")
    public Map<String, Long> getSizeToDownload() {
        return sizeToDownload;
    }

    public void setSizeToDownload(Map<String, Long> sizes) {
        sizeToDownload = sizes;
    }

    public boolean isNotSuccess() {
        return status == Status.DOWNLOAD_FAILED || status == Status.RESTORE_FAILED;
    }

    /**
     * The status of pulled backup set
     */
    @XmlType(name = "restore_Status")
    public enum Status {
        READY (true, false, "Ready"),
        DOWNLOADING (true, false, "Downloading"),
        DOWNLOAD_SUCCESS (false, true, "Download success"),
        DOWNLOAD_FAILED (false, true, "Download failed"),
        DOWNLOAD_CANCELLED (false, true, "Download Canceled"),
        RESTORING(false, false, "Restoring"),
        RESTORE_FAILED (false, false, "Restore failed"),
        RESTORE_SUCCESS (false, false, "Restore success");

        private boolean cancellable = false;
        private boolean removeListener = false;
        private String message = "";

        Status(boolean cancellable, boolean removeListener, String msg) {
            this.cancellable = cancellable;
            this.removeListener = removeListener;
            message = msg;
        }

        public boolean canBeCanceled() {
            return cancellable;
        }

        public boolean removeListener() {
            return removeListener;
        }

        @XmlTransient
        public String getMessage () {
            return message;
        }
    }


    //convert to Map to persist to ZK
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap();
        map.put(KEY_BACKUP_NAME, backupName);

        String str = toString(sizeToDownload);
        map.put(KEY_PULL_SIZE, str);

        str = toString(downloadedSize);
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

        //skip leading MAP_ENTRY_SEPARATOR
        String str = s.substring(1);

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
                    sizeToDownload = toMap(value);
                    break;
                case KEY_DOWNLOADED_SIZE:
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
        sb.append("\nBackupName:")
          .append(backupName)
          .append("\nsize to download:")
          .append(sizeToDownload)
          .append("\ndownloaded size: ")
          .append(downloadedSize)
          .append("\nfilesDownloaded:")
          .append(backupFileNames)
          .append("\nnodesCompleted:")
          .append(nodeCompleted)
          .append("\nisGeo:")
          .append(isGeo)
          .append("\nstatus:")
          .append(status)
          .append("\ndetails:")
          .append(details);

        return sb.toString();
    }
}
