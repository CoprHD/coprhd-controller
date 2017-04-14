/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.remotereplication;


import java.net.URI;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;

@Cf("RemoteReplicationGroup")
public class RemoteReplicationGroup extends DiscoveredDataObject {

    // native id of this group
    private String nativeId;

    // If replication group is reachable.
    private Boolean reachable;

    // Device label of this replication group.
    private String deviceLabel;

    // Type of storage systems in this replication group.
    private String storageSystemType;

    // Display name of this replication group (when provisioned by the system).
    private String displayName;

    // Source storage system of this group
    private URI sourceSystem;

    // Target storage system of this group
    private URI targetSystem;

    // replication mode of this group
    private String replicationMode;


    // replication state of this group
    private String replicationState;

    // Defines if group consistency of link operations is enforced.
    private Boolean isGroupConsistencyEnforced;

    // Properties of replication group
    private StringMap properties;

    @Deprecated
    private URI replicationSet;

    // Tenant of this pair
    private NamedURI tenant;


    @Name("tenant")
    public NamedURI getTenant() {
        return tenant;
    }

    public void setTenant(NamedURI tenant) {
        this.tenant = tenant;
        setChanged("tenant");
    }

    @Name("nativeId")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
        setChanged("nativeId");
    }

    @Name("reachable")
    public Boolean getReachable() {
        return reachable == null ? false : reachable;
    }

    public void setReachable(final Boolean reachable) {
        this.reachable = reachable;
        setChanged("reachable");
    }

    @AlternateId("AltIdIndex")
    @Name("storageSystemType")
    public String getStorageSystemType() {
        return storageSystemType;
    }


    public void setStorageSystemType(String storageSystemType) {
        this.storageSystemType = storageSystemType;
        setChanged("storageSystemType");
    }

    @Name("deviceLabel")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
        setChanged("deviceLabel");
    }


    @Name("displayName")
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        setChanged("displayName");
    }

    @RelationIndex(cf = "RRGroupSourceSystem", type = StorageSystem.class)
    @Name("sourceSystem")
    public URI getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(URI sourceSystem) {
        this.sourceSystem = sourceSystem;
        setChanged("sourceSystem");
    }

    @RelationIndex(cf = "RRGroupTargetSystem", type = StorageSystem.class)
    @Name("targetSystem")
    public URI getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(URI targetSystem) {
        this.targetSystem = targetSystem;
        setChanged("targetSystem");
    }

    @Name("isGroupConsistencyEnforced")
    public Boolean getIsGroupConsistencyEnforced() {
        return isGroupConsistencyEnforced;
    }

    public void setIsGroupConsistencyEnforced(Boolean isGroupConsistencyEnforced) {
        this.isGroupConsistencyEnforced = isGroupConsistencyEnforced;
        setChanged("isGroupConsistencyEnforced");
    }

    @Name("replicationMode")
    public String getReplicationMode() {
        return replicationMode;
    }

    public void setReplicationMode(String replicationMode) {
        this.replicationMode = replicationMode;
        setChanged("replicationMode");
    }

    @Name("replicationState")
    public String getReplicationState() {
        return replicationState;
    }

    public void setReplicationState(String replicationState) {
        this.replicationState = replicationState;
        setChanged("replicationState");
    }

    @Name("properties")
    public StringMap getProperties() {
        return properties;
    }

    public void setProperties(StringMap properties) {
        this.properties = properties;
        setChanged("properties");
    }

    public void addProperty(String name, String value) {
        if (getProperties() == null) {
            setProperties(new StringMap());
        }
        getProperties().put(name, value);
    }

    @Override
    public String toString() {
        return getLabel()+"/"+_id;
    }

    @Deprecated
    @RelationIndex(cf = "RelationIndex", type = RemoteReplicationSet.class)
    @Name("replicationSet")
    public URI getReplicationSet() {
        return replicationSet;
    }

    @Deprecated
    public void setReplicationSet(URI replicationSet) {
        this.replicationSet = replicationSet;
        setChanged("replicationSet");
    }
}
