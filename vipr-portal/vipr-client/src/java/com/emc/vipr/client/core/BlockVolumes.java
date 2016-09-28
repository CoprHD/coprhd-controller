/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.UriBuilder;

import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.emc.storageos.model.block.BulkDeleteParam;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.MigrationList;
import com.emc.storageos.model.block.MigrationParam;
import com.emc.storageos.model.block.MirrorList;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VirtualArrayChangeParam;
import com.emc.storageos.model.block.VolumeBulkRep;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeExpandParam;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualArrayChangeParam;
import com.emc.storageos.model.block.VolumeVirtualPoolChangeParam;
import com.emc.storageos.model.block.export.ExportBlockParam;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ITLBulkRep;
import com.emc.storageos.model.block.export.ITLRestRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.model.protection.ProtectionSetRestRep;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolChangeRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.search.VolumeSearchBuilder;
import com.emc.vipr.client.impl.RestClient;

/**
 * Block Volumes resources.
 * <p>
 * Base URL: <tt>/block/volumes</tt>
 */
public class BlockVolumes extends BulkExportResources<VolumeRestRep> implements TaskResources<VolumeRestRep> {

    public BlockVolumes(ViPRCoreClient parent, RestClient client) {
        super(parent, client, VolumeRestRep.class, PathConstants.BLOCK_VOLUMES_URL);
    }

    @Override
    public BlockVolumes withInactive(boolean inactive) {
        return (BlockVolumes) super.withInactive(inactive);
    }

    @Override
    public BlockVolumes withInternal(boolean internal) {
        return (BlockVolumes) super.withInternal(internal);
    }

    @Override
    protected List<VolumeRestRep> getBulkResources(BulkIdParam input) {
        VolumeBulkRep response = client.post(VolumeBulkRep.class, input, getBulkUrl());
        return defaultList(response.getVolumes());
    }

    @Override
    public Tasks<VolumeRestRep> getTasks(URI id) {
        return doGetTasks(id);
    }

    @Override
    public Task<VolumeRestRep> getTask(URI id, URI taskId) {
        return doGetTask(id, taskId);
    }

    /**
     * Finds a volume by its WWN.
     * <p>
     * API Call: <tt>GET /block/volumes/search?wwn={wwn}</tt>
     *
     * @param wwn
     *            the volume WWN.
     * @return the list of matching volumes.
     */
    public List<VolumeRestRep> findByWwn(String wwn) {
        return search().byWwn(wwn).run();
    }

    /**
     * Finds a volume by its name.
     * <p>
     * API Call: <tt>GET /block/volumes/search?name={name}</tt>
     *
     * @param name
     *            the volume name.
     * @return the list of matching volumes.
     */
    public List<VolumeRestRep> findByName(String name) {
        return search().byName(name).run();
    }

    /**
     * Begins creating one or more block volumes.
     * <p>
     * API Call: <tt>POST /block/volumes</tt>
     *
     * @param create
     *            the block volume create configuration.
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<VolumeRestRep> create(VolumeCreate create) {
        return postTasks(create, baseUrl);
    }

    /**
     * Adds journal capacity
     *
     * @param create
     *            the block volume create configuration for journal volumes.
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<VolumeRestRep> addJournalCapacity(VolumeCreate create) {
        return postTasks(create, baseUrl + "/protection/addJournalCapacity");
    }

    /**
     * Begins deactivating a block volume by ID.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/deactivate?type=FULL</tt>
     *
     * @param id
     *            the ID of the block volume to deactivate.
     * @return a task for monitoring the progress of the operation.
     *
     * @see #deactivate(List, VolumeDeleteTypeEnum)
     */
    public Task<VolumeRestRep> deactivate(URI id) {
        return deactivate(id, VolumeDeleteTypeEnum.FULL);
    }

    /**
     * Begins deactivating a block volume by ID.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/deactivate?type={deletionType}</tt>
     *
     * @param id
     *            the ID of the block volume to deactivate.
     * @param deletionType
     *            {@code FULL} or {@code VIPR_ONLY}
     * @return a task for monitoring the progress of the operation.
     *
     * @see com.emc.storageos.model.block.VolumeDeleteTypeEnum
     */
    public Task<VolumeRestRep> deactivate(URI id, VolumeDeleteTypeEnum deletionType) {
        URI uri = client.uriBuilder(getDeactivateUrl()).queryParam("type", deletionType).build(id);
        TaskResourceRep task = client.postURI(TaskResourceRep.class, uri);
        return new Task<>(client, task, resourceClass);
    }

    /**
     * Begins deactivating multiple block volumes by their IDs.
     * <p>
     * API Call: <tt>POST /block/volumes/deactivate?type=FULL</tt>
     *
     * @param ids
     *            The IDs of the block volumes to deactivate.
     * @return tasks for monitoring the progress of the operations.
     */
    public Tasks<VolumeRestRep> deactivate(List<URI> ids) {
        return deactivate(ids, VolumeDeleteTypeEnum.FULL);
    }

    /**
     * Begins deactivating multiple block volumes by their IDs.
     * <p>
     * API Call: <tt>POST /block/volumes/deactivate?type={deletionType}</tt>
     *
     * @param ids
     *            The IDs of the block volumes to deactivate.
     * @param deletionType
     *            {@code FULL} or {@code VIPR_ONLY}
     * @return tasks for monitoring the progress of the operations.
     *
     * @see com.emc.storageos.model.block.VolumeDeleteTypeEnum
     */
    public Tasks<VolumeRestRep> deactivate(List<URI> ids, VolumeDeleteTypeEnum deletionType) {
        URI uri = client.uriBuilder(baseUrl + "/deactivate").queryParam("type", deletionType).build();
        TaskList tasks = client.postURI(TaskList.class, new BulkDeleteParam(ids), uri);
        return new Tasks<>(client, tasks.getTaskList(), resourceClass);
    }

    /**
     * Begins expanding a block volume by ID.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/expand</tt>
     *
     * @param id
     *            the ID of the block volume to expand.
     * @param input
     *            the expand configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Task<VolumeRestRep> expand(URI id, VolumeExpandParam input) {
        return postTask(input, getIdUrl() + "/expand", id);
    }

    /**
     * Gets the list of volumes for the given export groups.
     *
     * @param exportGroups
     *            the export groups.
     * @return the list of volumes in the given export groups.
     */
    public List<VolumeRestRep> getByExportGroups(Collection<? extends ExportGroupRestRep> exportGroups) {
        return getByExportGroups(exportGroups, null);
    }

    /**
     * Gets the list of volumes for the given export groups, optionally filtering the results.
     *
     * @param exportGroups
     *            the export groups.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of block volumes.
     *
     * @see ExportGroupRestRep#getVolumes()
     */
    public List<VolumeRestRep> getByExportGroups(Collection<? extends ExportGroupRestRep> exportGroups,
            ResourceFilter<VolumeRestRep> filter) {
        Set<URI> ids = new LinkedHashSet<>();
        for (ExportGroupRestRep exportGroup : exportGroups) {
            if (exportGroup.getVolumes() != null) {
                for (ExportBlockParam volume : exportGroup.getVolumes()) {
                    ids.add(volume.getId());
                }
            }
        }
        return getByIds(ids, filter);
    }

    /**
     * Gets the list of exports for a given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{id}/exports</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @return the list of exports.
     */
    public List<ITLRestRep> getExports(URI id) {
        ITLRestRepList response = client.get(ITLRestRepList.class, getIdUrl() + "/exports", id);
        return defaultList(response.getExportList());
    }

    /**
     * Gets the exports for a list of volumes.
     * <p>
     * API Call: <tt>POST /block/volumes/exports/bulk</tt>
     *
     * @param ids
     *            the IDs of the block volumes.
     *
     * @return the list of exports.
     */
    public ITLBulkRep getExports(BulkIdParam ids) {
        return getBulkExports(ids);
    }

    /**
     * Gets the base URL for volume continuous copies: <tt>/block/volumes/{id}/protection/continuous-copies</tt>
     *
     * @return the URL for continuous copies.
     */
    protected String getContinuousCopiesUrl() {
        return getIdUrl() + "/protection/continuous-copies";
    }

    /**
     * Begins creating a number of continuous copies for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/start</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the configuration of the new continuous copies.
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<VolumeRestRep> startContinuousCopies(URI id, CopiesParam input) {
        TaskList tasks = client.post(TaskList.class, input, getContinuousCopiesUrl() + "/start", id);
        return new Tasks<VolumeRestRep>(client, tasks.getTaskList(), BlockMirrorRestRep.class);
    }

    /**
     * Begins stopping a number of continuous copies for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/stop</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the configuration of the copies to stop.
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<VolumeRestRep> stopContinuousCopies(URI id, CopiesParam input) {
        TaskList tasks = client.post(TaskList.class, input, getContinuousCopiesUrl() + "/stop", id);
        return new Tasks<VolumeRestRep>(client, tasks.getTaskList(), BlockMirrorRestRep.class);
    }

    /**
     * Begins pausing a number of continuous copies for a given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/pause</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the copy configurations.
     * @return tasks for monitoring the progress if the operations.
     */
    public Tasks<VolumeRestRep> pauseContinuousCopies(URI id, CopiesParam input) {
        TaskList tasks = client.post(TaskList.class, input, getContinuousCopiesUrl() + "/pause", id);
        return new Tasks<VolumeRestRep>(client, tasks.getTaskList(), BlockMirrorRestRep.class);
    }

    /**
     * Begins resuming a number of continuous copies for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/resume</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the copy configurations.
     * @return tasks for monitoring the progress of the operations.
     */
    public Tasks<VolumeRestRep> resumeContinuousCopies(URI id, CopiesParam input) {
        TaskList tasks = client.post(TaskList.class, input, getContinuousCopiesUrl() + "/resume", id);
        return new Tasks<VolumeRestRep>(client, tasks.getTaskList(), BlockMirrorRestRep.class);
    }

    /**
     * Request to reverse the replication direction, i.e. R1 and R2 are interchanged for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/swap</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the copy configurations.
     * @return tasks for monitoring the progress of the operations.
     */
    public Tasks<VolumeRestRep> swapContinuousCopies(URI id, CopiesParam input) {
        TaskList tasks = client.post(TaskList.class, input, getContinuousCopiesUrl() + "/swap", id);
        return new Tasks<VolumeRestRep>(client, tasks.getTaskList(), BlockMirrorRestRep.class);
    }

    /**
     * Lists the continuous copies for the given volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{id}/protection/continuous-copies</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @return the list of continuous copy references.
     */
    public List<NamedRelatedResourceRep> listContinuousCopies(URI id) {
        MirrorList response = client.get(MirrorList.class, getContinuousCopiesUrl(), id);
        return defaultList(response.getMirrorList());
    }

    /**
     * Gets the base URL for a single continuous copy by ID: <tt>/block/volumes/{id}/protection/continuous-copies/{copyId}</tt>
     *
     * @return the continuous copy URL.
     */
    protected String getContinuousCopiesIdUrl() {
        return getContinuousCopiesUrl() + "/{copyId}";
    }

    /**
     * Gets a continuous copy for the given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{id}/protection/continuous-copies/{copyId}</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param copyId
     *            the ID of the continuous copy.
     * @return the continuous copy.
     */
    public BlockMirrorRestRep getContinuousCopy(URI id, URI copyId) {
        return client.get(BlockMirrorRestRep.class, getContinuousCopiesIdUrl(), id, copyId);
    }

    /**
     * Gets the list of continuous copies for the given block volume.
     *
     * @param id
     *            the ID of the block volume.
     * @return the list of continuous copies.
     */
    public List<BlockMirrorRestRep> getContinuousCopies(URI id) {
        return getContinuousCopies(id, null);
    }

    /**
     * Gets the list of continuous copies for the given block volume, optionally filtering the results.
     *
     * @param id
     *            the ID of the block volume.
     * @param filter
     *            the resource filter to apply to the results as they are returned (optional).
     * @return the list of continuous copies.
     */
    public List<BlockMirrorRestRep> getContinuousCopies(URI id, ResourceFilter<BlockMirrorRestRep> filter) {
        List<BlockMirrorRestRep> continuousCopies = new ArrayList<>();
        for (NamedRelatedResourceRep ref : listContinuousCopies(id)) {
            if (acceptId(ref.getId(), filter)) {
                BlockMirrorRestRep continuousCopy = getContinuousCopy(id, ref.getId());
                if (accept(continuousCopy, filter)) {
                    continuousCopies.add(continuousCopy);
                }
            }
        }
        return continuousCopies;
    }

    /**
     * Begins deactivating a number of continuous copies for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/deactivate</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the copy configurations.
     *
     * @return tasks for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> deactivateContinuousCopies(URI id, CopiesParam input) {
        return deactivateContinuousCopies(id, input, VolumeDeleteTypeEnum.FULL);
    }

    /**
     * Begins deactivating a number of continuous copies for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/deactivate</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the copy configurations.
     * @param type
     *            {@code FULL} or {@code VIPR_ONLY}
     *
     * @return tasks for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> deactivateContinuousCopies(URI id, CopiesParam input, VolumeDeleteTypeEnum type) {
        URI uri = client.uriBuilder(getContinuousCopiesUrl() + "/deactivate").queryParam("type", type).build(id);
        TaskList tasks = client.postURI(TaskList.class, input, uri);
        return new Tasks<>(client, tasks.getTaskList(), resourceClass);
    }

    /**
     * Gets the base URL for full copies for a single block volume: <tt>/block/volumes/{id}/protection/full-copies</tt>
     *
     * @return the base full copy URL.
     */
    protected String getFullCopyUrl() {
        return getIdUrl() + "/protection/full-copies";
    }

    /**
     * List the full copies associated with the given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{id}/protection/full-copies</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @return the list of full copy IDs.
     */
    public List<NamedRelatedResourceRep> listFullCopies(URI id) {
        NamedVolumesList response = client.get(NamedVolumesList.class, getFullCopyUrl(), id);
        return defaultList(response.getVolumes());
    }

    /**
     * Gets the full copies associated with the given block volume. This is a convenience method for: <tt>getByRefs(listFullCopies(id))</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @return the full copy volumes.
     *
     * @see #listFullCopies(URI)
     * @see #getByRefs(Collection)
     */
    public List<VolumeRestRep> getFullCopies(URI id) {
        return getByRefs(listFullCopies(id));
    }

    /**
     * Gets the full copies associated with the given block volume, with filtering support. This is a convenience method
     * for: <tt>getByRefs(listFullCopies(id), filter)</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param filter
     *            the resource filter to apply (optional).
     * @return the full copy volumes.
     *
     * @see #listFullCopies(URI)
     * @see #getByRefs(Collection, ResourceFilter)
     */
    public List<VolumeRestRep> getFullCopies(URI id, ResourceFilter<VolumeRestRep> filter) {
        return getByRefs(listFullCopies(id), filter);
    }

    /**
     * Begins creating a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/full-copies</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the create configuration.
     * @return tasks for monitoring the progress of the operation(s).
     */
    public Tasks<VolumeRestRep> createFullCopy(URI id, VolumeFullCopyCreateParam input) {
        return postTasks(input, getFullCopyUrl(), id);
    }

    /**
     * Begins activating a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/full-copies/{copyId}/activate</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param copyId
     *            the ID of the full copy to activate.
     * @return a task for monitoring the progress of the operation.
     */
    @Deprecated
    public Task<VolumeRestRep> activateFullCopy(URI id, URI copyId) {
        return postTask(getFullCopyUrl() + "/{copyId}/activate", id, copyId);
    }

    /**
     * Begins detaching a full copy of the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/full-copies/{copyId}/detach</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param copyId
     *            the ID of the full copy to detach.
     * @return a task for monitoring the progress of the operation.
     */
    @Deprecated
    public Task<VolumeRestRep> detachFullCopy(URI id, URI copyId) {
        return postTask(getFullCopyUrl() + "/{copyId}/detach", id, copyId);
    }

    /**
     * Gets the full copy volume with an updated value of the progress of the operation. The progress can be retrieved
     * from: <tt>volume.getProtection().getFullCopy().getPercentSynced()</tt>
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/full-copies/{copyId}/check-progress</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param copyId
     *            the ID of the full copy.
     * @return the full copy volume.
     *
     * @see VolumeRestRep.FullCopyRestRep#getPercentSynced()
     */
    @Deprecated
    public VolumeRestRep checkFullCopyProgress(URI id, URI copyId) {
        return client.post(VolumeRestRep.class, getFullCopyUrl() + "/{copyId}/check-progress", id, copyId);
    }

    /**
     * Begins initiating failover for a given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/failover</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> failover(URI id, CopiesParam input) {
        return postTasks(input, getContinuousCopiesUrl() + "/failover", id);
    }

    /**
     * Begins initiating a failover test for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/failover-test</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     *
     * @deprecated failover-test is being replaced by failover.
     */
    @Deprecated
    public Tasks<VolumeRestRep> failoverTest(URI id, CopiesParam input) {
        return postTasks(input, getContinuousCopiesUrl() + "/failover-test", id);
    }

    /**
     * Begins canceling a previously initiated failover test for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/failover-test-cancel</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     *
     * @see #failoverTest(URI, CopiesParam)
     *
     * @deprecated failover-test-cancel needs to be replaced by failover-cancel.
     *             TO-DO: Add client support for failover-cancel.
     */
    @Deprecated
    public Tasks<VolumeRestRep> failoverTestCancel(URI id, CopiesParam input) {
        return postTasks(input, getContinuousCopiesUrl() + "/failover-test-cancel", id);
    }

    /**
     * Begins canceling a previously initiated failover for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/failover-cancel</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     *
     * @see #failoverTest(URI, CopiesParam)
     *
     */
    public Tasks<VolumeRestRep> failoverCancel(URI id, CopiesParam input) {
        return postTasks(input, getContinuousCopiesUrl() + "/failover-cancel", id);
    }

    /**
     * Begins initiating access mode update for a given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/accessmode</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> updateCopyAccessMode(URI id, CopiesParam input) {
        return postTasks(input, getContinuousCopiesUrl() + "/accessmode", id);
    }

    /**
     * Sync continuous copies.
     * <p>
     * API Call: <tt>POST /block/volumes/{id}/protection/continuous-copies/sync</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @param input
     *            the input configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> syncContinuousCopies(URI id, CopiesParam input) {
        return postTasks(input, getContinuousCopiesUrl() + "/sync", id);
    }

    /**
     * Gets a protection set for the given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{volumeId}/protection/protection-sets/{protectionSetId}</tt>
     *
     * @param volumeId
     *            the ID of the block volume.
     * @param protectionSetId
     *            the ID of the protection set.
     * @return the protection set.
     */
    public ProtectionSetRestRep getProtectionSet(URI volumeId, URI protectionSetId) {
        return client.get(ProtectionSetRestRep.class, getIdUrl() + "/protection/protection-sets/{protectionSetId}",
                volumeId, protectionSetId);
    }

    /**
     * Begin migrating a block volume.
     * <p>
     * API Call: <tt>POST /block/migrations</tt>
     *
     * @param input
     *            the migration configuration.
     * @return a task for monitoring the operation progress.
     * @deprecated Use the Change Virtual Pool API instead
     */
    @Deprecated
    public Task<VolumeRestRep> migrate(MigrationParam input) {
        return postTask(input, PathConstants.MIGRATION_URL);
    }

    /**
     * Lists migrations for the given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{id}/migrations</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @return the list of migration references.
     */
    public List<NamedRelatedResourceRep> listMigrations(URI id) {
        MigrationList response = client.get(MigrationList.class, getIdUrl() + "/migrations", id);
        return defaultList(response.getMigrations());
    }

    /**
     * Lists virtual pool change candidates for the given block volume.
     * <p>
     * API Call: <tt>GET /block/volumes/{id}/vpool-change/vpool</tt>
     *
     * @param id
     *            the ID of the block volume.
     * @return the list of virtual pool candidates.
     */
    public List<VirtualPoolChangeRep> listVirtualPoolChangeCandidates(URI id) {
        VirtualPoolChangeList response = client
                .get(VirtualPoolChangeList.class, getIdUrl() + "/vpool-change/vpool", id);
        return defaultList(response.getVirtualPools());
    }

    /**
     * Lists volumes in the given project that can potentially be moved to the given virtual array.
     *
     * @param projectId
     *            the ID of the project to search for potential virtual array change volumes
     * @param varrayId
     *            the ID of the virtual array to use as a target when searching
     * @return the list of volumes that are virtual array change candidates
     */
    public NamedVolumesList listVirtualArrayChangeCandidates(URI projectId, URI varrayId) {
        UriBuilder builder = client.uriBuilder(baseUrl).path("/varray-change");
        builder.queryParam("project", projectId);
        builder.queryParam("targetVarray", varrayId);
        return client.getURI(NamedVolumesList.class, builder.build());
    }

    /**
     * Changes the virtual pool for the given block volume.
     * <p>
     * API Call: <tt>POST /block/volumes/vpool-change</tt>
     *
     * @param input
     *            the virtual pool change configuration.
     * @return a task for monitoring the progress of the operation.
     */
    public Tasks<VolumeRestRep> changeVirtualPool(VolumeVirtualPoolChangeParam input) {
        return postTasks(input, baseUrl + "/vpool-change");
    }

    /**
     * Changes the virtual array for the given block volume.
     * <p>
     * API Call: <tt>PUT /block/volumes/{id}/varray</tt>
     *
     * @param id
     *            the id of the block volume.
     * @param input
     *            the virtual array change configuration.
     * @return a task for monitoring the progress of the operation.
     */
    @Deprecated
    public Task<VolumeRestRep> changeVirtualArray(URI id, VirtualArrayChangeParam input) {
        return putTask(input, getIdUrl() + "/varray", id);
    }

    /**
     * Changes the virtual array for the given block volumes.
     * <p>
     * API Call; <tt>POST /block/volumes/change-varray</tt>
     *
     * @param input
     *            the VolumeVirtualArrayChangeParam
     * @return
     *         a list of tasks for monitoring the progress of the Virtual Array Change operation
     */
    public Tasks<VolumeRestRep> changeVirtualArrayForVolumes(VolumeVirtualArrayChangeParam input) {
        return postTasks(input, baseUrl + "/varray-change");
    }

    /**
     * Creates a search builder specifically for creating volume search queries.
     *
     * @return a volume search builder.
     */
    @Override
    public VolumeSearchBuilder search() {
        return new VolumeSearchBuilder(this);
    }
}
