/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteNetworkLatency implements CoordinatorSerializable {

    public static final String CONFIG_KIND = "siteNetworkLatency";
    public static final String CONFIG_ID = "global";

    private double networkLatencyInMs;

    public SiteNetworkLatency() {

    }

    private SiteNetworkLatency(double networkLatencyInMs) {
        this.networkLatencyInMs = networkLatencyInMs;
    }

    public double getNetworkLatencyInMs() {
        return networkLatencyInMs;
    }

    public void setNetworkLatencyInMs(double networkLatencyInMs) {
        this.networkLatencyInMs = networkLatencyInMs;
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(networkLatencyInMs);
        return sb.toString();
    }

    @Override
    public SiteNetworkLatency decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }
        
        return new SiteNetworkLatency(Double.parseDouble(infoStr));
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteNetworkLatency");
    }

}
