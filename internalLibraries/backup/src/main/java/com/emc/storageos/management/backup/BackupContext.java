/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.management.backup;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class BackupContext {
    private static final Logger log = LoggerFactory.getLogger(BackupContext.class);
    private File backupDir;
    private String nodeId;
    private String nodeName;
    private List<String> vdcList;

    /**
     * Sets local location which stores backup files
     * 
     * @param backupDirParam
     *            The new location path
     */
    public void setBackupDir(final File backupDir) {
        if (backupDir == null) {
            log.error("Invalid backup location: null");
            return;
        }
        if (!backupDir.exists()) {
            if (!backupDir.mkdirs()) {
                log.error("Can't create backup folder: {}", backupDir.getAbsolutePath());
            } else {
                log.info("ViPR backup folder is created: {}", backupDir.getAbsoluteFile());
            }
        }
        this.backupDir = backupDir;
    }

    /**
     * Gets backup directory
     */
    public File getBackupDir() {
        return this.backupDir;
    }

    /**
     * Sets id of current node
     * 
     * @param nodeIdParam
     *            The id of node
     */
    public void setNodeId(String nodeId) {
        Preconditions.checkArgument(nodeId != null && !nodeId.trim().isEmpty(),
                "ViPR node id is invalid");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public void setNodeName(String nodeName) {
        Preconditions.checkArgument(nodeName != null && !nodeName.trim().isEmpty(),
                "ViPR node name is invalid");
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return this.nodeName;
    }

    public void setVdcList(List<String> vdcList) {
        this.vdcList = vdcList;
    }

    public List<String> getVdcList() {
        return this.vdcList;
    }
}
