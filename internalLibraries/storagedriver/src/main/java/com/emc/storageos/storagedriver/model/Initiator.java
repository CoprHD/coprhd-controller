/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.storagedriver.model;

public class Initiator extends StorageObject {


    /**
     * Initiator port identifier. For FC, this is the port WWN.
     * For iSCSI, this is port name in IQN or EUI format.
     * For IP port this is port IP address.
     */
    private String port;

    /**
     * For IP initiators this is initiator port network mask.
     */
    private String networkMask;

    /**
     * Initiator MAC address.
     */
    private String macAddress;

    /**
     * The initiator node identifier.
     */
    private String node;

    /**
     * Port communication protocol
     */
    private Protocol protocol;

    public static enum Protocol {
        FC,
        iSCSI,
        IPV4,
        IPV6,
        ScaleIO
    }

    /**
     * Type of initiator: host initiator, cluster initiator, vplex backend initiator, etc.
     * For host/cluster export requests initiator type is set to host/cluster respectively
     * to indicate to driver context of this request.
     */
    private Type initiatorType;
    public static enum Type {
        Host,
        Cluster,
        VPLEX,
        RP
    }

    /**
     * Type of host OS.
     */
    private HostOsType hostOsType;
    public static enum HostOsType {
        Windows, HPUX, Linux, Esx, AIX, AIXVIO, SUNVCS, No_OS, Other
    }

    /**
     * The FQDN of the initiator host.
     */
    private String hostName;

    /**
     * The FQDN of the cluster.
     */
    private String clusterName;

    /**
     * Initiator network native id. Should be set by driver.
     */
    private String networkId;

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public Type getInitiatorType() {
        return initiatorType;
    }

    public void setInitiatorType(Type initiatorType) {
        this.initiatorType = initiatorType;
    }

    public String getNetworkMask() {
        return networkMask;
    }

    public void setNetworkMask(String networkMask) {
        this.networkMask = networkMask;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public HostOsType getHostOsType() {
        return hostOsType;
    }

    public void setHostOsType(HostOsType hostOsType) {
        this.hostOsType = hostOsType;
    }

    @Override
    public String toString() {
        return "Initiator port: "+ port+"; native id:"+getNativeId();
    }
}
