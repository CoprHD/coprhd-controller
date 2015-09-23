/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import java.net.URI;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "site")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteRestRep extends DataObjectRestRep {
    private String uuid;
    private URI vdcId;
    private String name;
    private String description;
    private String vip;
    private String state;

    @XmlElement(name = "uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @XmlElement(name = "vdc")
    public URI getVdcId() {
        return vdcId;
    }

    public void setVdcId(URI vdcId) {
        this.vdcId = vdcId;
    }

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "vip")
    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
    }

    @XmlElement(name = "state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteRestRep [uuid=");
        builder.append(uuid);
        builder.append(", name=");
        builder.append(name);
        builder.append(", vip=");
        builder.append(vip);
        builder.append(", state=");
        builder.append(state);
        builder.append("]");
        return builder.toString();
    }
}
