/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "tenant_update")
public class TenantUpdateParam extends TenantParam {

    private String label;
    private UserMappingChanges userMappingChanges;

    public TenantUpdateParam() {}
    
    public TenantUpdateParam(String label, UserMappingChanges userMappingChanges) {
        this.label = label;
        this.userMappingChanges = userMappingChanges;
    }

    /**
     * Name change for the tenant
     * @valid any string within length limits
     */
    @XmlElement(required = false, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Changes of user mappings for this tenant
     * @valid There can be at most one add element and at most one remove element
     */
    @XmlElement(name="user_mapping_changes")
    @JsonProperty("user_mapping_changes")
    public UserMappingChanges getUserMappingChanges() {
        return userMappingChanges;
    }

    public void setUserMappingChanges(UserMappingChanges userMappingChanges) {
        this.userMappingChanges = userMappingChanges;
    }
  
}
