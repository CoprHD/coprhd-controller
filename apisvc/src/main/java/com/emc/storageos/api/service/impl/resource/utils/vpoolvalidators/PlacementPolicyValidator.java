/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.ResourcePlacementPolicyType;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPChanges;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteMirrorProtectionParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionUpdateParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class PlacementPolicyValidator extends VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {

    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(
            VirtualPool vPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        if (updateParam.getPlacementPolicy() == null) {
            // validate policy with system type, high availability, protection update
            if (ResourcePlacementPolicyType.array_affinity.name().equals(vPool.getPlacementPolicy())) {
                // current placement policy is array affinity, and it is not being updated
                // only need to validate with system type, high availability, protection update, not current values of those
                validateSystemType(updateParam.getSystemType(), null);
                validateHighAvailability(updateParam.getHighAvailability(), null);
                validateProtection(updateParam.getProtection(), null);
            }
        } else if (isArrayAffinity(updateParam.getPlacementPolicy())) { // validate policy with system type, high availability, protection update
                                                                        // or current values
            validateSystemType(updateParam.getSystemType(), vPool);
            validateHighAvailability(updateParam.getHighAvailability(), vPool);
            validateProtection(updateParam.getProtection(), vPool);
        }
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        // must validate update of system type, high availability and protection for current array affinity policy
        // if policy is not being updated
        return updateParam != null &&
                (updateParam.getPlacementPolicy() != null || updateParam.getSystemType() != null ||
                updateParam.getHighAvailability() != null || updateParam.getProtection() != null);
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        if (isArrayAffinity(createParam.getPlacementPolicy())) {
            validateSystemType(createParam.getSystemType(), null);
            validateHighAvailability(createParam.getHighAvailability(), null);
            validateProtection(createParam.getProtection());
        }
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        return createParam.getPlacementPolicy() != null;
    }

    private boolean isArrayAffinity(String policyName) {
        ResourcePlacementPolicyType policyType = ResourcePlacementPolicyType.lookup(policyName);
        if (policyType == null) {
            throw APIException.badRequests.unsupportedPlacementPolicy(policyName);
        }

        return policyType.equals(ResourcePlacementPolicyType.array_affinity);
    }

    /**
     * Checks to see if the updated system type or current system type has the system types that support array affinity
     *
     * @param system systemType being updated
     * @param vPool Virtual Pool (null for creating new virtual pool, or validating current value is not necessary)
     */
    private void validateSystemType(String system, VirtualPool vPool) {
        // validate system type is vmax/vnxblock/xtremio/unity if aray_affinity is used
        // Array affinity is only supported for these array types
        if (system != null) {
            if (!VirtualPool.SystemType.NONE.name().equals(system)) {
                SystemType systemType = SystemType.lookup(system);
                if (systemType != null && !systemType.equals(SystemType.vmax) &&
                        !systemType.equals(SystemType.vnxblock) && !systemType.equals(SystemType.xtremio) &&
                        !systemType.equals(SystemType.unity)) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForSystemType(system);
                }
            }
        } else if (vPool != null) {
            StringSet systemTypes = null;
            StringSetMap arrayInfo = vPool.getArrayInfo();
            if (arrayInfo != null && !arrayInfo.isEmpty()) {
                systemTypes = arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            }

            if (systemTypes != null && !systemTypes.isEmpty() && !systemTypes.contains(VirtualPool.SystemType.NONE.name())) {
                if (!systemTypes.contains(SystemType.vmax.name()) && !systemTypes.contains(SystemType.vnxblock.name()) && !systemTypes.contains(SystemType.xtremio.name()) &&
                        !systemTypes.contains(SystemType.unity.name())) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForSystemType(CommonTransformerFunctions.collectionString(systemTypes));
                }
            }
        }
    }

    /**
     * Validate high availability
     * Array affinity policy cannot be used for VPool with high availability
     *
     * @param haParam VirtualPoolHighAvailabilityPara
     * @param vPool Virtual Pool (null for creating new virtual pool, or validating current value is not necessary)
     */
    private void validateHighAvailability(VirtualPoolHighAvailabilityParam haParam, VirtualPool vPool) {
        if (haParam != null) {
            if (VirtualPool.HighAvailabilityType.vplex_local.name().equals(haParam.getType()) ||
                    VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(haParam.getType())) {
                throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForHighAvailability();
            }
        } else if (vPool != null) {
            if (VirtualPool.vPoolSpecifiesHighAvailability(vPool) || VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(vPool)) {
                throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForHighAvailability();
            }
        }
    }

    /**
     * Validates protection
     * Array affinity policy cannot be used for VPool with RP or remote copies
     *
     * @param protectionParam BlockVirtualPoolProtectionParam
     */
    private void validateProtection(BlockVirtualPoolProtectionParam protectionParam) {
        if (protectionParam != null) {
            VirtualPoolProtectionRPParam rpParam = protectionParam.getRecoverPoint();
            if (rpParam != null) {
                if (!CollectionUtils.isEmpty(rpParam.getCopies())) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForRPOrRemoteCopies();
                }

                ProtectionSourcePolicy rpPolicy = rpParam.getSourcePolicy();
                if (rpPolicy != null &&
                        (rpPolicy.getJournalSize() != null || rpPolicy.getJournalVarray() != null ||
                         rpPolicy.getJournalVpool() != null || rpPolicy.getRemoteCopyMode() != null ||
                         rpPolicy.getRpoType() != null || rpPolicy.getRpoValue() != null ||
                         rpPolicy.getStandbyJournalVarray() != null || rpPolicy.getStandbyJournalVpool() != null)) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForRPOrRemoteCopies();
                }
            }

            VirtualPoolRemoteMirrorProtectionParam remoteProtection = protectionParam.getRemoteCopies();
            if (remoteProtection != null) {
                if (!CollectionUtils.isEmpty(remoteProtection.getRemoteCopySettings())) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForRPOrRemoteCopies();
                }
            }
        }
    }

    /**
     * Validates protection
     * Array affinity policy cannot be used for VPool with RP or remote copies
     *
     * @param protectionParam BlockVirtualPoolProtectionUpdateParam
     * @param vPool Virtual Pool (null for creating new virtual pool, or validating current value is not necessary)
     */
    private void validateProtection(BlockVirtualPoolProtectionUpdateParam protectionParam, VirtualPool vPool) {
        if (protectionParam != null) {
            VirtualPoolProtectionRPChanges rpChanges = protectionParam.getRecoverPoint();
            if (rpChanges != null) {
                if ((rpChanges.getAdd() != null && !rpChanges.getAdd().isEmpty()) ||
                        (rpChanges.getRemove() != null && !rpChanges.getRemove().isEmpty())
                        || rpChanges.getSourcePolicy() != null) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForRPOrRemoteCopies();
                }
            }

            VirtualPoolRemoteProtectionUpdateParam remoteCopies = protectionParam.getRemoteCopies() ;
            if (remoteCopies != null) {
                if ((remoteCopies.getAdd() != null && !remoteCopies.getAdd().isEmpty()) ||
                        (remoteCopies.getRemove() != null && !remoteCopies.getRemove().isEmpty())) {
                    throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForRPOrRemoteCopies();
                }
            }
        } else if (vPool != null) {
            if (VirtualPool.vPoolSpecifiesProtection(vPool) || VirtualPool.vPoolSpecifiesSRDF(vPool)) {
                throw APIException.badRequests.arrayAffinityPlacementPolicyNotAllowedForRPOrRemoteCopies();
            }
        }
    }
}
