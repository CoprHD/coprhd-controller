package com.emc.vipr.model.sys.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.HashMap;
import java.util.Map;

@XmlRootElement(name = "restore_status")
public class BackupRestoreStatus {
    private static final Logger log = LoggerFactory.getLogger(BackupRestoreStatus.class);
    private static final String KEY_BACKUP_NAME = "backupName";
    private static final String KEY_BACKUP_SIZE = "backupSize";
    private static final String KEY_DOWNLOAD_SIZE = "downloadSize";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NODE_COMPLETED= "nodeCompleted";

    private String backupName;
    private long backupSize = 0;
    private long downloadSize = 0;
    private Status status = Status.NOT_STARTED;
    private int nodeCompleted = 0;

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

    public void increaseNodeCompleted() {
        nodeCompleted++;
    }

    /**
     * The status of uploading backup set
     */
    @XmlType(name = "restore_Status")
    public enum Status {
        NOT_STARTED,
        DOWNLOADING,
        DOWNLOAD_SUCCESS,
        DOWNLOAD_FAILED,
        RESTORE_FAILED,
        RESTORE_SUCCESS,
        RESTORE_CANCELLED
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

    //convert to Map to persist to ZK
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap();
        map.put(KEY_BACKUP_NAME, backupName);
        map.put(KEY_BACKUP_SIZE, Long.toString(backupSize));
        map.put(KEY_DOWNLOAD_SIZE, Long.toString(downloadSize));
        map.put(KEY_STATUS, status.name());
        map.put(KEY_NODE_COMPLETED, Integer.toString(nodeCompleted));

        return map;
    }

    public BackupRestoreStatus(Map<String, String> configs) {
        log.info("lbymt0 configs={}", configs);
        update(configs);
    }

    private void update(Map<String, String> configs) {
        log.info("lbymt");
        for (Map.Entry<String, String> config: configs.entrySet()) {
            String key = config.getKey();
            String value = config.getValue();
            switch (key) {
                case KEY_BACKUP_NAME:
                    backupName = value;
                    log.info("lbymt0");
                    break;
                case KEY_BACKUP_SIZE:
                    log.info("lbymt3");
                    backupSize = Long.parseLong(value);
                    break;
                case KEY_DOWNLOAD_SIZE:
                    downloadSize = Long.parseLong(value);
                    break;
                case KEY_STATUS:
                    status = Status.valueOf(value);
                    break;
                case KEY_NODE_COMPLETED:
                    nodeCompleted = Integer.parseInt(value);
                    break;
            }
        }
        log.info("lbymt done");
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("BackupName:");
        sb.append(getBackupName());
        sb.append(", backupSize:");
        sb.append(getBackupSize());
        sb.append(", downloadSize:");
        sb.append(getDownoadSize());
        sb.append(", nodeCompleted:");
        sb.append(getNodeCompleted());
        sb.append(", status:");
        sb.append(getStatus());

        return sb.toString();
    }
}



