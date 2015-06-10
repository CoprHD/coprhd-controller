/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.model;

import javax.xml.bind.annotation.XmlAttribute;

import java.net.URI;

/**
 *  Class represent a link for the Rest API
 *
 */
public class RestLinkRep{

    private String linkName;
    private URI linkRef;

    public RestLinkRep() {}

    public RestLinkRep(String link, URI ref) {
        this.linkName = link;
        this.linkRef = ref;
    }

    @XmlAttribute(name = "rel")
    public String getLinkName() { 
        return linkName; 
    }
    
    public void setLinkName(String link) {
        linkName = link; 
    }

    @XmlAttribute(name = "href")
    public URI getLinkRef() { 
        return linkRef; 
    }
    
    public void setLinkRef(URI ref) {
        linkRef = ref;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((linkName == null) ? 0 : linkName.hashCode());
        result = prime * result
                + ((linkRef == null) ? 0 : linkRef.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RestLinkRep other = (RestLinkRep) obj;
        if (linkName == null) {
            if (other.linkName != null)
                return false;
        } else if (!linkName.equals(other.linkName))
            return false;
        if (linkRef == null) {
            if (other.linkRef != null)
                return false;
        } else if (!linkRef.equals(other.linkRef))
            return false;
        return true;
    }
}


