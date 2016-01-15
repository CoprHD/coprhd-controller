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
    private String _name;
    
    // id of namespace is native id
    private String _nativeId;

    // Flag if mapped to a tenant or not
    private Boolean _mapped;
    
    // Tenant to which this namepsace is mapped;
    private NamedURI _tenantOrg;
    
    // storage controller where this pool is located
    private URI _storageDevice;
    
    // Type indicating allowed or not-allowed
    private ECS_RepGroup_Type _rgType;
    
    // Allowed or not-allowed ECS replication groups. Its mutually exclusive
    private List<String> _replicationGroups;      

    // Namespace visible or deleted in ECS
    private String _discoveryStatus = DiscoveryStatus.VISIBLE.name();
    
    public enum ECS_RepGroup_Type {
        ALLOWED,
        DISALLOWED,
        NONE
    };
    
    // get set methods
    @Name("name")
    public String getName() {
        return _name;
    }
    
    public void setName(String name) {
        this._name = name;
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }
    
    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
    }

    @Name("mapped")
    public Boolean getMapped() {
        return _mapped;
    }
    
    public void setMapped(Boolean mapped) {
        this._mapped = mapped;
    }
    
    @NamedRelationIndex(cf = "NamedRelationIndex", type = TenantOrg.class)
    @Name("tenantOrg")
    public NamedURI getTenantOrg() {
        return _tenantOrg;
    }

    public void setTenantOrg(NamedURI tenantOrg) {
        _tenantOrg = tenantOrg;
        setChanged("tenantOrg");
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
    }

    @Name("replicationGroups")
    public List<String> getReplicationGroups() {
        return _replicationGroups;
    }
    
    public void setReplicationGroups(List<String> replicationGroups) {
        this._replicationGroups = replicationGroups;
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
