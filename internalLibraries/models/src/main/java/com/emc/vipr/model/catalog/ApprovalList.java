/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "approvals")
public class ApprovalList {

    private List<NamedRelatedResourceRep> approvals;

    public ApprovalList() {
    }

    public ApprovalList(List<NamedRelatedResourceRep> approvals) {
        this.approvals = approvals;
    }

    /**
     * List of approvals
     * 
     * @valid none
     */
    @XmlElement(name = "approval")
    public List<NamedRelatedResourceRep> getApprovals() {
        if (approvals == null) {
            approvals = new ArrayList<>();
        }
        return approvals;
    }

    public void setApprovals(List<NamedRelatedResourceRep> approvals) {
        this.approvals = approvals;
    }
}
