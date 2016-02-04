/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Specifies one or more Volume URIs for a bulk delete operation.
 */
@XmlRootElement(name = "volume_ids")
public class BulkDeleteParam {

    private List<URI> ids;

    public BulkDeleteParam() {
    }

    public BulkDeleteParam(List<URI> ids) {
        this.ids = ids;
    }

    /**
     * The list of volume URIs to be deleted
     * 
     */
    @XmlElement(name = "id")
    public List<URI> getIds() {
        if (ids == null) {
            ids = new ArrayList<URI>();
        }
        return ids;
    }

    public void setIds(List<URI> ids) {
        this.ids = ids;
    }

}
