/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class DescriptionValidator extends VirtualPoolValidator<VirtualPoolCommonParam, VirtualPoolUpdateParam> {
   
    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, VirtualPoolUpdateParam updateParam, DbClient dbClient) {
        checkNullString(updateParam.getDescription());
    }

    @Override
    protected boolean isUpdateAttributeOn(VirtualPoolUpdateParam updateParam) {
        if (null != updateParam.getDescription())
            return true;
        return false;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(VirtualPoolCommonParam createParam, DbClient dbClient) {
        checkNullString(createParam.getDescription());
    }
    
    /**
     * Fires APIException.badRequests.invalidParameter if the given collection
     * is empty
     * 
     */
    public void checkNullString(String description){
        if(NullColumnValueGetter.getNullStr().equalsIgnoreCase(description)) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("description");
        }
    }

    @Override
    protected boolean isCreateAttributeOn(VirtualPoolCommonParam createParam) {
        if (null != createParam.getDescription())
            return true;
        return false;
    }
}
