/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.vnxe.models.HostLun;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeUtils;

/**
 * Validator class for removing initiators from export mask and deleting export mask
 */
public class VNXeExportMaskVolumesValidator extends AbstractVNXeValidator {

    private static final Logger log = LoggerFactory.getLogger(VNXeExportMaskVolumesValidator.class);

    private final Collection<? extends BlockObject> blockObjects;
    private boolean isRemoveAllInitiators = false;

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
        boolean failValidation = false;
        String lunId = null;

        try {
            String vnxeHostId = getVNXeHostFromInitiators();
            VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
            if (vnxeHost != null) {
                List<VNXeBase> hostLunIds = vnxeHost.getHostLUNs();
                if (hostLunIds != null && !hostLunIds.isEmpty()) {
                    if (!isRemoveAllInitiators) {
                        failValidation = true;
                    } else {
                        Set<String> lunIds = new HashSet<>();
                        VNXeUtils.getAllLUNsForHost(dbClient, storage, exportMask).values().forEach(lunIds::addAll);
                        for (VNXeBase hostLunId : hostLunIds) {
                            HostLun hostLun = apiClient.getHostLun(hostLunId.getId());
                            int type = hostLun.getType();
                            VNXeBase vnxelunId = null;
                            // get lun from from hostLun
                            if (HostLun.HostLUNTypeEnum.LUN_SNAP.getValue() == type) {
                                // snap
                                vnxelunId = hostLun.getSnap();
                            } else {
                                vnxelunId = hostLun.getLun();
                            }

                            if (vnxelunId != null) {
                                lunId = vnxelunId.getId().toString();
                                if (!lunIds.contains(lunId)) {
                                    log.info("LUN/LUN Snap {} is unknown", lunId);
                                    failValidation = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating ExportMask volumes: " + ex.getMessage(), ex);
            throw DeviceControllerException.exceptions.unexpectedCondition(
                    "Unexpected exception validating ExportMask volumes: " + ex.getMessage());
        }

        if (failValidation) {
            String msg = null;
            if (lunId != null) {
                msg = "Cannot delete initiator due to unknown LUN " + lunId;
            } else {
                msg = "Cannot delete initiator because there are storage resources associated with the host that the initiator is registered to";
            }

            logger.getMsgs().append(msg);
            logger.generateException(ValidatorLogger.VOLUME_TYPE);
        }

        log.info("Completed volume validation of VNXe ExportMask: " + id);

        return true;
    }

    public boolean isRemoveAllInitiators() {
        return isRemoveAllInitiators;
    }

    public void setRemoveAllInitiators(boolean isRemoveAllInitiators) {
        this.isRemoveAllInitiators = isRemoveAllInitiators;
    }
}
