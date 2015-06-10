/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import com.emc.storageos.model.NamedRelatedResourceRep;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "authnproviders")
public class AuthnProviderList {

    /**
     * List of the authentication providers in the system
     * @valid none
     */
    private List<NamedRelatedResourceRep> providers;

    public AuthnProviderList() {}
    
    public AuthnProviderList(List<NamedRelatedResourceRep> providers) {
        this.providers = providers;
    }
    
    @XmlElement(name = "authnprovider")
    public List<NamedRelatedResourceRep> getProviders() {
        if (providers == null) {
            providers = new ArrayList<NamedRelatedResourceRep>();
        }
        return providers;
    }

    public void setProviders(List<NamedRelatedResourceRep> providers) {
        this.providers = providers;
    }
    
}
