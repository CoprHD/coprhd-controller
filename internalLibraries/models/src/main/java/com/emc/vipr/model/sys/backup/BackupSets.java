/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.sys.backup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlRootElement(name = "backupsets")
public class BackupSets {
    private static final Logger log = LoggerFactory.getLogger(BackupSets.class);

    private List<BackupSet> backupSets;

    public BackupSets() {
    }

    @XmlElementWrapper(name = "backupsets_info")
    @XmlElement(name = "backupset")
    public List<BackupSet> getBackupSets() {
        if (backupSets == null) {
            backupSets = new ArrayList<BackupSet>();
        }
        return backupSets;
    }

    public void setBackupSets(List<BackupSet> backupSets) {
        this.backupSets = backupSets;
    }

    /**
     * Class to encapsulate the data for a specific backup set.
     */
    @XmlRootElement(name = "backupset")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class BackupSet {
        private String name;
        private long size = 0;
        private long createTime = 0;
        private BackupUploadStatus uploadStatus;

        public BackupSet() {
        }

        public BackupSet(String name, long size, long createTime, BackupUploadStatus uploadStatus) {
            this.name = name;
            this.size = size;
            this.createTime = createTime;
            this.uploadStatus = uploadStatus;
        }

        @XmlElement(name = "name")
        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement(name = "size")
        public long getSize() {
            return this.size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        @XmlElement(name = "create_time")
        public long getCreateTime() {
            return this.createTime;
        }

        public void setCreateTime(long time) {
            this.createTime = time;
        }

        @XmlElement(name = "upload_status")
        public BackupUploadStatus getUploadStatus() {
            return this.uploadStatus;
        }

        public void setUploadStatus(BackupUploadStatus uploadStatus) {
            this.uploadStatus = uploadStatus;
        }
    }
}
