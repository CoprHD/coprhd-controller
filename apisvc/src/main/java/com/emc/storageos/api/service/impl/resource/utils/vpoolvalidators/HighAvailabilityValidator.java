/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import java.net.URI;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class HighAvailabilityValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool virtualPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        validateHighAvailabilityTypeForUpdate(virtualPool, updateParam);
        validateDistributedHighAvailabilityForUpdate(virtualPool, updateParam, dbClient);
        validateVplexHANotSRDFProtected(updateParam.getHighAvailability(), dbClient);
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        if (null != updateParam && updateParam.getHighAvailability() != null) {
            return true;
        }
        return false;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        // Validations similar to the update ones in this class are currently defined
        // in the BlockVirtualPoolService.createBlockVirtualPool() - for create. These
        // should be moved over here. Validations for mixed protection are done in the
        // ProtectionValidator so no need to have them in this class.
        
        validateVplexHANotSRDFProtected(createParam.getHighAvailability(), dbClient);
    }

    /**
     * Validates that the high availability type is one of the required
     * values.
     * 
     * @param virtualPool The virtual pool being updated
     * @param updateParam The paramater containing the updates
     */
    public void validateHighAvailabilityTypeForUpdate(
            VirtualPool virtualPool, BlockVirtualPoolUpdateParam updateParam) {
        // If the type is null high availability is attempting to be removed so skip the validation.
        if (updateParam.getHighAvailability().getType() != null) {
            final boolean condition = updateParam.specifiesHighAvailability();
            if (!condition) {
                throw APIException.badRequests.invalidParameterHighAvailabilityType(updateParam.getHighAvailability().getType());
            }
        }
    }

    /**
     * Validates high availability on a block virtual pool update.
     * 
     * @param virtualPool The virtual pool being updated
     * @param updateParam The parameter containing the updates
     * @param dbClient The dbclient
     */
    public void validateDistributedHighAvailabilityForUpdate(
            VirtualPool virtualPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        // If the high availability type is distributed, then the user must also specify the high
        // availability varray. The user may also specify the high availability VirtualPool.
        if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(
                updateParam.getHighAvailability().getType())) {

            if ((updateParam.getHighAvailability().getHaVirtualArrayVirtualPool() == null)
                    || updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray() == null
                    || String.valueOf(updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray()).isEmpty()) {
                throw APIException.badRequests.invalidParameterHighAvailabilityVirtualArrayRequiredForType(updateParam
                        .getHighAvailability().getType());
            }

            // High availability varray must be specified and valid.
            _logger.debug("HA varray VirtualPool map specifies the HA varray {}",
                    updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray());
            VirtualArray haVirtualArray = dbClient.queryObject(VirtualArray.class,
                    updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray());
            ArgValidator.checkEntity(haVirtualArray,
                    updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray(), false);
            String haVirtualArrayId = updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray()
                    .toString();

            // Check the HA varray VirtualPool, which is not required.
            String haNhVirtualPoolId = null;
            if (updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool() != null &&
                    !String.valueOf(updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool()).isEmpty()) {
                _logger.debug("HA varray VirtualPool map specifies the HA vpool {}",
                        updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool());
                VirtualPool haVirtualPool = dbClient.queryObject(VirtualPool.class,
                        updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool());
                ArgValidator.checkEntity(haVirtualPool,
                        updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool(), false);
                haNhVirtualPoolId = updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool()
                        .toString();

                // Further validate that this VirtualPool is valid for the
                // specified high availability varray.
                StringSet haVirtualPoolNHs = haVirtualPool.getVirtualArrays();
                if ((haVirtualPoolNHs != null) && (!haVirtualPoolNHs.isEmpty())) {
                    if (!haVirtualPoolNHs.contains(haVirtualArrayId)) {
                        throw APIException.badRequests.invalidParameterHighAvailabilityVirtualPoolNotValidForVirtualArray(
                                haNhVirtualPoolId, haVirtualArrayId);
                    }
                }
            }
        } else if (updateParam.getHighAvailability().getType() != null
                && !String.valueOf(updateParam.getHighAvailability().getType()).isEmpty()) {
            if (updateParam.getHighAvailability().getHaVirtualArrayVirtualPool() != null
                    && (updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray() != null
                    && !String.valueOf(updateParam.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray()).isEmpty())) {
                throw APIException.badRequests.invalidParameterVirtualArrayAndVirtualPoolDoNotApplyForType(updateParam
                        .getHighAvailability().getType());
            }

        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        if (null != createParam && createParam.getHighAvailability() != null) {
            return true;
        }
        return false;
    }
    
    private void validateVplexHANotSRDFProtected(VirtualPoolHighAvailabilityParam haParam, DbClient dbClient) {
        if (haParam == null) {
            return;
        }
        if (haParam.getHaVirtualArrayVirtualPool() != null) {
            // Look up the high availability virtual pool.
            URI haVpoolURI = haParam.getHaVirtualArrayVirtualPool().getVirtualPool();
            if (haVpoolURI != null) {
                VirtualPool haVpool = dbClient.queryObject(VirtualPool.class, haVpoolURI);
                if (VirtualPool.vPoolSpecifiesSRDF(haVpool)) {
                    throw APIException.badRequests.srdfNotSupportedOnHighAvailabilityVpool();
                }
            }
        }
    }
}
