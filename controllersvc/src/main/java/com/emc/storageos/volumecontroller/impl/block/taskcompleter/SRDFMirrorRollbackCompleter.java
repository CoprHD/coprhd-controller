/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext;
import com.emc.storageos.workflow.WorkflowService;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext.SRDFOperationType.CHANGE_VPOOL_ON_SOURCE;
import static java.lang.String.format;

public class SRDFMirrorRollbackCompleter extends SRDFTaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(SRDFMirrorRollbackCompleter.class);
    private Map<String, Operation.Status> detachStatuses;

    public SRDFMirrorRollbackCompleter(List<URI> sourceURIs, String opId) {
        super(sourceURIs, opId);
        this.detachStatuses = new HashMap<>();
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        SRDFOperationContext ctx = (SRDFOperationContext) WorkflowService.getInstance().loadStepData(this.getOpId());

        List<Function<Volume, Volume>> undoActions = new ArrayList<>();

        if (ctx != null) {
            for (SRDFOperationContext.SRDFOperationContextEntry entry : ctx.getEntries()) {
                if (CHANGE_VPOOL_ON_SOURCE.toString().equals(entry.getOperation())) {
                    undoActions.add(undoVPoolChange(entry));
                }
            }
        }

        List<Volume> sourceVolumeList = dbClient.queryObject(Volume.class, getIds());
        if (null != sourceVolumeList && !sourceVolumeList.isEmpty()) {
            List<Volume> volumesToUpdate = new ArrayList<Volume>();
            for (Volume volume : sourceVolumeList) {
                if (null != volume.getSrdfTargets() && !volume.getSrdfTargets().isEmpty()) {
                    List<URI> targetVolumeURIs = new ArrayList<>(Collections2.transform(volume.getSrdfTargets(),
                            CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetVolumeURIs);
                    for (Volume targetVolume : targetVolumes) {
                        targetVolume.setPersonality(NullColumnValueGetter.getNullStr());
                        volumesToUpdate.add(targetVolume);

                        logDetachStatus(volume, targetVolume);
                    }
                }
                volume.setLinkStatus(Volume.LinkStatus.OTHER.toString());
                volume.setPersonality(NullColumnValueGetter.getNullStr());

                for (Function<Volume, Volume> action : undoActions) {
                    action.apply(volume);
                }

                volumesToUpdate.add(volume);
            }
            dbClient.updateObject(volumesToUpdate);

            if (coded != null && coded instanceof ServiceError) {
                ServiceError error = (ServiceError) coded;
                String originalMessage = error.getMessage();
                String updatedMessage = String.format("%s\n%s", originalMessage,
                        "Rollback of SRDF volumes may require manual cleanup, involving RDF group cleanup." +
                                "  Check logs for more detail.");
                error.setMessage(updatedMessage);
            }
        }

        updateWorkflowStatus(status, coded);
    }

    /**
     * Returns a function for undo'ing the VirtualPool change operation.
     *
     * @param entry Entry from the SRDFOperationContext
     * @return  Function
     */
    private Function<Volume, Volume> undoVPoolChange(SRDFOperationContext.SRDFOperationContextEntry entry) {
        final URI from = (URI) entry.getArgs().get(0);
        final URI to = (URI) entry.getArgs().get(1);

        return new Function<Volume, Volume>() {

            @Override
            public Volume apply(Volume volume) {
                log.info(format("Reverting VirtualPool change for %s from %s to %s", volume.getId(), from, to));
                volume.setVirtualPool(from);
                return volume;
            }
        };
    }

    public void updateDetachStatus(Volume source, Volume target, Operation.Status status, ServiceCoded coded) {
        detachStatuses.put(detachStatusKey(source, target), status);
    }

    private String detachStatusKey(Volume source, Volume target) {
        return String.format("%s:%s", source.getNativeGuid(), target.getNativeGuid());
    }

    private boolean pairWasDetached(Volume source, Volume target) {
        Operation.Status status = detachStatuses.get(detachStatusKey(source, target));
        return Operation.Status.ready.equals(status);
    }

    private void logDetachStatus(Volume volume, Volume targetVolume) {
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Rollback of Source:%s, Target:%s for Task:%s ",
                volume.getNativeGuid(), targetVolume.getNativeGuid(), getOpId()));
        if (pairWasDetached(volume, targetVolume)) {
            msg.append("was detached.");
        } else {
            msg.append("failed to detach.  May require manually suspending and removal from RDF group.");
        }
        log.info(msg.toString());
    }
}
