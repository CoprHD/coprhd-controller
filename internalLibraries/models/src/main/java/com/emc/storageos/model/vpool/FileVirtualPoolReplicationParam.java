/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class FileVirtualPoolReplicationParam {
	
	private Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies;
    private FileReplicationPolicy fileReplicationPolicy;
    
    public FileVirtualPoolReplicationParam() {
    }

    public FileVirtualPoolReplicationParam(
            Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies,
            FileReplicationPolicy sourcePolicy) {
        this.copies = copies;
        this.fileReplicationPolicy = sourcePolicy;
    }

    @XmlElementWrapper(name = "copies")
    /**
     * The file replication protection virtual array settings for a virtual pool.
     * 
     */
    @XmlElement(name = "protection_varray_vpool", required = false)
    public Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getCopies() {
        if (copies == null) {
            copies = new LinkedHashSet<VirtualPoolRemoteProtectionVirtualArraySettingsParam>();
        }
        return copies;
    }

    public void setCopies(Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies) {
        this.copies = copies;
    }

    /**
     * The file replication protection policy for a virtual pool.
     * 
     */
    @XmlElement(name = "file_replication_policy")
    public FileReplicationPolicy getSourcePolicy() {
        return fileReplicationPolicy;
    }

    public void setSourcePolicy(FileReplicationPolicy sourcePolicy) {
        this.fileReplicationPolicy = sourcePolicy;
    }
}
