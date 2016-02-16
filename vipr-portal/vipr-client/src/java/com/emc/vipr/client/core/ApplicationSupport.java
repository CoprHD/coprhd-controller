/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CLONE_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CREATE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CREATE_FULL_COPY_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CREATE_SNAPSHOT_SESSION_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_CREATE_SNAPSHOT_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_DEACTIVATE_SNAPSHOT_SESSION_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_DEACTIVATE_SNAPSHOT_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_DELETE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_DETACH_FULL_COPY_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_FULL_COPY_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_GET_SNAPSHOT_COPY_SETS_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_GET_SNAPSHOT_SESSION_COPY_SETS_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_LINK_SNAPSHOT_SESSION_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_LINK_SNAPSHOT_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_RESTORE_FULL_COPY_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_RESTORE_SNAPSHOT_SESSION_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_RESTORE_SNAPSHOT_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_RESYNCHRONIZE_FULL_COPY_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_RESYNCHRONIZE_SNAPSHOT_SESSION_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_RESYNCHRONIZE_SNAPSHOT_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_UNLINK_SNAPSHOT_SESSION_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_UNLINK_SNAPSHOT_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_UPDATE_APP_URL;
import static com.emc.vipr.client.core.impl.PathConstants.APP_SUPPORT_VOLUME_URL;
import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.application.VolumeGroupCopySetList;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyCreateParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyDetachParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyRestoreParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyResynchronizeParam;
import com.emc.storageos.model.application.VolumeGroupList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionCreateParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionDeactivateParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionOperationParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionRestoreParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeGroupSnapshotCreateParam;
import com.emc.storageos.model.block.VolumeGroupSnapshotOperationParam;
import com.emc.vipr.client.impl.RestClient;

public class ApplicationSupport {
    protected final RestClient client;

    public ApplicationSupport(RestClient client) {
        this.client = client;
    }

    /**
     * Creates an application.
     * <p>
     * API Call: POST /volume-groups/block
     * 
     * @return The new state of the cluster
     */
    public VolumeGroupRestRep createApplication(VolumeGroupCreateParam input) {
        return client.post(VolumeGroupRestRep.class, input, APP_SUPPORT_CREATE_APP_URL);
    }

    /**
     * Get List of applications
     * API call: GET /volume-groups/block
     * 
     * @return List of applications
     */

    public VolumeGroupList getApplications() {
        return client.get(VolumeGroupList.class, APP_SUPPORT_CREATE_APP_URL, "");
    }

    /**
     * Deletes an application
     * API Call: POST /volume-groups/block/{id}/deactivate
     * 
     */
    public void deleteApplication(URI id) {
        client.post(String.class, APP_SUPPORT_DELETE_APP_URL, id);
    }

    /**
     * Update an application
     * API call: PUT /volume-groups/block/{id}
     * 
     */
    public TaskList updateApplication(URI id, VolumeGroupUpdateParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_UPDATE_APP_URL);
        return client.putURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Get application based on ID
     * 
     */
    public VolumeGroupRestRep getApplication(URI id) {
        return client.get(VolumeGroupRestRep.class, APP_SUPPORT_UPDATE_APP_URL, id);
    }

    /**
     * Get volumes associated with an application
     * 
     * @param id application id
     * @return list of volumes
     */
    public List<NamedRelatedResourceRep> listVolumes(URI id) {
        NamedVolumesList response = getVolumeByApplication(id);
        return defaultList(response.getVolumes());
    }

    /*
     * Get volumes for application
     * GET /volume-groups/block/{id}/volumes
     */
    public NamedVolumesList getVolumeByApplication(URI id) {
        return client.get(NamedVolumesList.class, APP_SUPPORT_VOLUME_URL, id);
    }

    /*
     * Get full copies for application
     * GET /volume-groups/block/{id}/protection/full-copies
     */
    public NamedVolumesList getClonesByApplication(URI id) {
        return client.get(NamedVolumesList.class, APP_SUPPORT_CLONE_URL, id);
    }

    /**
     * Creates a full copy of an application.
     * API Call: POST /volume-groups/block/{id}/protection/full-copies
     * 
     * @param id application id to create full copy of
     * @param input input parameters for create full copy request
     * @return list of tasks
     */
    public TaskList createFullCopyOfApplication(URI id, VolumeGroupFullCopyCreateParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_CREATE_FULL_COPY_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Gets snapshot copy sets of an application.
     * API Call: GET /volume-groups/block/{id}/protection/snapshots/copy-sets
     * 
     * @param id application id to get copy sets of
     * @param input input parameters for copy sets
     * @return list of tasks
     */
    public VolumeGroupCopySetList getSnapshotCopySets(URI id) {
        return client.get(VolumeGroupCopySetList.class, APP_SUPPORT_GET_SNAPSHOT_COPY_SETS_URL, id);
    }

    /**
     * Gets snapshot session copy sets of an application.
     * API Call: GET /volume-groups/block/{id}/protection/snap-sessions/copy-sets
     * 
     * @param id application id to get copy sets of
     * @param input input parameters for copy sets request
     * @return list of tasks
     */
    public VolumeGroupCopySetList getSnapshotSessionCopySets(URI id) {
        return client.get(VolumeGroupCopySetList.class, APP_SUPPORT_GET_SNAPSHOT_SESSION_COPY_SETS_URL, id);
    }

    /**
     * Creates a snapshot of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snapshots
     * 
     * @param id application id to create snapshot of
     * @param input input parameters for create snapshot request
     * @return list of tasks
     */
    public TaskList createSnapshotOfApplication(URI id, VolumeGroupSnapshotCreateParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_CREATE_SNAPSHOT_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Creates a snapshot session of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snap-sessions
     * 
     * @param id application id to create snapshot session of
     * @param input input parameters for create snapshot session request
     * @return list of tasks
     */
    public TaskList createSnapshotSessionOfApplication(URI id, VolumeGroupSnapshotSessionCreateParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_CREATE_SNAPSHOT_SESSION_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /*
     * Get full copies for application
     * GET /volume-groups/block/{id}/volumes
     */
    public NamedVolumesList getFullCopiesByApplication(URI id) {
        return client.get(NamedVolumesList.class, APP_SUPPORT_FULL_COPY_URL, id);
    }

    /**
     * Detaches a full copy of an application.
     * API Call: POST /volume-groups/block/{id}/protection/full-copies/detach
     * 
     * @param id application id with full copy
     * @param input input parameters for application full copy request
     * @return list of tasks
     */
    public TaskList detachApplicationFullCopy(URI id, VolumeGroupFullCopyDetachParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_DETACH_FULL_COPY_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Restores a snapshot of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snapshots/restore
     * 
     * @param id application id with snapshot
     * @param input input parameters for application snapshot request
     * @return list of tasks
     */
    public TaskList restoreApplicationSnapshot(URI id, VolumeGroupSnapshotOperationParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_RESTORE_SNAPSHOT_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Restores a snapshot session of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snap-sessions/restore
     * 
     * @param id application id with snapshot session
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList restoreApplicationSnapshotSession(URI id, VolumeGroupSnapshotSessionRestoreParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_RESTORE_SNAPSHOT_SESSION_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Resynchronizes a snapshot session of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snap-sessions/resynchronize
     * 
     * @param id application id with snapshot session
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList resynchronizeApplicationSnapshotSession(URI id, VolumeGroupSnapshotSessionOperationParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_RESYNCHRONIZE_SNAPSHOT_SESSION_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Resynchronizes a snapshot of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snapshots/resynchronize
     * 
     * @param id application id with snapshot session
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList resynchronizeApplicationSnapshot(URI id, VolumeGroupSnapshotOperationParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_RESYNCHRONIZE_SNAPSHOT_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Deactivates a snapshot session of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snap-sessions/deactivate
     * 
     * @param id application id with snapshot session
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList deactivateApplicationSnapshotSession(URI id, VolumeGroupSnapshotSessionDeactivateParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_DEACTIVATE_SNAPSHOT_SESSION_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Deactivates a snapshot of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snapshots/deactivate
     * 
     * @param id application id with snapshot
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList deactivateApplicationSnapshot(URI id, VolumeGroupSnapshotOperationParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_DEACTIVATE_SNAPSHOT_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Links a snapshot session of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snap-sessions/link-targets
     * 
     * @param id application id with snapshot session
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList linkApplicationSnapshotSession(URI id, VolumeGroupSnapshotSessionLinkTargetsParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_LINK_SNAPSHOT_SESSION_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Links a snapshot of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snapshots/link-targets
     * 
     * @param id application id with snapshot
     * @param input input parameters for application snapshot request
     * @return list of tasks
     */
    public TaskList linkApplicationSnapshot(URI id, VolumeGroupSnapshotSessionLinkTargetsParam input) {
        // TODO change the input parameter
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_LINK_SNAPSHOT_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Unlinks a snapshot session of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snap-sessions/unlink-targets
     * 
     * @param id application id with snapshot session
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList unlinkApplicationSnapshotSession(URI id, VolumeGroupSnapshotSessionUnlinkTargetsParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_UNLINK_SNAPSHOT_SESSION_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Unlinks a snapshot of an application.
     * API Call: POST /volume-groups/block/{id}/protection/snapshots/unlink-targets
     * 
     * @param id application id with snapshot
     * @param input input parameters for application snapshot session request
     * @return list of tasks
     */
    public TaskList unlinkApplicationSnapshot(URI id, VolumeGroupSnapshotSessionUnlinkTargetsParam input) {
        // TODO change the input parameter
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_UNLINK_SNAPSHOT_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Restores a full copy of an application.
     * API Call: POST /volume-groups/block/{id}/protection/full-copies/restore
     * 
     * @param id application id with full copy
     * @param input input parameters for application full copy request
     * @return list of tasks
     */
    public TaskList restoreApplicationFullCopy(URI id, VolumeGroupFullCopyRestoreParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_RESTORE_FULL_COPY_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }

    /**
     * Resynchronizes a full copy of an application.
     * API Call: POST /volume-groups/block/{id}/protection/full-copies/resynchronize
     * 
     * @param id application id with full copy
     * @param input input parameters for application full copy request
     * @return list of tasks
     */
    public TaskList resynchronizeApplicationFullCopy(URI id, VolumeGroupFullCopyResynchronizeParam input) {
        UriBuilder uriBuilder = client.uriBuilder(APP_SUPPORT_RESYNCHRONIZE_FULL_COPY_URL);
        return client.postURI(TaskList.class, input, uriBuilder.build(id));
    }
}