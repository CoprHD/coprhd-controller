/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.map.annotate.JsonSerialize;

@XmlRootElement
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class FileSystemParam {
    private VNXeBase pool;
    private VNXeBase nasServer;
    // private FSSupportedProtocolEnum supportedProtocols;
    private int supportedProtocols;
    private boolean isThinEnabled;
    private long size;
    private long sizeAllocated;
    private FastVPParam fastVPParameters;
    private boolean isCacheDisabled;
    private DeduplicationParam deduplication;

    public VNXeBase getPool() {
        return pool;
    }

    public void setPool(VNXeBase pool) {
        this.pool = pool;
    }

    public int getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(int supportedProtocol) {
        this.supportedProtocols = supportedProtocol;
    }

    public VNXeBase getNasServer() {
        return nasServer;
    }

    public void setNasServer(VNXeBase nasServer) {
        this.nasServer = nasServer;
    }

    /*
     * public FSSupportedProtocolEnum getSupportedProtocols() {
     * return supportedProtocols;
     * }
     * public void setSupportedProtocols(FSSupportedProtocolEnum supportedProtocols) {
     * this.supportedProtocols = supportedProtocols;
     * }
     */
    public boolean getIsThinEnabled() {
        return isThinEnabled;
    }

    public void setIsThinEnabled(boolean isThinEnabled) {
        this.isThinEnabled = isThinEnabled;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getSizeAllocated() {
        return sizeAllocated;
    }

    public void setSizeAllocated(long sizeAllocated) {
        this.sizeAllocated = sizeAllocated;
    }

    public FastVPParam getFastVPParameters() {
        return fastVPParameters;
    }

    public void setFastVPParameters(FastVPParam fastVPParameters) {
        this.fastVPParameters = fastVPParameters;
    }

    public boolean getIsCacheDisabled() {
        return isCacheDisabled;
    }

    public void setIsCacheDisabled(boolean isCacheDisabled) {
        this.isCacheDisabled = isCacheDisabled;
    }

    public DeduplicationParam getDeduplication() {
        return deduplication;
    }

    public void setDeduplication(DeduplicationParam deduplication) {
        this.deduplication = deduplication;
    }

}
