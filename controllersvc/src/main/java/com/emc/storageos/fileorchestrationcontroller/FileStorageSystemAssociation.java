package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

public class FileStorageSystemAssociation implements Serializable {
    private URI sourceSystem;
    private URI sourceVNAS;

    private Map<URI, URI> targetStorageDeviceToVNASMap;
    private URI vpool;

    public URI getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(URI sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public URI getSourceVNAS() {
        return sourceVNAS;
    }

    public void setSourceVNAS(URI sourceVNAS) {
        this.sourceVNAS = sourceVNAS;
    }

    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    public Map<URI, URI> getTargetStorageDeviceToVNASMap() {
        return targetStorageDeviceToVNASMap;
    }

    public void setTargetStorageDeviceToVNASMap(Map<URI, URI> targetStorageDeviceToVNASMap) {
        this.targetStorageDeviceToVNASMap = targetStorageDeviceToVNASMap;
    }

}
