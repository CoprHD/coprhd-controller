/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import com.emc.storageos.model.NamedRelatedResourceRep;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.LinkedHashSet;
import java.util.Set;

@XmlRootElement(name = "varray_connectivity")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualArrayConnectivityRestRep {
    private NamedRelatedResourceRep varray;
    private Set<String> connectionType;

    public VirtualArrayConnectivityRestRep() {
    }

    public VirtualArrayConnectivityRestRep(NamedRelatedResourceRep varray,
            Set<String> connectionType) {
        this.varray = varray;
        this.connectionType = connectionType;
    }

    /**
     * The virtual array.
     * 
     * 
     * @return The virtual array.
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public NamedRelatedResourceRep getVirtualArray() {
        return varray;
    }

    public void setVirtualArray(NamedRelatedResourceRep varray) {
        this.varray = varray;
    }

    /**
     * The connection type.
     * Valid values:
     *  vplex
     *  rp
     * 
     * @return The connection type
     */
    @XmlElement(name = "connection_type")
    public Set<String> getConnectionType() {
        if (connectionType == null) {
            connectionType = new LinkedHashSet<String>();
        }
        return connectionType;
    }

    public void setConnectionType(Set<String> connectionType) {
        this.connectionType = connectionType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((varray == null) ? 0 : varray.hashCode());
        result = prime * result
                + ((connectionType == null) ? 0 : connectionType.hashCode());
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
        VirtualArrayConnectivityRestRep other = (VirtualArrayConnectivityRestRep) obj;
        if (varray == null) {
            if (other.varray != null) {
                return false;
            }
        } else if (!varray.equals(other.varray)) {
            return false;
        }
        if (connectionType == null) {
            if (other.connectionType != null) {
                return false;
            }
        } else if (!connectionType.equals(other.connectionType)) {
            return false;
        }
        return true;
    }
}
