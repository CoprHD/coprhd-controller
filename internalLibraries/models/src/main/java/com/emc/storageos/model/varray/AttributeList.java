/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.varray;

import com.emc.storageos.model.vpool.VirtualPoolAvailableAttributesResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;

@XmlRootElement(name = "available_attributes")
public class AttributeList {
    private URI vArrayId;

    private List<VirtualPoolAvailableAttributesResourceRep> attributes;

    public AttributeList() {
    }

    public AttributeList(
            List<VirtualPoolAvailableAttributesResourceRep> attributes,
            URI vArrayId) {
        this.attributes = attributes;
        this.vArrayId = vArrayId;
    }

    /**
     * A list of virtual pool available attribute response instances.
     * 
     * @valid none
     * 
     * @return A list of virtual pool available attribute response instances.
     */
    @XmlElement(name = "attribute")
    public List<VirtualPoolAvailableAttributesResourceRep> getAttributes() {
        if (attributes == null) {
            attributes = new ArrayList<VirtualPoolAvailableAttributesResourceRep>();
        }
        return attributes;
    }

    public void setAttributes(List<VirtualPoolAvailableAttributesResourceRep> attributes) {
        this.attributes = attributes;
    }

    /**
     * ID of the VArray for this Attributes
     * 
     * @valid none
     * 
     * @return A list of virtual pool available attribute response instances.
     */
    @XmlElement(name = "virtual_array")
    public URI getVArrayId() {
        return vArrayId;
    }

    public void setVArrayId(URI vArrayId) {
        this.vArrayId = vArrayId;
    }
}
