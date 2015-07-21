/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class NameValidator extends VirtualPoolValidator<VirtualPoolCommonParam, VirtualPoolUpdateParam> {
   
    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool cos, VirtualPoolUpdateParam updateParam, DbClient dbClient) {
        validateNameString(updateParam.getName());
    }

    @Override
    protected boolean isUpdateAttributeOn(VirtualPoolUpdateParam updateParam) {
        if (null != updateParam.getName())
            return true;
        return false;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(VirtualPoolCommonParam createParam, DbClient dbClient) {
        validateNameString(createParam.getName());
    }
    
    /**
     * Fires APIException.badRequests.requiredParameterMissingOrEmpty if the
     * given collection is empty
     * 
     */
    public void validateNameString(String name){
        if(StringUtils.isEmpty(name) || NullColumnValueGetter.getNullStr().equalsIgnoreCase(name)) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("name");
        }
    }

    @Override
    protected boolean isCreateAttributeOn(VirtualPoolCommonParam createParam) {
        return true;
    }
}
