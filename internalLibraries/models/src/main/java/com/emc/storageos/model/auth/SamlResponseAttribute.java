/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement(name = "saml_response_attribute")
public class SamlResponseAttribute implements Serializable{
    private static final long serialVersionUID = 89647268124256710L;

    private String attributeName;
    private String attributeType;

    @XmlElement(name = "attribute_name")
    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    @XmlElement(name = "attribute_type")
    public String getAttributeType(){
        return attributeType;
    }

    public void setAttributeType(String attributeType) {
        this.attributeType = attributeType;
    }
}
