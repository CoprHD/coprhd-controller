/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.sa.catalog;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.ApprovalStatus;
import com.emc.sa.model.dao.ModelClient;
import com.emc.storageos.security.authentication.StorageOSUser;

@Component
public class ApprovalManagerImpl implements ApprovalManager {
    
    private static final Logger log = Logger.getLogger(ApprovalManagerImpl.class);
    
    @Autowired
    private ModelClient client;
 
    public ApprovalRequest getApprovalById(URI id) {
        if (id == null) {
            return null;
        }

        ApprovalRequest approval = client.approvalRequests().findById(id);

        return approval;
    }          
    
    public List<ApprovalRequest> getApprovals(URI tenantId) {
        return client.approvalRequests().findAll(tenantId.toString());
    }
    
    public List<ApprovalRequest> findApprovalsByStatus(URI tenantId, ApprovalStatus approvalStatus) {
        return client.approvalRequests().findByApprovalStatus(tenantId.toString(), approvalStatus);
    }
    
    public List<ApprovalRequest> findApprovalsByOrderId(URI orderId) {
        return client.approvalRequests().findByOrderId(orderId);
    }
    
    public ApprovalRequest findFirstApprovalsByOrderId(URI orderId) {
        List<ApprovalRequest> apporvalRequests = client.approvalRequests().findByOrderId(orderId);
        if (apporvalRequests != null && apporvalRequests.size() > 0) {
            return apporvalRequests.get(0);
        }
        return null;
    } 
    
    public void createApproval(ApprovalRequest approval) {
        client.save(approval);
    }
    
    public void updateApproval(ApprovalRequest approval, StorageOSUser user) {
        approval.setDateActioned(new Date());
        if (approval.approved() || approval.rejected()) {
            approval.setApprovedBy(user.getUserName());
        }
        client.save(approval);
    }
    
    public void deleteApproval(ApprovalRequest approval) {
        client.delete(approval);
    }    
    
    
}
