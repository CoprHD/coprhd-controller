/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.smis;

import com.emc.storageos.model.BulkRestRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "bulk_smis_providers")
public class SMISProviderBulkRep extends BulkRestRep {
    private List<SMISProviderRestRep> smisProviders;

    /**
     * List of SMIS Providers.
     * 
     */
    @XmlElement(name = "smis_provider")
    public List<SMISProviderRestRep> getSmisProviders() {
        if (smisProviders == null) {
            smisProviders = new ArrayList<SMISProviderRestRep>();
        }
        return smisProviders;
    }

    public void setSmisProviders(List<SMISProviderRestRep> smisProviders) {
        this.smisProviders = smisProviders;
    }

    public SMISProviderBulkRep() {
    }

    public SMISProviderBulkRep(List<SMISProviderRestRep> smisProviders) {
        this.smisProviders = smisProviders;
    }
}
