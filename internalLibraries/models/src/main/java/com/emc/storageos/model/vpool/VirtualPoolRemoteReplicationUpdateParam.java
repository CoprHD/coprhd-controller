/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class VirtualPoolRemoteReplicationUpdateParam {

    private Set<VirtualPoolRemoteReplicationSettingsParam> add;
    private Set<VirtualPoolRemoteReplicationSettingsParam> remove;

    public VirtualPoolRemoteReplicationUpdateParam(
            Set<VirtualPoolRemoteReplicationSettingsParam> add,
            Set<VirtualPoolRemoteReplicationSettingsParam> remove) {
        this.add = add;
        this.remove = remove;
    }

    public VirtualPoolRemoteReplicationUpdateParam() {
    }

    @XmlElementWrapper(name = "add_remote_replication_settings")
    @XmlElement(name = "add_remote_replication_setting", required = false)
    public Set<VirtualPoolRemoteReplicationSettingsParam> getAdd() {
        if (null == add) {
            add = new LinkedHashSet<VirtualPoolRemoteReplicationSettingsParam>();
        }
        return add;
    }

    public void setAdd(Set<VirtualPoolRemoteReplicationSettingsParam> add) {
        this.add = add;
    }

    @XmlElementWrapper(name = "remove_remote_replication_settings")
    @XmlElement(name = "remove_remote_replication_setting", required = false)
    public Set<VirtualPoolRemoteReplicationSettingsParam> getRemove() {
        if (null == remove) {
            remove = new LinkedHashSet<VirtualPoolRemoteReplicationSettingsParam>();
        }
        return remove;
    }

    public void setRemove(Set<VirtualPoolRemoteReplicationSettingsParam> remove) {
        this.remove = remove;
    }
}
