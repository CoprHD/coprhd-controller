/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

/**
 * Response to a bulk query for the list of unmanaged CGs.
 * 
 */
@XmlRootElement(name = "bulk_unmanaged_cgs")
public class UnManagedCGsBulkRep extends BulkRestRep {
    private List<UnManagedCGRestRep> unManagedCGs;

    /**
     * List of unmanaged CGs. UnManaged CGs are CGs that are
     * present within ViPR, but are not under ViPR management. ViPR provides
     * an ingest capability that enables users to bring the unmanaged
     * CGs under ViPR management.
     * 
     */
    @XmlElement(name = "unmanaged_cg")
    public List<UnManagedCGRestRep> getUnManagedCGs() {
        if (unManagedCGs == null) {
            unManagedCGs = new ArrayList<UnManagedCGRestRep>();
        }
        return unManagedCGs;
    }

    public void setUnManagedCGs(List<UnManagedCGRestRep> unManagedCGs) {
        this.unManagedCGs = unManagedCGs;
    }

    public UnManagedCGsBulkRep() {
    }

    public UnManagedCGsBulkRep(List<UnManagedCGRestRep> unManagedCGs) {
        this.unManagedCGs = unManagedCGs;
    }
}
