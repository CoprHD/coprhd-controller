/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class FileVirtualPoolReplicationUpdateParam {
	
	private Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> add;
	private Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remove;
    private FileReplicationPolicy fileReplicationPolicy;
    
    public FileVirtualPoolReplicationUpdateParam() {
    }

    public FileVirtualPoolReplicationUpdateParam(
            Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> add,
            Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remove,
            FileReplicationPolicy sourcePolicy) {
        this.add = add;
        this.remove = remove;
        this.fileReplicationPolicy = sourcePolicy;
    }

    @XmlElementWrapper(name = "add_copies")
    /**
     * The file replication protection virtual array settings add to a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "protection_varray_vpool", required = false)
    public Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getAddRemoteCopies() {
        if (add == null) {
            add = new LinkedHashSet<VirtualPoolRemoteProtectionVirtualArraySettingsParam>();
        }
        return add;
    }

    public void setAddRemoteCopies(Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> add) {
        this.add = add;
    }
    
    @XmlElementWrapper(name = "remove_copies")
    /**
     * The file replication protection virtual array settings remove from a virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "protection_varray_vpool", required = false)
    public Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> getRemoveRemoteCopies() {
        if (remove == null) {
            remove = new LinkedHashSet<VirtualPoolRemoteProtectionVirtualArraySettingsParam>();
        }
        return remove;
    }

    public void setRemoveRemoteCopies(Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> remove) {
        this.remove = remove;
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
