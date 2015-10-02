/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.search;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;

import java.util.ArrayList;
import java.util.List;
import com.emc.storageos.model.search.SearchResultResourceRep;

/**
 * Resource representation results searched from database
 */
@XmlRootElement(name = "results")
public class SearchResults {
    private List<SearchResultResourceRep> resource;

    public SearchResults() {
    }

    /**
     * A list of resources matching the search parameters
     * 
     */
    @XmlElement(name = "resource")
    public List<SearchResultResourceRep> getResource() {
        if (resource == null) {
            resource = new ArrayList<SearchResultResourceRep>();
        }
        return resource;
    }

    public void setResource(List<SearchResultResourceRep> resource) {
        this.resource = resource;
    }
}
