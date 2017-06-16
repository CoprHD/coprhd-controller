/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

import static com.emc.storageos.volumecontroller.impl.utils.SRDFOperationContext.SRDFOperationType.CHANGE_VPOOL_ON_SOURCE;
import static java.util.Arrays.asList;

public class SRDFMirrorCreateCompleter extends SRDFTaskCompleter {
    private static final Logger log = LoggerFactory.getLogger(SRDFMirrorCreateCompleter.class);

    private String sourceRepGroup;

    private String targetRepGroup;

    private URI sourceCGUri;

    private URI vpoolChangeURI;

    public SRDFMirrorCreateCompleter(final URI sourceURI, final URI targetUri, final URI vPoolChangeUri, final String opId) {
        super(asList(sourceURI, targetUri), opId);
        vpoolChangeURI = vPoolChangeUri;
    }

    public SRDFMirrorCreateCompleter(List<URI> volumeURIs, final URI vPoolChangeUri, String opId) {
        super(volumeURIs, opId);
        vpoolChangeURI = vPoolChangeUri;
    }

    public void setCGName(final String sourceGroupName, final String targetGroupName,
            final URI sourceCGUri) {
        this.sourceRepGroup = sourceGroupName;
        this.targetRepGroup = targetGroupName;
        this.sourceCGUri = sourceCGUri;
    }
    
    public URI getVirtualPoolChangeURI() {
      return vpoolChangeURI;
    }

    @Override
    public void complete(final DbClient dbClient, final Operation.Status status,
            final ServiceCoded coded) throws DeviceControllerException {
        log.info("Completing with status: {}", status);
        setDbClient(dbClient);

        try {
            switch (status) {

                case ready:
                    if (null != vpoolChangeURI) {
                        // updating source volumes with changed vPool
                        URI previousVPool = null;
                        for (Volume volume : getVolumes()) {
                            if (volume.isSRDFSource()) {
                                previousVPool = volume.getVirtualPool();
                                volume.setVirtualPool(vpoolChangeURI);
                                dbClient.updateObject(volume);
                            }
                        }
                        SRDFOperationContext.insertContextOperation(this, CHANGE_VPOOL_ON_SOURCE, previousVPool, vpoolChangeURI);
                    }
                    // Pin the target System with the source CG, which helps to identify this system is
                    // a target R2 for CG.
                    Volume target = getTargetVolume();
                    if (null != sourceCGUri) {
                        URI targetSystemUri = target.getStorageController();
                        StorageSystem targetSystem = dbClient.queryObject(StorageSystem.class,
                                targetSystemUri);
                        if (targetSystem.getTargetCgs() == null) {
                            targetSystem.setTargetCgs(new StringSet());
                        }
                        targetSystem.getTargetCgs().add(sourceCGUri.toString());
                        dbClient.updateObject(targetSystem);
                    }

                    String copyMode = target.getSrdfCopyMode();
                    RemoteDirectorGroup group = dbClient.queryObject(RemoteDirectorGroup.class,
                            target.getSrdfGroup());
                    log.info("Setting copyMode {} to RAGroup {}", copyMode, group.getId());
                    group.setSupportedCopyMode(copyMode);
                    group.setSourceReplicationGroupName(sourceRepGroup);
                    group.setTargetReplicationGroupName(targetRepGroup);
                    if (null == group.getVolumes()) {
                        group.setVolumes(new StringSet());
                    }

                    group.getVolumes().addAll(getVolumeIds());
                    dbClient.updateObject(group);
                    break;

                default:
                    log.info("Unable to handle status: {}", status);
            }
            recordSRDFOperation(dbClient, OperationTypeEnum.CREATE_SRDF_LINK, status, getSourceVolume().getId().toString(),
                    getTargetVolume().getId().toString());
        } catch (Exception e) {
            log.info("Failed to update status for task {}", getOpId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }

    }

    @Override
    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.IN_SYNC;
    }
}
