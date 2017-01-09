/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeUtils;
import com.google.common.base.Joiner;

/**
 * Validator class for removing initiators from export mask and deleting export mask
 */
public class VNXeExportMaskVolumesValidator extends AbstractVNXeValidator {

    private static final Logger log = LoggerFactory.getLogger(VNXeExportMaskVolumesValidator.class);

    private final Collection<? extends BlockObject> blockObjects;

    VNXeExportMaskVolumesValidator(StorageSystem storage, ExportMask exportMask,
            Collection<? extends BlockObject> blockObjects) {
        super(storage, exportMask);
        this.blockObjects = blockObjects;
    }

    @Override
    public boolean validate() throws Exception {
        log.info("Initiating volume validation of VNXe ExportMask: " + id);
        DbClient dbClient = getDbClient();
        apiClient = getApiClient();

        try {
            String vnxeHostId = getVNXeHostFromInitiators();
            if (vnxeHostId != null) {
                VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
                if (vnxeHost != null) {
                    Set<String> lunIds = VNXeUtils.getAllLUNsForHost(dbClient, storage, exportMask);
                    Set<String> lunIdsOnArray = apiClient.getHostLUNIds(vnxeHostId);
                    lunIdsOnArray.removeAll(lunIds);
                    if (!lunIdsOnArray.isEmpty()) {
                        String unknownLUNs = Joiner.on(',').join(lunIdsOnArray);
                        log.info("Unknown LUN/LUN Snap {}", unknownLUNs);
                        getLogger().logDiff(exportMask.getId().toString(), "volumes", ValidatorLogger.NO_MATCHING_ENTRY, unknownLUNs);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating ExportMask volumes: " + ex.getMessage(), ex);
            throw DeviceControllerException.exceptions.unexpectedCondition(
                    "Unexpected exception validating ExportMask volumes: " + ex.getMessage());
        }

        checkForErrors();
        log.info("Completed volume validation of VNXe ExportMask: " + id);

        return true;
    }
}
