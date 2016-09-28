/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.application.tasks.CreateSnapshotForApplication;
import com.emc.sa.service.vipr.application.tasks.DeleteSnapshotForApplication;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.uimodels.RetainedReplica;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.NamedVolumesList;
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
        Tasks<? extends DataObjectRestRep> tasks = execute(new CreateSnapshotForApplication(applicationId, volumeIds, name, readOnly));
        addAffectedResources(tasks);
        addRetainedReplicas(applicationId, tasks.getTasks());
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
            List<URI> snapshotIds = new ArrayList<URI>();
            for (String obsoleteSnapshotId : replica.getAssociatedReplicaIds()) {
                Class clazz = URIUtil.getModelClass(uri(obsoleteSnapshotId));
                if (clazz.equals(BlockSnapshot.class)) {
                    info("Deactivating snapshot %s since it exceeds max number of snapshots allowed", obsoleteSnapshotId);
                    snapshotIds.add(uri(obsoleteSnapshotId));
                } else {
                    info("Skip object %s. It is not a full copy", obsoleteSnapshotId);
                }
            }
            execute(new DeleteSnapshotForApplication(applicationId, snapshotIds));
            getModelClient().delete(replica);
        }
    }
    
}
