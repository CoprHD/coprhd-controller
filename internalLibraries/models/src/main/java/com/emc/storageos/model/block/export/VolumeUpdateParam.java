/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * Update parameter for block volume of snapshot
 */
public class VolumeUpdateParam {

    private List<VolumeParam> add;
    private List<URI> remove;

    public VolumeUpdateParam() {
    }

    public VolumeUpdateParam(List<VolumeParam> add, List<URI> remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * Add lists of volume or volume snapshot changes.
     * 
     */
    @XmlElementWrapper(name = "add", required = false)
    @XmlElement(name = "volume")
    public List<VolumeParam> getAdd() {
        if (add == null) {
            add = new ArrayList<VolumeParam>();
        }
        return add;
    }

    /**
     * Remove lists of volume or volume snapshot changes.
     * 
     */
    @XmlElementWrapper(required = false)
    @XmlElement(name = "volume")
    public List<URI> getRemove() {
        if (remove == null) {
            remove = new ArrayList<URI>();
        }
        return remove;
    }

    public void addVolume(URI volumeId) {
        getAdd().add(new VolumeParam(volumeId));
    }

    public void removeVolume(URI volumeId) {
        getRemove().add(volumeId);
    }
}
