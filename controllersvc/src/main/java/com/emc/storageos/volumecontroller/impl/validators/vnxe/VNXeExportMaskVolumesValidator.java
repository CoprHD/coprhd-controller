/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.vnxe;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.models.VNXeHost;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.google.common.base.Joiner;

/**
 * Validator class for removing initiators from export mask and deleting export mask
 */
public class VNXeExportMaskVolumesValidator extends AbstractVNXeValidator {

    private static final Logger log = LoggerFactory.getLogger(VNXeExportMaskVolumesValidator.class);
    private static final String unknownLUNRemediation = "Unmap the LUN/LUN Snap from the host on array";

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
                    Set<String> lunIds = getAllLUNsForHost(dbClient, exportMask);
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

        setRemediation(unknownLUNRemediation);
        checkForErrors();
        log.info("Completed volume validation of VNXe ExportMask: " + getId());

        return true;
    }

    /**
     * Get all LUNs on the array that mapped to a host identified by initiators in the mask
     *
     * @param dbClient
     * @param exportMask
     * @return LUNs mapped to the host
     */
    private Set<String> getAllLUNsForHost(DbClient dbClient, ExportMask exportMask) {
        Set<String> lunIds = new HashSet<>();
        URI storageUri = exportMask.getStorageDevice();
        if (NullColumnValueGetter.isNullURI(storageUri)) {
            return lunIds;
        }

        URI hostUri = null;
        for (String init : exportMask.getInitiators()) {
            Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(init));
            if (initiator != null && !initiator.getInactive()) {
                hostUri = initiator.getHost();
                if (!NullColumnValueGetter.isNullURI(hostUri)) {
                    break;
                }
            }
        }

        // get initiators from host
        Map<URI, ExportMask> exportMasks = new HashMap<>();
        if (!NullColumnValueGetter.isNullURI(hostUri)) {
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(hostUri, Initiator.class, "host"), list);
            Iterator<URI> uriIter = list.iterator();
            while (uriIter.hasNext()) {
                URI initiatorId = uriIter.next();
                URIQueryResultList egUris = new URIQueryResultList();
                dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getExportGroupInitiatorConstraint(initiatorId.toString()), egUris);
                ExportGroup exportGroup = null;
                for (URI egUri : egUris) {
                    exportGroup = dbClient.queryObject(ExportGroup.class, egUri);
                    if (exportGroup == null || exportGroup.getInactive() || exportGroup.getExportMasks() == null) {
                        continue;
                    }
                    List<ExportMask> masks = ExportMaskUtils.getExportMasks(dbClient, exportGroup);
                    for (ExportMask mask : masks) {
                        if (mask != null &&
                                !mask.getInactive() &&
                                mask.hasInitiator(initiatorId.toString()) &&
                                mask.getVolumes() != null &&
                                storageUri.equals(mask.getStorageDevice())) {
                            exportMasks.put(mask.getId(), mask);
                        }
                    }
                }
            }
        }

        for (ExportMask mask : exportMasks.values()) {
            StringMap volumeMap = mask.getVolumes();
            if (volumeMap != null && !volumeMap.isEmpty()) {
                for (String strUri : mask.getVolumes().keySet()) {
                    BlockObject bo = BlockObject.fetch(dbClient, URI.create(strUri));
                    if (bo != null && !bo.getInactive()) {
                        lunIds.add(bo.getNativeId());
                    }
                }
            }
        }

        return lunIds;
    }
}
