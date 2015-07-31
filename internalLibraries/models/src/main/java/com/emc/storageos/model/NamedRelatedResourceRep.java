/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model;

import java.net.URI;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class NamedRelatedResourceRep extends RelatedResourceRep {

    private String name;

    public NamedRelatedResourceRep() {
    }

    public NamedRelatedResourceRep(URI id, RestLinkRep selfLink, String name) {
        super(id, selfLink);
        this.name = name;
    }

    /**
     * The name of the resource
     * 
     * @valid none
     */
    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            NamedRelatedResourceRep other = (NamedRelatedResourceRep) obj;
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public String toString() {
        return getId().toString() + " " + getName();
    }
}
