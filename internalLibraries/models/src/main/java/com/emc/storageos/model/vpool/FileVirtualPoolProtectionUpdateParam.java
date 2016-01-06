/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_protection")
public class FileVirtualPoolProtectionUpdateParam extends VirtualPoolProtectionParam {
	
	FileVirtualPoolReplicationUpdateParam replicationParam;
	
	public FileVirtualPoolProtectionUpdateParam() {
    }

    public FileVirtualPoolProtectionUpdateParam(
    		FileVirtualPoolReplicationUpdateParam replicationParam) {
        this.replicationParam = replicationParam;
    }
    
    /**
     * The replication protection settings for a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "replication_params")
    public FileVirtualPoolReplicationUpdateParam getReplicationParam() {
        return replicationParam;
    }

    public void setReplicationParam(FileVirtualPoolReplicationUpdateParam replParam) {
        this.replicationParam = replParam;
    }
}