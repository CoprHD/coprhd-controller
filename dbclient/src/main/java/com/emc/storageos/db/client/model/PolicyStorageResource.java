package com.emc.storageos.db.client.model;

import java.net.URI;

public class PolicyStorageResource extends DataObject {
public URI getPolicyId() {
        return policyId;
    }
    public void setPolicyId(URI policyId) {
        this.policyId = policyId;
    }
    public URI getStorageSystem() {
        return storageSystem;
    }
    public void setStorageSystem(URI storageSystem) {
        this.storageSystem = storageSystem;
    }
    public URI getNasServer() {
        return nasServer;
    }
    public void setNasServer(URI nasServer) {
        this.nasServer = nasServer;
    }
    public String getNativeId() {
        return nativeId;
    }
    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }
private URI policyId;
private URI storageSystem;
private URI nasServer;
private String nativeId; 

}
