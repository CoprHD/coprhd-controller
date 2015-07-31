/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.block;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class BlockObjectRestRep extends DataObjectRestRep {
    private String wwn;
    private URI storageController;
    private Set<String> protocols;
    private RelatedResourceRep virtualArray;
    private String deviceLabel;
    private String nativeId;
    private RelatedResourceRep consistencyGroup;

    /**
     * Label assigned to the Block object.
     * An example of a block object is a volume.
     * 
     * @valid none
     */
    @XmlElement(name = "device_label")
    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    /**
     * Native ID for this Block object.
     * 
     * @valid none
     */
    @XmlElement(name = "native_id")
    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    /**
     * Virtual array where this Block object exists.
     * 
     * @valid none
     */
    @XmlElement(name = "varray")
    @JsonProperty("varray")
    public RelatedResourceRep getVirtualArray() {
        return virtualArray;
    }

    public void setVirtualArray(RelatedResourceRep virtualArray) {
        this.virtualArray = virtualArray;
    }

    @XmlElementWrapper(name = "protocols")
    /**
     * Storage protocols supported by this Block object.
     * @valid none
     */
    @XmlElement(name = "protocol")
    public Set<String> getProtocols() {
        if (protocols == null) {
            protocols = new HashSet<String>();
        }
        return protocols;
    }

    public void setProtocols(Set<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * Storage controller where this Block object is located.
     * 
     * @valid none
     */
    @XmlElement(name = "storage_controller")
    public URI getStorageController() {
        return storageController;
    }

    public void setStorageController(URI storageController) {
        this.storageController = storageController;
    }

    /**
     * World Wide name of this Block object.
     * 
     * @valid none
     */
    @XmlElement(name = "wwn")
    public String getWwn() {
        return wwn;
    }

    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    /**
     * Tag for grouping Block objects that need to have consistent
     * snapshots.
     * 
     * @valid none
     */
    @XmlElement(name = "consistency_group")
    public RelatedResourceRep getConsistencyGroup() {
        return consistencyGroup;
    }

    public void setConsistencyGroup(RelatedResourceRep consistencyGroup) {
        this.consistencyGroup = consistencyGroup;
    }
}
