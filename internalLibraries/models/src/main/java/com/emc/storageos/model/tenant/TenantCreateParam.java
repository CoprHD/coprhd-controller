/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.tenant;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.model.valid.Length;

@XmlRootElement(name = "tenant_create")
public class TenantCreateParam extends TenantParam {

    private String label;
    private List<UserMappingParam> userMappings;

    public TenantCreateParam() {
    }

    public TenantCreateParam(String label, List<UserMappingParam> userMappings) {
        this.label = label;
        this.userMappings = userMappings;
    }

    /**
     * Name of the tenant to create
     * 
     * @valid any free form string within length limits
     */
    @XmlElement(required = true, name = "name")
    @Length(min = 2, max = 128)
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * List of user mappings for this tenant
     * 
     * @valid none
     */
    @XmlElementWrapper(name = "user_mappings")
    @XmlElement(required = true, name = "user_mapping")
    public List<UserMappingParam> getUserMappings() {
        if (userMappings == null) {
            userMappings = new ArrayList<UserMappingParam>();
        }
        return userMappings;
    }

    public void setUserMappings(List<UserMappingParam> userMappings) {
        this.userMappings = userMappings;
    }

}
