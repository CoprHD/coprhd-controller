/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;

/**
 * Bucket data object
 */
@Cf("Bucket")
public class Bucket extends DataObject implements ProjectResource {
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
    // native device ID as created by storage device
    private String nativeId;

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
    @AlternateId("AltIdIndex")
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
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
        setChanged("nativeId");
    }
}
