/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.valid.EnumType;

/**
 * ECS object storage namespace configuration
 * 
 * This class extends DiscoveredDataObject and not TenantResource. 
 * There are different ECS Replication Groups that can be allowed or disallowed with namespace. 
 * RG can be dynamically changed. Fields are discovered/rediscovered to keep up to date.
 * Hence its better to derive from DiscoveredDataObject  
 */
@Cf("ECSNamespace")
public class ECSNamespace extends DiscoveredDataObject {
    // name of namespace; Its possible to have namespace name and id different
    private String _nsName;
    
    // id of namespace is native id
    private String _nativeId;

    // Flag if mapped to a tenant or not
    private Boolean _mapped;
    
    // Tenant to which this namepsace is mapped;
    private URI _tenant;
    
    // storage controller where this pool is located
    private URI _storageDevice;
    
    // Type indicating allowed or not-allowed
    private ECS_RepGroup_Type _rgType;
    
    // Allowed or not-allowed ECS replication groups. Its mutually exclusive
    private StringSet _replicationGroups;      

    // Namespace visible or deleted in ECS
    private String _discoveryStatus;
    
    public enum ECS_RepGroup_Type {
        ALLOWED,
        DISALLOWED,
        NONE
    };
    
    // get set methods
    @Name("nsName")
    public String getNsName() {
        return _nsName;
    }
    
    public void setNsName(String nsName) {
        this._nsName = nsName;
        setChanged("nsName");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }
    
    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
        setChanged("nativeId");
    }

    @Name("mapped")
    public Boolean getMapped() {
        return _mapped;
    }
    
    public void setMapped(Boolean mapped) {
        this._mapped = mapped;
        setChanged("mapped");
    }
    
    @RelationIndex(cf = "RelationIndex", type = TenantOrg.class)
    @Name("tenant")
    public URI getTenant() {
        return _tenant;
    }

    public void setTenant(URI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        this._storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @Name("rgType")
    public ECS_RepGroup_Type getRgType() {
        return _rgType;
    }
    
    public void setRgType(ECS_RepGroup_Type rgType) {
        this._rgType = rgType;
        setChanged("rgType");
    }

    @Name("replicationGroups")
    public StringSet getReplicationGroups() {
        return _replicationGroups;
    }
    
    public void setReplicationGroups(StringSet replicationGroups) {
        this._replicationGroups = replicationGroups;
        setChanged("replicationGroups");
    }
    
    @EnumType(DiscoveryStatus.class)
    @Name("discoveryStatus")
    public String getDiscoveryStatus() {
        return _discoveryStatus;
    }

    public void setDiscoveryStatus(String discoveryStatus) {
        this._discoveryStatus = discoveryStatus;
        setChanged("discoveryStatus");
    }

}
