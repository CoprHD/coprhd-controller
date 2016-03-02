/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.FatalCoordinatorException;

public class SiteNetworkState implements CoordinatorSerializable {

    public static final String CONFIG_KIND = "siteNetworkState";
    public static final String CONFIG_ID = "global";
    private static final String ENCODING_SEPARATOR = "\0";

    private NetworkHealth networkHealth;
    private double networkLatencyInMs;

    public enum NetworkHealth {
        GOOD,
        SLOW,
        BROKEN
    }

    public SiteNetworkState() {

    }

    private SiteNetworkState(double networkLatencyInMs, NetworkHealth networkHealth) {
        this.networkLatencyInMs = networkLatencyInMs;
        this.networkHealth = networkHealth;
    }

    public double getNetworkLatencyInMs() {
        return networkLatencyInMs;
    }

    public void setNetworkLatencyInMs(double networkLatencyInMs) {
        this.networkLatencyInMs = networkLatencyInMs;
    }

    public NetworkHealth getNetworkHealth() {
        return networkHealth;
    }

    public void setNetworkHealth(NetworkHealth networkHealth) {
        this.networkHealth = networkHealth;
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(networkLatencyInMs);
        sb.append(ENCODING_SEPARATOR);
        sb.append(networkHealth);
        return sb.toString();
    }

    @Override
    public SiteNetworkState decodeFromString(String infoStr) throws FatalCoordinatorException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length != 2) {
            throw CoordinatorException.fatals.decodingError("invalid site monitor network info");
        }

        return new SiteNetworkState(Double.parseDouble(strings[0]), NetworkHealth.valueOf(strings[1]));

    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteNetworkState");
    }

}
