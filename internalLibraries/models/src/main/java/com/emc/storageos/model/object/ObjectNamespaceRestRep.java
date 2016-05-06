/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.object;

import java.net.URI;

import javax.xml.bind.annotation.*;

import com.emc.storageos.model.DiscoveredDataObjectRestRep;

@XmlRootElement(name = "object_namespace")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ObjectNamespaceRestRep extends DiscoveredDataObjectRestRep {
    private String nsName;
    private String nativeId;
    private Boolean mapped;
    private URI tenant;
    private URI storageDevice;

    // get set methods
    @XmlElement(name = "namespace_name")
    public String getNsName() {
        return nsName;
    }

    public void setNsName(String nsName) {
        this.nsName = nsName;
    }

    @XmlElement(name = "namespace_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    @XmlElement(name = "mapped")
    public Boolean getMapped() {
        return mapped;
    }

    public void setMapped(Boolean mapped) {
        this.mapped = mapped;
    }

    @XmlElement(name = "tenant")
    public URI getTenant() {
        return tenant;
    }

    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    @XmlElement(name = "namespace_storage_system")
    public URI getStorageDevice() {
        return storageDevice;
    }

    public void setStorageDevice(URI storageDevice) {
        this.storageDevice = storageDevice;
    }

}
