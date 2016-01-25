/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.valid.EnumType;

/**
 * Object storage namespace configuration
 * 
 * This class extends DiscoveredDataObject and not TenantResource. 
 * There are different Object Replication Groups that can be allowed or disallowed with namespace. 
 * RG can be dynamically changed. Fields are discovered/rediscovered to keep up to date.
 * Hence its better to derive from DiscoveredDataObject  
 */
@Cf("ObjectNamespace")
public class ObjectNamespace extends DiscoveredDataObject {
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
    private Object_StoragePool_Type _poolType;
    
    // Allowed or not-allowed storage pools(ECS replication groups). Its mutually exclusive
    private StringSet _storagePools;      

    // Namespace visible or deleted in Object
    private String _discoveryStatus;
    
    public enum Object_StoragePool_Type {
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

    @Name("poolType")
    public Object_StoragePool_Type setStoragePoolType() {
        return _poolType;
    }
    
    public void setStoragePoolType(Object_StoragePool_Type poolType) {
        this._poolType = poolType;
        setChanged("poolType");
    }

    @Name("storagePools")
    public StringSet getStoragePools() {
        return _storagePools;
    }
    
    public void setStoragePools(StringSet storagePools) {
        this._storagePools = storagePools;
        setChanged("storagePools");
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
