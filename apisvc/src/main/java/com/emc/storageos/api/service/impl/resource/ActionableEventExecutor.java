/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.ComputeSystemDialogProperties;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemControllerImpl;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ActionableEventExecutor {

    private DbClient _dbClient;
    private ComputeSystemController computeController;

    public ActionableEventExecutor(DbClient dbClient, ComputeSystemController computeController) {
        this._dbClient = dbClient;
        this.computeController = computeController;
    }

    /**
     * Get details for the hostClusterChange method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving clusters
     * @param clusterId the cluster the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @param vCenterDataCenterId the datacenter to assign the host to
     * @return list of event details
     */
    @SuppressWarnings("unused")            // Invoked using reflection for the event framework
    public List<String> hostClusterChangeDetails(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter) {
        List<String> result = Lists.newArrayList();
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host == null) {
            return Lists.newArrayList("Host has been deleted");
        }
        URI oldClusterURI = host.getCluster();

        Cluster oldCluster = null;
        if (!NullColumnValueGetter.isNullURI(oldClusterURI)) {
            oldCluster = _dbClient.queryObject(Cluster.class, oldClusterURI);
        }
        Cluster newCluster = null;
        if (!NullColumnValueGetter.isNullURI(clusterId)) {
            newCluster = _dbClient.queryObject(Cluster.class, clusterId);
        }

        if (newCluster != null && oldCluster != null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeDetails", host.getLabel(),
                    oldCluster.getLabel(), newCluster.getLabel()));
        } else if (newCluster == null && oldCluster != null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeDetailsRemovedFromCluster",
                    host.getLabel(),
                    oldCluster.getLabel()));
        } else if (newCluster != null && oldCluster == null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeDetailsAddedToCluster", host.getLabel(),
                    newCluster.getLabel()));
        }

        if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)) {
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, oldClusterURI);
            for (ExportGroup export : exportGroups) {
                if (export != null) {
                    List<BlockObjectDetails> affectedVolumes = getBlockObjectDetails(hostId, export.getVolumes());
                    result.addAll(getVolumeDetails(affectedVolumes, false));
                }
            }
        } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Non-clustered host being added to a cluster
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, clusterId);
            for (ExportGroup eg : exportGroups) {
                List<BlockObjectDetails> affectedVolumes = getBlockObjectDetails(hostId, eg.getVolumes());
                result.addAll(getVolumeDetails(affectedVolumes, true));
            }

        } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && !oldClusterURI.equals(clusterId)
                && (ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                        || ComputeSystemHelper.isClusterInExport(_dbClient, clusterId))) {
            // Clustered host being moved to another cluster
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, oldClusterURI);
            for (ExportGroup export : exportGroups) {
                if (export != null) {
                    List<BlockObjectDetails> affectedVolumes = getBlockObjectDetails(hostId, export.getVolumes());
                    result.addAll(getVolumeDetails(affectedVolumes, false));
                }
            }
            exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, clusterId);
            for (ExportGroup eg : exportGroups) {
                List<BlockObjectDetails> affectedVolumes = getBlockObjectDetails(hostId, eg.getVolumes());
                result.addAll(getVolumeDetails(affectedVolumes, true));
            }
        }

        return result;
    }

    /**
     * Method to move a host to a new cluster and update shared exports.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving clusters
     * @param clusterId the cluster the host is moving to
     * @param vCenterDataCenterId the vcenter datacenter id to set
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @param eventId the event id
     * @return task for updating export groups
     */
    public TaskResourceRep hostClusterChange(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter, URI eventId) {
        Host hostObj = _dbClient.queryObject(Host.class, hostId);
        URI oldClusterURI = hostObj.getCluster();
        String taskId = UUID.randomUUID().toString();

        Operation op = _dbClient.createTaskOpStatus(Host.class, hostId, taskId,
                ResourceOperationTypeEnum.UPDATE_HOST);

        if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)) {
            // Remove host from shared export
            computeController.removeHostsFromExport(eventId, Arrays.asList(hostId), oldClusterURI, isVcenter, vCenterDataCenterId, taskId);
        } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Non-clustered host being added to a cluster
            computeController.addHostsToExport(eventId, Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
        } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && !oldClusterURI.equals(clusterId)
                && (ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                        || ComputeSystemHelper.isClusterInExport(_dbClient, clusterId))) {
            // Clustered host being moved to another cluster
            computeController.addHostsToExport(eventId, Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
        } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && oldClusterURI.equals(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Cluster hasn't changed but we should add host to the shared exports in case they weren't added to all of them
            computeController.addHostsToExport(eventId, Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
        } else {
            ComputeSystemHelper.updateHostAndInitiatorClusterReferences(_dbClient, clusterId, hostId);
            ComputeSystemHelper.updateHostVcenterDatacenterReference(_dbClient, hostId, vCenterDataCenterId);
            _dbClient.ready(Host.class, hostId, taskId);
        }

        return toTask(hostObj, taskId, op);
    }

    /**
     * Get details for the addInitiator method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the id if the initiator to add
     * @return list of event details
     */
    @SuppressWarnings("unused")           // Invoked using reflection for the event framework
    public List<String> addInitiatorDetails(URI initiatorId) {
        List<String> result = Lists.newArrayList();
        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
        if (initiator != null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.addInitiatorDetails",
                    initiator.getInitiatorPort()));
            List<ExportGroup> exportGroups = ComputeSystemHelper.findExportsByHost(_dbClient, initiator.getHost().toString());

            for (ExportGroup export : exportGroups) {
                List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());

                List<Initiator> validInitiator = ComputeSystemHelper.validatePortConnectivity(_dbClient, export,
                        Lists.newArrayList(initiator));
                if (!validInitiator.isEmpty()) {
                    boolean update = false;
                    for (Initiator initiatorObj : validInitiator) {
                        // if the initiators is not already in the list add it.
                        if (!updatedInitiators.contains(initiator.getId())) {
                            updatedInitiators.add(initiator.getId());
                            update = true;
                        }
                    }

                    if (update) {
                        List<BlockObjectDetails> volumeDetails = getBlockObjectDetails(initiator.getHost(), export.getVolumes());
                        result.addAll(getVolumeInitiatorDetails(volumeDetails, true));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Method to add an initiator to existing exports for a host.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the initiator to add
     * @param eventId the event id
     * @return task for adding an initiator
     */
    public TaskResourceRep addInitiator(URI initiatorId, URI eventId) {
        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
        Host host = _dbClient.queryObject(Host.class, initiator.getHost());

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiatorId, taskId,
                ResourceOperationTypeEnum.ADD_HOST_INITIATOR);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())) {
            computeController.addInitiatorsToExport(eventId, initiator.getHost(), Arrays.asList(initiator.getId()), taskId);
        } else {
            // No updates were necessary, so we can close out the task.
            _dbClient.ready(Initiator.class, initiator.getId(), taskId);
        }

        return toTask(initiator, taskId, op);
    }

    /**
     * Get details for the removeInitiator method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the id if the initiator to remove
     * @return list of event details
     */
    @SuppressWarnings("unused")           // Invoked using reflection for the event framework
    public List<String> removeInitiatorDetails(URI initiatorId) {
        List<String> result = Lists.newArrayList();

        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
        if (initiator != null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.removeInitiatorDetails",
                    initiator.getInitiatorPort()));
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getExportGroups(_dbClient, initiator.getId(),
                    Lists.newArrayList(initiator));

            for (ExportGroup export : exportGroups) {
                List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
                // Only update if the list as changed
                if (updatedInitiators.remove(initiatorId)) {
                    List<BlockObjectDetails> volumeDetails = getBlockObjectDetails(initiator.getHost(), export.getVolumes());
                    result.addAll(getVolumeInitiatorDetails(volumeDetails, false));
                }
            }
        }

        return result;
    }

    /**
     * Method to remove an initiator from existing exports for a host.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiatorId the initiator to remove
     * @param eventId the event id
     * @return task for removing an initiator
     */
    public TaskResourceRep removeInitiator(URI initiatorId, URI eventId) {
        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiator.getId(), taskId,
                ResourceOperationTypeEnum.DELETE_INITIATOR);

        if (ComputeSystemHelper.isInitiatorInUse(_dbClient, initiatorId.toString())) {
            computeController.removeInitiatorFromExport(eventId, initiator.getHost(), initiator.getId(), taskId);
        } else {
            _dbClient.ready(Initiator.class, initiator.getId(), taskId);
            _dbClient.markForDeletion(initiator);
        }

        return toTask(initiator, taskId, op);
    }

    /**
     * Unassign host from a vCenter
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host to unassign
     * @param eventId the event id
     * @return task for updating host
     */
    public TaskResourceRep hostVcenterUnassign(URI hostId, URI eventId) {
        return hostClusterChange(hostId, NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullURI(), true, eventId);
    }

    /**
     * Get details for the hostVcenterUnassign method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host id to unassign from vCenter
     * @return list of event details
     */
    @SuppressWarnings("unused")           // Invoked using reflection for the event framework
    public List<String> hostVcenterUnassignDetails(URI hostId) {
        List<String> result = Lists.newArrayList();
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (host != null) {
            Vcenter vcenter = ComputeSystemHelper.getHostVcenter(_dbClient, host);
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterUnassignDetails", host.getLabel(),
                    vcenter == null ? "N/A" : vcenter.getLabel()));
            result.addAll(hostClusterChangeDetails(hostId, NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullURI(), true));
        }
        return result;
    }

    /**
     * Get details for the hostDatacenterChange method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving datacenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return list of event details
     */
    @SuppressWarnings("unused")           // Invoked using reflection for the event framework
    public List<String> hostDatacenterChangeDetails(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        List<String> result = Lists.newArrayList();
        Host host = _dbClient.queryObject(Host.class, hostId);
        VcenterDataCenter datacenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterId);
        if (host != null && datacenter != null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostDatacenterChangeDetails", host.getLabel(),
                    datacenter.getLabel()));
            result.addAll(hostClusterChangeDetails(hostId, clusterId, datacenterId, isVcenter));
        }
        return result;
    }

    /**
     * Method to move a host to a new datacenter and update shared exports.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving datacenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @param eventId the event id
     * @return task for updating export groups
     */

    public TaskResourceRep hostDatacenterChange(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter, URI eventId) {
        return hostClusterChange(hostId, clusterId, datacenterId, isVcenter, eventId);
    }

    /**
     * Get details for the hostVcenterChange method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving vcenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @return list of event details
     */
    @SuppressWarnings("unused")           // Invoked using reflection for the event framework
    public List<String> hostVcenterChangeDetails(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        List<String> result = Lists.newArrayList();
        Host host = _dbClient.queryObject(Host.class, hostId);
        VcenterDataCenter datacenter = _dbClient.queryObject(VcenterDataCenter.class, datacenterId);
        if (host != null && datacenter != null) {
            result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterChangeDetails", host.getLabel(),
                    datacenter.getLabel()));
            result.addAll(hostClusterChangeDetails(hostId, clusterId, datacenterId, isVcenter));
        }
        return result;
    }

    /**
     * Method to move a host to a new vcenter and update shared exports.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moving vcenters
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, vcenter api operations will be executed against the host to detach/unmount and attach/mount disks and
     *            datastores
     * @param eventId the event id
     * @return task for updating export groups
     */

    public TaskResourceRep hostVcenterChange(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter, URI eventId) {
        return hostClusterChange(hostId, clusterId, datacenterId, isVcenter, eventId);
    }

    private List<BlockObjectDetails> getBlockObjectDetails(URI hostId, StringMap volumes) {
        List<BlockObjectDetails> result = Lists.newArrayList();
        Set<String> hostVolumes = Sets.newHashSet();

        if (volumes != null) {
            for (Entry<String, String> volume : volumes.entrySet()) {
                // if host has access to volume in an exclusive export, skip it from the list of changes
                if (hostVolumes.contains(volume.getKey())) {
                    continue;
                }
                URI project = null;
                String volumeName = null;
                URI blockURI = URI.create(volume.getKey());
                if (URIUtil.isType(blockURI, Volume.class)) {
                    Volume block = _dbClient.queryObject(Volume.class, blockURI);
                    project = block.getProject().getURI();
                    volumeName = block.getLabel();
                } else if (URIUtil.isType(blockURI, BlockSnapshot.class)) {
                    BlockSnapshot block = _dbClient.queryObject(BlockSnapshot.class, blockURI);
                    project = block.getProject().getURI();
                    volumeName = block.getLabel();
                } else if (URIUtil.isType(blockURI, BlockMirror.class)) {
                    BlockMirror block = _dbClient.queryObject(BlockMirror.class, blockURI);
                    project = block.getProject().getURI();
                    volumeName = block.getLabel();
                }

                Project projectObj = _dbClient.queryObject(Project.class, project);
                String projectName = null;
                if (projectObj != null) {
                    projectName = projectObj.getLabel();
                }

                result.add(new BlockObjectDetails(blockURI, projectName, volumeName));
            }
        }
        return result;
    }

    /**
     * Creates human readable output when an initiator is added or removed
     * 
     * @param affectedVolumes the list of affected volumes
     * @param addPath if true, initiator is being added, else the initiator is being removed
     * @return list of volume details
     */
    private List<String> getVolumeInitiatorDetails(List<BlockObjectDetails> affectedVolumes, boolean addPath) {
        List<String> result = Lists.newArrayList();
        for (BlockObjectDetails details : affectedVolumes) {
            String projectName = details.getProjectName();
            String volumeName = details.getVolumeName();
            URI blockURI = details.getBlockURI();
            if (addPath) {
                result.add(
                        ComputeSystemDialogProperties.getMessage("ComputeSystem.hostPathAdded",
                                (volumeName == null ? "N/A" : volumeName), (projectName == null ? "N/A" : projectName), blockURI));
            } else {
                result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostPathRemoved",
                        (volumeName == null ? "N/A" : volumeName), (projectName == null ? "N/A" : projectName), blockURI));
            }
        }
        return result;
    }

    /**
     * Creates human readable output when access to a volume is affected
     * 
     * @param affectedVolumes the list of affected volumes
     * @param gainAccess if true, host is gaining access to a volume, else access is being removed
     * @return list of volume details
     */
    private List<String> getVolumeDetails(List<BlockObjectDetails> affectedVolumes, boolean gainAccess) {
        List<String> result = Lists.newArrayList();
        for (BlockObjectDetails details : affectedVolumes) {
            String projectName = details.getProjectName();
            String volumeName = details.getVolumeName();
            URI blockURI = details.getBlockURI();
            if (gainAccess) {
                result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostGainAccess",
                        (volumeName == null ? "N/A" : volumeName), (projectName == null ? "N/A" : projectName),
                        blockURI));
            } else {
                result.add(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostLoseAccess",
                        (volumeName == null ? "N/A" : volumeName), (projectName == null ? "N/A" : projectName),
                        blockURI));
            }
        }
        return result;
    }

    /**
     * Decline method that is invoked when the hostVcenterUnassign event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param host the host that is unassigned from vCenter
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep hostVcenterUnassignDecline(URI host, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for hostVcenterUnassign
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is unassigned from vCenter
     * @return list of details
     */
    public List<String> hostVcenterUnassignDeclineDetails(URI hostId) {
        Host host = _dbClient.queryObject(Host.class, hostId);
        return Lists
                .newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterUnassignDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the hostVcenterChange event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different vCenter
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep hostVcenterChangeDecline(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for hostVcenterChange
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different vCenter
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @return list of details
     */
    public List<String> hostVcenterChangeDeclineDetails(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        Host host = _dbClient.queryObject(Host.class, hostId);
        return Lists
                .newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterChangeDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the hostDatacenterChange event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different datacenter
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep hostDatacenterChangeDecline(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for hostDatacenterChange
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different datacenter
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @return list of details
     */
    public List<String> hostDatacenterChangeDeclineDetails(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        Host host = _dbClient.queryObject(Host.class, hostId);
        return Lists.newArrayList(
                ComputeSystemDialogProperties.getMessage("ComputeSystem.hostDatacenterChangeDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the hostClusterChange event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different cluster
     * @param clusterId the cluster the host is moving to
     * @param vCenterDataCenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep hostClusterChangeDecline(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for hostClusterChange
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different cluster
     * @param clusterId the cluster the host is moving to
     * @param vCenterDataCenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @return list of details
     */
    public List<String> hostClusterChangeDeclineDetails(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter) {
        Host host = _dbClient.queryObject(Host.class, hostId);
        if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
            return Lists
                    .newArrayList(
                            ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeDeclineDetails", host.getLabel()));
        } else {
            return Lists
                    .newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostNotInClusterChangeDeclineDetails",
                            host.getLabel()));

        }
    }

    /**
     * Decline method that is invoked when the removeInitiator event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiator the initiator to remove
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep removeInitiatorDecline(URI initiator, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for removeInitiator
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiator the initiator to remove
     * @return list of details
     */
    public List<String> removeInitiatorDeclineDetails(URI initiator) {
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.removeInitiatorDeclineDetails"));

    }

    /**
     * Decline method that is invoked when the addInitiator event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiator the initiator to add
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep addInitiatorDecline(URI initiator, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for addInitiator
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiator the initiator to add
     * @return list of details
     */
    public List<String> addInitiatorDeclineDetails(URI initiator) {
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.addInitiatorDeclineDetails"));
    }

    /**
     * Method to update initiators of existing exports for a host.
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host id
     * @param newInitiators the initiators to add
     * @param oldInitiators the initiators to remove
     * @param eventId the event id
     * @return task for adding an initiator
     */
    public TaskResourceRep updateInitiators(URI hostId, List<URI> newInitiators, List<URI> oldInitiators, URI eventId) {
        Host host = _dbClient.queryObject(Host.class, hostId);
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Host.class, host.getId(), taskId,
                ResourceOperationTypeEnum.UPDATE_HOST_INITIATORS);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())) {
            computeController.updateHostInitiators(eventId, hostId, newInitiators, oldInitiators, taskId);
        } else {
            // No updates were necessary, so we can close out the task.
            _dbClient.ready(Host.class, host.getId(), taskId);
        }
        return toTask(host, taskId, op);
    }

    /**
     * Get details for the updateInitiators method
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param host the host
     * @param newInitiators the initiators to add
     * @param oldInitiators the initiators to remove
     * @return list of event details
     */
    public List<String> updateInitiatorsDetails(URI host, List<URI> newInitiators, List<URI> oldInitiators) {
        List<String> result = Lists.newArrayList();
        for (URI initiatorId : newInitiators) {
            result.addAll(addInitiatorDetails(initiatorId));
        }
        for (URI initiatorId : oldInitiators) {
            result.addAll(removeInitiatorDetails(initiatorId));
        }
        return result;
    }

    /**
     * Decline method that is invoked when the updateInitiators event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param host the host
     * @param newInitiators the initiators to add
     * @param oldInitiators the initiators to remove
     * @param eventId the event id
     * @return task
     */
    public TaskResourceRep updateInitiatorsDecline(URI host, List<URI> newInitiators, List<URI> oldInitiators, URI eventId) {
        return null;
    }

    /**
     * Get details for a decline event for updateInitiators
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param host the host
     * @param newInitiators the initiators to add
     * @param oldInitiators the initiators to remove
     * @return list of details
     */
    public List<String> updateInitiatorsDeclineDetails(URI host, List<URI> newInitiators, List<URI> oldInitiators) {
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.updateInitiatorsDeclineDetails"));
    }

    /**
     * Inner class to hold details for a block object that are used to display actionable event details
     *
     */
    class BlockObjectDetails {
        private URI blockURI;
        private String projectName;
        private String volumeName;

        public BlockObjectDetails(URI blockURI, String projectName, String volumeName) {
            this.blockURI = blockURI;
            this.projectName = projectName;
            this.volumeName = volumeName;
        }

        public URI getBlockURI() {
            return this.blockURI;
        }

        public String getProjectName() {
            return this.projectName;
        }

        public String getVolumeName() {
            return this.volumeName;
        }
    }

}
