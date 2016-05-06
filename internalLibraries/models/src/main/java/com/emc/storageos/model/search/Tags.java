/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.search;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tags that have been assigned to this resource
 */
@XmlRootElement(name = "tags")
public class Tags {
    private Set<String> tag;

    public Tags() {
    }

    public Tags(Set<String> tag) {
        this.tag = tag;
    }

    /**
     * A set of tags
     * 
     */
    @XmlElement(name = "tag")
    public Set<String> getTag() {
        if (tag == null) {
            tag = new LinkedHashSet<String>();
        }
        return tag;
    }

    public void setTag(Set<String> tag) {
        this.tag = tag;
    }
}
