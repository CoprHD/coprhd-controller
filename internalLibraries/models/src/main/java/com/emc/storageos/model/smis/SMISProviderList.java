/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "smis_providers")
public class SMISProviderList {

    private List<NamedRelatedResourceRep> smisProviders;

    public SMISProviderList() {
    }

    public SMISProviderList(List<NamedRelatedResourceRep> smisProviders) {
        this.smisProviders = smisProviders;
    }

    /**
     * List of SMIS Providers.
     * 
     */
    @XmlElement(name = "smis_provider")
    public List<NamedRelatedResourceRep> getSmisProviders() {
        if (smisProviders == null) {
            smisProviders = new ArrayList<NamedRelatedResourceRep>();
        }
        return smisProviders;
    }

    public void setSmisProviders(List<NamedRelatedResourceRep> smisProviders) {
        this.smisProviders = smisProviders;
    }

}
