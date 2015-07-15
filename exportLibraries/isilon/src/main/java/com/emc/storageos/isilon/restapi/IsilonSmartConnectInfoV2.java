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

public class IsilonSmartConnectInfoV2 {

    public static class IsilonSmartZone {
        private String service_ip;
        private ArrayList<String> zones;

        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append("service_ip: " + service_ip);
            if(zones != null)str.append(", zones: " + zones.toString());
            return str.toString();
        }

        public ArrayList<IsilonStoragePort> getPorts() {
            ArrayList<IsilonStoragePort> ports = new ArrayList();
            for(String zone: zones){
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

    public ArrayList<IsilonSmartZone> getSmartZones(){
        return settings;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        if(settings!= null) {
            for(IsilonSmartZone zone: settings ) {
                str.append("service_ip: " + zone.getServiceIp());
                if(zone != null)str.append(", zones: " + zone.toString());
                str.append("Port: " + zone.getPorts());
            }
        }
        return str.toString();
    }

    public ArrayList<IsilonStoragePort> getPorts() {
        ArrayList<IsilonStoragePort> ports = new ArrayList();
        if(settings!= null) {
            for(IsilonSmartZone zone: settings){
                ports.addAll(zone.getPorts());
            }
        }
        return ports;
    }
}
