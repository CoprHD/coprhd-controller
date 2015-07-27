/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.model.valid.EnumType;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.Date;

/**
 * Model for an approval request.
 *
 * @author Chris Dail
 */
@Cf("ApprovalRequest")
public class ApprovalRequest extends ModelObject implements TenantDataObject {
    public static final String MESSAGE = "message";
    public static final String DATE_ACTIONED = "dateActioned";
    public static final String APPROVAL_STATUS = "approvalStatus";
    public static final String APPROVED_BY = "approvedBy";
    public static final String ORDER_ID = "orderId";
    public static final String TENANT = TenantDataObject.TENANT_COLUMN_NAME;
    
    private String message;

    private Date dateActioned;

    private String approvalStatus;    

    private String approvedBy;
    
    private URI orderId;    
    
    private String tenant;

    @Name(MESSAGE)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        setChanged(MESSAGE);
    }

    @Name(DATE_ACTIONED)
    public Date getDateActioned() {
        return dateActioned;
    }

    public void setDateActioned(Date dateActioned) {
        this.dateActioned = dateActioned;
        setChanged(DATE_ACTIONED);
    }

    @AlternateId("ApprovalStatusToApproval")
    @EnumType(ApprovalStatus.class)
    @Name("approvalStatus")
    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String status) {
        this.approvalStatus = status;
        setChanged("approvalStatus");
    }

    @Name(APPROVED_BY)
    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
        setChanged(APPROVED_BY);
    }

    @RelationIndex(cf = "RelationIndex", type = Order.class)
    @Name(ORDER_ID)    
    public URI getOrderId() {
        return orderId;
    }

    public void setOrderId(URI orderId) {
        this.orderId = orderId;
        setChanged(ORDER_ID);
    }

    @AlternateId("TenantToApprovalRequest")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }    
    
    public boolean approved() {
       return StringUtils.equalsIgnoreCase(approvalStatus, ApprovalStatus.APPROVED.name());
    }
    
    public boolean rejected() {
        return StringUtils.equalsIgnoreCase(approvalStatus, ApprovalStatus.REJECTED.name()); 
    }

    public boolean pending() {
        return StringUtils.equalsIgnoreCase(approvalStatus, ApprovalStatus.PENDING.name()); 
    }    
    
    @Override
    public Object[] auditParameters() {
        return new Object[] {getLabel(), 
                getApprovalStatus(), getTenant(), getId() };
    }        
   
}
