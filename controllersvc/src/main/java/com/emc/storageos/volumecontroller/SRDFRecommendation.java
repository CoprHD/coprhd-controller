/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

/**
 * Recommendation for a placement is a storage pool and its storage device.
 */
@SuppressWarnings("serial")
public class SRDFRecommendation extends Recommendation {
    public static class Target implements Serializable {
        // Target (for protection only)
        private URI targetDevice;
        private URI targetPool;
        // The target device houses the source storage system's RA Group this should be protected over.
        private URI sourceRAGroup;
        private String copyMode;

        public URI getTargetStorageDevice() {
            return targetDevice;
        }

        public URI getTargetStoragePool() {
            return targetPool;
        }

        public URI getSourceRAGroup() {
            return sourceRAGroup;
        }

        public void setTargetPool(URI targetPool) {
            this.targetPool = targetPool;
        }

        public void setTargetStorageDevice(URI targetDevice) {
            this.targetDevice = targetDevice;
        }

        public void setSourceRAGroup(URI sourceRAGroup) {
            this.sourceRAGroup = sourceRAGroup;
        }

        public String getCopyMode() {
            return copyMode;
        }

        public void setCopyMode(String copyMode) {
            this.copyMode = copyMode;
        }

    }

    // Source
    private Map<URI, SRDFRecommendation.Target> _varrayTargetMap;
    private URI _vpoolChangeVolume;
    private URI _vpoolChangeVpool;

    public Map<URI, SRDFRecommendation.Target> getVirtualArrayTargetMap() {
        return _varrayTargetMap;
    }

    public void setVirtualArrayTargetMap(
            Map<URI, SRDFRecommendation.Target> varrayTargetMap) {
        this._varrayTargetMap = varrayTargetMap;
    }

    public void setVpoolChangeVolume(URI id) {
        _vpoolChangeVolume = id;
    }

    public URI getVpoolChangeVolume() {
        return _vpoolChangeVolume;
    }

    public void setVpoolChangeVpool(URI id) {
        _vpoolChangeVpool = id;
    }

    public URI getVpoolChangeVpool() {
        return _vpoolChangeVpool;
    }

}
