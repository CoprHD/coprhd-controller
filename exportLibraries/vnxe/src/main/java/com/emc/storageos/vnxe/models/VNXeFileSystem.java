/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VNXeFileSystem extends VNXeBase {
    private String name;
    private VNXeBase pool;
    private VNXeBase nasServer;
    private boolean isThinEnabled;
    private VNXeBase storageResource;
    private long sizeTotal;
    private long sizeFree;
    private long sizeUsed;
    private int supportedProtocols;
    private int snapCount;
    private boolean isFLREnabled;
    private long sizeAllocated;
    private int nfsShareCount;
    private int cifsShareCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VNXeBase getPool() {
        return pool;
    }

    public void setPool(VNXeBase pool) {
        this.pool = pool;
    }

    public VNXeBase getNasServer() {
        return nasServer;
    }

    public void setNasServer(VNXeBase nasServer) {
        this.nasServer = nasServer;
    }

    public boolean getIsThinEnabled() {
        return isThinEnabled;
    }

    public void setIsThinEnabled(boolean isThinEnabled) {
        this.isThinEnabled = isThinEnabled;
    }

    public VNXeBase getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(VNXeBase storageResource) {
        this.storageResource = storageResource;
    }

    public long getSizeTotal() {
        return sizeTotal;
    }

    public void setSizeTotal(long sizeTotal) {
        this.sizeTotal = sizeTotal;
    }

    public long getSizeFree() {
        return sizeFree;
    }

    public void setSizeFree(long sizeFree) {
        this.sizeFree = sizeFree;
    }

    public long getSizeUsed() {
        return sizeUsed;
    }

    public void setSizeUsed(long sizeUsed) {
        this.sizeUsed = sizeUsed;
    }

    public int getSupportedProtocols() {
        return supportedProtocols;
    }

    public void setSupportedProtocols(int supportedProtocols) {
        this.supportedProtocols = supportedProtocols;
    }

    public int getSnapCount() {
        return snapCount;
    }

    public void setSnapCount(int snapCount) {
        this.snapCount = snapCount;
    }

    public boolean getIsFLREnabled() {
        return isFLREnabled;
    }

    public void setIsFLREnabled(boolean isFLREnabled) {
        this.isFLREnabled = isFLREnabled;
    }

    public long getSizeAllocated() {
        return sizeAllocated;
    }

    public void setSizeAllocated(long sizeAllocated) {
        this.sizeAllocated = sizeAllocated;
    }

    public int getNfsShareCount() {
        return nfsShareCount;
    }

    public void setNfsShareCount(int nfsShareCount) {
        this.nfsShareCount = nfsShareCount;
    }

    public int getCifsShareCount() {
        return cifsShareCount;
    }

    public void setCifsShareCount(int cifsShareCount) {
        this.cifsShareCount = cifsShareCount;
    }

}
