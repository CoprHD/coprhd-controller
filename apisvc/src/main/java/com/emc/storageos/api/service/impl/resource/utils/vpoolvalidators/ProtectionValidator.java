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

import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Maps.filterEntries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPChanges;
import com.emc.storageos.model.vpool.VirtualPoolProtectionRPParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Predicate;

public class ProtectionValidator extends VirtualPoolValidator<BlockVirtualPoolParam,BlockVirtualPoolUpdateParam> {

    private static final String HIGH_AVAILABILITY         = "highAvailability";
    private static final String HIGH_AVAILABILITY_NONE    = "NONE";
    
    private static final String PROTECTION_RP        = "protection.rp";
    private static final String PROTECTION_MIRRORS   = "protection.mirrors";
    private static final String PROTECTION_SNAPSHOTS = "protection.snapshots";

    private static final String JOURNAL_REGEX_1 = "[0-9]+(GB|TB|MB)";
    private static final String JOURNAL_REGEX_2 = "[0-9]+(\\.[0-9]+)?[xX]";
    private static final String JOURNAL_MIN     = "min";


    /**
     * Add known protections to the returned Map instance.
     *
     * @param createParam VirtualPool CreateParam instance
     * @return A Map containing known protection parameters
     */
    private Map<String, Object> getProtectionParameters(BlockVirtualPoolParam createParam) {
        Map<String, Object> protections = new HashMap<String, Object>();

        if (createParam.getHighAvailability() != null) {
            protections.put(HIGH_AVAILABILITY, createParam.getHighAvailability().getType());
        }
        if (createParam.getProtection() != null) {
            if (createParam.getProtection().getRecoverPoint() != null) {
                protections.put(PROTECTION_RP, createParam.getProtection().getRecoverPoint());
            }
            if (createParam.getProtection().getContinuousCopies() != null &&
                createParam.getProtection().getContinuousCopies().getMaxMirrors() != null &&
                createParam.getProtection().getContinuousCopies().getMaxMirrors() != VirtualPool.MAX_DISABLED) {
                protections.put(PROTECTION_MIRRORS, createParam.getProtection().getContinuousCopies().getMaxMirrors());
            }
            if (createParam.getProtection().getSnapshots() != null &&
                createParam.getProtection().getSnapshots().getMaxSnapshots() != null &&
                createParam.getProtection().getSnapshots().getMaxSnapshots() != VirtualPool.MAX_DISABLED) {
                protections.put(PROTECTION_SNAPSHOTS, createParam.getProtection().getSnapshots().getMaxSnapshots());
            }
        }

        return protections;
    }

    /**
     * Add known protection updates to the returned Map instance.
     *
     * @param updateParam VirtualPoolUpdateParam instance
     * @return A Map containing known protection update parameters
     */
    private Map<String, Object> getProtectionUpdateParameters(BlockVirtualPoolUpdateParam updateParam) {
        Map<String, Object> protections = new HashMap<String, Object>();

        if (updateParam.getHighAvailability() != null) {
            protections.put(HIGH_AVAILABILITY, updateParam.getHighAvailability().getType());
        }
        if (updateParam.getProtection() != null) {
            if (updateParam.getProtection().getRecoverPoint() != null) {
                protections.put(PROTECTION_RP, updateParam.getProtection().getRecoverPoint());
            }
            if (updateParam.getProtection().getContinuousCopies() != null &&
                updateParam.getProtection().getContinuousCopies().getMaxMirrors() != null &&
                updateParam.getProtection().getContinuousCopies().getMaxMirrors() != VirtualPool.MAX_DISABLED) {
                protections.put(PROTECTION_MIRRORS, updateParam.getProtection().getContinuousCopies().getMaxMirrors());
            }
            if (updateParam.getProtection().getSnapshots() != null &&
                updateParam.getProtection().getSnapshots().getMaxSnapshots() != null &&
                updateParam.getProtection().getSnapshots().getMaxSnapshots() != VirtualPool.MAX_DISABLED) {
                protections.put(PROTECTION_SNAPSHOTS, updateParam.getProtection().getSnapshots().getMaxSnapshots());
            }
        }

        return protections;
    }
    
    @Override
    public void setNextValidator(VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(BlockVirtualPoolParam createParam, DbClient dbClient) {
        validateNoExpansionWithMirroringOrFail(createParam);
        validateJournalSizes(createParam, dbClient);
        validateMultiVolumeConsistency(createParam);
        validateRpNonHaCopyVpools(createParam, dbClient);
        validateMetroPoint(createParam, dbClient);
    }

    @Override
    protected boolean isCreateAttributeOn(BlockVirtualPoolParam createParam) {
        return (createParam.getProtection() != null);
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(VirtualPool virtualPool, BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        validateNoExpansionWithMirroringOrFail(virtualPool, updateParam);
        validateJournalSizes(virtualPool, updateParam, dbClient);
        validateRpNonHaCopyVpools(updateParam, dbClient);
        validateMultiVolumeConsistency(virtualPool);
        validateMetroPoint(virtualPool);
    }

    @Override
    protected boolean isUpdateAttributeOn(BlockVirtualPoolUpdateParam updateParam) {
        // must also check high availability due to its validation relationship with continuous copy count
        return (updateParam.getProtection() != null || updateParam.getHighAvailability() != null);
    }

    /**
     * Validation of journal sizes and other protection parameters, if they exist
     * 
     * @param createParam creation parameters
     * @param dbClient db client 
     */
    private void validateJournalSizes(BlockVirtualPoolParam createParam, DbClient dbClient) {
        Map<String, Object> protection = getProtectionParameters(createParam);
        Map<String, Object> enabledProtections = filterEntries(protection,
                and(paramEntryValueNotNull(), paramEntryValueNotNone()));
        _logger.info("Requested VirtualPool protections: {}", enabledProtections);

        if (enabledProtections.get(PROTECTION_RP) != null) {
            
            VirtualPoolProtectionRPParam rp = (VirtualPoolProtectionRPParam) enabledProtections.get(PROTECTION_RP);
            if (rp != null) {
                if (rp.getCopies() != null) {
                    validateSourcePolicy(rp.getSourcePolicy());
                    validateProtectionCopies(rp.getCopies(), dbClient);
                } else if (rp.getSourcePolicy() != null && rp.getSourcePolicy().getJournalSize() != null) {
                    throwServicePolicyNoProtectionException();  
                }
            } 
        }
    }

    /**
     * Validation of journal sizes and other protection parameters, if they exist.
     *
     * @param virtualPool
     * @param updateParam
     * @param dbClient
     */
    private void validateJournalSizes(VirtualPool virtualPool, 
            BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        Map<String, Object> protection = getProtectionUpdateParameters(updateParam);
        Map<String, Object> enabledProtections = filterEntries(protection,
                and(paramEntryValueNotNull(), paramEntryValueNotNone()));
        _logger.info("Requested VirtualPool protections: {}", enabledProtections);

        if (enabledProtections.get(PROTECTION_RP) != null) {
            
            VirtualPoolProtectionRPChanges rp = (VirtualPoolProtectionRPChanges) enabledProtections.get(PROTECTION_RP);
            if (rp != null) {
                // The virtual pool must specify RP protection or RP protection
                // must be specified in the update request.
                if (VirtualPool.vPoolSpecifiesProtection(virtualPool) || (rp.getAdd() != null && !rp.getAdd().isEmpty())) {
                    validateSourcePolicy(rp.getSourcePolicy());
                    
                    if (rp.getAdd() != null) {
                        validateProtectionCopies(rp.getAdd(), dbClient);
                    }
                } else if (rp.getSourcePolicy() != null && rp.getSourcePolicy().getJournalSize() != null) {
                    throwServicePolicyNoProtectionException();
                }
            }
        }
    }
    
    /**
     * Validates the source policy to ensure valid values are specified.
     * 
     * @param sourcePolicy The policy to validate
     */
    private void validateSourcePolicy(ProtectionSourcePolicy sourcePolicy) {
        // Validate source policy settings
        if (sourcePolicy != null) {
            if (sourcePolicy.getJournalSize() != null) {
                if(!isParsableToDouble(sourcePolicy.getJournalSize()) && !sourcePolicy.getJournalSize()
                        .matches(JOURNAL_REGEX_1) && !sourcePolicy.getJournalSize()
                        .matches(JOURNAL_REGEX_2) && !sourcePolicy.getJournalSize()
                        .equals(JOURNAL_MIN)) {
                    throw APIException.badRequests.protectionVirtualPoolJournalSizeInvalid("source", sourcePolicy.getJournalSize());
                }
            }
            
            if (sourcePolicy.getRemoteCopyMode()!=null) {
                if (VirtualPool.RPCopyMode.lookup(sourcePolicy.getRemoteCopyMode())==null) {
                    throw APIException.badRequests.protectionVirtualPoolRemoteCopyModeInvalid(sourcePolicy.getRemoteCopyMode());
                }
            }

            if (sourcePolicy.getRpoType()!=null) {
                if (VirtualPool.RPOType.lookup(sourcePolicy.getRpoType())==null) {
                    throw APIException.badRequests.protectionVirtualPoolRPOTypeInvalid(sourcePolicy.getRpoType());
                }
            }

            if (sourcePolicy.getRpoValue()!=null && sourcePolicy.getRpoType()==null) {
                throw APIException.badRequests.protectionVirtualPoolRPOTypeNotSpecified(sourcePolicy.getRpoValue());
            }

            if (sourcePolicy.getRpoValue()==null && sourcePolicy.getRpoType()!=null) {
                throw APIException.badRequests.protectionVirtualPoolRPOValueNotSpecified(sourcePolicy.getRpoType());
            }
        }
    }
    
    /**
     * Validates the RP protection copies.  The protection copy consists of
     * a virtual array, virtual pool, and copy policy - each of which are
     * validated if they are specified.
     * 
     * @param copies The protection copies
     * @param dbClient The dbclient
     */
    private void validateProtectionCopies(Set<VirtualPoolProtectionVirtualArraySettingsParam> copies, DbClient dbClient) {
        if (copies != null) {
            for (VirtualPoolProtectionVirtualArraySettingsParam settingsParam : copies) {
                
                // Validate the protection copy virtual array.  This is a required filed when adding
                // a protection copy.
                if (settingsParam.getVarray() != null && !settingsParam.getVarray().toString().isEmpty()) {
                                        ArgValidator.checkUri(settingsParam.getVarray());
                    VirtualArray neighborhood = dbClient.queryObject(VirtualArray.class, settingsParam.getVarray());
                    ArgValidator.checkEntity(neighborhood, settingsParam.getVarray(), false);
                } else {
                    throw APIException.badRequests.protectionVirtualPoolArrayMissing();
                }
                
                // Validate the protection copy virtual pool if it has been specified.
                if (settingsParam.getVpool() != null && !String.valueOf(settingsParam.getVpool()).isEmpty()) {
                                ArgValidator.checkUri(settingsParam.getVpool());
                    VirtualPool protectionCopyVPool = dbClient.queryObject(VirtualPool.class, settingsParam.getVpool());
                    ArgValidator.checkEntity(protectionCopyVPool, settingsParam.getVpool(), false);
                }
                    
                // Validate the copy policy    
                if (settingsParam.getCopyPolicy() != null && settingsParam.getCopyPolicy().getJournalSize() != null) {
                    // Make sure the journal size is of the correct format
                    
                    if(!isParsableToDouble(settingsParam.getCopyPolicy().getJournalSize()) 
                            && !settingsParam.getCopyPolicy().getJournalSize().matches(JOURNAL_REGEX_1) 
                            && !settingsParam.getCopyPolicy().getJournalSize().matches(JOURNAL_REGEX_2) 
                            && !settingsParam.getCopyPolicy().getJournalSize().equals(JOURNAL_MIN)) {
                        throw APIException.badRequests.protectionVirtualPoolJournalSizeInvalid("copy",settingsParam.getCopyPolicy().getJournalSize());
                    }
                }
            }
        }
    }
    
    /**
     * quick method to test for a string, in a method to shield the exception from the caller.
     *
     * @param i string
     * @return true if it is a number (double, in this case)
     */
    private boolean isParsableToDouble(String i)
    {
        return i.matches("\\d+\\.\\d+");
    }

    /**
     * Checks to see if the virtual pool has expandable expansion enabled
     * and mirroring enabled.  This combination is not allowed.
     * 
     * @param createParam The create parameter
     */
    private void validateNoExpansionWithMirroringOrFail(BlockVirtualPoolParam createParam) {
        Map<String, Object> protections = getProtectionParameters(createParam);
        if (protections.get(PROTECTION_MIRRORS) != null &&
                (createParam.getExpandable() == null || createParam.getExpandable())) {
            throw APIException.badRequests.virtualPoolDoesNotSupportExpandable();
        }
    }
    
    /**
     * Checks to see if the current virtual pool has expandable enabled
     * and the update request has mirroring enabled, or vice-versa.  This combination 
     * is not allowed.
     * 
     * @param virtualPool The Virtual Pool being updated
     * @param updateParam The parameter containing the updates
     */
    private void validateNoExpansionWithMirroringOrFail(VirtualPool virtualPool, 
            BlockVirtualPoolUpdateParam updateParam) {

        if (updateParam.getProtection() != null) {
            if ((updateParam.getProtection().enablesContinuousCopies() && updateParam.allowsExpansion())
                    || (updateParam.getProtection().enablesContinuousCopies()
                    && VirtualPool.vPoolAllowsExpansion(virtualPool)
                    && (updateParam.getExpandable() == null || updateParam.allowsExpansion()))) {
                throwExpandableWithMirroringException(virtualPool);
            }
        }
    }
    
    /**
     * Convenience method for throwing an exception when an attempt is
     * made to set the expandable attribute to true when mirroring is
     * enabled.
     * @param virtualPool
     */
    private void throwExpandableWithMirroringException(VirtualPool virtualPool) {
        throw APIException.badRequests.protectionVirtualPoolDoesNotSupportExpandingMirrors(virtualPool.getId());
    }
    
    /**
     * Convenience method for throwing an exception when an attempt is
     * made to set a source policy when no protection is set.
     */
    private void throwServicePolicyNoProtectionException() {
        throw APIException.badRequests.protectionNotSpecifiedInVirtualPool();
    }
    
    private Predicate<Map.Entry<String,Object>> paramEntryValueNotNull() {
        return new Predicate<Map.Entry<String, Object>>() {
            @Override
            public boolean apply(Map.Entry<String, Object> paramEntry) {
                return paramEntry.getValue() != null;
            }
        };
    }

    private Predicate<Map.Entry<String,Object>> paramEntryValueNotNone() {
        return new Predicate<Map.Entry<String, Object>>() {
            @Override
            public boolean apply(Map.Entry<String, Object> paramEntry) {
                return !paramEntry.getValue().toString().toLowerCase().equals("none");
            }
        };
    }
    
    /**
     * Validates that the multi-volume consistency flag is set if RP
     * protection is being specified.
     * @param createParam the Virtual Pool create param
     */
    private void validateMultiVolumeConsistency(BlockVirtualPoolParam createParam) {
        if (createParam.getProtection() != null 
                && createParam.getProtection().specifiesRPProtection()
                && (createParam.getMultiVolumeConsistency() == null || !createParam.getMultiVolumeConsistency())) {
            // RP protection is specified so the volume consistency flag must be set
            throw APIException.badRequests.multiVolumeConsistencyMustBeEnabledWithRP();
        }
    }
    
    /**
     * 
     * 
     * @param createParam
     * @param dbClient
     */
    private void validateRpNonHaCopyVpools(BlockVirtualPoolParam createParam, DbClient dbClient) {
        if (createParam.getProtection() != null 
                && createParam.getProtection().specifiesRPProtection() 
                && !createParam.specifiesHighAvailability()) {
            // validate the copy target vpools
            BlockVirtualPoolProtectionParam protection = createParam.getProtection();
            VirtualPoolProtectionRPParam rpParam = protection.getRecoverPoint();            
            for (VirtualPoolProtectionVirtualArraySettingsParam protectionSettings : rpParam.getCopies()) {                
                if (protectionSettings.getVpool() != null) {
                    VirtualPool protectionVirtualPool = 
                            dbClient.queryObject(VirtualPool.class, protectionSettings.getVpool());
                
                    if (VirtualPool.vPoolSpecifiesHighAvailability(protectionVirtualPool)) {
                        // throw exception - all target Vpools must not support HA if the base vpool
                        // does not specify HA.
                        throw APIException.badRequests.nonVirtualToVirtualProtectionNotSupported();
                    }
                }
            }
        } 
    }
    
    private void validateRpNonHaCopyVpools(BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        if (updateParam.getProtection() != null 
                && updateParam.getProtection().specifiesRPProtection() 
                && !updateParam.specifiesHighAvailability()) {
            // validate the copy target vpools
            BlockVirtualPoolProtectionUpdateParam protection = updateParam.getProtection();
            VirtualPoolProtectionRPChanges rpParam = protection.getRecoverPoint();
            if (rpParam != null) {
                for (VirtualPoolProtectionVirtualArraySettingsParam protectionSettings : rpParam.getAdd()) {
                    if (protectionSettings.getVpool() != null) {
                        VirtualPool protectionVirtualPool = 
                                dbClient.queryObject(VirtualPool.class, protectionSettings.getVpool());
                        
                        if (VirtualPool.vPoolSpecifiesHighAvailability(protectionVirtualPool)) {
                            // throw exception - all target Vpools must not support HA if the base vpool
                            // does not specify HA.
                            throw APIException.badRequests.nonVirtualToVirtualProtectionNotSupported();                    
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates that the multi-volume consistency flag is set if RP
     * protection is being specified.
     * @param virtualPool the Virtual Pool to validate
     */
    private void validateMultiVolumeConsistency(VirtualPool virtualPool) {
        if (VirtualPool.vPoolSpecifiesProtection(virtualPool)
                && (virtualPool.getMultivolumeConsistency() == null || !virtualPool.getMultivolumeConsistency())) {
            // RP protection is specified so the volume consistency flag must be set
            throw APIException.badRequests.multiVolumeConsistencyMustBeEnabledWithRP();
        }
    }
    
    /**
     * Validates the MetroPoint VirtualPool configuration if MetroPoint has been selected.
     * @param createParam
     * @param dbClient
     */
    private void validateMetroPoint(BlockVirtualPoolParam createParam, DbClient dbClient) {
    	if (createParam != null && createParam.getHighAvailability() != null) {
	        if (createParam.getHighAvailability().getMetroPoint() != null && createParam.getHighAvailability().getMetroPoint()) {
	        	// If MetroPoint is selected, verify that RP protection and VPlex distributed are selected.
	            if (createParam.getHighAvailability().getType() == null
	            		|| createParam.getHighAvailability().getType().equals(HIGH_AVAILABILITY_NONE)
	            		|| !createParam.getHighAvailability().getType().equals(VirtualPool.HighAvailabilityType.vplex_distributed.name())
	            		|| createParam.getProtection() == null
	            		|| !createParam.getProtection().specifiesRPProtection()) {
	            	throw APIException.badRequests.metroPointConfigurationNotSupported();
	            }
	        }
    	}
    }
    
    /**
     * Validates the MetroPoint VirtualPool configuration if MetroPoint has been selected.
     * @param createParam
     */
    private void validateMetroPoint(VirtualPool virtualPool) {
    	// If MetroPoint is selected, verify that RP protection and VPlex distributed are selected.
    	if (virtualPool.getMetroPoint() != null && virtualPool.getMetroPoint()) {
    		String highAvailability = virtualPool.getHighAvailability();

    		if (highAvailability == null 
    				|| !VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(highAvailability)
    				|| !VirtualPool.vPoolSpecifiesProtection(virtualPool)) {
    					throw APIException.badRequests.metroPointConfigurationNotSupported();
    		}
    	}
    }
}
