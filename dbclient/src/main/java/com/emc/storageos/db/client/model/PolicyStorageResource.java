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
    private String name;
    private String resourcePath;
    private String nativeGuid;
    private FileReplicaPolicyTargetMap fileReplicaPolicyTargetMap;

    @RelationIndex(cf = "RelationIndex", type = FilePolicy.class)
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

    @Name("resourcePath")
    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
        setChanged("resourcePath");
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @Name("filePolicyTargetMap")
    public FileReplicaPolicyTargetMap getFileReplicaPolicyTargetMap() {
        return fileReplicaPolicyTargetMap;
    }

    public void setFileReplicaPolicyTargetMap(FileReplicaPolicyTargetMap fileReplicaPolicyTargetMap) {
        this.fileReplicaPolicyTargetMap = fileReplicaPolicyTargetMap;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "PolicyStorageResource [filePolicyId=" + filePolicyId + ", storageSystem=" + storageSystem + ", nasServer=" + nasServer
                + ", appliedAt=" + appliedAt + ", policyNativeId=" + policyNativeId + ", resourcePath=" + resourcePath + ", nativeGuid="
                + nativeGuid + ", fileReplicaPolicyTargetMap=" + fileReplicaPolicyTargetMap + "]";
    }

}
