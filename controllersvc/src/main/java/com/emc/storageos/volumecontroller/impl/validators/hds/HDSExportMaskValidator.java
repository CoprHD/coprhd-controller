/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.hds;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiExportManager;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExceptionContext;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

public class HDSExportMaskValidator extends AbstractHDSValidator {

    private static final Logger log = LoggerFactory.getLogger(HDSExportMaskValidator.class);

    public HDSExportMaskValidator(StorageSystem storage, ExportMask exportMask, HDSSystemValidatorFactory factory,
            ExceptionContext exceptionContext) {
        super(log, storage, exportMask, factory, exceptionContext);
    }

    @Override
    public boolean validate() throws Exception {
        log.info("Initiating validation of HDS ExportMask: {}", id);

        try {
            ExportMask exportMask = getExportMask();
            StorageSystem system = getStorage();
            if (exportMask != null && !CollectionUtils.isEmpty(exportMask.getDeviceDataMap())) {
                Set<String> hsdList = exportMask.getDeviceDataMap().keySet();
                HDSApiClient client = getClientFactory().getClient(HDSUtils.getHDSServerManagementServerInfo(system),
                        system.getSmisUserName(), system.getSmisPassword());
                HDSApiExportManager exportManager = client.getHDSApiExportManager();

                String maskName = null;
                String systemObjectID = HDSUtils.getSystemObjectID(system);

                Set<String> volumesInExportMask = new HashSet<>();
                Set<String> initiatorsInExportMask = new HashSet<>();

                if (!CollectionUtils.isEmpty(exportMask.getUserAddedVolumes())) {
                    volumesInExportMask.addAll(exportMask.getUserAddedVolumes().keySet());
                }
                if (!CollectionUtils.isEmpty(exportMask.getUserAddedInitiators())) {
                    initiatorsInExportMask.addAll(exportMask.getUserAddedInitiators().keySet());
                }

                log.info("Volumes {} in Export Mask {}", volumesInExportMask, exportMask.forDisplay());
                log.info("Initiators {} in Export Mask {}", initiatorsInExportMask, exportMask.forDisplay());

                for (String hsdObjectIdFromDb : hsdList) {
                    Set<String> discoveredInitiators = new HashSet<>();
                    Set<String> discoveredVolumes = new HashSet<>();
                    HostStorageDomain hsd = exportManager.getHostStorageDomain(
                            systemObjectID, hsdObjectIdFromDb);
                    if (null == hsd) {
                        continue;
                    }
                    maskName = (null == hsd.getName()) ? hsd.getNickname() : hsd.getName();

                    // Get volumes and initiators from storage system
                    discoveredVolumes.addAll(HDSUtils.getVolumesFromHSD(hsd, system).keySet());
                    discoveredInitiators.addAll(HDSUtils.getInitiatorsFromHSD(hsd));

                    log.info("Volumes {} discovered from array for the HSD {}", discoveredVolumes, maskName);
                    log.info("Initiators {} discovered from array for the HSD {}", discoveredInitiators, maskName);
                    Set<String> additionalVolumesOnArray = Sets.difference(discoveredVolumes, volumesInExportMask);
                    Set<String> additionalInitiatorsOnArray = Sets.difference(discoveredInitiators, initiatorsInExportMask);
                    if (!CollectionUtils.isEmpty(additionalVolumesOnArray)) {
                        getValidatorLogger().logDiff(String.format("%s - %s", id, maskName), "volumes", ValidatorLogger.NO_MATCHING_ENTRY,
                                Joiner.on("\t").join(additionalVolumesOnArray));
                    }
                    if (!CollectionUtils.isEmpty(additionalInitiatorsOnArray)) {
                        getValidatorLogger().logDiff(String.format("%s - %s", id, maskName), "initiators",
                                ValidatorLogger.NO_MATCHING_ENTRY,
                                Joiner.on("\t").join(additionalInitiatorsOnArray));
                    }

                }
                checkForErrors();
            }

        } catch (Exception ex) {
            log.error("Unexpected exception validating ExportMask initiators: " + ex.getMessage(), ex);
            if (getValidatorConfig().isValidationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(
                        "Unexpected exception validating ExportMask initiators: " + ex.getMessage());
            }
        }
        return true;
    }

}
