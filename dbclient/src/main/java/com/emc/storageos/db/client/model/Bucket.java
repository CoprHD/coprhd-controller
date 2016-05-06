/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Bucket data object
 */
@Cf("Bucket")
public class Bucket extends DataObject implements ProjectResource {
    //Bucket name at source
    private String name;
    // project this Bucket is associated with
    private NamedURI _project;
    // soft quota capacity in bytes (Notification Quota)
    private Long softQuota;
    // hard quota capacity in bytes (Max Quota)
    private Long hardQuota;
    // class of service for this Bucket
    private URI _virtualPool;
    // Setting pool so that the it is available for the delete method
    private URI _pool;
    // Tenant who owns this Bucket
    private NamedURI _tenant;
    // storage controller where this Bucket is located
    private URI _storageDevice;
    // Virtual Array where this bucket exists
    private URI _virtualArray;
    // storage protocols supported by this Bucket
    private StringSet _protocols;
    // Retention period for objects in bucket
    private Integer _retention;
    // Bucket Path
    private String _path;
    // Bucket namespace
    private String _namespace;
    // Bucket Owner
    private String _owner;
    // native device ID as created by storage device
    private String _nativeId;
    // version of the bucket created. Used by vipr developers only.
    private String version;

    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @XmlTransient
    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return _tenant;
    }

    public void setTenant(NamedURI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    @Name("softQuota")
    public Long getSoftQuota() {
        return (null == softQuota) ? 0L : softQuota;
    }

    public void setSoftQuota(Long softQuota) {
        this.softQuota = softQuota;
        setChanged("softQuota");
    }

    @Name("hardQuota")
    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    public Long getHardQuota() {
        return (null == hardQuota) ? 0L : hardQuota;
    }

    public void setHardQuota(Long hardQuota) {
        this.hardQuota = hardQuota;
        setChanged("hardQuota");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualPool.class)
    @Name("virtualPool")
    public URI getVirtualPool() {
        return _virtualPool;
    }

    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
        setChanged("virtualPool");
    }

    @RelationIndex(cf = "RelationIndex", type = StoragePool.class)
    @Name("pool")
    public URI getPool() {
        return _pool;
    }

    public void setPool(URI pool) {
        _pool = pool;
        setChanged("pool");
    }

    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageDevice() {
        return _storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        _storageDevice = storageDevice;
        setChanged("storageDevice");
    }

    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @Name("varray")
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @Name("protocols")
    public StringSet getProtocol() {
        return _protocols;
    }

    public void setProtocol(StringSet protocols) {
        _protocols = protocols;
        setChanged("protocols");
    }

    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
        setChanged("nativeId");
    }

    @Name("retention")
    public Integer getRetention() {
        return null!=_retention ? _retention : 0;
    }

    public void setRetention(Integer retention) {
        this._retention = retention;
        setChanged("retention");
    }

    @Name("path")
    public String getPath() {
        return _path;
    }

    public void setPath(String path) {
        this._path = path;
        setChanged("path");
    }

    @Name("namespace")
    public String getNamespace() {
        return _namespace;
    }

    public void setNamespace(String namespace) {
        this._namespace = namespace;
        setChanged("namespace");
    }

    @Name("owner")
    public String getOwner() {
        return _owner;
    }

    public void setOwner(String owner) {
        this._owner = owner;
        setChanged("owner");
    }

    @Name("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setChanged("name");
    }

    @Name("version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        setChanged("version");
    }
    
}
