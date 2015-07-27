/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.vpool;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Specifies protection parameters for the VirtualPool when created or retrieved.
 */
@XmlRootElement(name="protection_varray_vpool")
public class VirtualPoolProtectionVirtualArraySettingsParam {

    private URI varray;
    private URI vpool;
    private ProtectionCopyPolicy copyPolicy;

    public VirtualPoolProtectionVirtualArraySettingsParam() {}
    
    public VirtualPoolProtectionVirtualArraySettingsParam(URI varray,
            URI vpool, ProtectionCopyPolicy copyPolicy) {
        this.varray = varray;
        this.vpool = vpool;
        this.copyPolicy = copyPolicy;
    }

    /**
     * The virtual array.
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    public URI getVarray() {
        return varray;
    }

    public void setVarray(URI varray) {
        this.varray = varray;
    }

    /**
     * The virtual pool.
     * 
     * @valid none
     */
    @XmlElement(name = "vpool")
    public URI getVpool() {
        return vpool;
    }

    public void setVpool(URI vpool) {
        this.vpool = vpool;
    }

    /**
     * The copy policy.
     * 
     * @valid none
     */
    @XmlElement(name = "policy")
    public ProtectionCopyPolicy getCopyPolicy() {
        return copyPolicy;
    }

    public void setCopyPolicy(ProtectionCopyPolicy copyPolicy) {
        this.copyPolicy = copyPolicy;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((copyPolicy == null) ? 0 : copyPolicy.hashCode());
        result = prime * result + ((varray == null) ? 0 : varray.hashCode());
        result = prime * result + ((vpool == null) ? 0 : vpool.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VirtualPoolProtectionVirtualArraySettingsParam other = (VirtualPoolProtectionVirtualArraySettingsParam) obj;
        if (copyPolicy == null) {
            if (other.copyPolicy != null)
                return false;
        }
        else if (!copyPolicy.equals(other.copyPolicy))
            return false;
        if (varray == null) {
            if (other.varray != null)
                return false;
        }
        else if (!varray.equals(other.varray))
            return false;
        if (vpool == null) {
            if (other.vpool != null)
                return false;
        }
        else if (!vpool.equals(other.vpool))
            return false;
        return true;
    }
}
