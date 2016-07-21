/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vplex;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.validators.AbstractValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;
import com.google.common.collect.Lists;

/**
 * Factory for creating Vplex-specific validator instances.
 */
public class VplexSystemValidatorFactory extends AbstractValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(VplexSystemValidatorFactory.class);

    private final List<Volume> remediatedVolumes = Lists.newArrayList();
    private ValidatorLogger logger;

    /**
     * Verify storage system connectivity
     * 
     * @param storageSystem
     */
    private void checkVplexConnectivity(StorageSystem storageSystem) {
        try {
            VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), storageSystem, getDbClient());
        } catch (URISyntaxException ex) {
            log.error("Couldn't connect to VPLEX: " + storageSystem.getLabel(), ex);
        } catch (Exception ex) {
            log.error("Couldn't connect to VPLEX: " + storageSystem.getLabel(), ex);
            throw ex;
        }
    }

    @Override
    public Validator exportMaskDelete(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList,
            Collection<Initiator> initiatorList) {
        checkVplexConnectivity(storage);
        logger = new ValidatorLogger(log);
        VplexExportMaskValidator validator = new VplexExportMaskValidator(getDbClient(), logger, storage, exportMask);
        validator.setVolumesToValidate(volumeURIList);
        validator.setInitiatorsToValidate(initiatorList);
        return validator;
    }

    @Override
    public Validator removeVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        checkVplexConnectivity(storage);
        logger = new ValidatorLogger(log);
        ExportMask exportMask = getDbClient().queryObject(ExportMask.class, exportMaskURI);
        VplexExportMaskValidator validator = new VplexExportMaskValidator(getDbClient(), logger, storage, exportMask);
        validator.setInitiatorsToValidate(initiators);
        return validator;
    }

    @Override
    public Validator removeInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        checkVplexConnectivity(storage);
        logger = new ValidatorLogger(log);
        VplexExportMaskValidator validator = new VplexExportMaskValidator(getDbClient(), logger, storage, exportMask);
        validator.setVolumesToValidate(volumeURIList);
        return validator;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        return null;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate,
            ValCk[] checks) {
        checkVplexConnectivity(storageSystem);
        try {
            logger = new ValidatorLogger(log);
            VplexVolumeValidator vplexVolumeValidator = new VplexVolumeValidator(getDbClient(), logger);
            vplexVolumeValidator.validateVolumes(storageSystem, volumes, delete, remediate, checks);
            if (logger.hasErrors()) {
                throw DeviceControllerException.exceptions.validationError("vplex volume(s)",
                        logger.getMsgs().toString(), ValidatorLogger.INVENTORY_DELETE_VOLUME);
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating VPLEX: " + storageSystem.getId(), ex);
            throw ex;
        }
        return remediatedVolumes;
    }

    @Override
    public Validator expandVolumes(StorageSystem storageSystem, Volume volume) {
        return null;
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        return null;
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        // TODO Auto-generated method stub
        return null;
    }
}
