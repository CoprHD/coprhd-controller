/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
     * @valid none
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

