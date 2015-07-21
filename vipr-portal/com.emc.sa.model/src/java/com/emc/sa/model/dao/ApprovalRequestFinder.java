/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.db.client.model.uimodels.ApprovalStatus;
import com.emc.sa.model.util.TenantUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.google.common.collect.Lists;

public class ApprovalRequestFinder extends TenantModelFinder<ApprovalRequest> {

    public ApprovalRequestFinder(DBClientWrapper client) {
        super(ApprovalRequest.class, client);
    }

    public List<ApprovalRequest> findByOrderId(URI orderId) {
        if (orderId == null) {
            return Lists.newArrayList();
        }

        List<NamedElement> approvalRequestIds = client.findBy(ApprovalRequest.class, ApprovalRequest.ORDER_ID, orderId);

        return findByIds(toURIs(approvalRequestIds));
    }

    public List<ApprovalRequest> findByApprovalStatus(String tenant, ApprovalStatus approvalStatus) {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        
        List<NamedElement> ids = client.findByAlternateId(ApprovalRequest.class, ApprovalRequest.APPROVAL_STATUS,
                approvalStatus.name());
        List<ApprovalRequest> approvalRequests = findByIds(toURIs(ids));
        return TenantUtils.filter(approvalRequests, tenant) ;
    }
    
    
    
}
