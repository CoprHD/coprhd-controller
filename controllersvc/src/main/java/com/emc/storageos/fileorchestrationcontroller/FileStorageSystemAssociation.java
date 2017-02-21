package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

public class FileStorageSystemAssociation implements Serializable {
    private URI sourceSystem;
    private URI sourceVNAS;
    // virtual pool for project associations
    private URI projectvPool;
    // set of virtual pools or projects at which policy should be applied.
    private URI appliedAtResource;

    private Map<URI, URI> targetStorageDeviceToVNASMap;

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

    public Map<URI, URI> getTargetStorageDeviceToVNASMap() {
        return targetStorageDeviceToVNASMap;
    }

    public void setTargetStorageDeviceToVNASMap(Map<URI, URI> targetStorageDeviceToVNASMap) {
        this.targetStorageDeviceToVNASMap = targetStorageDeviceToVNASMap;
    }

    public URI getProjectvPool() {
        return projectvPool;
    }

    public void setProjectvPool(URI projectvPool) {
        this.projectvPool = projectvPool;
    }

    public URI getAppliedAtResource() {
        return appliedAtResource;
    }

    public void setAppliedAtResource(URI appliedAtResource) {
        this.appliedAtResource = appliedAtResource;
    }

}
