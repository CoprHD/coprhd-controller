/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
