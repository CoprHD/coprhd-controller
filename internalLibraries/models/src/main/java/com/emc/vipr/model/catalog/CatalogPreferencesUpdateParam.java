/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "catalog_preferences_update")
public class CatalogPreferencesUpdateParam {

    private String tenantId;
    private String approverEmail;
    private String approvalUrl;

    @XmlElement(name = "approver_email", required = false, nillable = true)
    public String getApproverEmail() {
        return approverEmail;
    }

    public void setApproverEmail(String approverEmail) {
        this.approverEmail = approverEmail;
    }

    @XmlElement(name = "approval_url", required = false, nillable = true)
    public String getApprovalUrl() {
        return approvalUrl;
    }

    public void setApprovalUrl(String approvalUrl) {
        this.approvalUrl = approvalUrl;
    }

    @XmlElement(name = "tenant_id", required = false, nillable = true)
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

}
