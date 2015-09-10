/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.object;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlRootElement(name = "bucket")
public class BucketRestRep extends DataObjectRestRep {
    private RelatedResourceRep project;
    private RelatedResourceRep tenant;
    private String softQuota;
    private String hardQuota;
    private RelatedResourceRep vpool;
    private RelatedResourceRep varray;
    private Set<String> protocols;
    private RelatedResourceRep storageSystem;
    private RelatedResourceRep pool;
    private RelatedResourceRep storagePort;
    private String namespace;
    private String owner;
    private String retention;
    private String path;
    private String nativeId;

    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    /**
     * SoftQuota of the bucket in GB
     * 
     * @valid none
     */
    @XmlElement(name = "soft_quota")
    public String getSoftQuota() {
        return softQuota;
    }

    public void setSoftQuota(String softQuota) {
        this.softQuota = softQuota;
    }

    /**
     * HardQuota of the Bucket in GB
     * 
     * @valid none
     */
    @XmlElement(name = "hard_quota")
    public String getHardQuota() {
        return hardQuota;
    }

    public void setHardQuota(String hardQuota) {
        this.hardQuota = hardQuota;
    }

    /**
     * URI for the virtual pool the Bucket resides on.
     * 
     * @valid none
     */
    @XmlElement(name = "vpool")
    @JsonProperty("vpool")
    public RelatedResourceRep getVirtualPool() {
        return vpool;
    }

    public void setVirtualPool(RelatedResourceRep vpool) {
        this.vpool = vpool;
    }

    /**
     * URI for the virtual array containing the virtual pool and the Bucket.
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return varray;
    }

    public void setVirtualArray(RelatedResourceRep varray) {
        this.varray = varray;
    }

    /**
     * URI for the storage pool containing storage allocated for the Bucket.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_pool")
    public RelatedResourceRep getPool() {
        return pool;
    }

    public void setPool(RelatedResourceRep pool) {
        this.pool = pool;
    }

    /**
     * URI for the project containing the Bucket.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getProject() {
        return project;
    }

    public void setProject(RelatedResourceRep project) {
        this.project = project;
    }

    /**
     * Set of valid protocols (S3, Swift & Atmos).
     */
    @XmlElementWrapper(name = "protocols")
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new LinkedHashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * URI representing the storage system supporting the Bucket.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_system")
    public RelatedResourceRep getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(RelatedResourceRep storageSystem) {
        this.storageSystem = storageSystem;
    }

    /**
     * URI representing the storage port.
     * 
     * @valid 1 - 65535
     */
    @XmlElement(name = "storage_port")
    public RelatedResourceRep getStoragePort() {
        return storagePort;
    }

    public void setStoragePort(RelatedResourceRep storagePort) {
        this.storagePort = storagePort;
    }

    /**
     * The URI of the tenant to which the Bucket belongs.
     * 
     * @valid none
     */
    @XmlElement
    public RelatedResourceRep getTenant() {
        return tenant;
    }

    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }

    @XmlElement
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @XmlElement
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @XmlElement
    public String getRetention() {
        return retention;
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }

    @XmlElement
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
