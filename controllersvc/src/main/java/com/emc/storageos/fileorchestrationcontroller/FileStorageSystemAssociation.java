package com.emc.storageos.fileorchestrationcontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class FileStorageSystemAssociation implements Serializable {

    private static final long serialVersionUID = -1219151102632867535L;

    private URI sourceSystem;
    private URI sourceVNAS;
    // virtual pool for project associations
    private URI projectvPool;
    // set of virtual pools or projects at which policy should be applied.
    private URI appliedAtResource;

    private List<TargetAssociation> targets;

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

    public List<TargetAssociation> getTargets() {
        return targets;
    }

    public void setTargets(List<TargetAssociation> targets) {
        this.targets = targets;
    }

    public void addTargetAssociation(TargetAssociation target) {
        if (targets == null) {
            targets = new ArrayList<TargetAssociation>();
        }
        targets.add(target);
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

    public static class TargetAssociation implements Serializable {

        private static final long serialVersionUID = 1832257216470344471L;

        private URI storageSystemURI;
        private URI vNASURI;
        private URI vArrayURI;

        public URI getStorageSystemURI() {
            return storageSystemURI;
        }

        public URI getvNASURI() {
            return vNASURI;
        }

        public URI getvArrayURI() {
            return vArrayURI;
        }

        public void setStorageSystemURI(URI storageSystemURI) {
            this.storageSystemURI = storageSystemURI;
        }

        public void setvNASURI(URI vNASURI) {
            this.vNASURI = vNASURI;
        }

        public void setvArrayURI(URI vArrayURI) {
            this.vArrayURI = vArrayURI;
        }

    }

}
