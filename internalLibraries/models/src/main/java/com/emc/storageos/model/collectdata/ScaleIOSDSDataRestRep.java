/**
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.collectdata;

import java.util.List;

public class ScaleIOSDSDataRestRep {

    private List<ScaleIODeviceDataRestRep> devices;
    private ScaleIOProtectionDomainDataRestRep protectionDomain;
    private ScaleIOFaultSetDataRestRep faultSet;
    private String id;
    private List<ScaleIOSDSIPDataRestRep> ipList;
    private String protectionDomainId;
    private String name;
    private String sdsState;
    private String port;
    private String faultSetId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ScaleIOSDSIPDataRestRep> getIpList() {
        return ipList;
    }

    public void setIpList(List<ScaleIOSDSIPDataRestRep> ipList) {
        this.ipList = ipList;
    }

    public String getProtectionDomainId() {
        return protectionDomainId;
    }

    public void setProtectionDomainId(String protectionDomainId) {
        this.protectionDomainId = protectionDomainId;
    }

    public String getFaultSetId() {
        return faultSetId;
    }

    public void setFaultSetId(String faultSetId) {
        this.faultSetId = faultSetId;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSdsState() {
        return sdsState;
    }

    public void setSdsState(String sdsState) {
        this.sdsState = sdsState;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }


    public ScaleIOProtectionDomainDataRestRep getProtectionDomain() {
        return protectionDomain;
    }

    public void setProtectionDomain(ScaleIOProtectionDomainDataRestRep protectionDomain) {
        this.protectionDomain = protectionDomain;
    }


    public ScaleIOFaultSetDataRestRep getFaultSet() {
        return faultSet;
    }

    public void setFaultSet(ScaleIOFaultSetDataRestRep faultSet) {
        this.faultSet = faultSet;
    }


    public List<ScaleIODeviceDataRestRep> getDevices() {
        return devices;
    }

    public void setDevices(List<ScaleIODeviceDataRestRep> devices) {
        this.devices = devices;
    }

}
