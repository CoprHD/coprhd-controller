/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * Update parameter for host
 */
public class HostsUpdateParam extends UpdateParam {

    public HostsUpdateParam() {
    }

    public HostsUpdateParam(Set<URI> add, Set<URI> remove) {
        this.add = add;
        this.remove = remove;
    }

    @XmlElementWrapper(required = false)
    /**
     * List of host URIs to be added to the export group.
     */
    @XmlElement(name = "host")
    public Set<URI> getAdd() {
        if (add == null) {
            add = new HashSet<URI>();
        }
        return add;
    }

    public void setAdd(Set<URI> add) {
        this.add = add;
    }

    @XmlElementWrapper(required = false)
    /**
     * List of host URIs to be removed from the export group.
     */
    @XmlElement(name = "host")
    public Set<URI> getRemove() {
        if (remove == null) {
            remove = new HashSet<URI>();
        }
        return remove;
    }

    public void setRemove(Set<URI> remove) {
        this.remove = remove;
    }
}
