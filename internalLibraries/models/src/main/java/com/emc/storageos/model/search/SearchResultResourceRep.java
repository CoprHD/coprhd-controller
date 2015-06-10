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

import java.net.URI;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;

import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.RestLinkRep;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class SearchResultResourceRep extends RelatedResourceRep {

    public String match;

    public SearchResultResourceRep() {
    }

    public SearchResultResourceRep(URI id, RestLinkRep selfLink, String match) {
        super(id, selfLink);
        this.match = match;
    }

    /**
     * The name or tag of the resource matching the search parameters
     * @valid none
     */
    @XmlElement(name = "match")
    public String getMatch() {
        return match;
    }
    public void setMatch(String match) {
        this.match = match;
    }
}

