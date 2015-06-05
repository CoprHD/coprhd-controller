/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.ApprovalStatus;
import com.emc.storageos.security.authentication.StorageOSUser;

public interface ApprovalManager {

    public ApprovalRequest getApprovalById(URI id);

    public List<ApprovalRequest> getApprovals(URI tenantId);

    public List<ApprovalRequest> findApprovalsByStatus(URI tenantId, ApprovalStatus approvalStatus);

    public List<ApprovalRequest> findApprovalsByOrderId(URI orderId);

    public ApprovalRequest findFirstApprovalsByOrderId(URI orderId);

    public void createApproval(ApprovalRequest approval);

    public void updateApproval(ApprovalRequest approval, StorageOSUser user);

    public void deleteApproval(ApprovalRequest approval);

}
