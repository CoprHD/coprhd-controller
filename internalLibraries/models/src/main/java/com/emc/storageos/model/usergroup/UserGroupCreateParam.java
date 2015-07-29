/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.usergroup;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashSet;
import java.util.Set;

/**
 * POST API payload for user groups creation.
 */
@XmlRootElement(name = "create_user_group")
public class UserGroupCreateParam extends UserGroupBaseParam {
    private Set<UserAttributeParam> _attributes;

    @XmlElementWrapper(name = "attributes")
    @XmlElement(required = true, name = "attribute")
    @JsonProperty("attributes")
    public Set<UserAttributeParam> getAttributes() {
        if (_attributes == null) {
            _attributes = new HashSet<>();
        }
        return _attributes;
    }

    public void setAttributes(Set<UserAttributeParam> _attributes) {
        this._attributes = _attributes;
    }
}
