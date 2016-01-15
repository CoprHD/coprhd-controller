/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import com.emc.storageos.volumecontroller.Recommendation;

public class FileMirrorRecommendation extends FileRecommendation {

    public FileMirrorRecommendation(Recommendation recommendation) {
        super(recommendation);
        // TODO Auto-generated constructor stub
    }

    public FileMirrorRecommendation() {
        // TODO Auto-generated constructor stub
    }
    
    public static class Target implements Serializable {
        // Target (for protection only)
        private URI targetDevice;
        private URI targetPool;
        private String copyMode;

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
    }

    private Map<URI, FileMirrorRecommendation.Target> _varrayTargetMap;

    public Map<URI, FileMirrorRecommendation.Target> getVirtualArrayTargetMap() {
        return _varrayTargetMap;
    }

    public void setVirtualArrayTargetMap(
            Map<URI, FileMirrorRecommendation.Target> varrayTargetMap) {
        this._varrayTargetMap = varrayTargetMap;
    }
}
