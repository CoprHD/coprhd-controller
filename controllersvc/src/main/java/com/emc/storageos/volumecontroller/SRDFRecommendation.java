/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;

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
        // The volume descriptors are kept here to pass up to SRDFTargetRecommendations
        private List<VolumeDescriptor> descriptors;

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

        public List<VolumeDescriptor> getDescriptors() {
            return descriptors;
        }

        public void setDescriptors(List<VolumeDescriptor> descriptors) {
            this.descriptors = descriptors;
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
