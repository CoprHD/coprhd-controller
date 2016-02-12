/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.auth;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="saml_metadata_response")
public class SamlMetadataResponse {
    private String metadataString;

    @XmlElement(name="metadata_string")
    public String getMetadataString() {
        return metadataString;
    }

    public void setMetadataString(String metadataString) {
        this.metadataString = metadataString;
    }
}
