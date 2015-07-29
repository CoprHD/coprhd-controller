/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * Parameter for endpoint changes
 */
public class EndpointChanges {
    private List<String> add;
    private List<String> remove;

    /**
     * List of endpoints to be added
     * 
     * @return list of endpoints to be added
     */
    @XmlElementWrapper(required = false)
    @XmlElement(name = "endpoint")
    public List<String> getAdd() {
        return add;
    }

    public void setAdd(List<String> add) {
        this.add = add;
    }

    /**
     * List of endpoints to be removed
     * 
     * @return list of endpoints to be removed
     */
    @XmlElementWrapper(required = false)
    @XmlElement(name = "endpoint")
    public List<String> getRemove() {
        return remove;
    }

    public void setRemove(List<String> remove) {
        this.remove = remove;
    }

    public boolean hasAdded() {
        return add != null && add.size() > 0;
    }

    public boolean hasRemoved() {
        return remove != null && remove.size() > 0;
    }

    public boolean hasUpdates() {
        return hasAdded() || hasRemoved();
    }
}