/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;

@Deprecated
@XmlRootElement
public class ApprovalInfo extends ModelInfo {

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
    private String status;

    /**
     * User ID this approval is approved or rejected by
     */
    private String approvedBy;

    /**
     * Reference to the order this approval is related to
     */
    private Reference order;

    /**
     * Tenant for this approval request
     */
    private String tenant;

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Date getDateActioned() {
        return dateActioned;
    }

    public void setDateActioned(Date dateActioned) {
        this.dateActioned = dateActioned;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Reference getOrder() {
        return order;
    }

    public void setOrder(Reference order) {
        this.order = order;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
