/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import org.codehaus.jackson.annotate.JsonProperty;
import javax.xml.bind.annotation.XmlElement;

import java.util.HashSet;
import java.util.Set;

/**
 * Class captures a list of Protocols to update in VirtualPool.
 */
public class ProtocolAssignments {

    private Set<String> protocols;

    /**
     * Default Constructor.
     */
    public ProtocolAssignments() {
    }

    public ProtocolAssignments(Set<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * The set of protocols.
     * Valid values:
     *  FC = Fibre Channel (block)
     *  ISCSI = Internet Small Computer System Interface (block)
     *  FCoE = Fibre Channel over Ethernet (block)
     *  NFS = Network File System (file)
     *  NFSV4 = Network File System Version 4 (file)
     *  CIFS = Common Internet File System (file)
     * 
     */
    @XmlElement(name = "protocol")
    @JsonProperty("protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new HashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

}
