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
 * PUT API payload for user groups update.
 */
@XmlRootElement(name = "update_user_group")
public class UserGroupUpdateParam extends UserGroupBaseParam {
    private Set<UserAttributeParam> _addAttributes;

    @XmlElementWrapper(name="add_attributes")
    @XmlElement(required=true, name="attribute")
    @JsonProperty("add_attributes")
    public Set<UserAttributeParam> getAddAttributes() {
        if(_addAttributes == null){
            _addAttributes = new HashSet<>();
        }
        return _addAttributes;
    }

    public void setAddAttributes(Set<UserAttributeParam> _addAttributes) {
        this._addAttributes = _addAttributes;
    }

    private Set<String> _removeAttributes;

    @XmlElementWrapper(name="remove_attributes")
    @XmlElement(required=true, name="key")
    @JsonProperty("remove_attributes")
    public Set<String> getRemoveAttributes() {
        if(_removeAttributes == null){
            _removeAttributes = new HashSet<>();
        }
        return _removeAttributes;
    }

    public void setRemoveAttributes(Set<String> _removeAttributes) {
        this._removeAttributes = _removeAttributes;
    }
}
