/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import com.emc.storageos.db.client.model.uimodels.TenantPreferences;
import com.emc.vipr.model.catalog.CatalogPreferencesRestRep;
import com.emc.vipr.model.catalog.CatalogPreferencesUpdateParam;

public class CatalogPreferencesMapper {

    public static CatalogPreferencesRestRep map(TenantPreferences from) {
        if (from == null) {
            return null;
        }
        CatalogPreferencesRestRep to = new CatalogPreferencesRestRep();

        mapDataObjectFields(from, to);
        
        to.setApprovalUrl(from.getApprovalUrl());
        to.setApproverEmail(from.getApproverEmail());
        
        return to;
    }    

    public static void updateObject(TenantPreferences object, CatalogPreferencesUpdateParam param) {
        if (param.getApprovalUrl() != null) {
            object.setApprovalUrl(param.getApprovalUrl());
        }
        if (param.getApproverEmail() != null) {
            object.setApproverEmail(param.getApproverEmail());
        }
    }           
    
}
