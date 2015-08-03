/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "varray_available_attributes")
public class VArrayAttributeList {
    private List<AttributeList> attributes;

    public VArrayAttributeList() {
    }

    public VArrayAttributeList(
            List<AttributeList> attributes) {
        this.attributes = attributes;
    }

    /**
     * A list of virtual pool available attribute response instances.
     * 
     * @valid none
     * 
     * @return A list of virtual pool available attribute response instances.
     */
    @XmlElement(name = "varray_attributes")
    public List<AttributeList> getAttributes() {
        if (attributes == null) {
            attributes = new ArrayList<AttributeList>();
        }
        return attributes;
    }

    public void setAttributes(List<AttributeList> attributes) {
        this.attributes = attributes;
    }
}
