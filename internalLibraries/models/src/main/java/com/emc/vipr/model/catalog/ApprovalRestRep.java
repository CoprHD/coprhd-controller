/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.Date;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

@XmlRootElement(name = "approval")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApprovalRestRep extends DataObjectRestRep {
    
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    
    /**
     * Approval or rejection message
     */
    private String message;
    
    /**
     * Date approve or reject was performed
     */
    private Date dateActioned;
    
    /**
     * Approval Status. One of PENDING, APPROVED, REJECTED
     */
    private String approvalStatus;
    
    /**
     * User ID this approval is approved or rejected by
     */
    private String approvedBy;     
    
    /**
     * Reference to the order this approval is related to
     */
    private RelatedResourceRep order;       
    
    /**
     * Tenant for this approval request
     */
    private RelatedResourceRep tenant;         
    
    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    
    @XmlElement(name = "date_actioned")
    public Date getDateActioned() {
        return dateActioned;
    }
    public void setDateActioned(Date dateActioned) {
        this.dateActioned = dateActioned;
    }

    @XmlElement(name = "approval_status")
    public String getApprovalStatus() {
        return approvalStatus;
    }
    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
    
    @XmlElement(name = "approved_by")
    public String getApprovedBy() {
        return approvedBy;
    }
    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }
    
    @XmlElement(name = "order")
    public RelatedResourceRep getOrder() {
        return order;
    }
    public void setOrder(RelatedResourceRep order) {
        this.order = order;
    }
    
    @XmlElement(name = "tenant")
    public RelatedResourceRep getTenant() {
        return tenant;
    }
    public void setTenant(RelatedResourceRep tenant) {
        this.tenant = tenant;
    }
    
    public boolean isPending() {
        return PENDING.equalsIgnoreCase(approvalStatus);
    }
    
    public boolean isApproved() {
        return APPROVED.equalsIgnoreCase(approvalStatus);
    }
    
    public boolean isRejected() {
        return REJECTED.equalsIgnoreCase(approvalStatus);
    }
    

}
