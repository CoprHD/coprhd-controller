/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlRootElement(name = "external_backup_info")
public class ExternalBackupInfo {
    private static final Logger log = LoggerFactory.getLogger(BackupSets.class);

    private String fileName;
    private Long createTime;
    private Integer fileSize;
    private BackupRestoreStatus restoreStatus;

    public ExternalBackupInfo() {
    }

    @XmlElement(name = "file_name")
    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @XmlElement(name = "create_time")
    public Long getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    @XmlElement(name = "file_size")
    public Integer getFileSize() {
        return this.fileSize;
    }

    public void setFileSize(Integer fileSize) {
        this.fileSize = fileSize;
    }

    @XmlElement(name = "restore_status")
    public BackupRestoreStatus getRestoreStatus() {
        return this.restoreStatus;
    }

    public void setRestoreStatus(BackupRestoreStatus restoreStatus) {
        this.restoreStatus = restoreStatus;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("FileName:");
        sb.append(getFileName());
        sb.append(", CreateTime:");
        sb.append(getCreateTime());
        sb.append(", FileSize:");
        sb.append(getFileSize());
        sb.append(", RestoreStatus:");
        sb.append(getRestoreStatus());

        return sb.toString();
    }
}
