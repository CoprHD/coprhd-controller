/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

@XmlRootElement(name = "backup_info")
public class BackupInfo implements Serializable {
    private static final long serialVersionUID = -1125934063451834517L;

    private static final Logger log = LoggerFactory.getLogger(BackupSets.class);

    private String backupName="";
    private long backupSize = 0;
    private String version ="";
    private String siteId="";
    private String siteName="";
    private long createTime = 0;
    private BackupRestoreStatus restoreStatus;

    public BackupInfo() {
    }

    @XmlElement(name = "backup_name")
    public String getBackupName() {
        return this.backupName;
    }

    public void setBackupName(String name) {
        backupName = name;
    }

    @XmlElement(name = "create_time")
    public long getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @XmlElement(name = "backup_size")
    public long getBackupSize() {
        return backupSize;
    }

    public void setBackupSize(long size) {
        backupSize = size;
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

    @XmlElement(name = "site_id")
    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    @XmlElement(name = "site_name")
    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("FileName:");
        sb.append(backupName);
        sb.append(", version:");
        sb.append(version);
        sb.append(", site_id:");
        sb.append(siteId);
        sb.append(", site_name:");
        sb.append(siteName);
        sb.append(", CreateTime:");
        sb.append(createTime);
        sb.append(", backupSize:");
        sb.append(backupSize);
        sb.append(", RestoreStatus:");
        sb.append(restoreStatus);

        return sb.toString();
    }
}
