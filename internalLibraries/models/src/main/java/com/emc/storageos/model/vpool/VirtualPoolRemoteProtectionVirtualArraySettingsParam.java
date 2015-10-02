/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;

public class VirtualPoolRemoteProtectionVirtualArraySettingsParam {

    public VirtualPoolRemoteProtectionVirtualArraySettingsParam(URI varray, URI vpool,
            String remoteCopyMode) {
        super();
        this.varray = varray;
        this.vpool = vpool;
        this.remoteCopyMode = remoteCopyMode;
    }

    public VirtualPoolRemoteProtectionVirtualArraySettingsParam() {

    }

    /**
     * The remote virtual array.
     * 
     */

    private URI varray;

    /**
     * The remote virtual pool.
     * 
     */
    private URI vpool;

    /**
     * remote copy modes
     * 
     */
    private String remoteCopyMode;

    @XmlElement(name = "varray", required = true)
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    @XmlElement(name = "vpool")
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    @XmlElement(name = "remote_copy_mode", required = false)
    public String getRemoteCopyMode() {
        return remoteCopyMode;
    }

    public void setRemoteCopyMode(String remoteCopyMode) {
        this.remoteCopyMode = remoteCopyMode;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((remoteCopyMode == null) ? 0 : remoteCopyMode.hashCode());
        result = prime * result + ((varray == null) ? 0 : varray.hashCode());
        result = prime * result + ((vpool == null) ? 0 : vpool.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        VirtualPoolRemoteProtectionVirtualArraySettingsParam other = (VirtualPoolRemoteProtectionVirtualArraySettingsParam) obj;
        if (remoteCopyMode == null) {
            if (other.remoteCopyMode != null) {
                return false;
            }
        } else if (!remoteCopyMode.equals(other.remoteCopyMode)) {
            return false;
        }
        if (varray == null) {
            if (other.varray != null) {
                return false;
            }
        } else if (!varray.equals(other.varray)) {
            return false;
        }
        if (vpool == null) {
            if (other.vpool != null) {
                return false;
            }
        } else if (!vpool.equals(other.vpool)) {
            return false;
        }
        return true;
    }
}
