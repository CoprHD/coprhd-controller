/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.usergroup;

import com.emc.storageos.model.DataObjectRestRep;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Class that encapsulates the REST representation of a user group.
 * It also allows conversion from a UserGroup
 * data model object.
 */

@XmlRootElement(name = "user_group")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class UserGroupRestRep extends DataObjectRestRep {
    private String _domain;
    private Set<UserAttributeParam> _attributes;

    @XmlElement(required = true, name = "domain")
    @JsonProperty("domain")
    public String getDomain() {
        return _domain;
    }

    public void setDomain(String _domain) {
        this._domain = _domain;
    }

    @XmlElementWrapper(required = true, name = "attributes")
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
