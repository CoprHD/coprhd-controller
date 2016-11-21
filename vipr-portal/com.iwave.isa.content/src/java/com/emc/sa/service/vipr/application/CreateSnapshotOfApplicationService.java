/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.DeleteSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.DeleteSnapshotSessionForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.services.util.TimeUtils;
import com.emc.vipr.client.Tasks;

@Service("CreateSnapshotOfApplication")
public class CreateSnapshotOfApplicationService extends ViPRService {

    @Param(ServiceParams.APPLICATION)
    private URI applicationId;

    @Param(ServiceParams.APPLICATION_COPY_SETS)
    protected String name;

    @Param(ServiceParams.APPLICATION_SITE)
    protected String virtualArrayParameter;

    @Param(ServiceParams.READ_ONLY)
    protected Boolean readOnly;

    @Param(ServiceParams.APPLICATION_SUB_GROUP)
    protected List<String> subGroups;

    @Override
    public void execute() throws Exception {
        NamedVolumesList volumesToUse = BlockStorageUtils.getVolumesBySite(getClient(), virtualArrayParameter, applicationId);

        List<URI> volumeIds = BlockStorageUtils.getSingleVolumePerSubGroupAndStorageSystem(volumesToUse, subGroups);

        checkAndPurgeObsoleteSnapshots(applicationId);
        String snapshotName = TimeUtils.formatDateForCurrent(name);
        Tasks<? extends DataObjectRestRep> tasks = execute(new CreateSnapshotForApplication(applicationId, volumeIds, snapshotName, readOnly));
        addAffectedResources(tasks);
        addRetainedReplicas(applicationId, snapshotName);
    }
    
    /**
     * Check retention policy and delete obsolete snapshots if necessary
     * 
     * @param applicationId - application id
     */
    private void checkAndPurgeObsoleteSnapshots(URI applicationId) {
        if (!isRetentionRequired()) {
            return;
        }
        List<RetainedReplica> replicas = findObsoleteReplica(applicationId.toString());
        for (RetainedReplica replica : replicas) {
            for (String applicationCopySet : replica.getAssociatedReplicaIds()) {
                info("Delete application snapshots %s since it exceeds max number of clones allowed", applicationCopySet);
                
                List<URI> snapshotSessionIds = BlockStorageUtils.getSingleSnapshotSessionPerSubGroupAndStorageSystem(applicationId,
                        applicationCopySet,
                        subGroups);
                if (snapshotSessionIds.size() > 0) {
                    info("Delete snapshot sessions %s ", StringUtils.join(snapshotSessionIds, ","));
                    execute(new DeleteSnapshotSessionForApplication(applicationId, snapshotSessionIds));
                } else {
                    List<URI> snapshotIds = BlockStorageUtils.getSingleSnapshotPerSubGroupAndStorageSystem(applicationId, applicationCopySet,
                            subGroups);
                    info("Delete snapshot %s ", StringUtils.join(snapshotIds, ","));
                    execute(new DeleteSnapshotForApplication(applicationId, snapshotIds));
                }
            }
            getModelClient().delete(replica);
        }
    }
    
}
