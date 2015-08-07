/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.catalog.search;

import static com.emc.vipr.client.catalog.impl.SearchConstants.APPROVAL_STATUS_PARAM;
import static com.emc.vipr.client.catalog.impl.SearchConstants.ORDER_ID_PARAM;
import static com.emc.vipr.client.catalog.impl.SearchConstants.TENANT_ID_PARAM;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.emc.vipr.client.core.AbstractResources;
import com.emc.vipr.client.core.search.SearchBuilder;
import com.emc.vipr.model.catalog.ApprovalRestRep;

public class ApprovalSearchBuilder extends SearchBuilder<ApprovalRestRep> {

    public ApprovalSearchBuilder(AbstractResources<ApprovalRestRep> resources) {
        super(resources);
    }

    public SearchBuilder<ApprovalRestRep> byStatus(String status) {
        return byStatus(status, null);
    }

    public SearchBuilder<ApprovalRestRep> byStatus(String status, URI tenantId) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (tenantId != null) {
            parameters.put(TENANT_ID_PARAM, tenantId);
        }
        parameters.put(APPROVAL_STATUS_PARAM, status);
        return byAll(parameters);
    }

    public SearchBuilder<ApprovalRestRep> byOrderId(URI orderId) {
        return byOrderId(orderId, null);
    }

    public SearchBuilder<ApprovalRestRep> byOrderId(URI orderId, URI tenantId) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (tenantId != null) {
            parameters.put(TENANT_ID_PARAM, tenantId);
        }
        parameters.put(ORDER_ID_PARAM, orderId);
        return byAll(parameters);
    }

}
