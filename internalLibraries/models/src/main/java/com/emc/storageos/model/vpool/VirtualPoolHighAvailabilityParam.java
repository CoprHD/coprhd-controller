/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import javax.xml.bind.annotation.XmlElement;

public class VirtualPoolHighAvailabilityParam {

    private String type;
    private VirtualArrayVirtualPoolMapEntry haVirtualArrayVirtualPool;
    private Boolean metroPoint;
    private Boolean autoCrossConnectExport;

    public VirtualPoolHighAvailabilityParam() {
    }

    public VirtualPoolHighAvailabilityParam(String type) {
        this.type = type;
    }

    public VirtualPoolHighAvailabilityParam(String type,
            VirtualArrayVirtualPoolMapEntry haVirtualArrayVirtualPool) {
        this.type = type;
        this.haVirtualArrayVirtualPool = haVirtualArrayVirtualPool;
    }

    /**
     * The high availability type.
     * Valid values:
     *      vplex_local
     *      vplex_distributed
     */
    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The virtual pool for the high availability virtual array.
     * 
     */
    @XmlElement(name = "ha_varray_vpool")
    @JsonProperty("ha_varray_vpool")
    public VirtualArrayVirtualPoolMapEntry getHaVirtualArrayVirtualPool() {
        return haVirtualArrayVirtualPool;
    }

    public void setHaVirtualArrayVirtualPool(
            VirtualArrayVirtualPoolMapEntry haVirtualArrayVirtualPool) {
        this.haVirtualArrayVirtualPool = haVirtualArrayVirtualPool;
    }

    /**
     * Flag to specify whether or not MetroPoint configuration will be used.
     * 
     */
    @XmlElement(name = "metroPoint", required = false)
    public Boolean getMetroPoint() {
        return metroPoint;
    }

    public void setMetroPoint(Boolean metroPoint) {
        this.metroPoint = metroPoint;
    }

    /**
     * The class provides the REST representation of an entry in a
     * VirtualArray VirtualPool map.
     */
    public static class VirtualArrayVirtualPoolMapEntry {

        private URI virtualArray;
        private URI virtualPool;
        private Boolean activeProtectionAtHASite;

        public VirtualArrayVirtualPoolMapEntry() {
        }

        public VirtualArrayVirtualPoolMapEntry(URI key, URI val) {
            virtualArray = key;
            virtualPool = val;
        }

        public VirtualArrayVirtualPoolMapEntry(URI key, URI val, Boolean rp) {
            virtualArray = key;
            virtualPool = val;
            activeProtectionAtHASite = rp;
        }

        /**
         * The virtual array.
         * 
         */
        @XmlElement(name = "varray")
        @JsonProperty("varray")
        public URI getVirtualArray() {
            return virtualArray;
        }

        public void setVirtualArray(URI virtualArray) {
            this.virtualArray = virtualArray;
        }

        /**
         * The virtual pool.
         * 
         */
        @XmlElement(name = "vpool")
        @JsonProperty("vpool")
        public URI getVirtualPool() {
            return virtualPool;
        }

        public void setVirtualPool(URI virtualPool) {
            this.virtualPool = virtualPool;
        }

        /**
         * Indicates whether or not to use the HA side of the VPlex as the
         * RecoverPoint protected site in an RP+VPLEX setup. In a MetroPoint
         * context, if true, this field indicates that the HA VPlex site will be
         * the active site.
         * 
         */
        @XmlElement(name = "activeProtectionAtHASite", required = false)
        @JsonProperty("activeProtectionAtHASite")
        public Boolean getActiveProtectionAtHASite() {
            return activeProtectionAtHASite;
        }

        public void setActiveProtectionAtHASite(Boolean activeProtectionAtHASite) {
            this.activeProtectionAtHASite = activeProtectionAtHASite;
        }
    }

    /**
     * Flag to specify whether to automatically export both VPlex Clusters to
     * cross-connected hosts.
     * 
     */
    @XmlElement(name = "autoCrossConnectExport", required = false)
    public Boolean getAutoCrossConnectExport() {
        return autoCrossConnectExport;
    }

    public void setAutoCrossConnectExport(Boolean autoCrossConnectExport) {
        this.autoCrossConnectExport = autoCrossConnectExport;
    }
}
