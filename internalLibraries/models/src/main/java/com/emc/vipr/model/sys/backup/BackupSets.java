/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys.backup;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlRootElement(name = "backupsets")
public class BackupSets {

    private List<BackupSet> backupSets;

    public BackupSets() {}

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

        public BackupSet() {}

        public BackupSet(String name, long size, long createTime) {
            this.name = name;
            this.size = size;
            this.createTime = createTime;
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
    }
}
