/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block.export;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * Update parameter for cluster
 */
public class ClustersUpdateParam extends UpdateParam {
    
    public ClustersUpdateParam() {}
    
    public ClustersUpdateParam(Set<URI> add, Set<URI> remove) {
        this.add = add;
        this.remove = remove;
    }
    
    @XmlElementWrapper(required = false)
    @XmlElement(name = "cluster")
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
    @XmlElement(name = "cluster")
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