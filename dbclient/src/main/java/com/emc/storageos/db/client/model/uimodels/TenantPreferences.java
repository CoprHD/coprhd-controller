/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.Name;

@Cf("TenantPreferences")
public class TenantPreferences extends ModelObject implements TenantDataObject {

    public static final String TENANT = TENANT_COLUMN_NAME;
    public static final String APPROVER_EMAIL = "approverEmail";
    public static final String APPROVAL_URL = "approvalUrl";

    private String tenant;
    private String approverEmail;
    private String approvalUrl;

    @AlternateId("TenantToTenantPreferences")
    @Name(TENANT)
    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
        setChanged(TENANT);
    }

    @Name(APPROVER_EMAIL)
    public String getApproverEmail() {
        return approverEmail;
    }

    public void setApproverEmail(String approverEmail) {
        this.approverEmail = approverEmail;
        setChanged(APPROVER_EMAIL);
    }

    @Name(APPROVAL_URL)
    public String getApprovalUrl() {
        return approvalUrl;
    }

    public void setApprovalUrl(String approvalUrl) {
        this.approvalUrl = approvalUrl;
        setChanged(APPROVAL_URL);
    }

    @Override
    public Object[] auditParameters() {
        return new Object[] { getLabel(),
                getApprovalUrl(), getApproverEmail(), getTenant(), getId() };
    }
}
