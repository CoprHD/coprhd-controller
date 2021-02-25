/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.xtremio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.impl.xtremio.prov.utils.XtremIOProvUtils;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOVolume;

/**
 * Validator class for XtremIO export mask delete operations.
 */
public class XtremIOExportMaskVolumesValidator extends AbstractXtremIOValidator {

    private static final Logger log = LoggerFactory.getLogger(XtremIOExportMaskVolumesValidator.class);

    private final Collection<? extends BlockObject> blockObjects;
    private Collection<String> igNames;
    private static final String XTREMIO_BULK_API_CALL = "xtremio_bulk_api";

    XtremIOExportMaskVolumesValidator(StorageSystem storage, ExportMask exportMask,
            Collection<? extends BlockObject> blockObjects) {
        super(storage, exportMask);
        this.blockObjects = blockObjects;
    }

    public void setIgNames(Collection<String> igNames) {
        this.igNames = igNames;
    }

    @Override
    public boolean validate() throws Exception {
        log.info("Initiating volume validation of XtremIO ExportMask: " + id);
        try {
            XtremIOClient client = XtremIOProvUtils.getXtremIOClient(getDbClient(), storage, getClientFactory());
            String xioClusterName = client.getClusterDetails(storage.getSerialNumber()).getName();
            Set<String> knownVolumes = new HashSet<>();
            Set<String> igVols = new HashSet<>();
            // get the volumes in the IGs and validate against passed impacted block objects
            for (BlockObject maskVolume : blockObjects) {
                knownVolumes.add(maskVolume.getDeviceLabel());
            }

            List<XtremIOVolume> igVolumes = new ArrayList<>();

            boolean bulkApiCallFlag = Boolean.valueOf(
                    ControllerUtils.getPropertyValueFromCoordinator(getCoordinatorClient(), XTREMIO_BULK_API_CALL));
            Map<String, List<XtremIOVolume>> igNameToVolumesMap = new HashMap<>();

            Set<String> igNameSet = new HashSet<>(igNames);
            if (client.isVersion2() && XtremIOProvUtils.isBulkAPISupported(storage.getFirmwareVersion(),client)
                    && bulkApiCallFlag) {
                if (!igNameSet.isEmpty())
                    igNameToVolumesMap = XtremIOProvUtils.getLunMapAndVolumes(igNameSet, xioClusterName, client,
                            igNameToVolumesMap);
                for (Map.Entry<String, List<XtremIOVolume>> entry : igNameToVolumesMap.entrySet()) {
                    igVolumes.addAll(entry.getValue());

                }

            } else {
                for (String igName : igNames) {
                    igVolumes.addAll(XtremIOProvUtils.getInitiatorGroupVolumes(igName, xioClusterName, client));
                }
            }

            for (XtremIOVolume igVolume : igVolumes) {
                igVols.add(igVolume.getVolInfo().get(1));
            }

            log.info("ViPR known volumes present in IG: {}, volumes in IG: {}", knownVolumes, igVols);
            igVols.removeAll(knownVolumes);
            for (String igVol : igVols) {
                getLogger().logDiff(id, "volumes", ValidatorLogger.NO_MATCHING_ENTRY, igVol);
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating ExportMask volumes: " + ex.getMessage(), ex);
            if (getConfig().isValidationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(
                        "Unexpected exception validating ExportMask volumes: " + ex.getMessage());
            }
        }

        checkForErrors();

        log.info("Completed volume validation of XtremIO ExportMask: " + id);

        return true;
    }
}
