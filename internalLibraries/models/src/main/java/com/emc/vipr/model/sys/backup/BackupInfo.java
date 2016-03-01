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
public class BackupInfo {
    private static final Logger log = LoggerFactory.getLogger(BackupSets.class);

    private String fileName;
    private long createTime;
    private long fileSize;
    private String version;
    private BackupRestoreStatus restoreStatus;

    public BackupInfo() {
    }

    @XmlElement(name = "file_name")
    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @XmlElement(name = "create_time")
    public long getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @XmlElement(name = "file_size")
    public long getFileSize() {
        return this.fileSize;
    }

    public void setFileSize(long size) {
        this.fileSize = size;
    }

    @XmlElement(name = "restore_status")
    public BackupRestoreStatus getRestoreStatus() {
        return this.restoreStatus;
    }

    public void setRestoreStatus(BackupRestoreStatus restoreStatus) {
        this.restoreStatus = restoreStatus;
    }

    @XmlElement(name = "version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("FileName:");
        sb.append(getFileName());
        sb.append(", version:");
        sb.append(getVersion());
        sb.append(", CreateTime:");
        sb.append(getCreateTime());
        sb.append(", FileSize:");
        sb.append(getFileSize());
        sb.append(", RestoreStatus:");
        sb.append(getRestoreStatus());

        return sb.toString();
    }
}
