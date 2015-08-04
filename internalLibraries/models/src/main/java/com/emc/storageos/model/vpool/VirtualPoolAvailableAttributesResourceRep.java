/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.vpool;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class VirtualPoolAvailableAttributesResourceRep {

    private String name;
    private Set<String> attributeValues;

    public VirtualPoolAvailableAttributesResourceRep() {
    }

    public VirtualPoolAvailableAttributesResourceRep(String name, Set<String> attributeValues) {
        this.name = name;
        this.attributeValues = attributeValues;
    }

    /**
     * The attribute name.
     * 
     * @valid none
     * 
     * @return The attribute name
     */
    @XmlElement(name = "name")
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElementWrapper(name = "values")
    /**
     * The possible values for the attribute.
     * 
     * @valid none
     * 
     * @return The set of values for the attribute.
     */
    @XmlElement(name = "value")
    public Set<String> getAttributeValues() {
        if (attributeValues == null) {
            attributeValues = new LinkedHashSet<String>();
        }
        return attributeValues;
    }

    public void setAttributeValues(Set<String> attributeValues) {
        this.attributeValues = attributeValues;
    }
}
