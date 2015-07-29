/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model;

import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class RelatedResourceRep {

    private URI id;
    private RestLinkRep selfLink;

    public RelatedResourceRep() {
    }

    public RelatedResourceRep(URI id, RestLinkRep selfLink) {
        this.id = id;
        this.selfLink = selfLink;
    }

    /**
     * 
     * ViPR ID of the related object
     * 
     * @valid none
     */
    @XmlElement(name = "id")
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    /**
     * 
     * A hyperlink to the related object
     * 
     * @valid none
     */
    @XmlElement(name = "link")
    public RestLinkRep getLink() {
        return selfLink;
    }

    public void setLink(RestLinkRep link) {
        selfLink = link;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result
                + ((selfLink == null) ? 0 : selfLink.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RelatedResourceRep other = (RelatedResourceRep) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (selfLink == null) {
            if (other.selfLink != null) {
                return false;
            }
        } else if (!selfLink.equals(other.selfLink)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
