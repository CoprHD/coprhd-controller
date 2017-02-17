/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author lakhiv
 *
 */
public class FileProtectionRemoteSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    private String replicationType;
    private String replicationCopyMode;
    private String targetVirtualPool;
    private List<String> targetVirtualArrays;

    public FileProtectionRemoteSettings() {

    }

    /**
     * File Replication type
     * Valid values are: LOCAL, REMOTE
     * 
     * @return
     */
    public String getReplicationType() {
        return this.replicationType;
    }

    public void setReplicationType(String replicationType) {
        this.replicationType = replicationType;
    }

    /**
     * File Replication copy type
     * Valid values are: SYNC, ASYNC
     * 
     * @return
     */
    public String getReplicationCopyMode() {
        return this.replicationCopyMode;
    }

    public void setReplicationCopyMode(String replicationCopyMode) {
        this.replicationCopyMode = replicationCopyMode;
    }

    public String getTargetVirtualPool() {
        return targetVirtualPool;
    }

    public void setTargetVirtualPool(String targetVirtualPool) {
        this.targetVirtualPool = targetVirtualPool;
    }

    public List<String> getTargetVirtualArrys() {
        return targetVirtualArrays;
    }

    public void setTargetVirtualArrys(List<String> targetVirtualArrys) {
        this.targetVirtualArrays = targetVirtualArrys;
    }

    public void addTargetVirtualArry(String targetVirtualArray) {
        if (this.targetVirtualArrays == null) {
            this.targetVirtualArrays = new ArrayList<String>();
        }
        this.targetVirtualArrays.add(targetVirtualArray);
    }

}
