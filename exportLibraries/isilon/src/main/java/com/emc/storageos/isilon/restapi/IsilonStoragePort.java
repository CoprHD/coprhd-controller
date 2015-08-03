/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.isilon.restapi;

import java.util.ArrayList;

public class IsilonStoragePort {

    ArrayList<String> zones;

    public ArrayList<String> getZones() {
        return zones;
    }

    private String portName;
    private String ipAddress;
    // port speed
    private Long portSpeed;
    // port container tag, e.g. for front-end director
    private String portGroup;

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Long getPortSpeed() {
        return portSpeed;
    }

    public void setPortSpeed(Long portSpeed) {
        this.portSpeed = portSpeed;
    }

    public String getPortGroup() {
        return portGroup;
    }

    public void setPortGroup(String portGroup) {
        this.portGroup = portGroup;
    }
}
