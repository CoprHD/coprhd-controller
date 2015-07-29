/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.usergroup;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;

/**
 * Base API payload for user groups creation and update.
 */
public class UserGroupBaseParam {
    private String _domain;
    private String _label;

    @XmlElement(required = true, name = "domain")
    @JsonProperty("domain")
    public String getDomain() {
        return _domain;
    }

    public void setDomain(String _domain) {
        this._domain = _domain;
    }

    @XmlElement(required = true, name = "label")
    @JsonProperty("label")
    public String getLabel() {
        return _label;
    }

    public void setLabel(String _label) {
        this._label = _label;
    }
}
