/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.model.customconfig;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "scopes")
public class ScopeParamList {
    private List<ConfigTypeScopeParam> scopes;

    public ScopeParamList() {
    }

    public ScopeParamList(List<ConfigTypeScopeParam> scopes) {
        this.scopes = scopes;
    }

    @XmlElement(name = "scope")
    public List<ConfigTypeScopeParam> getScopes() {
        return scopes;
    }

    public void setScopes(List<ConfigTypeScopeParam> scopes) {
        this.scopes = scopes;
    }

}
