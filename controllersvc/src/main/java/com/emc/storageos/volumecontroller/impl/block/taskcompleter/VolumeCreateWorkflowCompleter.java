/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.google.common.base.Joiner;

public class VolumeCreateWorkflowCompleter extends VolumeWorkflowCompleter {

    private static final long serialVersionUID = -322417427255890556L;
    
    @XmlTransient
    private List<VolumeDescriptor> _volumeDescriptors = new ArrayList<VolumeDescriptor>();

    public VolumeCreateWorkflowCompleter(List<URI> volUris, String task, List<VolumeDescriptor> volumeDescriptors) {
        super(volUris, task);
        this._volumeDescriptors = volumeDescriptors;
    }

    public VolumeCreateWorkflowCompleter(URI volUri, String task) {
        super(volUri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded){

        super.complete(dbClient, status, serviceCoded);

        switch (status) {
        case error:

            handleBlockVolumeErrors(dbClient);
            handleVplexVolumeErrors(dbClient);

            break;
        default:
            break;
        }        
    }
    
    private void handleBlockVolumeErrors(DbClient dbClient) {
        for (VolumeDescriptor volumeDescriptor : 
            VolumeDescriptor.getDescriptors(_volumeDescriptors, VolumeDescriptor.Type.BLOCK_DATA)) {
            
            Volume volume = dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());
            
            if (volume != null && (volume.getNativeId() == null || volume.getNativeId().equals(""))) {
                _log.info("No native id was present on volume {}, marking inactive", volume.getLabel());
                dbClient.markForDeletion(volume);
            }
        }
    }
    
    private void handleVplexVolumeErrors(DbClient dbClient) {
        
        List<String> finalMessages = new ArrayList<String>();
        
        for (VolumeDescriptor volumeDescriptor : 
            VolumeDescriptor.getDescriptors(_volumeDescriptors, VolumeDescriptor.Type.VPLEX_VIRT_VOLUME)) {
            
            Volume volume = dbClient.queryObject(Volume.class, volumeDescriptor.getVolumeURI());
            
            _log.info("Looking at VPLEX virtual volume {}", volume.getLabel());
            
            boolean deactivateVirtualVolume = true;
            List<String> livingVolumeNames = new ArrayList<String>();
            
            _log.info("Its associated volumes are: " + volume.getAssociatedVolumes());
            for (String associatedVolumeUri : volume.getAssociatedVolumes()) {
                Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolumeUri));
                if (associatedVolume != null && !associatedVolume.getInactive()) {
                    _log.warn("VPLEX virtual volume {} has active associated volume {}", volume.getLabel(), associatedVolume.getLabel());
                    livingVolumeNames.add(associatedVolume.getLabel());
                    deactivateVirtualVolume = false;
                }
            }
            
            if (deactivateVirtualVolume) {
                _log.info("VPLEX virtual volume {} has no active associated volumes, marking for deletion", volume.getLabel());
                dbClient.markForDeletion(volume);
            } else {
                String message = "VPLEX virtual volume "  + volume.getLabel() + " will not be marked for deletion "
                               + "because it still has active associated volumes (";
                message += Joiner.on(",").join(livingVolumeNames) + ")";
                finalMessages.add(message);
                _log.warn(message);
            }
        }
        
        if (finalMessages.size() > 0) {
            String finalMessage = Joiner.on("; ").join(finalMessages) + ".";
            _log.error(finalMessage);
        }
    }
}
