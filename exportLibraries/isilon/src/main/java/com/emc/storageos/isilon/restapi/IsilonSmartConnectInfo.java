/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.util.ArrayList;

public class IsilonSmartConnectInfo {

    private String description;
    private ArrayList<String> zones;

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("SmartConnectInfo ( description: " + description);
        if (zones != null) {
            str.append(", zones: " + zones.toString());
        }
        return str.toString();
    }

    public ArrayList<IsilonStoragePort> getPorts() {
        ArrayList<IsilonStoragePort> ports = new ArrayList();
        if (zones != null) {
            for (String zone : zones) {
                IsilonStoragePort port = new IsilonStoragePort();
                port.setPortName(zone);
                port.setIpAddress(zone);
                ports.add(port);
            }
        }
        return ports;
    }

}
