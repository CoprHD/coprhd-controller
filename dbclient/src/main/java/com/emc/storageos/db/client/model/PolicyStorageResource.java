/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * 
 * @author jainm15
 *
 */

@Cf("PolicyStorageResource")
public class PolicyStorageResource extends DataObject {

    private static final long serialVersionUID = 1L;
    private URI filePolicyId;
    private URI storageSystem;
    private URI nasServer;
    private URI appliedAt;
    private String policyNativeId;

    @Name("filePolicyId")
    public URI getFilePolicyId() {
        return filePolicyId;
    }

    public void setFilePolicyId(URI filePolicyId) {
        this.filePolicyId = filePolicyId;
        setChanged("filePolicyId");
    }

    @Name("storageSystem")
    public URI getStorageSystem() {
        return storageSystem;
    }

    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
        setChanged("storageSystem");
    }

    @Name("nasServer")
    public URI getNasServer() {
        return nasServer;
    }

    public void setNasServer(URI nasServer) {
        this.nasServer = nasServer;
        setChanged("nasServer");
    }

    @Name("policyNativeId")
    public String getPolicyNativeId() {
        return policyNativeId;
    }

    public void setPolicyNativeId(String policyNativeId) {
        this.policyNativeId = policyNativeId;
        setChanged("policyNativeId");
    }

    @Name("appliedAt")
    public URI getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(URI appliedAt) {
        this.appliedAt = appliedAt;
        setChanged("appliedAt");
    }

}
