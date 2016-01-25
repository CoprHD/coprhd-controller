/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.google.common.collect.Sets;

/**
 * APIs to view, create, modify and remove volume groups
 */

@Path("/volume-groups/block")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class VolumeGroupService extends TaskResourceService {
    private static final String VOLUME_GROUP_NAME = "name";
    private static final String VOLUME_GROUP_ROLES = "roles";
    private static final String EVENT_SERVICE_TYPE = "application";
    private static final Set<String> ALLOWED_SYSTEM_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vplex.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.rp.name(),
            DiscoveredDataObject.Type.srdf.name(),
            DiscoveredDataObject.Type.ibmxiv.name()));
    private static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.ibmxiv.name(),
            DiscoveredDataObject.Type.srdf.name()));
    private static final String BLOCK = "block";

    static final Logger log = LoggerFactory.getLogger(VolumeGroupService.class);

    // Block service implementations
    private static Map<String, BlockServiceApi> _blockServiceApis;

    public void setBlockServiceApis(final Map<String, BlockServiceApi> serviceInterfaces) {
        _blockServiceApis = serviceInterfaces;
    }

    private static BlockServiceApi getBlockServiceImpl(final String type) {
        return _blockServiceApis.get(type);
    }

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkUri(id);
        VolumeGroup volumeGroup = _permissionsHelper.getObjectById(id, VolumeGroup.class);
        ArgValidator.checkEntityNotNull(volumeGroup, id, isIdEmbeddedInURL(id));
        return volumeGroup;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VOLUME_GROUP;
    }

    @Override
    protected URI getTenantOwner(final URI id) {
        return null;
    }

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    /**
     * Create a volume group
     * 
     * @param param Parameters for creating a volume group
     * @return created volume group
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VolumeGroupRestRep createVolumeGroup(VolumeGroupCreateParam param) {
        ArgValidator.checkFieldNotEmpty(param.getName(), VOLUME_GROUP_NAME);
        checkDuplicateLabel(VolumeGroup.class, param.getName(), "Volume Group");
        Set<String> roles = param.getRoles();
        ArgValidator.checkFieldNotEmpty(roles, VOLUME_GROUP_ROLES);
        for (String role : roles) {
            ArgValidator.checkFieldValueFromEnum(role, VOLUME_GROUP_ROLES, VolumeGroup.VolumeGroupRole.class);
        }
        VolumeGroup volumeGroup = new VolumeGroup();
        volumeGroup.setId(URIUtil.createId(VolumeGroup.class));
        volumeGroup.setLabel(param.getName());
        volumeGroup.setDescription(param.getDescription());
        volumeGroup.addRoles(param.getRoles());

        // add parent if specified
        String msg = setParent(volumeGroup, param.getParent(), true);
        if (msg != null && !msg.isEmpty()) {
            throw APIException.badRequests.volumeGroupCantBeCreated(volumeGroup.getLabel(), msg);
        }

        // TODO make sure properties (migration type, etc) are set when creating mobility group
        if (param.getRoles().contains(VolumeGroup.VolumeGroupRole.MOBILITY.name())) {
            volumeGroup.setMigrationType(param.getMigrationType());
            volumeGroup.setMigrationGroupBy(param.getMigrationGroupBy());
            volumeGroup.setSourceStorageSystem(param.getSourceStorageSystem());
            volumeGroup.setSourceVirtualPool(param.getSourceVirtualPool());
        }

        _dbClient.createObject(volumeGroup);
        auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP, true, null, volumeGroup.getId().toString(),
                volumeGroup.getLabel());
        return DbObjectMapper.map(volumeGroup);
    }

    /**
     * List a volume group
     * 
     * @param id volume group Id
     * @return ApplicationRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public VolumeGroupRestRep getVolumeGroup(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        return DbObjectMapper.map(volumeGroup);
    }

    /**
     * List volume groups.
     * 
     * @return A reference to VolumeGroupList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VolumeGroupList getVolumeGroups() {
        VolumeGroupList volumeGroupList = new VolumeGroupList();

        List<URI> ids = _dbClient.queryByType(VolumeGroup.class, true);
        Iterator<VolumeGroup> iter = _dbClient.queryIterativeObjects(VolumeGroup.class, ids);
        while (iter.hasNext()) {
            volumeGroupList.getVolumeGroupss().add(toNamedRelatedResource(iter.next()));
        }
        return volumeGroupList;
    }

    /**
     * Get application volumes
     * 
     * @param id Application Id
     * @return NamedVolumesList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/volumes")
    public NamedVolumesList getVolumes(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, id);
        NamedVolumesList result = new NamedVolumesList();
        List<Volume> volumes = getVolumeGroupVolumes(_dbClient, volumeGroup);
        for (Volume volume : volumes) {
            result.getVolumes().add(toNamedRelatedResource(volume));
        }
        return result;
    }

    /**
     * Get application hosts
     * 
     * @param id Application Id
     * @return HostList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/hosts")
    public HostList getHosts(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, id);
        HostList result = new HostList();
        List<Host> hosts = getVolumeGroupHosts(_dbClient, volumeGroup);
        for (Host host : hosts) {
            result.getHosts().add(toNamedRelatedResource(host));
        }
        return result;
    }

    /**
     * Get application clusters
     * 
     * @param id Application Id
     * @return ClusterList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/clusters")
    public ClusterList getClusters(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, id);
        ClusterList result = new ClusterList();
        List<Cluster> clusters = getVolumeGroupClusters(_dbClient, volumeGroup);
        for (Cluster cluster : clusters) {
            result.getClusters().add(toNamedRelatedResource(cluster));
        }
        return result;
    }

    /**
     * Delete the volume group.
     * When a volume group is deleted it will move to a "marked for deletion" state.
     * 
     * @param id the URN of the volume group
     * @brief Deactivate application
     * @return No data returned in response body
     */
    @POST
    @Path("/{id}/deactivate")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public Response deactivateVolumeGroup(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        ArgValidator.checkReference(VolumeGroup.class, id, checkForDelete(volumeGroup));

        if (!getVolumeGroupVolumes(_dbClient, volumeGroup).isEmpty()) {
            // application could not be deleted if it has volumes
            throw APIException.badRequests.volumeGroupWithVolumesCantBeDeleted(volumeGroup.getLabel());
        }

        if (!getVolumeGroupHosts(_dbClient, volumeGroup).isEmpty()) {
            // application could not be deleted if it has hosts
            throw APIException.badRequests.volumeGroupWithHostsCantBeDeleted(volumeGroup.getLabel());
        }

        if (!getVolumeGroupClusters(_dbClient, volumeGroup).isEmpty()) {
            // application could not be deleted if it has clusters
            throw APIException.badRequests.volumeGroupWithClustersCantBeDeleted(volumeGroup.getLabel());
        }

        _dbClient.markForDeletion(volumeGroup);

        auditOp(OperationTypeEnum.DELETE_CONFIG, true, null, id.toString(),
                volumeGroup.getLabel());
        return Response.ok().build();
    }

    /**
     * update a volume group
     * 
     * @param id volume group id
     * @param param volume group update parameters
     * @return
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public TaskList updateVolumeGroup(@PathParam("id") final URI id,
            final VolumeGroupUpdateParam param) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        if (volumeGroup.getInactive()) {
            throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(), "The Volume Group has been deleted");
        }
        checkForApplicationPendingTasks(volumeGroup);
        boolean isChanged = false;
        String vgName = param.getName();
        if (vgName != null && !vgName.isEmpty() && !vgName.equalsIgnoreCase(volumeGroup.getLabel())) {
            checkDuplicateLabel(VolumeGroup.class, vgName, "Volume Group");
            volumeGroup.setLabel(vgName);
            isChanged = true;
        }
        String description = param.getDescription();
        if (description != null && !description.isEmpty()) {
            volumeGroup.setDescription(description);
            isChanged = true;
        }

        List<URI> addParents = param.getAddParents();
        for (URI addParent : addParents) {
            String msg = setParent(volumeGroup, addParent.toString(), true);
            if (msg != null && !msg.isEmpty()) {
                throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(), msg);
            }
            isChanged = true;
        }

        List<URI> removeParents = param.getRemoveParents();
        for (URI removeParent : removeParents) {
            String msg = setParent(volumeGroup, removeParent.toString(), false);
            if (msg != null && !msg.isEmpty()) {
                throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(), msg);
            }
            isChanged = true;
        }

        if (isChanged) {
            _dbClient.updateObject(volumeGroup);
        }
        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = null;
        if (!param.hasEitherAddOrRemoveVolumes() && !param.hasEitherAddOrRemoveHosts() && !param.hasEitherAddOrRemoveClusters()) {
            op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
            op.ready();
            volumeGroup.getOpStatus().createTaskStatus(taskId, op);
            _dbClient.updateObject(volumeGroup);
            TaskResourceRep task = toTask(volumeGroup, taskId, op);
            taskList.getTaskList().add(task);
            return taskList;
        }

        List<VolumeGroupUtils> utils = getVolumeGroupUtils(volumeGroup);
        for (VolumeGroupUtils util : utils) {
            util.validateUpdateVolumesInVolumeGroup(_dbClient, param, volumeGroup);
        }
        for (VolumeGroupUtils util : utils) {
            util.updateVolumesInVolumeGroup(_dbClient, param, volumeGroup, taskId, taskList);
        }
        auditOp(OperationTypeEnum.UPDATE_VOLUME_GROUP, true, null, volumeGroup.getId().toString(),
                volumeGroup.getLabel());
        return taskList;
    }

    private List<VolumeGroupUtils> getVolumeGroupUtils(VolumeGroup volumeGroup) {
        List<VolumeGroupUtils> utilsList = new ArrayList<VolumeGroupUtils>();

        if (volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())) {
            utilsList.add(new CopyVolumeGroupUtils());
        }
        if (volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.MOBILITY.toString())) {
            utilsList.add(new MobilityVolumeGroupUtils());
        }
        if (volumeGroup.getRoles().contains(VolumeGroup.VolumeGroupRole.DR.toString())) {
            utilsList.add(new DRVolumeGroupUtils());
        }

        return utilsList;
    }

    private static abstract class VolumeGroupUtils {
        /**
         * @param param
         * @param volumeGroup
         * @param taskId
         * @param taskList
         * @return
         */
        public abstract void updateVolumesInVolumeGroup(DbClient dbClient, final VolumeGroupUpdateParam param, VolumeGroup volumeGroup,
                String taskId, TaskList taskList);

        /**
         * @param dbClient
         * @param param
         * @param volumeGroup
         * @param taskId
         * @param taskList
         */
        public abstract void validateUpdateVolumesInVolumeGroup(DbClient dbClient, final VolumeGroupUpdateParam param,
                VolumeGroup volumeGroup);

        protected void updateHostObjects(DbClient dbClient, List<Host> addHosts, List<Host> removeHosts, VolumeGroup volumeGroup) {
            for (Host addHost : addHosts) {
                addHost.getVolumeGroupIds().add(volumeGroup.getId().toString());
            }
            for (Host remHost : removeHosts) {
                remHost.getVolumeGroupIds().remove(volumeGroup.getId().toString());
            }
            dbClient.updateObject(addHosts);
            dbClient.updateObject(removeHosts);
        }

        protected void updateClusterObjects(DbClient dbClient, List<Cluster> addClusters, List<Cluster> removeClusters,
                VolumeGroup volumeGroup) {
            for (Cluster addCluster : addClusters) {
                addCluster.getVolumeGroupIds().add(volumeGroup.getId().toString());
            }
            for (Cluster remCluster : removeClusters) {
                remCluster.getVolumeGroupIds().remove(volumeGroup.getId().toString());
            }
            dbClient.updateObject(addClusters);
            dbClient.updateObject(removeClusters);
        }

        protected void updateVolumeObjects(DbClient dbClient, List<Volume> addVols, List<Volume> removeVols, VolumeGroup volumeGroup) {
            for (Volume addVol : addVols) {
                addVol.getVolumeGroupIds().add(volumeGroup.getId().toString());
            }
            for (Volume remVol : removeVols) {
                remVol.getVolumeGroupIds().remove(volumeGroup.getId().toString());
            }
            dbClient.updateObject(addVols);
            dbClient.updateObject(removeVols);
        }

        /**
         * Add task for volumes and consistency groups
         * 
         * @param addVols
         * @param removeVols
         * @param removeVolumeCGs
         * @param taskId
         * @param taskList
         */
        protected void addTasksForVolumesAndCGs(DbClient dbClient, List<Volume> addVols, List<Volume> removeVols, Set<URI> removeVolumeCGs,
                String taskId, TaskList taskList) {
            if (addVols != null && !addVols.isEmpty()) {
                for (Volume vol : addVols) {
                    addVolumeTask(dbClient, vol, taskList, taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
                }
            }
            if (removeVols != null && !removeVols.isEmpty()) {
                for (Volume vol : removeVols) {
                    addVolumeTask(dbClient, vol, taskList, taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
                }
            }

            if (removeVolumeCGs != null && !removeVolumeCGs.isEmpty()) {
                for (URI cg : removeVolumeCGs) {
                    addConsistencyGroupTask(dbClient, cg, taskList, taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
                }
            }
        }

        /**
         * @param dbClient
         * @param uriList
         * @param taskId
         * @param e
         */
        protected void updateFailedVolumeTasks(DbClient dbClient, List<URI> uriList, String taskId, ServiceCoded e) {
            for (URI uri : uriList) {
                Volume vol = dbClient.queryObject(Volume.class, uri);
                Operation op = vol.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(e);
                    vol.getOpStatus().updateTaskStatus(taskId, op);
                    dbClient.updateObject(vol);
                }
            }
        }

        /**
         * Creates tasks against consistency group associated with a request and adds them to the given task list.
         * 
         * @param group
         * @param taskList
         * @param taskId
         * @param operationTypeEnum
         */
        private void addConsistencyGroupTask(DbClient dbClient, URI groupUri, TaskList taskList,
                String taskId,
                ResourceOperationTypeEnum operationTypeEnum) {
            BlockConsistencyGroup group = dbClient.queryObject(BlockConsistencyGroup.class, groupUri);
            Operation op = dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                    operationTypeEnum);
            taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
        }

        /**
         * Creates tasks against volume associated with a request and adds them to the given task list.
         * 
         * @param volume
         * @param taskList
         * @param taskId
         * @param operationTypeEnum
         */
        private void addVolumeTask(DbClient dbClient, Volume volume, TaskList taskList,
                String taskId,
                ResourceOperationTypeEnum operationTypeEnum) {
            Operation op = dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                    operationTypeEnum);
            taskList.getTaskList().add(TaskMapper.toTask(volume, taskId, op));
        }
    }

    private static class MobilityVolumeGroupUtils extends VolumeGroupUtils {

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#updateVolumesInVolumeGroup(com.emc.storageos.
         * db.client.DbClient, java.net.URI, com.emc.storageos.model.application.VolumeGroupUpdateParam,
         * com.emc.storageos.db.client.model.VolumeGroup, java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void updateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId,
                TaskList taskList) {

            List<Volume> removeVols = new ArrayList<Volume>();
            List<Volume> addVols = new ArrayList<Volume>();

            if (param.hasVolumesToAdd()) {
                Iterator<Volume> addVolItr = dbClient.queryIterativeObjects(Volume.class, param.getAddVolumesList().getVolumes());
                while (addVolItr.hasNext()) {
                    addVols.add(addVolItr.next());
                }
            }
            if (param.hasVolumesToRemove()) {
                Iterator<Volume> remVolItr = dbClient.queryIterativeObjects(Volume.class, param.getRemoveVolumesList().getVolumes());
                while (remVolItr.hasNext()) {
                    removeVols.add(remVolItr.next());
                }
            }

            List<Host> removeHosts = new ArrayList<Host>();
            List<Host> addHosts = new ArrayList<Host>();

            if (param.hasHostsToAdd()) {
                Iterator<Host> addHostItr = dbClient.queryIterativeObjects(Host.class, param.getAddHostsList());
                while (addHostItr.hasNext()) {
                    addHosts.add(addHostItr.next());
                }
            }
            if (param.hasHostsToRemove()) {
                Iterator<Host> remHostItr = dbClient.queryIterativeObjects(Host.class, param.getRemoveHostsList());
                while (remHostItr.hasNext()) {
                    removeHosts.add(remHostItr.next());
                }
            }

            List<Cluster> removeClusters = new ArrayList<Cluster>();
            List<Cluster> addClusters = new ArrayList<Cluster>();

            if (param.hasClustersToAdd()) {
                Iterator<Cluster> addClusterItr = dbClient.queryIterativeObjects(Cluster.class, param.getAddClustersList());
                while (addClusterItr.hasNext()) {
                    addClusters.add(addClusterItr.next());
                }
            }
            if (param.hasClustersToRemove()) {
                Iterator<Cluster> remClusterItr = dbClient.queryIterativeObjects(Cluster.class, param.getRemoveClustersList());
                while (remClusterItr.hasNext()) {
                    removeClusters.add(remClusterItr.next());
                }
            }

            Operation op = dbClient.createTaskOpStatus(VolumeGroup.class, volumeGroup.getId(),
                    taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
            taskList.getTaskList().add(toTask(volumeGroup, taskId, op));
            addTasksForVolumesAndCGs(dbClient, addVols, removeVols, null, taskId, taskList);

            try {
                updateVolumeObjects(dbClient, addVols, removeVols, volumeGroup);
                updateHostObjects(dbClient, addHosts, removeHosts, volumeGroup);
                updateClusterObjects(dbClient, addClusters, removeClusters, volumeGroup);
            } catch (InternalException | APIException e) {
                VolumeGroup app = dbClient.queryObject(VolumeGroup.class, volumeGroup.getId());
                op = app.getOpStatus().get(taskId);
                op.error(e);
                app.getOpStatus().updateTaskStatus(taskId, op);
                dbClient.updateObject(app);
                if (param.hasVolumesToAdd()) {
                    List<URI> addURIs = param.getAddVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, addURIs, taskId, e);
                }
                if (param.hasVolumesToRemove()) {
                    List<URI> removeURIs = param.getRemoveVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, removeURIs, taskId, e);
                }
                throw e;
            }

            updateVolumeAndGroupTasks(dbClient, addVols, removeVols, volumeGroup.getId(), taskId);
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#validateUpdateVolumesInVolumeGroup(com.emc.storageos
         * .db.client.DbClient, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup)
         */
        @Override
        public void validateUpdateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup) {
            // TODO Auto-generated method stub

        }

        protected void updateVolumeAndGroupTasks(DbClient dbClient, List<Volume> addVols, List<Volume> removeVols, URI volumeGroupId,
                String taskId) {
            if (addVols != null && !addVols.isEmpty()) {
                updateVolumeTasks(dbClient, addVols, taskId);
            }
            if (removeVols != null && !removeVols.isEmpty()) {
                updateVolumeTasks(dbClient, removeVols, taskId);
            }
            VolumeGroup volumeGroup = dbClient.queryObject(VolumeGroup.class, volumeGroupId);
            Operation op = volumeGroup.getOpStatus().get(taskId);
            op.ready();
            volumeGroup.getOpStatus().updateTaskStatus(taskId, op);
            dbClient.updateObject(volumeGroup);
        }

        protected void updateVolumeTasks(DbClient dbClient, List<Volume> vols, String taskId) {
            for (Volume vol : vols) {
                vol = dbClient.queryObject(Volume.class, vol.getId());
                Operation op = vol.getOpStatus().get(taskId);
                if (op != null) {
                    op.ready();
                    vol.getOpStatus().updateTaskStatus(taskId, op);
                    dbClient.updateObject(vol);
                }
            }
        }

    }

    private static class DRVolumeGroupUtils extends VolumeGroupUtils {

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#updateVolumesInVolumeGroup(com.emc.storageos.
         * db.client.DbClient, java.net.URI, com.emc.storageos.model.application.VolumeGroupUpdateParam,
         * com.emc.storageos.db.client.model.VolumeGroup, java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void updateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId,
                TaskList taskList) {
            // TODO Auto-generated method stub

        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#validateUpdateVolumesInVolumeGroup(com.emc.storageos
         * .db.client.DbClient, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup)
         */
        @Override
        public void validateUpdateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup) {
            // TODO Auto-generated method stub

        }

    }

    private static class CopyVolumeGroupUtils extends VolumeGroupUtils {

        private List<Volume> removeVols;
        private List<Volume> addVols;
        private Set<URI> impactedCGs = new HashSet<URI>();
        private Volume firstVol;
        private boolean validated;

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#validateUpdateVolumesInVolumeGroup(com.emc.storageos
         * .db.client.DbClient, com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup,
         * java.lang.String, com.emc.storageos.model.TaskList)
         */
        @Override
        public void validateUpdateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup) {
            impactedCGs = new HashSet<URI>();

            if (param.hasVolumesToAdd()) {
                addVols = validateAddVolumes(dbClient, param, volumeGroup, impactedCGs);
                firstVol = addVols.get(0);
            }
            if (param.hasVolumesToRemove()) {
                List<URI> removeVolList = param.getRemoveVolumesList().getVolumes();
                removeVols = validateRemoveVolumes(dbClient, removeVolList, volumeGroup, impactedCGs);
                if (!removeVols.isEmpty() && firstVol == null) {
                    firstVol = removeVols.get(0);
                }
            }
            validated = true;
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.emc.storageos.api.service.impl.resource.VolumeGroupService.VolumeGroupUtils#updateVolumesInVolumeGroup(java.net.URI,
         * com.emc.storageos.model.application.VolumeGroupUpdateParam, com.emc.storageos.db.client.model.VolumeGroup, java.lang.String,
         * com.emc.storageos.model.TaskList)
         */
        @Override
        public void updateVolumesInVolumeGroup(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup, String taskId,
                TaskList taskList) {

            if (!validated) {
                validateUpdateVolumesInVolumeGroup(dbClient, param, volumeGroup);
            }

            if (removeVols != null && !removeVols.isEmpty()) {
                // if any of the remove volumes are not in a CG, just update the database
                // this shouldn't happen but it will add robustness if anything goes wrong
                List<Volume> checkVols = new ArrayList<Volume>(removeVols);
                removeVols.clear();
                for (Volume removeVol : checkVols) {
                    if (NullColumnValueGetter.isNullURI(removeVol.getConsistencyGroup())) {
                        removeVol.getVolumeGroupIds().remove(volumeGroup.getId().toString());
                        dbClient.updateObject(removeVol);
                    } else {
                        removeVols.add(removeVol);
                    }
                }
            }

            if ((addVols == null || addVols.isEmpty()) && (removeVols == null || removeVols.isEmpty())) {
                // no volumes to add or remove
                return;
            }

            BlockServiceApi serviceAPI = getBlockService(dbClient, firstVol);
            Operation op = dbClient.createTaskOpStatus(VolumeGroup.class, volumeGroup.getId(),
                    taskId, ResourceOperationTypeEnum.UPDATE_VOLUME_GROUP);
            try {
                taskList.getTaskList().add(toTask(volumeGroup, taskId, op));
                addTasksForVolumesAndCGs(dbClient, addVols, removeVols, impactedCGs, taskId, taskList);
                serviceAPI.updateVolumesInVolumeGroup(param.getAddVolumesList(), removeVols, volumeGroup.getId(), taskId);
            } catch (InternalException | APIException e) {
                VolumeGroup app = dbClient.queryObject(VolumeGroup.class, volumeGroup.getId());
                op = app.getOpStatus().get(taskId);
                op.error(e);
                app.getOpStatus().updateTaskStatus(taskId, op);
                dbClient.updateObject(app);
                if (param.hasVolumesToAdd()) {
                    List<URI> addURIs = param.getAddVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, addURIs, taskId, e);
                }
                if (param.hasVolumesToRemove()) {
                    List<URI> removeURIs = param.getRemoveVolumesList().getVolumes();
                    updateFailedVolumeTasks(dbClient, removeURIs, taskId, e);
                }
                if (!impactedCGs.isEmpty()) {
                    updateFailedCGTasks(dbClient, impactedCGs, taskId, e);
                }
                throw e;
            }
        }

        /**
         * Validate the volumes to be added to the volume group.
         * For role COPY:
         * All volumes should be the same type (block, or RP, or VPLEX, or SRDF),
         * If the volumes are not in a consistency group, it should specify a CG that the volumes to be add to
         * 
         * @param volumes
         * @return The validated volumes
         */
        private List<Volume> validateAddVolumes(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup,
                Set<URI> impactedCGs) {
            String addedVolType = null;
            String firstVolLabel = null;
            List<URI> addVolList = param.getAddVolumesList().getVolumes();
            URI paramCG = param.getAddVolumesList().getConsistencyGroup();
            List<Volume> volumes = new ArrayList<Volume>();
            for (URI volUri : addVolList) {
                ArgValidator.checkFieldUriType(volUri, Volume.class, "id");
                Volume volume = dbClient.queryObject(Volume.class, volUri);
                if (volume == null || volume.getInactive()) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(), "the volume has been deleted");
                }
                URI cgUri = volume.getConsistencyGroup();
                if (NullColumnValueGetter.isNullURI(cgUri)) {
                    if (paramCG == null) {
                        throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                "consistency group is not specified for the volumes not in a consistency group");
                    }
                    ArgValidator.checkFieldUriType(paramCG, BlockConsistencyGroup.class, "consistency_group");

                    BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, paramCG);
                    if (cg == null || cg.getInactive()) {
                        throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                "consistency group does not exist or has been deleted");
                    }

                    // this volume will be added to a CG as it's put in the application
                    // check to make sure there are no other volumes in that CG that are either not in a
                    // application or in a different application
                    URIQueryResultList uriQueryResultList = new URIQueryResultList();
                    dbClient.queryByConstraint(getVolumesByConsistencyGroup(paramCG), uriQueryResultList);
                    Iterator<Volume> volumeIterator = dbClient.queryIterativeObjects(Volume.class, uriQueryResultList);
                    while (volumeIterator.hasNext()) {
                        Volume otherVolInCg = volumeIterator.next();

                        // skip if the volume is on the add list
                        if (addVolList.contains(otherVolInCg.getId())) {
                            continue;
                        }

                        // fail if the volume is not a member of this application
                        if (otherVolInCg.getVolumeGroupIds() == null
                                || !otherVolInCg.getVolumeGroupIds().contains(volumeGroup.getId().toString())) {
                            throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                    "Another volume in the same consistency group is not part of the application");
                        }
                    }

                    impactedCGs.add(paramCG);
                }
                URI systemUri = volume.getStorageController();
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
                String type = system.getSystemType();
                if (!ALLOWED_SYSTEM_TYPES.contains(type)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "The storage system type that the volume created in is not allowed ");
                }
                String volType = getVolumeType(type);
                if (addedVolType == null) {
                    addedVolType = volType;
                    firstVolLabel = volume.getLabel();
                }
                if (!volType.equals(addedVolType)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "The volume type is not same as others");
                }

                // check to make sure this volume is not part of another application
                StringSet volumeGroups = volume.getVolumeGroupIds();
                List<String> badVolumeGroups = new ArrayList<String>();
                if (volumeGroups != null && !volumeGroups.isEmpty()) {
                    for (String vgId : volumeGroups) {
                        VolumeGroup vg = dbClient.queryObject(VolumeGroup.class, URI.create(vgId));
                        if (vg == null || vg.getInactive()) {
                            // this means the volume points to a non-existent volume group;
                            // this shouldn't happen but we can clean this dangling reference up
                            badVolumeGroups.add(vgId);
                        } else {
                            if (vg.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())) {
                                throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                                        String.format("The volume is a member of another application: %s", vg.getLabel()));
                            }
                        }
                    }
                    if (!badVolumeGroups.isEmpty()) {
                        for (String vgId : badVolumeGroups) {
                            volume.getVolumeGroupIds().remove(vgId);
                        }
                        dbClient.updateObject(volume);
                        volume = dbClient.queryObject(Volume.class, volume.getId());
                    }
                }
                volumes.add(volume);
            }
            // Check if the to-add volumes are the same volume type as existing volumes in the application
            List<Volume> existingVols = getVolumeGroupVolumes(dbClient, volumeGroup);
            if (!existingVols.isEmpty()) {
                Volume firstVolume = existingVols.get(0);
                URI systemUri = firstVolume.getStorageController();
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
                String type = system.getSystemType();
                String existingType = getVolumeType(type);
                if (!existingType.equals(addedVolType)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(firstVolLabel,
                            "The volume type is not same as existing volumes in the application");
                }
            }
            return volumes;
        }

        /**
         * Valid the volumes to be removed from the volume group. Called by updateVolumeGroup()
         * 
         * @param volumes the volumes to be removed from volume group
         * @param volumeGroup The volume group
         * @return The validated volumes
         */
        private List<Volume> validateRemoveVolumes(DbClient dbClient, List<URI> volumes, VolumeGroup volumeGroup, Set<URI> removeVolumeCGs) {
            List<Volume> removeVolumes = new ArrayList<Volume>();
            for (URI voluri : volumes) {
                ArgValidator.checkFieldUriType(voluri, Volume.class, "id");
                Volume vol = dbClient.queryObject(Volume.class, voluri);
                if (vol == null || vol.getInactive()) {
                    log.info(String.format("The volume does not exist or has been deleted", voluri.toString()));
                    continue;
                }
                StringSet volumeGroups = vol.getVolumeGroupIds();
                if (volumeGroups == null || !volumeGroups.contains(volumeGroup.getId().toString())) {
                    log.info(String.format("The volume %s is not assigned to the application", vol.getLabel()));
                    continue;
                }

                if (!NullColumnValueGetter.isNullURI(vol.getConsistencyGroup())) {
                    removeVolumeCGs.add(vol.getConsistencyGroup());
                }

                removeVolumes.add(vol);
            }
            return removeVolumes;
        }

        /**
         * Get Volume type, either block, rp, vplex or srdf
         * 
         * @param type The system type
         * @return
         */
        private String getVolumeType(String type) {
            if (BLOCK_TYPES.contains(type)) {
                return BLOCK;
            } else {
                return type;
            }
        }

        private void updateFailedCGTasks(DbClient dbClient, Set<URI> uriList, String taskId, ServiceCoded e) {
            for (URI uri : uriList) {
                BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, uri);
                Operation op = cg.getOpStatus().get(taskId);
                if (op != null) {
                    op.error(e);
                    cg.getOpStatus().updateTaskStatus(taskId, op);
                    dbClient.updateObject(cg);
                }
            }
        }

        private BlockServiceApi getBlockService(DbClient dbClient, final Volume volume) {
            URI systemUri = volume.getStorageController();
            StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
            String type = system.getSystemType();
            String volType = getVolumeType(type);
            return getBlockServiceImpl(volType);
        }

    }

    /**
     * Get volume group volumes
     * 
     * @param volumeGroup
     * @return The list of volumes in volume group
     */
    private static List<Volume> getVolumeGroupVolumes(DbClient dbClient, VolumeGroup volumeGroup) {
        List<Volume> result = new ArrayList<Volume>();
        final List<Volume> volumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        AlternateIdConstraint.Factory.getVolumesByVolumeGroupId(volumeGroup.getId().toString()));
        for (Volume vol : volumes) {
            if (!vol.getInactive()) {
                result.add(vol);
            }
        }
        return result;
    }

    /**
     * <<<<<<< HEAD
     * Get volume group hosts
     * 
     * @param volumeGroup
     * @return The list of hosts in volume group
     */
    private static List<Host> getVolumeGroupHosts(DbClient dbClient, VolumeGroup volumeGroup) {
        List<Host> result = new ArrayList<Host>();
        final List<Host> hosts = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Host.class,
                        AlternateIdConstraint.Factory.getHostsByVolumeGroupId(volumeGroup.getId().toString()));
        for (Host host : hosts) {
            if (!host.getInactive()) {
                result.add(host);
            }
        }
        return result;
    }

    /**
     * Get volume group clusters
     * 
     * @param volumeGroup
     * @return The list of clusters in volume group
     */
    private static List<Cluster> getVolumeGroupClusters(DbClient dbClient, VolumeGroup volumeGroup) {
        List<Cluster> result = new ArrayList<Cluster>();
        final List<Cluster> clusters = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Cluster.class,
                        AlternateIdConstraint.Factory.getClustersByVolumeGroupId(volumeGroup.getId().toString()));
        for (Cluster cluster : clusters) {
            if (!cluster.getInactive()) {
                result.add(cluster);
            }
        }
        return result;
    }

    /**
     * Check if the application has any pending task
     * 
     * =======
     * Check if the application has any pending task
     * >>>>>>> origin/feature-COP-17840-application-support-in-coprhd
     * 
     * @param application
     */
    private void checkForApplicationPendingTasks(VolumeGroup volumeGroup) {
        List<Task> newTasks = TaskUtils.findResourceTasks(_dbClient, volumeGroup.getId());
        for (Task task : newTasks) {
            if (task != null && !task.getInactive() && task.isPending()) {
                throw APIException.badRequests.cannotExecuteOperationWhilePendingTask(volumeGroup.getLabel());
            }
        }
    }

    private void setParents(VolumeGroup volumeGroup, URI parent, boolean add) {
        Set<URI> parents = volumeGroup.getParents();
        if (parents == null) {
            parents = Sets.newHashSet();
        }
        if (add) {
            parents.add(parent);
        } else {
            parents.remove(parent);
        }
        volumeGroup.setParents(parents);
    }

    private String setParent(VolumeGroup volumeGroup, String parent, boolean add) {
        String errorMsg = null;
        // add parent if specified
        if (parent != null) {
            if (URIUtil.isValid(parent)) {
                URI parentId = URI.create(parent);
                ArgValidator.checkFieldUriType(parentId, VolumeGroup.class, "parent");
                VolumeGroup parentVG = _dbClient.queryObject(VolumeGroup.class, parentId);
                if (parentVG == null || parentVG.getInactive()) {
                    errorMsg = "The parent volume group does not exist";
                } else {
                    setParents(volumeGroup, URI.create(parent), add);
                }
            } else {
                List<VolumeGroup> parentVg = CustomQueryUtility
                        .queryActiveResourcesByConstraint(_dbClient, VolumeGroup.class,
                                PrefixConstraint.Factory.getLabelPrefixConstraint(VolumeGroup.class, parent));
                if (parentVg == null || parentVg.isEmpty()) {
                    errorMsg = "The parent volume group does not exist";
                } else {
                    setParents(volumeGroup, parentVg.iterator().next().getId(), add);
                }
            }
        }
        return errorMsg;
    }

}
