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
import java.util.Collection;
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

            if (supportsPartialInitiatorAddition() && status != Operation.Status.ready) {
                // Get the list of initiator/port URIs. Only update those specific entries.
                List<URI> uris = getInitiatorsOrPortsPhysicallyAdded();

                // Update the database with the context initiators and ports only
                if (uris != null && !uris.isEmpty()) {
                    updateDatabase(dbClient, uris);
                }
            } else if (status == Operation.Status.ready) {
                updateDatabase(dbClient, null);
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

    /**
     * Update the export mask and export group with the initiators are ports
     * 
     * @param dbClient
     *            dbclient
     * @param uris
     *            uris of Initiators and storage ports
     */
    private void updateDatabase(DbClient dbClient, Collection<URI> uris) {
        List<URI> targetURIs = _targetURIs;
        List<URI> initiatorURIs = _initiatorURIs;

        // If there are any initiators or storage ports, let's only update the ports AND
        // initiators that appear in the context.
        if (uris != null && !uris.isEmpty()) {
            targetURIs = URIUtil.getURIsofType(uris, Initiator.class);
            initiatorURIs = URIUtil.getURIsofType(uris, StoragePort.class);
        }

        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
        ExportMask exportMask = (getMask() != null) ? dbClient.queryObject(ExportMask.class, getMask()) : null;

        if (exportMask != null) {
            // Update the initiator tracking containers
            exportMask.addToUserCreatedInitiators(dbClient.queryObject(Initiator.class, initiatorURIs));

            // Save the initiators to the ExportMask
            for (URI initiatorURI : initiatorURIs) {
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
            for (URI newTarget : targetURIs) {
                exportMask.addTarget(newTarget);
            }
            dbClient.updateObject(exportMask);
        }

        ExportUtils.reconcileExportGroupsHLUs(dbClient, exportGroup);
        dbClient.updateObject(exportGroup);

    }

    /**
     * This method will check to see if there is a context object associated with the step,
     * which will tell us if the platform that performed the add initiator operation supports
     * adding only a portion of the initiators in the request.
     * 
     * @return true if the platform supports only adding a subset of initiators to the mask
     */
    private boolean supportsPartialInitiatorAddition() {
        if (getContextOperations() != null) {
            return true;
        }
        return false;
    }

    /**
     * Retrieves the context operations
     * 
     * @return the operations context object from the step data
     */
    private List<ExportOperationContext.ExportOperationContextOperation> getContextOperations() {
        List<ExportOperationContext.ExportOperationContextOperation> operations = null;
        try {
            ExportOperationContext context = null;

            // Only specific platforms create a context object. If there is no context object, default to updating the
            // object in the DB
            context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(getOpId());
            if (context == null) {
                return null;
            }

            // If initiators/ports were added to the mask, there will be operations hanging off the context
            operations = context.getOperations();

        } catch (ClassCastException cce) {
            // Step state data was stored, but it's not a context object, so return true by default.
        }

        return operations;
    }

    /**
     * This method will determine if any initiators/ports were added as a result of this operation.
     * 
     * @return list of initiator URIs if any were physically added
     */
    private List<URI> getInitiatorsOrPortsPhysicallyAdded() {
        List<URI> uris = new ArrayList<>();

        List<ExportOperationContext.ExportOperationContextOperation> operations = getContextOperations();
        if (operations == null) {
            return null;
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
                    uris.add(uri);
                }
            }
        }

        return uris;
    }
}
