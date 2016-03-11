/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.util.ExportUtils;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class ExportMaskAddInitiatorCompleter extends ExportMaskInitiatorCompleter {
    private static final org.slf4j.Logger _log = LoggerFactory
            .getLogger(ExportMaskAddInitiatorCompleter.class);
    private List<URI> _initiatorURIs;
    private List<URI> _targetURIs;

    public ExportMaskAddInitiatorCompleter(URI egUri, URI emUri, List<URI> initiatorURIs,
            List<URI> targetURIs, String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.addAll(initiatorURIs);
        _targetURIs = new ArrayList<URI>(targetURIs);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ? dbClient.queryObject(ExportMask.class, getMask()) : null;

            if (exportMask != null) {
                // Update the initiator tracking containers
                // Add only the initiators that we created and added. Currently only VMAX will fill in these initiators.
                StorageSystem system = dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice());
                if (getUserAddedInitiatorURIs() != null && !getUserAddedInitiatorURIs().isEmpty()) {
                    exportMask.addToUserCreatedInitiators(dbClient.queryObject(Initiator.class, getUserAddedInitiatorURIs()));
                } else if (!Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
                    // Fall-back, add all initiators. Currently only VMAX cherry-picks specific initiators that
                    // are user-added based on the initiators that it found on the array. Other arrays need to
                    // fall back to the original way of tracking user added initiators, which is to add all of them.
                    exportMask.addToUserCreatedInitiators(dbClient.queryObject(Initiator.class, _initiatorURIs));
                }

                // Save the initiators to the ExportMask
                for (URI initiatorURI : _initiatorURIs) {
                    Initiator initiator = dbClient.queryObject(Initiator.class, initiatorURI);
                    if (initiator != null) {
                        exportMask.addInitiator(initiator);
                    } else {
                        _log.warn("Initiator {} does not exist.", initiatorURI);
                    }
                }

                // Save the target StoragePort URIs to the ExportMask
                for (URI newTarget : _targetURIs) {
                    exportMask.addTarget(newTarget);
                }
                dbClient.updateAndReindexObject(exportMask);
            }

            ExportUtils.reconcileExportGroupsHLUs(dbClient, exportGroup);
            dbClient.persistObject(exportGroup);
            _log.info(String.format(
                    "Done ExportMaskAddInitiator - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));

        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskAddInitiator - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }


    public void setTargetURIs(List<URI> _targetURIs) {
        this._targetURIs = _targetURIs;
    }
}
