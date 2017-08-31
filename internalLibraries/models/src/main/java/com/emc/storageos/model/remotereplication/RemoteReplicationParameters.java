/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "remote_replication")
public class RemoteReplicationParameters {

    private String remoteReplicationMode;
    private URI remoteReplicationSet;
    private URI remoteReplicationGroup;
    private Boolean createInactive = Boolean.FALSE;

    public RemoteReplicationParameters() {
    }

    @XmlElement(name = "replication_mode", required = true)
    public String getRemoteReplicationMode() {
        return remoteReplicationMode;
    }

    public void setRemoteReplicationMode(String remoteReplicationMode) {
        this.remoteReplicationMode = remoteReplicationMode;
    }

    @XmlElement(name = "replication_set", required = true)
    public URI getRemoteReplicationSet() {
        return remoteReplicationSet;
    }

    public void setRemoteReplicationSet(URI remoteReplicationSet) {
        this.remoteReplicationSet = remoteReplicationSet;
    }

    @XmlElement(name = "replication_group", required = false)
    public URI getRemoteReplicationGroup() {
        return remoteReplicationGroup;
    }

    public void setRemoteReplicationGroup(URI remoteReplicationGroup) {
        this.remoteReplicationGroup = remoteReplicationGroup;
    }

    @XmlElement(name = "create_inactive", defaultValue = "false")
    public Boolean getCreateInactive() {
        return createInactive;
    }

    public void setCreateInactive(Boolean createInactive) {
        this.createInactive = createInactive;
    }
}
