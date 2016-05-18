/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.dr;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "site")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class SiteRestRep extends DataObjectRestRep {
    private String uuid;
    private String vdcShortId;
    private String sitename;
    private String description;
    private String vipEndpoint;
    private String state;
    private String networkHealth;
    private long createTime;
    private Boolean runningState;

    @XmlElement(name = "create_time")
    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @XmlElement(name = "uuid")
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @XmlElement(name = "vdc_id")
    public String getVdcShortId() {
        return vdcShortId;
    }

    public void setVdcShortId(String vdcShortId) {
        this.vdcShortId = vdcShortId;
    }

    @XmlElement(name = "name")
    public String getName() {
        return sitename;
    }

    public void setName(String name) {
        this.sitename = name;
    }

    @XmlElement(name = "vip_endpoint")
    public String getVipEndpoint() {
        return vipEndpoint;
    }

    public void setVipEndpoint(String vipEndpoint) {
        this.vipEndpoint = vipEndpoint;
    }

    @XmlElement(name = "state")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "network_health")
    public String getNetworkHealth() {
        return networkHealth;
    }

    public void setNetworkHealth(String networkHealth) {
        this.networkHealth = networkHealth;
    }
    
    @XmlElement(name = "running_state")
    public Boolean getRunningState() {
        return runningState;
    }

    public void setRunningState(Boolean runningState) {
        this.runningState = runningState;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteRestRep [uuid=");
        builder.append(uuid);
        builder.append(", vdcId=");
        builder.append(vdcShortId);
        builder.append(", name=");
        builder.append(sitename);
        builder.append(", description=");
        builder.append(description);
        builder.append(", vip=");
        builder.append(vipEndpoint);
        builder.append(", state=");
        builder.append(state);
        builder.append(", networkHealth=");
        builder.append(networkHealth);
        builder.append(", runningState=");
        builder.append(runningState);
        builder.append("]");
        return builder.toString();
    }

    
}
