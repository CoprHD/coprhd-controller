/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils.vpoolvalidators;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.VirtualPoolValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings.CopyModes;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations.Mode;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class RemoteMirrorProtectionValidator extends
        VirtualPoolValidator<BlockVirtualPoolParam, BlockVirtualPoolUpdateParam> {
    private static final Logger _logger = LoggerFactory
            .getLogger(RemoteMirrorProtectionValidator.class);
    private static final String SYMMETRIX = "SYMMETRIX";

    @Override
    public void setNextValidator(@SuppressWarnings("rawtypes") final VirtualPoolValidator validator) {
        _nextValidator = validator;
    }

    private void checkSystemIsVMAX(final VirtualPool cos,
            final BlockVirtualPoolUpdateParam updateParam) {
        StringSetMap arrayInfo = cos.getArrayInfo();
        if ((null == arrayInfo || null == arrayInfo
                .get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE))
                && (null == updateParam.getSystemType() || VirtualPool.SystemType.NONE.name()
                        .equalsIgnoreCase(updateParam.getSystemType()))) {
            throw APIException.badRequests
                    .parameterOnlySupportedForVmax("SRDF Remote Protection: Check A");
        }
        if (null != updateParam.getSystemType()) {
            if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(
                    updateParam.getSystemType())) {
                throw APIException.badRequests
                        .parameterOnlySupportedForVmax("SRDF Remote Protection");
            }
        } else if (null != arrayInfo
                && null != arrayInfo.get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            StringSet deviceTypes = arrayInfo
                    .get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE);
            if (!deviceTypes.contains(VirtualPool.SystemType.vmax.toString())) {
                throw APIException.badRequests
                        .parameterOnlySupportedForVmax("SRDF Remote Protection: Check C");
            }
        }
    }

    // Only vmax arrays allowed as srdf targets
    private void validateSRDFTargetAsVMAX(URI virtualPool, DbClient dbClient) {
        if (null != virtualPool) {
            VirtualPool targetVPool = dbClient.queryObject(VirtualPool.class, virtualPool);
            List<StoragePool> targetPools = VirtualPool.getValidStoragePools(targetVPool, dbClient, true);
            for (StoragePool targetPool : targetPools) {
                if (!targetPool.getNativeGuid().toUpperCase().contains(SYMMETRIX)) {
                    throw APIException.badRequests.vmaxAllowedOnlyAsSrdfTargets();
                }
            }
        }
    }

    @Override
    protected void validateVirtualPoolUpdateAttributeValue(final VirtualPool vpool,
            final BlockVirtualPoolUpdateParam updateParam, final DbClient dbClient) {
        if (!VirtualPool.vPoolSpecifiesSRDF(vpool)) {
            // this code is not added under updateAttributeOn, as SRDF CopyModes
            // can be updated without altering Add or remove
            _logger.info("Not SRDF Specified");
            return;
        }
        updateVplexProtection(updateParam, dbClient);
        checkSystemIsVMAX(vpool, updateParam);
        Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap =
                VirtualPool.getRemoteProtectionSettings(vpool, dbClient);
        if (remoteSettingsMap != null && !remoteSettingsMap.isEmpty()) {
            for (VpoolRemoteCopyProtectionSettings remoteSettings : remoteSettingsMap.values()) {
                validateSRDFTargetAsVMAX(remoteSettings.getVirtualPool(), dbClient);
            }
        }
        Map<String, List<String>> mapping = VirtualPool
                .groupRemoteCopyModesByVPool(vpool, dbClient);
        List<String> availableCopyModes = new ArrayList<String>();
        _logger.info("Multi Volume Consistency {} :", vpool.getMultivolumeConsistency());
        if (mapping != null) {
            for (Collection<String> copyModes : mapping.values()) {
                availableCopyModes.addAll(copyModes);
            }
        }
        int frequency = Collections.frequency(availableCopyModes, Mode.ASYNCHRONOUS.toString());
        if (frequency > 1) {
            throw APIException.badRequests.unsupportedConfigurationWithMultipleAsyncModes();
        }

    }

    @Override
    protected boolean isCreateAttributeOn(final BlockVirtualPoolParam createParam) {
        _logger.info("Entered Remote Protection Validator");
        if (null == createParam.getProtection()
                || null == createParam.getProtection().getRemoteCopies()
                || null == createParam.getProtection().getRemoteCopies().getRemoteCopySettings()
                || createParam.getProtection().getRemoteCopies().getRemoteCopySettings().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * check VMAX system in VPool
     * 
     * @param createParam
     */
    private void checkSystemTypeIsVMAX(final BlockVirtualPoolParam createParam) {
        if (null == createParam.getSystemType()
                || createParam.getSystemType().equalsIgnoreCase(NONE)) {
            throw APIException.badRequests.parameterOnlySupportedForVmax("SRDF Remote Protection");
        }
        if (!VirtualPool.SystemType.vmax.toString().equalsIgnoreCase(createParam.getSystemType())) {
            throw APIException.badRequests.parameterOnlySupportedForVmax("SRDF Remote Protection");
        }
    }

    /**
     * check RP or VPlex enabled in VPool
     * 
     * @param createParam
     */
    private void checkForVPlexProtectionEnabled(
            final BlockVirtualPoolParam createParam, DbClient dbClient) {
        // RP not allowed if SRDF Protection is specified in VPOOL
        if (null != createParam.getProtection()
                && null != createParam.getProtection().getRecoverPoint()
                && null != createParam.getProtection().getRecoverPoint().getCopies()
                && !createParam.getProtection().getRecoverPoint().getCopies().isEmpty()) {
            throw APIException.badRequests.parameterRPNotSupportedWithSRDF();
        }
        // VPLEX checks with SRDF
        if (createParam.specifiesHighAvailability()) {
            // SRDF Copy Mode Active not supported with Vplex
            if (createParam.hasRemoteCopyProtection()) {
                for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : createParam
                        .getProtection().getRemoteCopies().getRemoteCopySettings()) {
                    if (null != remoteSettings.getRemoteCopyMode() 
                            && remoteSettings.getRemoteCopyMode().equals(Mode.ACTIVE.name())) {
                        throw APIException.badRequests.vplexNotSupportedWithSRDFActive(); 
                    }
                    if (null != remoteSettings.getVpool()) {
                        URI uri = remoteSettings.getVpool();
                        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, uri);
                        if (vpool != null && VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(vpool)) {
                            throw APIException.badRequests.vplexDistributedNotSupportedOnSRDFTarget();
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Check vplex / srdf compatibility on update.
     * 
     * @param updateParam -- BlockVirtualPoolUpdateParam
     * @param dbClient -- database handle
     */
    private void updateVplexProtection(
            final BlockVirtualPoolUpdateParam updateParam, DbClient dbClient) {
        // VPLEX checks with SRDF
        if (updateParam.specifiesHighAvailability()) {
            // SRDF Copy Mode Active not supported with Vplex
            if (updateParam.getProtection() != null 
                    && updateParam.getProtection().getRemoteCopies()!= null
                    && updateParam.getProtection().getRemoteCopies().getAdd() != null) {
                for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : 
                    updateParam.getProtection().getRemoteCopies().getAdd()) {
                    if (null != remoteSettings.getRemoteCopyMode() 
                            && remoteSettings.getRemoteCopyMode().equals(Mode.ACTIVE.name())) {
                        throw APIException.badRequests.vplexNotSupportedWithSRDFActive(); 
                    }
                    if (null != remoteSettings.getVpool()) {
                        URI uri = remoteSettings.getVpool();
                        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, uri);
                        if (vpool != null && VirtualPool.vPoolSpecifiesHighAvailabilityDistributed(vpool)) {
                            throw APIException.badRequests.vplexDistributedNotSupportedOnSRDFTarget();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void validateVirtualPoolCreateAttributeValue(final BlockVirtualPoolParam createParam,
            final DbClient dbClient) {
        _logger.info("Entered Remote Protection creation validator");
        // RP or VPlex enabled
        checkForVPlexProtectionEnabled(createParam, dbClient);
        // remote Mirroring is applicable only for VMAX system type
        checkSystemTypeIsVMAX(createParam);
        // Validate whether remote Copy Settings are valid for the source VPool
        // If vArrays are not given, then no validation needed, as by default all the vArrays are
        // applicable for this VPool.
        if (null == createParam.getVarrays()) {
            return;
        }
        if (createParam.hasRemoteCopyProtection()) {
            List<String> availableCopyModes = new ArrayList();
            for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : createParam
                    .getProtection().getRemoteCopies().getRemoteCopySettings()) {
                VirtualPool vPool = null;
                if (remoteSettings.getVpool() != null) {
                    validateSRDFTargetAsVMAX(remoteSettings.getVpool(), dbClient);
                    vPool = dbClient.queryObject(VirtualPool.class, remoteSettings.getVpool());
                }
                checkVArrayIsValidForVPool(remoteSettings, vPool, createParam);
                if (null == remoteSettings.getRemoteCopyMode()) {
                    availableCopyModes.add(Mode.ASYNCHRONOUS.toString());
                } else if (CopyModes.lookup(remoteSettings.getRemoteCopyMode())) {
                    availableCopyModes.add(remoteSettings.getRemoteCopyMode());
                } else {
                    throw APIException.badRequests.invalidCopyMode(remoteSettings.getRemoteCopyMode());
                }
            }

            int frequency = Collections.frequency(availableCopyModes, Mode.ASYNCHRONOUS.toString());
            if (frequency > 1) {
                throw APIException.badRequests.unsupportedConfigurationWithMultipleAsyncModes();
            }
        }
    }

    /**
     * For each remote setting, check given VArray is part of attached VArrays of given remote VPool
     * 
     * @param remoteSettings
     * @param dbClient
     * @param createParam
     */
    private void checkVArrayIsValidForVPool(
            final VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings,
            final VirtualPool vPool, final BlockVirtualPoolParam createParam) {
        if (null != vPool) {
            if (null != vPool.getVirtualArrays()
                    && !vPool.getVirtualArrays().contains(remoteSettings.getVarray().toString())) {
                throw APIException.badRequests.vArrayUnSupportedForGivenVPool(vPool.getId(),
                        remoteSettings.getVarray());
            }
        } else {
            Set<String> vArrays = createParam.getVarrays();
            if (null != vArrays && !vArrays.contains(remoteSettings.getVarray().toString())) {
                throw APIException.badRequests.vArrayUnSupportedForGivenVPool(remoteSettings.getVarray(),
                        remoteSettings.getVarray());
            }
        }
    }

    @Override
    protected boolean isUpdateAttributeOn(final BlockVirtualPoolUpdateParam updateParam) {
        // Add and Remove Sizes will be 0, if only copyMode is changed.
        if (null == updateParam.getProtection()
                || null == updateParam.getProtection().getRemoteCopies()
                || null == updateParam.getProtection().getRemoteCopies().getAdd()
                || null == updateParam.getProtection().getRemoteCopies().getRemove()) {
            _logger.debug("Remote Mirror Protection called");
            return false;
        }
        return true;
    }
}
