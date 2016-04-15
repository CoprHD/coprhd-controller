/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vnas;

import java.io.Serializable;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Attributes associated with a Virtual NAS, specified
 * during virtual NAS creation/updates.
 * 
 * @author prasaa9
 * 
 */

@XmlRootElement
public class VirtualNasParam implements Serializable {

    private static final long serialVersionUID = -2256076906843969208L;

    // List of protocols add to VNAS
    private Set<String> addProtocols;

    // Name of the IPSpace
    private String ipSpace;

    @XmlElementWrapper(name = "add_protocols")
    @XmlElement(name = "add_protocol")
    public Set<String> getAddProtocols() {
        return addProtocols;
    }

    public void setAddProtocols(Set<String> addProtocols) {
        this.addProtocols = addProtocols;
    }

    @XmlElement(name = "ip_space")
    public String getIpSpace() {
        return ipSpace;
    }

    public void setIpSpace(String ipSpace) {
        this.ipSpace = ipSpace;
    }

}
