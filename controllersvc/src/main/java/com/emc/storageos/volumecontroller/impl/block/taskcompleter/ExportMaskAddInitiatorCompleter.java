/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import static com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxExportOperationContext.OPERATION_ADD_EXISTING_INITIATOR_TO_EXPORT_GROUP;
import static com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxExportOperationContext.OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP;
import static com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxExportOperationContext.OPERATION_ADD_PORTS_TO_PORT_GROUP;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext.ExportOperationContextOperation;
import com.emc.storageos.workflow.WorkflowService;

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

            if (shouldUpdateDatabase(status)) {
                updateDatabase(dbClient);
            }

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

    public void setTargetURIs(List<URI> targetURIs) {
        this._targetURIs = targetURIs;
    }

    private void updateDatabase(DbClient dbClient) {
        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
        ExportMask exportMask = (getMask() != null) ? dbClient.queryObject(ExportMask.class, getMask()) : null;

        if (exportMask != null) {
            // Update the initiator tracking containers
            exportMask.addToUserCreatedInitiators(dbClient.queryObject(Initiator.class, _initiatorURIs));

            // Save the initiators to the ExportMask
            for (URI initiatorURI : _initiatorURIs) {
                Initiator initiator = dbClient.queryObject(Initiator.class, initiatorURI);
                if (initiator != null) {
                    exportMask.removeFromExistingInitiators(initiator);
                    exportMask.addInitiator(initiator);
                    exportGroup.addInitiator(initiator);
                } else {
                    _log.warn("Initiator {} does not exist.", initiatorURI);
                }
            }

            // Save the target StoragePort URIs to the ExportMask
            for (URI newTarget : _targetURIs) {
                exportMask.addTarget(newTarget);
            }
            dbClient.updateObject(exportMask);
        }

        ExportUtils.reconcileExportGroupsHLUs(dbClient, exportGroup);
        dbClient.updateObject(exportGroup);
    }

    /**
     * This completer may complete with a ready or error status.  In the case of an error status,
     * we can check the {@link ExportOperationContext} to see if the volumes were added and perform
     * the necessary database updates.
     *
     * @param status    Status of the operation.
     * @return          true, if the status is ready or the volumes were added despite error status.
     */
    private boolean shouldUpdateDatabase(Operation.Status status) {
        return wereInitiatorsOrPortsPhysicallyAdded() || status == Operation.Status.ready;
    }

    /**
     * This method will determine if any initiators/ports were added as a result of this operation.
     * 
     * @return true if initiators were added, otherwise false.
     */
    private boolean wereInitiatorsOrPortsPhysicallyAdded() {
        // If there were initiators or ports added to the mask (physically), we will have a context
        ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(getOpId());
        if (context == null) {
            return false;
        }

        // If initiators/ports were added to the mask, there will be operations hanging off the context
        List<ExportOperationContext.ExportOperationContextOperation> operations = context.getOperations();
        if (operations == null || operations.isEmpty()) {
            return false;
        }

        // Go through the operations in the context and find at least one initiator/port object.
        for (ExportOperationContextOperation op : operations) {

            // These are the specific operations that add initiators/ports to the mask.
            // So if the operation saved isn't one of these, skip it.
            if (!((op.getOperation().equals(OPERATION_ADD_PORTS_TO_PORT_GROUP)) ||
                    (op.getOperation().equals(OPERATION_ADD_EXISTING_INITIATOR_TO_EXPORT_GROUP)) ||
                    (op.getOperation().equals(OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP)))) {
                continue;
            }

            // Check for valid arguments for this operation. If there are none, skip it.
            List<Object> opArgs = op.getArgs();
            if (opArgs == null || opArgs.isEmpty()) {
                continue;
            }

            // Look for a List<URI> object within the list.
            for (Object opArg : opArgs) {

                // We're only interested in List types, skip all others
                if (!(opArg instanceof List)) {
                    continue;
                }

                // Cast to a List<Object> to see if it contains anything.
                List<Object> opArgObjList = (List<Object>) opArg;
                if (opArgObjList.isEmpty()) {
                    continue;
                }

                // Grab the first object of the list. We assume the List contains same-typed objects
                Object opArgObjListEntry = opArgObjList.get(0);
                if (!(opArgObjListEntry instanceof URI)) {
                    continue;
                }

                // If the object is a URI of type Initiator or StoragePort, then we have added an initiator or port
                URI uri = (URI) opArgObjListEntry;
                if (URIUtil.isType(uri, Initiator.class) || URIUtil.isType(uri, StoragePort.class)) {
                    return true;
                }
            }
        }

        return false;
    }
}
