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
import com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext.SRDFOperationType.CHANGE_VPOOL_ON_SOURCE;
import static java.lang.String.format;

public class SRDFMirrorRollbackCompleter extends SRDFTaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(SRDFMirrorRollbackCompleter.class);

    public SRDFMirrorRollbackCompleter(List<URI> sourceURIs, String opId) {
        super(sourceURIs, opId);
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
        }
        WorkflowStepCompleter.stepSucceded(getOpId());
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
}
