/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
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
        log.info("Initiating volume validation of VNXe ExportMask: " + getId());
        DbClient dbClient = getDbClient();
        VNXeApiClient apiClient = getApiClient();
        ExportMask exportMask = getExportMask();

        try {
            String vnxeHostId = getVNXeHostFromInitiators();
            if (vnxeHostId != null) {
                VNXeHost vnxeHost = apiClient.getHostById(vnxeHostId);
                if (vnxeHost != null) {
                    Set<String> lunIds = getAllLUNsForHost(dbClient, getStorage(), exportMask);
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
        log.info("Completed volume validation of VNXe ExportMask: " + getId());

        return true;
    }

    /**
     * Get all LUNs on the array that mapped to a host identified by initiators in the mask
     *
     * @param dbClient
     * @param storage
     * @param exportMask
     * @return LUNs mapped to the host
     */
    private Set<String> getAllLUNsForHost(DbClient dbClient, StorageSystem storage, ExportMask exportMask) {
        URI hostURI = null;
        for (String init : exportMask.getInitiators()) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(init));
            if (initiator != null && !initiator.getInactive()) {
                hostURI = initiator.getHost();
                if (!NullColumnValueGetter.isNullURI(hostURI)) {
                    break;
                }
            }
        }

        // get initiators from host
        Set<ExportMask> exportMasks = new HashSet<>();
        if (!NullColumnValueGetter.isNullURI(hostURI)) {
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(hostURI, Initiator.class, "host"), list);
            Iterator<URI> uriIter = list.iterator();
            while (uriIter.hasNext()) {
                Initiator initiator = dbClient.queryObject(Initiator.class, uriIter.next());
                exportMasks.addAll(ExportUtils.getInitiatorExportMasks(initiator, dbClient));
            }
        }

        Set<String> lunIds = new HashSet<>();
        for (ExportMask mask : exportMasks) {
            for (String strUri : mask.getVolumes().keySet()) {
                BlockObject bo = BlockObject.fetch(dbClient, URI.create(strUri));
                if (bo != null && !bo.getInactive() && storage.getId().equals(bo.getStorageController())) {
                    lunIds.add(bo.getNativeId());
                }
            }
        }

        return lunIds;
    }
}
