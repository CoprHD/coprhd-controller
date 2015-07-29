/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;

public class ApprovalCommonParam {

    private String message;
    private String approvalStatus;

    @XmlElement(name = "message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @XmlElement(name = "approval_status")
    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String status) {
        this.approvalStatus = status;
    }

}
