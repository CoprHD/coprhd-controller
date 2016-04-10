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

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class BackupContext {
    private static final Logger log = LoggerFactory.getLogger(BackupContext.class);
    private File backupDir;
    private String nodeId;
    private String nodeName;
    private CoordinatorClient coordinatorClient;

    /**
     * Sets local location which stores backup files
     * 
     * @param backupDir
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
     * @param nodeId
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

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public boolean isGeoEnv() {
        DrUtil drUtil = new DrUtil(coordinatorClient);
        return !drUtil.getOtherVdcIds().isEmpty();
    }
}
