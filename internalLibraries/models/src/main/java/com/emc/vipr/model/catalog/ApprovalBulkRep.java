/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_approvals")
public class ApprovalBulkRep extends BulkRestRep {

    private List<ApprovalRestRep> approvals;

    public ApprovalBulkRep() {

    }

    public ApprovalBulkRep(List<ApprovalRestRep> approvals) {
        this.approvals = approvals;
    }

    /**
     * List of approvals
     * 
     * @return
     */
    @XmlElement(name = "approval")
    public List<ApprovalRestRep> getApprovals() {
        if (approvals == null) {
            approvals = new ArrayList<>();
        }
        return approvals;
    }

    public void setApprovals(List<ApprovalRestRep> approvals) {
        this.approvals = approvals;
    }

}
