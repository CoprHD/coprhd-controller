/**
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
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ExpansionValidator extends VirtualPoolValidator<VirtualPoolCommonParam, BlockVirtualPoolUpdateParam> {
   
    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool virtualPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        validateExpansion(virtualPool, updateParam, dbClient);
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        if (null != updateParam && updateParam.getExpandable() != null)
            return true;
        return false;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(VirtualPoolCommonParam createParam, DbClient dbClient) {
        // No create validation required - ProtectionValidator takes care of this
    }
    
    /**
     * If there is an attempt to set expandable expansion to true when the
     * VirtualPool currently specifies mirroring, we must fail.
     *
     */
    public void validateExpansion(VirtualPool virtualPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient){
        // Validate only when the update param does not specify mirroring.
    	// The ProtectionValidator handles the case when mirroring is specified 
    	// by update param.

        // True, if expansion is being enabled on a mirror-enabled VPool and we're not explicitly disabling
        // mirrors within the same request.
        if (updateParam.allowsExpansion() && VirtualPool.vPoolSpecifiesMirrors(virtualPool, dbClient)
                && (updateParam.getProtection() == null || updateParam.getProtection().getContinuousCopies() == null
                || updateParam.getProtection().getContinuousCopies().getMaxMirrors() == null
                || updateParam.getProtection().getContinuousCopies().getMaxMirrors() != VirtualPoolProtectionMirrorParam.MAX_DISABLED)) {
            throw APIException.badRequests.protectionVirtualPoolDoesNotSupportExpandingMirrors(virtualPool.getId());
        }
    }

    @Override
    protected boolean isCreateAttributeOn(VirtualPoolCommonParam createParam) {
        return true;
    }
}
