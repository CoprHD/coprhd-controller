/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.remotereplication;


import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "remote_replication_group")
public class RemoteReplicationGroupRestRep extends DataObjectRestRep {

    // native id of this group
    private String nativeId;

    // If replication group is reachable.
    private Boolean reachable;

    // Device label of this replication group.
    private String deviceLabel;

    // Type of storage systems in this replication group.
    private String storageSystemType;

    // Display name of this replication group (whem provisioned by the systemt).
    private String displayName;

    // Replication mode of this group
    private String replicationMode;

    // Replication state
    String replicationState;

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @XmlElement(name = "reachable")
    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }

    @XmlElement(name = "name")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    @XmlElement(name = "storage_system_type")
    public String getStorageSystemType() {
        return storageSystemType;
    }

    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
    }

    @XmlElement(name = "display_name")
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @XmlElement(name = "replication_mode")
    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
    }

    @XmlElement(name = "replication_state")
    public String getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(String replicationState) {
        this.replicationState = replicationState;
    }
}
