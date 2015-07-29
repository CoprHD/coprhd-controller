/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.isilon.restapi;

import java.util.ArrayList;

public class IsilonSmartConnectInfoV2 {

    public static class IsilonSmartZone {
        private String service_ip;
        private ArrayList<String> zones;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("service_ip: " + service_ip);
            if (zones != null) {
                str.append(", zones: " + zones.toString());
            }
            return str.toString();
        }

        public ArrayList<IsilonStoragePort> getPorts() {
            ArrayList<IsilonStoragePort> ports = new ArrayList();
            for (String zone : zones) {
                IsilonStoragePort port = new IsilonStoragePort();
                port.setIpAddress(zone);
                port.setPortName(zone);
                ports.add(port);
            }
            return ports;
        }

        public String getServiceIp() {
            return service_ip;
        }
    }

    private ArrayList<IsilonSmartZone> settings;

    public ArrayList<IsilonSmartZone> getSmartZones() {
        return settings;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        if (settings != null) {
            for (IsilonSmartZone zone : settings) {
                if (zone != null) {
                    str.append("service_ip: " + zone.getServiceIp().toString());
                    str.append(", zones: " + zone.toString());
                    str.append("Port: " + zone.getPorts());
                }
            }
        }
        return str.toString();
    }

    public ArrayList<IsilonStoragePort> getPorts() {
        ArrayList<IsilonStoragePort> ports = new ArrayList();
        if (settings != null) {
            for (IsilonSmartZone zone : settings) {
                ports.addAll(zone.getPorts());
            }
        }
        return ports;
    }
}
