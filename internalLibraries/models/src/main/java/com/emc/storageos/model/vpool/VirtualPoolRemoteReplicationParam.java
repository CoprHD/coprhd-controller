/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * Wrapper type for a list of VirtualPoolRemoteReplicationSettingsParam instances.
 */
public class VirtualPoolRemoteReplicationParam {

    public VirtualPoolRemoteReplicationParam() {

    }

    /**
     * The remote replication virtual array settings for a virtual pool.
     * Defines list of target varray/vpool combination for remote replication.
     */
    private List<VirtualPoolRemoteReplicationSettingsParam> remoteReplicationSettings;

    @XmlElementWrapper(name = "remote_replication_settings")
    @XmlElement(name = "remote_replication_setting", required = false)
    public List<VirtualPoolRemoteReplicationSettingsParam> getRemoteReplicationSettings() {
        if (null == remoteReplicationSettings) {
            remoteReplicationSettings = new ArrayList<>();
        }
        return remoteReplicationSettings;
    }

    public VirtualPoolRemoteReplicationParam(
            List<VirtualPoolRemoteReplicationSettingsParam> remoteReplicationSettings) {
        this.remoteReplicationSettings = remoteReplicationSettings;
    }

    public void setRemoteReplicationSettings(List<VirtualPoolRemoteReplicationSettingsParam> remoteReplicationSettings) {
        this.remoteReplicationSettings = remoteReplicationSettings;
    }

}
