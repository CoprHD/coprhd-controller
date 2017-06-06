/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vplex;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;

public class VplexExportMaskValidator extends AbstractVplexValidator implements Validator {
    private Logger log = LoggerFactory.getLogger(VplexExportMaskValidator.class);
    private StorageSystem vplex;
    private ExportMask mask;
    private Collection<URI> volumesToValidate = null;
    private Collection<Initiator> initiatorsToValidate = null;
    private VPlexApiClient client = null;
    private String id = null; // identifying string for ExportMask

    public VplexExportMaskValidator(DbClient dbClient, ValidatorConfig config, ValidatorLogger logger, StorageSystem vplex,
                                    ExportMask mask) {
        super(dbClient, config, logger);
        this.vplex = vplex;
        this.mask = mask;
        id = String.format("%s (%s)(%s)", mask.getMaskName(), mask.getNativeId(), mask.getId().toString());
    }

    @Override
    public boolean validate() throws Exception {
        if (mask.getInactive()) {
            log.info("Export mask inactive: " + id);
            // no such export mask, ignore, but don't indicate pass
            return false;
        }

        log.info("Initiating validation of Vplex ExportMask: " + id);

        VPlexStorageViewInfo storageView = null;
        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), vplex, getDbClient());
            // This will throw an exception if the cluster name cannot be determined
            String vplexClusterName = VPlexUtil.getVplexClusterName(mask, vplex.getId(), client, getDbClient());
            // This will throw an exception if the StorageView cannot be found
            storageView = client.getStorageView(vplexClusterName, mask.getMaskName());
            if (volumesToValidate != null) {
                validateNoAdditionalVolumes(storageView);
            }
            if (initiatorsToValidate != null) {
                validateNoAdditionalInitiators(storageView);
            }
        } catch (Exception ex) {
            if (storageView == null) {
                // Not finding the storage view is not an error, so delete can be idempotent
                log.warn(String.format("Storage View %s cannot be located on VPLEX", id));
                return false;
            }
            log.error("Unexpected exception validating ExportMask: " + ex.getMessage(), ex);
            if (getValidatorConfig().isValidationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(
                        "Unexpected exception validating ExportMask: " + ex.getMessage());
            }
        }

        if (getValidatorLogger().hasErrors()) {
            return false;
        }

        log.info("Vplex ExportMask validation complete: " + id);

        return true;
    }

    /**
     * Validates the hardware has no additional volumes than were passed in the volumesToValidate list.
     * Uses the virtualVolumeWWNMap to retrieve the volume WWNs and match against hardware.
     * 
     * @param storageView
     *            -- VPlexStorageViewInfo
     */
    private void validateNoAdditionalVolumes(VPlexStorageViewInfo storageView) {
        List<? extends BlockObject> bos = BlockObject.fetchAll(getDbClient(), volumesToValidate);
        Set<String> storageViewWwns = storageView.getWwnToHluMap().keySet();
        for (BlockObject bo : bos) {
            if (bo == null || bo.getInactive()) {
                continue;
            }
            String boWwn = bo.getWWN();
            if (NullColumnValueGetter.isNotNullValue(boWwn)) {
                if (storageViewWwns.contains(boWwn)) {
                    // Remove matched WWNs
                    storageViewWwns.remove(boWwn);
                } else {
                    log.info(String.format("Database volume/snap %s (%s - %s) is not in StorageView [%s]", 
                            bo.getId(), bo.getNativeGuid(), bo.getWWN(), storageView.getName()));
                }
            }
        }
        // Any remaining WWNs in storageViewWwns had no matching volume, and therefore are error
        for (String wwn : storageViewWwns) {
            String volumeId = String.format("%s (%s)", wwn, storageView.getVolumeNameForWWN(wwn));
            getValidatorLogger().logDiff(id, "virtual-volume/snap WWN", ValidatorLogger.NO_MATCHING_ENTRY, volumeId);
        }
    }

    /**
     * Validate the hardware has no additional initiators than were passed in the initiatorsToValidate list.
     * Uses the Initiator PWWNs in the Storage View to match against initiator port WWN.
     * 
     * @param storageView
     *            -- VPlexStorageViewInfo
     */
    private void validateNoAdditionalInitiators(VPlexStorageViewInfo storageView) {
        Set<String> storageViewPwwns = new HashSet<String>(storageView.getInitiatorPwwns());

        // Don't validate against RP masks
        if (ExportMaskUtils.isBackendExportMask(getDbClient(), mask)) {
            log.info("validation against RP masks is disabled.");
            return;
        }

        for (Initiator initiator : initiatorsToValidate) {
            if (initiator == null || initiator.getInactive()) {
                continue;
            }
            String initiatorPwwn = Initiator.normalizePort(initiator.getInitiatorPort());
            if (storageViewPwwns.contains(initiatorPwwn)) {
                storageViewPwwns.remove(initiatorPwwn);
            } else {
                log.info(String.format("Database initiator %s (%s) not in StorageView", initiator.getId(), initiatorPwwn));
            }
        }
        // Any remaining WWNs in storageViewPwwns had no matching initiator, and therefore are error
        for (String wwn : storageViewPwwns) {
            getValidatorLogger().logDiff(id, "initiator port WWN", ValidatorLogger.NO_MATCHING_ENTRY, wwn);
        }
    }

    public StorageSystem getVplex() {
        return vplex;
    }

    public void setVplex(StorageSystem vplex) {
        this.vplex = vplex;
    }

    public ExportMask getMask() {
        return mask;
    }

    public void setMask(ExportMask mask) {
        this.mask = mask;
    }

    public Collection<URI> getVolumesToValidate() {
        return volumesToValidate;
    }

    public void setVolumesToValidate(Collection<URI> volumesToValidate) {
        this.volumesToValidate = volumesToValidate;
    }

    public Collection<Initiator> getInitiatorsToValidate() {
        return initiatorsToValidate;
    }

    public void setInitiatorsToValidate(Collection<Initiator> initiatorsToValidate) {
        this.initiatorsToValidate = initiatorsToValidate;
    }

}
