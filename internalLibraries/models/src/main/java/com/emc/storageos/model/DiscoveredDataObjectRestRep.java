/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model;

import java.net.URI;
import java.util.Calendar;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class DiscoveredDataObjectRestRep extends DataObjectRestRep {
    private String nativeGuid;

    public DiscoveredDataObjectRestRep() {
    }

    public DiscoveredDataObjectRestRep(String name, URI id, RestLinkRep link,
            Calendar creationTime, Boolean inactive, Set<String> tags, String nativeGuid) {
        super(name, id, link, creationTime, inactive, tags);
        this.nativeGuid = nativeGuid;
    }

    /**
     * The native unique identifier for this resource
     * <p>
     * 
     * Network Transport Types:
     * <ul>
     * <li>FC = Fibre-Channel
     * </ul>
     * <p>
     * 
     * Network System Types:
     * <ul>
     * <li>BROCADE
     * <li>MDS
     * </ul>
     * <p>
     * 
     * Storage System Types:
     * <ul>
     * <li>CELERRA = VNX File
     * <li>CLARIION = VNX Block
     * <li>ISILON
     * <li>NETAPP
     * <li>SYMMETRIX
     * </ul>
     * Valid values: 
     * Network
     * NetworkSystem
     * StoragePool
     * StoragePort
     * StorageSystem
     * StorageTier:VMAX
     * StorageTier: VNX
     * 
     */
    @XmlElement(name = "native_guid")
    public String getNativeGuid() {
        return nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        this.nativeGuid = nativeGuid;
    }
}
