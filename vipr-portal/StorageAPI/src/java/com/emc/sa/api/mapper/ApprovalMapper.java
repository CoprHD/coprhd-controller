/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.db.client.URIUtil.uri;
import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import com.emc.storageos.db.client.model.uimodels.ApprovalRequest;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.vipr.model.catalog.ApprovalCommonParam;
import com.emc.vipr.model.catalog.ApprovalRestRep;
import com.google.common.base.Function;

public class ApprovalMapper implements Function<ApprovalRequest, ApprovalRestRep> {
    
    public static final ApprovalMapper instance = new ApprovalMapper();
    
    public static ApprovalMapper getInstance() {
        return instance;
    }    
    
    public ApprovalRestRep apply(ApprovalRequest resource) {
        return map(resource);
    }    

    public static ApprovalRestRep map(ApprovalRequest from) {
        if (from == null) {
            return null;
        }
        ApprovalRestRep to = new ApprovalRestRep();
        mapDataObjectFields(from, to);
        
        if (from.getTenant() != null) {
            to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(from.getTenant())));
        }  
        
        if (from.getOrderId() != null) {
            to.setOrder(toRelatedResource(ResourceTypeEnum.ORDER, from.getOrderId()));
        }
        
        
        to.setApprovedBy(from.getApprovedBy());
        to.setDateActioned(from.getDateActioned());
        to.setMessage(from.getMessage());
        to.setApprovalStatus(from.getApprovalStatus());

        return to;
    }    
    
    public static void updateObject(ApprovalRequest object, ApprovalCommonParam param) {
        if (param.getMessage() != null) {
            object.setMessage(param.getMessage());
        }
        if (param.getApprovalStatus() != null) {
            object.setApprovalStatus(param.getApprovalStatus());
        }    
    }        
    
}
