package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Cf("ExportPathParam")
public class ExportPathParam extends DataObject {
    Integer maxPaths;
    Integer minPaths;
    Integer pathsPerInitiator;
    StringSet storagePorts;
    Boolean explicitlyCreated;
    
    
    @Name("maxPaths")
    public Integer getMaxPaths() {
        return maxPaths;
    }
    
    public void setMaxPaths(Integer maxPaths) {
        this.maxPaths = maxPaths;
        setChanged("maxPaths");
    }
    
    @Name("minPaths")
    public Integer getMinPaths() {
        return minPaths;
    }
    
    public void setMinPaths(Integer minPaths) {
        this.minPaths = minPaths;
        setChanged("minPaths");
    }
    
    @Name("pathsPerInitiator")
    public Integer getPathsPerInitiator() {
        return pathsPerInitiator;
    }
    
    public void setPathsPerInitiator(Integer pathsPerInitiator) {
        this.pathsPerInitiator = pathsPerInitiator;
        setChanged("pathsPerInitiator");
    }
    
    @Name("storagePorts")
    public StringSet getStoragePorts() {
        if (storagePorts == null) {
            return new StringSet();
        }
        return storagePorts;
    }
    
    public void setStoragePorts(StringSet storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

    @Name("explicitlyCreated")
    public Boolean getExplicitlyCreated() {
        return explicitlyCreated;
    }
    
    public boolean wasExplicitlyCreated() {
        return (explicitlyCreated != null && explicitlyCreated);
    }

    public void setExplicitlyCreated(Boolean explicitlyCreated) {
        this.explicitlyCreated = explicitlyCreated;
    }

}
