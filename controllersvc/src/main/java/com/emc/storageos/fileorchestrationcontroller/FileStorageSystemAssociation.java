package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

public class FileStorageSystemAssociation implements Serializable {
    private URI sourceSystem;
    private URI sourceVNAS;
    private Map<URI, URI> targetInfo;
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

    public Map<URI, URI> getTargetInfo() {
        return targetInfo;
    }

    public void setTargetInfo(Map<URI, URI> targetInfo) {
        this.targetInfo = targetInfo;
    }

    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

}
