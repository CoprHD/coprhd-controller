/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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

    @XmlElementWrapper(name="attributes")
    @XmlElement(required=true, name="attribute")
    @JsonProperty("attributes")
    public Set<UserAttributeParam> getAttributes() {
        if(_attributes == null){
            _attributes = new HashSet<>();
        }
        return _attributes;
    }

    public void setAttributes(Set<UserAttributeParam> _attributes) {
        this._attributes = _attributes;
    }
}
