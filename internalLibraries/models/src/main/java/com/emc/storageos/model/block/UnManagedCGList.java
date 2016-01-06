/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "unmanaged_cgs")
public class UnManagedCGList {

    private List<RelatedResourceRep> unManagedCGs;

    private List<NamedRelatedResourceRep> namedUnManagedCGs;

    public UnManagedCGList() {
    }

    public UnManagedCGList(List<RelatedResourceRep> unManagedCGs) {
        this.unManagedCGs = unManagedCGs;
    }

    /**
     * The list of unmanaged cgs which are available in a protection system.
     * Used primarily to ingest cgs into ViPR.
     * 
     */
    @XmlElement(name = "unmanaged_cg")
    public List<RelatedResourceRep> getUnManagedCGs() {
        if (unManagedCGs == null) {
            unManagedCGs = new ArrayList<RelatedResourceRep>();
        }
        return unManagedCGs;
    }

    public void setUnManagedCGs(List<RelatedResourceRep> unManagedCGs) {
        this.unManagedCGs = unManagedCGs;
    }

    /**
     * The list of unmanaged cgs with name which are available in a protection system.
     * Used primarily to ingest cgs into ViPR.
     * 
     */
    @XmlElement(name = "named_unmanaged_cg")
    public List<NamedRelatedResourceRep> getNamedUnManagedCGs() {
        if (namedUnManagedCGs == null) {
            namedUnManagedCGs = new ArrayList<NamedRelatedResourceRep>();
        }
        return namedUnManagedCGs;
    }

    public void setNamedUnManagedCGs(List<NamedRelatedResourceRep> namedUnManagedCGs) {
        this.namedUnManagedCGs = namedUnManagedCGs;
    }

}
