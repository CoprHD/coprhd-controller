/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.volumecontroller.Recommendation;

public class FileMirrorRecommendation extends FileRecommendation {
    private String copyMode;
    

    public FileMirrorRecommendation(FileRecommendation sourceFileRecommendation) {
        super(sourceFileRecommendation);
        // TODO Auto-generated constructor stub
    }

    public FileMirrorRecommendation() {
        // TODO Auto-generated constructor stub
    }
    
    public static class Target implements Serializable {
        
        // Target (for protection only)
        private URI targetDevice;
        private URI targetPool;
        
        private List<URI> _targetStoragePortUris;
        private URI targetvNASURI;

        public URI getTargetStorageDevice() {
            return targetDevice;
        }

        public URI getTargetStoragePool() {
            return targetPool;
        }

        public void setTargetPool(URI targetPool) {
            this.targetPool = targetPool;
        }

        public void setTargetStorageDevice(URI targetDevice) {
            this.targetDevice = targetDevice;
        }
        
        public List<URI> getTargetStoragePortUris() {
            return _targetStoragePortUris;
        }

        public void setTargetStoragePortUris(List<URI> _targetStoragePortUris) {
            this._targetStoragePortUris = _targetStoragePortUris;
        }

        public URI getTargetvNASURI() {
            return targetvNASURI;
        }

        public void setTargetvNASURI(URI targetvNASURI) {
            this.targetvNASURI = targetvNASURI;
        }
    }

    private Map<URI, FileMirrorRecommendation.Target> _varrayTargetMap;

    public Map<URI, FileMirrorRecommendation.Target> getVirtualArrayTargetMap() {
        return _varrayTargetMap;
    }

    public void setVirtualArrayTargetMap(
            Map<URI, FileMirrorRecommendation.Target> varrayTargetMap) {
        this._varrayTargetMap = varrayTargetMap;
    }
    
    public String getCopyMode() {
        return copyMode;
    }

    public void setCopyMode(String copyMode) {
        this.copyMode = copyMode;
    }
}
