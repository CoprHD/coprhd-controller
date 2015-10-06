package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Cf("ExportPathParam")
public class ExportPathParam extends DataObject {
    Integer maxPaths;
    Integer minPaths;
    Integer pathsPerInitiator;
    List<URI> storagePorts;
    
    
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
    public List<URI> getStoragePorts() {
        if (storagePorts == null) {
            return new ArrayList<URI>();
        }
        return storagePorts;
    }
    
    public void setStoragePorts(List<URI> storagePorts) {
        this.storagePorts = storagePorts;
        setChanged("storagePorts");
    }

}
