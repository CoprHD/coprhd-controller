/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeHostInitiator;
import com.emc.storageos.vnxe.models.VNXeHostInitiator.HostInitiatorTypeEnum;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;

/**
 * Validator class for removing volumes from export mask and deleting export mask
 */
public class VNXeExportMaskInitiatorsValidator extends AbstractVNXeValidator {
    private static final Logger log = LoggerFactory.getLogger(VNXeExportMaskInitiatorsValidator.class);
    private static final String misMatchInitiatorRemediation = "Remove the initiator from the host on array";
    private static final String unknownInitiatorRemediation = "Remove the initiator from the host on array, or add the initiator to the host in CoprHD";

    public VNXeExportMaskInitiatorsValidator(StorageSystem storage, ExportMask exportMask) {
        super(storage, exportMask);
    }

    /**
     * Get list of initiators associated with the host.
     * If there are unknown initiators on the host, fail the validation
     */
    @Override
    public boolean validate() throws Exception {
        log.info("Initiating initiator validation of VNXe ExportMask: " + getId());
        DbClient dbClient = getDbClient();
        VNXeApiClient apiClient = getApiClient();
        ExportMask exportMask = getExportMask();

        try {
            // Don't validate against backing masks or RP
            if (ExportMaskUtils.isBackendExportMask(getDbClient(), exportMask)) {
                log.info("validation against backing mask for VPLEX or RP is disabled.");
                return true;
            }

            // all initiators of VNXe host should be on single ViPR host, or unknown to ViPR
            String vnxeHostId = getHostId();
            if (vnxeHostId == null) {
                return true;
            }

            List<VNXeHostInitiator> initiatorList = apiClient.getInitiatorsByHostId(vnxeHostId);
            URI hostId = null;
            if (initiatorList != null) {
                for (VNXeHostInitiator initiator : initiatorList) {
                    String portWWN = null;
                    if (HostInitiatorTypeEnum.INITIATOR_TYPE_ISCSI.equals(initiator.getType())) {
                        portWWN = initiator.getInitiatorId();
                    } else {
                        portWWN = initiator.getPortWWN();
                    }

                    Initiator viprInitiator = NetworkUtil.findInitiatorInDB(portWWN, dbClient);
                    if (viprInitiator != null) {
                        if (NullColumnValueGetter.isNullURI(hostId)) {
                            hostId = viprInitiator.getHost();
                        } else if (!hostId.equals(viprInitiator.getHost())) {
                            log.info("Initiator {} belongs to different host", portWWN);
                            setRemediation(misMatchInitiatorRemediation);
                            getLogger().logDiff(exportMask.getId().toString(), "initiators", ValidatorLogger.NO_MATCHING_ENTRY,
                                    portWWN);
                            break;
                        }
                    } else {
                        // initiator not found in ViPR
                        log.info("Unknown initiator found: {}", portWWN);
                        setRemediation(unknownInitiatorRemediation);
                        getLogger().logDiff(exportMask.getId().toString(), "initiators", ValidatorLogger.NO_MATCHING_ENTRY,
                                portWWN);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating ExportMask initiators: " + ex.getMessage(), ex);
            if (getConfig().isValidationEnabled()) {
                throw DeviceControllerException.exceptions.unexpectedCondition(
                        "Unexpected exception validating ExportMask initiators: " + ex.getMessage());
            }
        }

        checkForErrors();
        log.info("Completed initiator validation of VNXe ExportMask: " + getId());

        return true;
    }
}