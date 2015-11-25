/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_protection")
public class FileVirtualPoolProtectionParam extends VirtualPoolProtectionParam {
	
	FileVirtualPoolReplicationParam replicationParam;
	
	public FileVirtualPoolProtectionParam() {
    }

    public FileVirtualPoolProtectionParam(
    		FileVirtualPoolReplicationParam replicationParam) {
        this.replicationParam = replicationParam;
    }	
}
