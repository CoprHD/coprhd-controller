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
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringMap;
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

        if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)) {
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, oldClusterURI);
            for (ExportGroup export : exportGroups) {
                if (export != null) {
                    result.addAll(getVolumes(hostId, export.getVolumes(), false));
                }
            }
        } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Non-clustered host being added to a cluster
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, clusterId);
            for (ExportGroup eg : exportGroups) {
                result.addAll(getVolumes(hostId, eg.getVolumes(), true));
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
                    result.addAll(getVolumes(hostId, export.getVolumes(), false));
                }
            }
            exportGroups = ComputeSystemControllerImpl.getSharedExports(_dbClient, clusterId);
            for (ExportGroup eg : exportGroups) {
                result.addAll(getVolumes(hostId, eg.getVolumes(), true));
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
     * @return task for updating export groups
     */
    public TaskResourceRep hostClusterChange(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter) {
        Host hostObj = _dbClient.queryObject(Host.class, hostId);
        URI oldClusterURI = hostObj.getCluster();
        String taskId = UUID.randomUUID().toString();

        Operation op = _dbClient.createTaskOpStatus(Host.class, hostId, taskId,
                ResourceOperationTypeEnum.UPDATE_HOST);

        if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)) {
            // Remove host from shared export
            computeController.removeHostsFromExport(Arrays.asList(hostId), oldClusterURI, isVcenter, vCenterDataCenterId, taskId);
        } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && ComputeSystemHelper.isClusterInExport(_dbClient, clusterId)) {
            // Non-clustered host being added to a cluster
            computeController.addHostsToExport(Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
        } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                && !NullColumnValueGetter.isNullURI(clusterId)
                && !oldClusterURI.equals(clusterId)
                && (ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                        || ComputeSystemHelper.isClusterInExport(_dbClient, clusterId))) {
            // Clustered host being moved to another cluster
            computeController.addHostsToExport(Arrays.asList(hostId), clusterId, taskId, oldClusterURI, isVcenter);
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
                        result.addAll(getVolumes(initiator.getHost(), export.getVolumes(), true));
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
     * @return task for adding an initiator
     */
    public TaskResourceRep addInitiator(URI initiatorId) {
        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);
        Host host = _dbClient.queryObject(Host.class, initiator.getHost());

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiatorId, taskId,
                ResourceOperationTypeEnum.ADD_HOST_INITIATOR);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())) {
            computeController.addInitiatorsToExport(initiator.getHost(), Arrays.asList(initiator.getId()), taskId);
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
            List<ExportGroup> exportGroups = ComputeSystemControllerImpl.getExportGroups(_dbClient, initiator.getId(),
                    Lists.newArrayList(initiator));

            for (ExportGroup export : exportGroups) {
                List<URI> updatedInitiators = StringSetUtil.stringSetToUriList(export.getInitiators());
                // Only update if the list as changed
                if (updatedInitiators.remove(initiatorId)) {
                    result.addAll(getVolumes(initiator.getHost(), export.getVolumes(), false));
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
     * @return task for removing an initiator
     */
    public TaskResourceRep removeInitiator(URI initiatorId) {
        Initiator initiator = _dbClient.queryObject(Initiator.class, initiatorId);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiator.getId(), taskId,
                ResourceOperationTypeEnum.DELETE_INITIATOR);

        if (ComputeSystemHelper.isInitiatorInUse(_dbClient, initiatorId.toString())) {
            computeController.removeInitiatorFromExport(initiator.getHost(), initiator.getId(), taskId);
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
     * @return task for updating host
     */
    public TaskResourceRep hostVcenterUnassign(URI hostId) {
        return hostClusterChange(hostId, NullColumnValueGetter.getNullURI(), NullColumnValueGetter.getNullURI(), true);
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
     * @return task for updating export groups
     */

    public TaskResourceRep hostDatacenterChange(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        return hostClusterChange(hostId, clusterId, datacenterId, isVcenter);
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
     * @return task for updating export groups
     */

    public TaskResourceRep hostVcenterChange(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
        return hostClusterChange(hostId, clusterId, datacenterId, isVcenter);
    }
    
    private Set<String> getHostVolumes(URI hostId) {
        List<Initiator> hostInitiators = ComputeSystemHelper.queryInitiators(_dbClient, hostId);
        Set<String> volumeIds = Sets.newHashSet();
        for (ExportGroup export : ComputeSystemControllerImpl.getExportGroups(_dbClient, hostId, hostInitiators)) {
            volumeIds.addAll(export.getVolumes().keySet());
        }
        return volumeIds;
    }

    private List<String> getVolumes(URI hostId, StringMap volumes, boolean gainAccess) {
        List<String> result = Lists.newArrayList();
        Set<String> hostVolumes = Sets.newHashSet();

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

            result.add("Host will" + (gainAccess ? " gain " : " lose ") + "access to volume: Project "
                    + (projectName == null ? "N/A" : projectName) + " " + (volumeName == null ? "N/A" : volumeName)
                    + " ID: " + blockURI);
        }
        return result;
    }

    /**
     * Decline method that is invoked when the hostVcenterUnassign event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param host the host that is unassigned from vCenter
     * @return task
     */
    public TaskResourceRep hostVcenterUnassignDecline(URI host) {
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
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterUnassignDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the hostVcenterChange event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different vCenter
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @return task
     */
    public TaskResourceRep hostVcenterChangeDecline(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
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
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostVcenterChangeDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the hostDatacenterChange event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different datacenter
     * @param clusterId the cluster the host is moving to
     * @param datacenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @return task
     */
    public TaskResourceRep hostDatacenterChangeDecline(URI hostId, URI clusterId, URI datacenterId, boolean isVcenter) {
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
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostDatacenterChangeDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the hostClusterChange event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param hostId the host that is moved to a different cluster
     * @param clusterId the cluster the host is moving to
     * @param vCenterDataCenterId the datacenter the host is moving to
     * @param isVcenter if true, will perform vCenter operations
     * @return task
     */
    public TaskResourceRep hostClusterChangeDecline(URI hostId, URI clusterId, URI vCenterDataCenterId, boolean isVcenter) {
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
        return Lists.newArrayList(ComputeSystemDialogProperties.getMessage("ComputeSystem.hostClusterChangeDeclineDetails", host.getLabel()));
    }

    /**
     * Decline method that is invoked when the removeInitiator event is declined
     * NOTE: In order to maintain backwards compatibility, do not change the signature of this method.
     * 
     * @param initiator the initiator to remove
     * @return task
     */
    public TaskResourceRep removeInitiatorDecline(URI initiator) {
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
     * @return task
     */
    public TaskResourceRep addInitiatorDecline(URI initiator) {
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

}
