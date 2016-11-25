/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;

/**
 * 
 * @author jainm15
 *
 */
public class FileReplicationPolicyParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private String replicationType;
    private String replicationCopyMode;
    private boolean replicateConfiguration = false;

    public FileReplicationPolicyParam() {

    }

    /**
     * File Replication type
     * Valid values are: LOCAL, REMOTE
     * 
     * @return
     */
    @XmlElement(name = "replicatio_type")
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
    @XmlElement(name = "replication_copy_mode")
    public String getReplicationCopyMode() {
        return this.replicationCopyMode;
    }

    public void setReplicationCopyMode(String replicationCopyMode) {
        this.replicationCopyMode = replicationCopyMode;
    }

    /**
     * Whether to replicate File System configurations i.e CIFS shares, NFS Exports at the time of failover/failback.
     * Default value is False
     * 
     * @return
     */
    @XmlElement(name = "replicate_configuration")
    public boolean getReplicateConfiguration() {
        return this.replicateConfiguration;
    }

    public void setReplicateConfiguration(boolean replicateConfiguration) {
        this.replicateConfiguration = replicateConfiguration;
    }

}
