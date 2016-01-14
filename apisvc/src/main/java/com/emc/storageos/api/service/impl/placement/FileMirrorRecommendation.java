package com.emc.storageos.api.service.impl.placement;

import com.emc.storageos.volumecontroller.Recommendation;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

/**
 * Created by bonduj on 1/11/2016.
 */
public class FileMirrorRecommendation extends FileRecommendation {

    public FileMirrorRecommendation(Recommendation recommendation) {
        super(recommendation);

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

        public String getCopyMode() {
            return copyMode;
        }

        public void setCopyMode(String copyMode) {
            this.copyMode = copyMode;
        }

    }

    // Source
    private Map<URI, FileMirrorRecommendation.Target> _varrayTargetMap;

    public Map<URI, FileMirrorRecommendation.Target> getVirtualArrayTargetMap() {
        return _varrayTargetMap;
    }

    public void setVirtualArrayTargetMap(
            Map<URI, FileMirrorRecommendation.Target> varrayTargetMap) {
        this._varrayTargetMap = varrayTargetMap;
    }


}
