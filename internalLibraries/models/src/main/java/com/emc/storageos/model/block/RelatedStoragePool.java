/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import com.emc.storageos.model.NamedRelatedResourceRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "related_storage_pool")
public class RelatedStoragePool {

    public static final RelatedStoragePool EMPTY = new RelatedStoragePool();

    private NamedRelatedResourceRep storagePool;

    public RelatedStoragePool() {
    }

    public RelatedStoragePool(NamedRelatedResourceRep storagePool) {
        this.storagePool = storagePool;
    }

    /**
     * The name and URI of the storage pool
     * 
     */
    @XmlElement(name = "storage_pool")
    public NamedRelatedResourceRep getStoragePool() {
        return storagePool;
    }

    public void setStoragePool(NamedRelatedResourceRep storagePool) {
        this.storagePool = storagePool;
    }

}
