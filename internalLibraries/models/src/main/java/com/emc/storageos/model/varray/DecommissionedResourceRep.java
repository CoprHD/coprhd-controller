/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "decommissioned_resource")
public class DecommissionedResourceRep {
    private String user;
    private String type;
    private String nativeGuid;
    private String decommissionedId;

    public DecommissionedResourceRep() {
    }

    /**
     * The user name.
     * 
     * 
     * @return The user name.
     */
    @XmlElement
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * The decommissioned resource type.
     * 
     * 
     * @return The decommissioned resource type
     */
    @XmlElement
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The native guid (unique device id) of the decommissioned resource.
     * 
     * 
     * @return The native guid of the decommissioned resource.
     */
    @XmlElement(name = "native_guid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }

    /**
     * The id of the decommissioned resource.
     * 
     * 
     * @return The id of the decommissioned resource.
     */
    @XmlElement(name = "decommissioned_id")
    public String getDecommissionedId() {
        return decommissionedId;
    }

    public void setDecommissionedId(String disposedId) {
        this.decommissionedId = disposedId;
    }
}
