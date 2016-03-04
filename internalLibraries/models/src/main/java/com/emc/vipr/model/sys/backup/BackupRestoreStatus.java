/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlRootElement(name = "restore_status")
public class BackupRestoreStatus {
    private static final Logger log = LoggerFactory.getLogger(BackupRestoreStatus.class);
    private static final String KEY_BACKUP_NAME = "backupName";
    private static final String KEY_BACKUP_SIZE = "backupSize";
    private static final String KEY_DOWNLOAD_SIZE = "downloadSize";
    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_NODE_COMPLETED= "nodeCompleted";
    private static final String KEY_IS_GEO_ENV= "isGeo";

    private String backupName;
    private long backupSize = 0;
    private long downloadSize = 0;
    private Status status = Status.READY;
    private int nodeCompleted = 0;
    private boolean isGeo = false;

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

    public int getNodeCompleted() {
        return nodeCompleted;
    }

    public void resetNodeCompleted() {
        this.nodeCompleted = 0;
    }

    @XmlElement (name = "is_geo")
    public boolean isGeo() {
        return isGeo;
    }

    public void setIsGeo(boolean isGeo) {
        this.isGeo = isGeo;
    }

    public void increaseNodeCompleted() {
        nodeCompleted++;
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

        public boolean removeDownloadFiles() {
            return removeDownloadedFiles;
        }

        public boolean removeListener() {
            return removeListener;
        }

        public void setMessage(String msg) {
            message = msg;
        }

        public String getMessage () {
            return message;
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

    @XmlElement(name = "download_size")
    public long getDownoadSize() {
        return downloadSize;
    }

    public void setBackupSize(long size) {
        backupSize = size;
    }

    @XmlElement(name = "backup_size")
    public long getBackupSize() {
        return backupSize;
    }

    public void setDownoadSize(long size) {
        downloadSize = size;
    }

    public boolean isNotSuccess() {
        return status == Status.DOWNLOAD_FAILED || status == Status.RESTORE_FAILED;
    }

    //convert to Map to persist to ZK
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap();
        map.put(KEY_BACKUP_NAME, backupName);
        map.put(KEY_BACKUP_SIZE, Long.toString(backupSize));
        map.put(KEY_DOWNLOAD_SIZE, Long.toString(downloadSize));
        map.put(KEY_STATUS, status.name());
        map.put(KEY_MESSAGE, status.getMessage());
        map.put(KEY_NODE_COMPLETED, Integer.toString(nodeCompleted));
        map.put(KEY_IS_GEO_ENV, Boolean.toString(isGeo));

        return map;
    }

    public BackupRestoreStatus(Map<String, String> configs) {
        update(configs);
    }

    private void update(Map<String, String> configs) {
        for (Map.Entry<String, String> config: configs.entrySet()) {
            String key = config.getKey();
            String value = config.getValue();
            String message = null;
            switch (key) {
                case KEY_BACKUP_NAME:
                    backupName = value;
                    break;
                case KEY_BACKUP_SIZE:
                    backupSize = Long.parseLong(value);
                    break;
                case KEY_DOWNLOAD_SIZE:
                    downloadSize = Long.parseLong(value);
                    break;
                case KEY_STATUS:
                    status = Status.valueOf(value);
                    if (message != null) {
                        status.setMessage(message);
                    }
                    break;
                case KEY_MESSAGE:
                    message = value;
                    if (status != null) {
                        status.setMessage(message);
                    }
                    break;
                case KEY_NODE_COMPLETED:
                    nodeCompleted = Integer.parseInt(value);
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
          .append(getBackupName())
          .append(", backupSize:")
          .append(getBackupSize())
          .append(", downloadSize:")
          .append(getDownoadSize())
          .append(", nodeCompleted:")
          .append(getNodeCompleted())
          .append(", isGeo:")
          .append(isGeo())
          .append(", status:")
          .append(getStatus());

        return sb.toString();
    }
}
