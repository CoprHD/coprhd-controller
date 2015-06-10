/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ids")
public class BulkIdParam {
    private List<URI> ids;

    public BulkIdParam() {}
    
    public BulkIdParam(List<URI> ids) {
        this.ids = ids;
    }
    
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
