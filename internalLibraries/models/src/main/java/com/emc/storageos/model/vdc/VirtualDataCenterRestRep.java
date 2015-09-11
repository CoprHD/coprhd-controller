/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vdc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "virtual_data_center")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualDataCenterRestRep extends DataObjectRestRep {

    private static final long NETWORK_ALARM_THRESHOLD = 30 * 60 * 1000; // 30 min

    private String description;
    private String apiEndpoint;
    private String status;
    private Boolean local;
    private String shortId;
    private String geoCommandEndpoint;
    private String geoDataEndpoint;
    private Long lastSeenTimeInMillis;

    private static Set<String> ALLOW_DISCONNECT_STATUS = new HashSet<String>(Arrays.asList("CONNECTED",
            "REMOVE_FAILED", "REMOVE_PRECHECK_FAILED", "UPDATE_FAILED",
            "DISCONNECT_PRECHECK_FAILED", "DISCONNECT_FAILED"));
    private static Set<String> ALLOW_RECONNECT_STATUS = new HashSet<String>(Arrays.asList("DISCONNECTED",
            "RECONNECT_PRECHECK_FAILED", "RECONNECT_FAILED"));

    private static Set<String> DISALLOW_DELETE_STATUS = new HashSet<String>(Arrays.asList("DISCONNECTING",
            "CONNECTING_SYNCED", "RECONNECTING", "DISCONNECT_PRECHECK_FAILED", "RECONNECT_PRECHECK_FAILED",
            "CONNECTING"));

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "apiEndpoint")
    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    @Deprecated
    @XmlElement(name = "connectionStatus")
    public String getConnectionStatus() {
        return status;
    }

    @Deprecated
    public void setConnectionStatus(String connectionStatus) {
        this.status = connectionStatus;
    }

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @XmlElement(name = "local")
    public Boolean isLocal() {
        return local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
    }

    @XmlElement(name = "shortId")
    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    @XmlElement(name = "geoCommandEndpoint")
    public String getGeoCommandEndpoint() {
        return geoCommandEndpoint;
    }

    public void setGeoCommandEndpoint(String geoCommandEndpoint) {
        this.geoCommandEndpoint = geoCommandEndpoint;
    }

    @XmlElement(name = "geoDataEndpoint")
    public String getGeoDataEndpoint() {
        return geoDataEndpoint;
    }

    public void setGeoDataEndpoint(String geoDataEndpoint) {
        this.geoDataEndpoint = geoDataEndpoint;
    }

    @XmlElement(name = "lastSeenTimeInMillis")
    public Long getLastSeenTimeInMillis() {
        return lastSeenTimeInMillis;
    }

    public void setLastSeenTimeInMillis(Long lastSeenTimeInMillis) {
        this.lastSeenTimeInMillis = lastSeenTimeInMillis;
    }

    public boolean canDisconnect() {
        return (Boolean.FALSE.equals(this.local)
        && ALLOW_DISCONNECT_STATUS.contains(this.status != null ? this.status.toUpperCase() : ""));
    }

    public boolean canReconnect() {
        return (Boolean.FALSE.equals(this.local)
        && ALLOW_RECONNECT_STATUS.contains(this.status != null ? this.status.toUpperCase() : ""));
    }

    public boolean canDelete() {
        return (Boolean.FALSE.equals(this.local)
        && !DISALLOW_DELETE_STATUS.contains(this.status != null ? this.status.toUpperCase() : ""));
    }

    public Boolean shouldAlarm() {
        if (this.lastSeenTimeInMillis == null) {
            return false;
        }
        long delta = System.currentTimeMillis() - this.lastSeenTimeInMillis;
        return delta > NETWORK_ALARM_THRESHOLD;
    }

}
