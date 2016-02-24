/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;
import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.PlacementManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyUtils;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionManager;
import com.emc.storageos.api.service.impl.resource.snapshot.BlockSnapshotSessionUtils;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.model.VolumeGroup.VolumeGroupRole;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.ResourceOnlyNameGenerator;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupCopySetList;
import com.emc.storageos.model.application.VolumeGroupCopySetParam;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyActivateParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyCreateParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyDetachParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyRestoreParam;
import com.emc.storageos.model.application.VolumeGroupFullCopyResynchronizeParam;
import com.emc.storageos.model.application.VolumeGroupList;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotCreateParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotOperationParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionCreateParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionOperationParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionDeactivateParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionRestoreParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.BlockConsistencyGroupSnapshotCreate;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionRestRep;
import com.emc.storageos.model.block.BlockSnapshotSessionList;
import com.emc.storageos.model.block.NamedVolumeGroupsList;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.SnapshotSessionCreateParam;
import com.emc.storageos.model.block.SnapshotSessionLinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionRelinkTargetsParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetParam;
import com.emc.storageos.model.block.SnapshotSessionUnlinkTargetsParam;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * APIs to view, create, modify and remove volume groups
 */

@Path("/volume-groups/block")
@DefaultPermissions(readRoles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = { ACL.OWN, ACL.ALL }, writeRoles = {
        Role.TENANT_ADMIN }, writeAcls = { ACL.OWN, ACL.ALL })
public class VolumeGroupService extends TaskResourceService {
    private static final String VOLUME_GROUP_NAME = "name";
    private static final String VOLUME_GROUP_ROLES = "roles";
    private static final String MIGRATION_GROUP_BY = "migration_group_by";
    private static final String MIGRATION_TYPE = "migration_type";
    private static final String EVENT_SERVICE_TYPE = "application";
    private static final String ADD_CLUSTERS = "add_clusters";
    private static final String REMOVE_CLUSTERS = "remove_clusters";
    private static final String ADD_HOSTS = "add_hosts";
    private static final String REMOVE_HOSTS = "remove_hosts";
    private static final String ADD_VOLUMES = "add_volumes";
    private static final String REMOVE_VOLUMES = "remove_volumes";
    private static final Set<String> ALLOWED_SYSTEM_TYPES = new HashSet<String>(Arrays.asList(
            DiscoveredDataObject.Type.vnxblock.name(),
            DiscoveredDataObject.Type.vplex.name(),
            DiscoveredDataObject.Type.vmax.name(),
            DiscoveredDataObject.Type.xtremio.name(),
            DiscoveredDataObject.Type.scaleio.name(),
            DiscoveredDataObject.Type.rp.name(),
            DiscoveredDataObject.Type.srdf.name(),
            DiscoveredDataObject.Type.ibmxiv.name()));

    private static final String BLOCK = "block";
    private static final String ID_FIELD = "id";
    private static final String NAME_FIELD = "name";
    private static final String VOLUMES_FIELD = "volumes";
    private static final String VOLUME_FIELD = "volume";
    private static final String COPY_SET_NAME_FIELD = "copy_set_name";
    private static final String SNAPSHOT_ID_FIELD = "sid";
    private static final String SNAPSHOTS_FIELD = "snapshots";
    private static final String SNAPSHOT_FIELD = "snapshot";
    private static final String SNAPSHOT_SESSIONS_FIELD = "snapshot_sessions";
    private static final String SNAPSHOT_SESSION_FIELD = "snapshot_session";

    private static enum ReplicaTypeEnum {
        FULL_COPY("Full copy"),
        SNAPSHOT("Snapshot"),
        SNAPSHOT_SESSION("Snapshot Session"),
        MIRROR("Continuous copy");

        private final String _name;
        private ReplicaTypeEnum(String name) {
            this._name = name;
        }

        @Override
        public String toString() {
            return _name;
        }
    };

    static final Logger log = LoggerFactory.getLogger(VolumeGroupService.class);

    // A reference to the placement manager.
    private PlacementManager _placementManager;

    // A reference to the block consistency group service.
    private BlockConsistencyGroupService _blockConsistencyGroupService;

    // Block service implementations
    private static Map<String, BlockServiceApi> _blockServiceApis;

    /**
     * Setter for the placement manager.
     * 
     * @param placementManager A reference to the placement manager.
     */
    public void setPlacementManager(PlacementManager placementManager) {
        _placementManager = placementManager;
    }

    /**
     * Setter for the block consistency group service.
     * 
     * @param blockConsistencyGroupService A reference to the block consistency group service.
     */
    public void setBlockConsistencyGroupService(BlockConsistencyGroupService blockConsistencyGroupService) {
        _blockConsistencyGroupService = blockConsistencyGroupService;
    }

    public void setBlockServiceApis(final Map<String, BlockServiceApi> serviceInterfaces) {
        _blockServiceApis = serviceInterfaces;
    }

    private static BlockServiceApi getBlockServiceImpl(final String type) {
        return _blockServiceApis.get(type);
    }

    @Override
    protected DataObject queryResource(URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
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
        checkDuplicateLabel(VolumeGroup.class, param.getName());
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
        String msg = setParent(volumeGroup, param.getParent());
        if (msg != null && !msg.isEmpty()) {
            throw APIException.badRequests.volumeGroupCantBeCreated(volumeGroup.getLabel(), msg);
        }

        if (param.getRoles().contains(VolumeGroup.VolumeGroupRole.MOBILITY.name())) {
            ArgValidator.checkFieldNotEmpty(param.getMigrationType(), MIGRATION_TYPE);
            ArgValidator.checkFieldNotEmpty(param.getMigrationGroupBy(), MIGRATION_GROUP_BY);
            ArgValidator.checkFieldValueFromEnum(param.getMigrationType(), MIGRATION_TYPE,
                    VolumeGroup.MigrationType.class);
            ArgValidator.checkFieldValueFromEnum(param.getMigrationGroupBy(), MIGRATION_GROUP_BY,
                    VolumeGroup.MigrationGroupBy.class);
            volumeGroup.setMigrationType(param.getMigrationType());
            volumeGroup.setMigrationGroupBy(param.getMigrationGroupBy());
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
        VolumeGroupRestRep resp = DbObjectMapper.map(volumeGroup);
        resp.setReplicationGroupNames(CopyVolumeGroupUtils.getReplicationGroupNames(volumeGroup, _dbClient));
        resp.setVirtualArrays(CopyVolumeGroupUtils.getVirtualArrays(volumeGroup, _dbClient));
        return resp;
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
            VolumeGroup vg = iter.next();
            volumeGroupList.getVolumeGroups().add(toNamedRelatedResource(vg));
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
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);
        NamedVolumesList result = new NamedVolumesList();
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
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
     * Get the list of child volume groups
     * 
     * @param id
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/volume-groups")
    public NamedVolumeGroupsList getChildrenVolumeGroups(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = _dbClient.queryObject(VolumeGroup.class, id);
        NamedVolumeGroupsList result = new NamedVolumeGroupsList();
        List<VolumeGroup> volumeGroups = getVolumeGroupChildren(_dbClient, volumeGroup);
        for (VolumeGroup group : volumeGroups) {
            result.getVolumeGroups().add(toNamedRelatedResource(group));
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
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public Response deactivateVolumeGroup(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VolumeGroup.class, "id");
        VolumeGroup volumeGroup = (VolumeGroup) queryResource(id);

        if (!ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup).isEmpty()) {
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
        if (!getVolumeGroupChildren(_dbClient, volumeGroup).isEmpty()) {
            // application could not be deleted if it has child volume groups
            throw APIException.badRequests.volumeGroupWithChildrenCantBeDeleted(volumeGroup.getLabel());
        }

        // check for any other references to this volume group
        ArgValidator.checkReference(VolumeGroup.class, id, checkForDelete(volumeGroup));

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
            checkDuplicateLabel(VolumeGroup.class, vgName);
            volumeGroup.setLabel(vgName);
            isChanged = true;
        }
        String description = param.getDescription();
        if (description != null && !description.isEmpty()) {
            volumeGroup.setDescription(description);
            isChanged = true;
        }

        String parent = param.getParent();
        if (parent != null && !parent.isEmpty()) {
            String msg = setParent(volumeGroup, parent);
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
        auditOp(OperationTypeEnum.UPDATE_VOLUME_GROUP, true, AuditLogManager.AUDITOP_BEGIN, volumeGroup.getId().toString(),
                volumeGroup.getLabel());
        return taskList;
    }

    /**
     * Creates a volume group full copy
     * - Creates full copy for all the array replication groups within this Application.
     * - If partial flag is specified, it creates full copy only for set of array replication groups.
     * A Volume from each array replication group can be provided to indicate which array replication
     * groups are required to take full copy.
     * 
     * @prereq none
     * 
     * @param volumeGroupId the URI of the Volume Group
     *            - Volume group URI
     * @param param VolumeGroupFullCopyCreateParam
     * 
     * @brief Create volume group fullcopy
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            VolumeGroupFullCopyCreateParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        ArgValidator.checkEntityNotNull(volumeGroup, volumeGroupId, isIdEmbeddedInURL(volumeGroupId));

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.FULL_COPY);

        TaskList taskList = new TaskList();

        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
        // validate that there should be some volumes in VolumeGroup
        if (volumes.isEmpty()) {
            throw APIException.badRequests.replicaOperationNotAllowedOnEmptyVolumeGroup(volumeGroup.getLabel(), ReplicaTypeEnum.FULL_COPY.toString());
        }

        List<VolumeGroupUtils> utils = getVolumeGroupUtils(volumeGroup);
        for (VolumeGroupUtils util : utils) {
            // TODO XtremIO array does not support clone.
            // If volume group has mix of storage arrays, entire Clone creation workflow will fail (rolled back)
            // In such cases and not to have partial clone, we may need to restrict user at API level.
            // may be use Copy-VolumeGroupUtils to validate such things.
        }

        if (param.getPartial()) {
            log.info("Full Copy requested for subset of array groups in Application.");

            // validate that at least one volume URI is provided
            ArgValidator.checkFieldNotEmpty(param.getVolumes(), "volumes");

            // validate that provided volumes
            Set<String> arrayGroupNames = new HashSet<String>();
            List<Volume> volumesInRequest = new ArrayList<Volume>();
            for (URI volumeURI : param.getVolumes()) {
                ArgValidator.checkFieldUriType(volumeURI, Volume.class, "volume");
                // Get the Volume.
                Volume volume = (Volume) BlockFullCopyUtils.queryFullCopyResource(volumeURI,
                        uriInfo, true, _dbClient);

                String arrayGroupName = volume.getReplicationGroupInstance();
                if (volume.isVPlexVolume(_dbClient)) {
                    // get backend source volume to get RG name
                    Volume backedVol = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient);
                    if (backedVol != null) {
                        arrayGroupName = backedVol.getReplicationGroupInstance();
                    }
                }

                // this shouldn't happen, but just in case, skip if replicationGroupInstance is null
                if (NullColumnValueGetter.isNullValue(arrayGroupName)) {
                    log.info("Skipping volume {} because replicationGroupInstance is null", volume.getLabel());
                    continue;
                }

                // skip repeated array groups
                if (arrayGroupNames.contains(arrayGroupName)) {
                    log.info("Skipping repetitive request for Volume array group {}. Volume: {}",
                            arrayGroupName, volume.getLabel());
                    continue;
                }
                arrayGroupNames.add(arrayGroupName);

                // validate that provided volumes are part of Volume Group
                if (!volume.getVolumeGroupIds().contains(volumeGroupId.toString())) {
                    throw APIException.badRequests
                            .replicaOperationNotAllowedVolumeNotInVolumeGroup(ReplicaTypeEnum.FULL_COPY.toString(), volume.getLabel());
                }

                volumesInRequest.add(volume);
            }

            // send create request after validating all volumes
            String name = param.getName();
            for (Volume volume : volumesInRequest) {
                // set Flag in Volume so that we will know about partial request during processing.
                volume.addInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                _dbClient.updateObject(volume);

                // Create full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and full copies will be created for all volumes in that ReplicationGroup.

                // In case of partial request, Tasks will be generated for each Array group
                // and they cannot be monitored together.

                // append replication group name to requested full copy name
                // to make the requested name unique across array replication groups
                param.setName(name + "_" + volume.getReplicationGroupInstance());
                try {
                    taskList.getTaskList().addAll(getFullCopyManager().createFullCopy(volume.getId(), param).getTaskList());
                } catch (Exception e) {
                    volume.clearInternalFlags(Flag.VOLUME_GROUP_PARTIAL_REQUEST);
                    _dbClient.updateObject(volume);
                    throw e;
                }
            }
        } else {
            log.info("Full Copy requested for entire Application");
            auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN, volumeGroup.getId().toString(),
                    param.getName(), param.getCount());

            // Full copy will be created for all volumes in Application
            taskList = getFullCopyManager().createFullCopy(volumes.get(0).getId(), param);
        }

        return taskList;
    }

    /**
     * List full copies for a volume group
     * 
     * @prereq none
     * 
     * @param volumeGroupId The URI of the volume group.
     * 
     * @brief List full copies for a volume group
     * 
     * @return The list of full copies for the volume group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public NamedVolumesList getVolumeGroupFullCopies(@PathParam("id") final URI volumeGroupId) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        // Cycle over the volumes in the volume group and
        // get the full copies for each volume in the group.
        NamedVolumesList fullCopyList = new NamedVolumesList();
        for (Volume volume : volumes) {
            NamedVolumesList volumeFullCopies = getFullCopyManager().getFullCopiesForSource(volume.getId());
            fullCopyList.getVolumes().addAll(volumeFullCopies.getVolumes());
        }

        return fullCopyList;
    }

    /**
     * List full copy set names for a volume group
     * 
     * @param volumeGroupId The URI of the volume group.
     * 
     * @brief List full copy set names for a volume group
     * 
     * @return The list of full copy set names for the volume group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/copy-sets")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public VolumeGroupCopySetList getVolumeGroupFullCopySets(@PathParam("id") final URI volumeGroupId) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        // Cycle over the volumes in the volume group and
        // get the full copies for each volume in the group.
        VolumeGroupCopySetList fullCopySets = new VolumeGroupCopySetList();
        for (Volume volume : volumes) {
            StringSet fullCopyIds = volume.getFullCopies();
            if (fullCopyIds != null) {
                for (String fullCopyId : fullCopyIds) {
                    Volume fullCopyVolume = _dbClient.queryObject(Volume.class,
                            URI.create(fullCopyId));
                    if (fullCopyVolume == null || fullCopyVolume.getInactive()) {
                        log.warn("Stale full copy {} found for volume {}", fullCopyId,
                                volume.getLabel());
                        continue;
                    }
                    String setName = fullCopyVolume.getFullCopySetName();
                    if (setName == null) {  // This should not happen
                        log.warn(String.format("skipping volume %s becuase fullCopySetName is null", fullCopyVolume.getLabel()));
                        continue;
                    }
                    fullCopySets.getCopySets().add(setName);
                }
            }
        }

        return fullCopySets;
    }

    /**
     * List full copies for a volume group belonging to the provided copy set name
     * 
     * @param volumeGroupId The URI of the volume group.
     * @param param VolumeGroupCopySetParam containing the copy set name
     * 
     * @brief List full copies for a volume group belonging to the provided copy set name
     * 
     * @return The list of full copies for the volume group belonging to the provided copy set name
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/copy-sets")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public NamedVolumesList getVolumeGroupFullCopiesForSet(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupCopySetParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate that full copy set name is provided
        String fullCopySetName = param.getCopySetName();
        ArgValidator.checkFieldNotEmpty(fullCopySetName, COPY_SET_NAME_FIELD);

        // validate that the provided set name actually belongs to this Application
        VolumeGroupCopySetList fullCopySetNames = getVolumeGroupFullCopySets(volumeGroupId);
        if (!fullCopySetNames.getCopySets().contains(fullCopySetName)) {
            throw APIException.badRequests.
                    setNameDoesNotBelongToVolumeGroup("Full Copy Set name", fullCopySetName, volumeGroup.getLabel());
        }

        NamedVolumesList fullCopyList = new NamedVolumesList();
        List<Volume> fullCopiesForSet = getClonesBySetName(fullCopySetName, volumeGroupId);
        for (Volume fullCopy : fullCopiesForSet) {
            fullCopyList.getVolumes().add(toNamedRelatedResource(fullCopy));
        }

        return fullCopyList;
    }

    /**
     * Get the specified volume group full copy.
     * 
     * @prereq none
     * @param volumeGroupId The URI of the volume group.
     * @param fullCopyURI The URI of the full copy.
     * 
     * @brief Get the specified volume group full copy.
     * 
     * @return The full copy volume.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/{fcid}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public VolumeRestRep getVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            @PathParam("fcid") URI fullCopyURI) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.FULL_COPY);

        Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                fullCopyURI, uriInfo, false, _dbClient);
        verifyReplicaForCopyRequest(fullCopyVolume, volumeGroupId);

        // Get and return the full copy.
        return BlockMapper.map(_dbClient, fullCopyVolume);
    }

    /**
     * Activate the specified Volume group full copy.
     * - Activates full copy for all the array replication groups within this Application.
     * - If partial flag is specified, it activates full copy only for set of array replication groups.
     * A Full Copy from each array replication group can be provided to indicate which array replication
     * groups's full copies needs to be activated.
     * 
     * @prereq Create Volume group full copy as inactive.
     * 
     * @param volumeGroupId The URI of the Volume group.
     * @param fullCopyURI The URI of the full copy.
     * 
     * @brief Activate Volume group full copy.
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/activate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList activateVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupFullCopyActivateParam param) {

        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        TaskList taskList = new TaskList();

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.FULL_COPY);

        // validate that at least one full copy URI is provided
        ArgValidator.checkFieldNotEmpty(param.getFullCopies(), "fullCopies");

        // validate the requested full copies
        List<Volume> fullCopyVolumesInRequest = validateFullCopiesInRequest(param.getFullCopies(), volumeGroupId);

        /**
         * 1. VolumeGroupService Clone API accepts a Clone URI (to identify clone set and RG)
         * - then get All full copies belonging to same full copy set
         * - get full copy set name from the requested full copy
         * 2. If partial, there will be a List of Clone URIs (one from each RG)
         * 3. Group the full copies by Replication Group(RG)
         * 4. For each RG, invoke the ConsistencyGroup full copy API (CG uri, clone uri)
         * - a. Skip the CG/RG calls when thrown error and continue with other entries; create 'ERROR' Task for this call
         * - b. Finally return the Task List (RG tasks may finish at different times as they are different calls)
         */
        if (!param.getPartial()) {
            Volume fullCopy = fullCopyVolumesInRequest.get(0);
            log.info("Full Copy operation requested for entire Application, Considering full copy {} in request.",
                    fullCopy.getLabel());
            fullCopyVolumesInRequest.clear();
            fullCopyVolumesInRequest.addAll(getClonesBySetName(fullCopy.getFullCopySetName(), volumeGroup.getId()));

        } else {
            log.info("Full Copy operation requested for subset of array replication groups in Application.");
        }

        Map<String, Volume> repGroupToFullCopyMap = groupFullCopiesByReplicationGroup(fullCopyVolumesInRequest);
        for (Map.Entry<String, Volume> entry : repGroupToFullCopyMap.entrySet()) {
            String replicationGroup = entry.getKey();
            Volume fullCopy = entry.getValue();
            log.info("Processing Array Replication Group {}, Full Copy {}", replicationGroup, fullCopy.getLabel());
            try {
                // get CG URI
                URI cgURI = getConsistencyGroupForFullCopy(fullCopy);

                // Activate the full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and all volumes in that ReplicationGroup will be activated.
                taskList.getTaskList().addAll(
                        _blockConsistencyGroupService.activateConsistencyGroupFullCopy(cgURI, fullCopy.getId())
                                .getTaskList());
            } catch (InternalException | APIException e) {
                String errMsg = String.format("Error activating Array Replication Group %s, Full Copy %s",
                        replicationGroup, fullCopy.getLabel());
                log.error(errMsg, e);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnVolume(_dbClient, fullCopy,
                        ResourceOperationTypeEnum.ACTIVATE_VOLUME_FULL_COPY, e);
                taskList.addTask(task);
            }
        }

        if (!param.getPartial()) {
            auditOp(OperationTypeEnum.ACTIVATE_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN,
                    volumeGroup.getId().toString(), fullCopyVolumesInRequest.get(0).getLabel());
        }

        return taskList;
    }

    /**
     * Detach the specified Volume group full copy.
     * - Detaches full copy for all the array replication groups within this Application.
     * - If partial flag is specified, it detaches full copy only for set of array replication groups.
     * A Full Copy from each array replication group can be provided to indicate which array replication
     * groups's full copies needs to be detached.
     * 
     * @prereq Create Volume group full copy as active.
     * 
     * @param volumeGroupId The URI of the Volume group.
     * @param fullCopyURI The URI of the full copy.
     * 
     * @brief Detach Volume group full copy.
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/detach")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList detachVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupFullCopyDetachParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        TaskList taskList = new TaskList();

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.FULL_COPY);

        // validate that at least one full copy URI is provided
        ArgValidator.checkFieldNotEmpty(param.getFullCopies(), "volumes");

        // validate the requested full copies
        List<Volume> fullCopyVolumesInRequest = validateFullCopiesInRequest(param.getFullCopies(), volumeGroupId);

        /**
         * 1. VolumeGroupService Clone API accepts a Clone URI (to identify clone set and RG)
         * - then get All full copies belonging to same full copy set
         * - get full copy set name from the requested full copy
         * 2. If partial, there will be a List of Clone URIs (one from each RG)
         * 3. Group the full copies by Replication Group(RG)
         * 4. For each RG, invoke the ConsistencyGroup full copy API (CG uri, clone uri)
         * - a. Skip the CG/RG calls when thrown error and continue with other entries; create 'ERROR' Task for this call
         * - b. Finally return the Task List (RG tasks may finish at different times as they are different calls)
         */
        if (!param.getPartial()) {
            Volume fullCopy = fullCopyVolumesInRequest.get(0);
            log.info("Full Copy operation requested for entire Application, Considering full copy {} in request.",
                    fullCopy.getLabel());
            fullCopyVolumesInRequest.clear();
            fullCopyVolumesInRequest.addAll(getClonesBySetName(fullCopy.getFullCopySetName(), volumeGroup.getId()));
        } else {
            log.info("Full Copy operation requested for subset of array replication groups in Application.");
        }

        Map<String, Volume> repGroupToFullCopyMap = groupFullCopiesByReplicationGroup(fullCopyVolumesInRequest);
        for (Map.Entry<String, Volume> entry : repGroupToFullCopyMap.entrySet()) {
            String replicationGroup = entry.getKey();
            Volume fullCopy = entry.getValue();
            log.info("Processing Array Replication Group {}, Full Copy {}", replicationGroup, fullCopy.getLabel());
            try {
                // get CG URI
                URI cgURI = getConsistencyGroupForFullCopy(fullCopy);

                // Detach the full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and all volumes in that ReplicationGroup will be detached.
                taskList.getTaskList().addAll(
                        _blockConsistencyGroupService.detachConsistencyGroupFullCopy(cgURI, fullCopy.getId())
                                .getTaskList());
            } catch (InternalException | APIException e) {
                String errMsg = String.format("Error detaching Array Replication Group %s, Full Copy %s",
                        replicationGroup, fullCopy.getLabel());
                log.error(errMsg, e);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnVolume(_dbClient, fullCopy,
                        ResourceOperationTypeEnum.DETACH_VOLUME_FULL_COPY, e);
                taskList.addTask(task);
            }
        }

        if (!param.getPartial()) {
            auditOp(OperationTypeEnum.DETACH_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN,
                    volumeGroup.getId().toString(), fullCopyVolumesInRequest.get(0).getLabel());
        }

        return taskList;
    }

    /**
     * Restore the specified Volume group full copy.
     * - Restores full copy for all the array replication groups within this Application.
     * - If partial flag is specified, it restores full copy only for set of array replication groups.
     * A Full Copy from each array replication group can be provided to indicate which array replication
     * groups's full copies needs to be restored.
     * 
     * @prereq Create Volume group full copy as active.
     * 
     * @param volumeGroupId The URI of the Volume group.
     * @param fullCopyURI The URI of the full copy.
     * 
     * @brief Restore Volume group full copy.
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList restoreVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupFullCopyRestoreParam param) {

        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        TaskList taskList = new TaskList();

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.FULL_COPY);

        // validate that at least one full copy URI is provided
        ArgValidator.checkFieldNotEmpty(param.getFullCopies(), "volumes");

        // validate the requested full copies
        List<Volume> fullCopyVolumesInRequest = validateFullCopiesInRequest(param.getFullCopies(), volumeGroupId);

        /**
         * 1. VolumeGroupService Clone API accepts a Clone URI (to identify clone set and RG)
         * - then get All full copies belonging to same full copy set
         * - get full copy set name from the requested full copy
         * 2. If partial, there will be a List of Clone URIs (one from each RG)
         * 3. Group the full copies by Replication Group(RG)
         * 4. For each RG, invoke the ConsistencyGroup full copy API (CG uri, clone uri)
         * - a. Skip the CG/RG calls when thrown error and continue with other entries; create 'ERROR' Task for this call
         * - b. Finally return the Task List (RG tasks may finish at different times as they are different calls)
         */
        if (!param.getPartial()) {
            Volume fullCopy = fullCopyVolumesInRequest.get(0);
            log.info("Full Copy operation requested for entire Application, Considering full copy {} in request.",
                    fullCopy.getLabel());
            fullCopyVolumesInRequest.clear();
            fullCopyVolumesInRequest.addAll(getClonesBySetName(fullCopy.getFullCopySetName(), volumeGroup.getId()));
        } else {
            log.info("Full Copy operation requested for subset of array replication groups in Application.");
        }

        Map<String, Volume> repGroupToFullCopyMap = groupFullCopiesByReplicationGroup(fullCopyVolumesInRequest);
        for (Map.Entry<String, Volume> entry : repGroupToFullCopyMap.entrySet()) {
            String replicationGroup = entry.getKey();
            Volume fullCopy = entry.getValue();
            log.info("Processing Array Replication Group {}, Full Copy {}", replicationGroup, fullCopy.getLabel());
            try {
                // get CG URI
                URI cgURI = getConsistencyGroupForFullCopy(fullCopy);

                // Restore the full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and all volumes in that ReplicationGroup will be restored.
                taskList.getTaskList().addAll(
                        _blockConsistencyGroupService.restoreConsistencyGroupFullCopy(cgURI, fullCopy.getId())
                                .getTaskList());
            } catch (InternalException | APIException e) {
                String errMsg = String.format("Error restoring Array Replication Group %s, Full Copy %s",
                        replicationGroup, fullCopy.getLabel());
                log.error(errMsg, e);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnVolume(_dbClient, fullCopy,
                        ResourceOperationTypeEnum.RESTORE_VOLUME_FULL_COPY, e);
                taskList.addTask(task);
            }
        }

        if (!param.getPartial()) {
            auditOp(OperationTypeEnum.RESTORE_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN,
                    volumeGroup.getId().toString(), fullCopyVolumesInRequest.get(0).getLabel());
        }

        return taskList;
    }

    /**
     * Resynchronize the specified Volume group full copy.
     * - Resynchronizes full copy for all the array replication groups within this Application.
     * - If partial flag is specified, it resynchronizes full copy only for set of array replication groups.
     * A Full Copy from each array replication group can be provided to indicate which array replication
     * groups's full copies needs to be resynchronized.
     * 
     * @prereq Create Volume group full copy as active.
     * 
     * @param volumeGroupId The URI of the Volume group.
     * @param fullCopyURI The URI of the full copy.
     * 
     * @brief Resynchronize Volume group full copy.
     * 
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/full-copies/resynchronize")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList resynchronizeVolumeGroupFullCopy(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupFullCopyResynchronizeParam param) {

        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        TaskList taskList = new TaskList();

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.FULL_COPY);

        // validate that at least one full copy URI is provided
        ArgValidator.checkFieldNotEmpty(param.getFullCopies(), "volumes");

        // validate the requested full copies
        List<Volume> fullCopyVolumesInRequest = validateFullCopiesInRequest(param.getFullCopies(), volumeGroupId);

        /**
         * 1. VolumeGroupService Clone API accepts a Clone URI (to identify clone set and RG)
         * - then get All full copies belonging to same full copy set
         * - get full copy set name from the requested full copy
         * 2. If partial, there will be a List of Clone URIs (one from each RG)
         * 3. Group the full copies by Replication Group(RG)
         * 4. For each RG, invoke the ConsistencyGroup full copy API (CG uri, clone uri)
         * - a. Skip the CG/RG calls when thrown error and continue with other entries; create 'ERROR' Task for this call
         * - b. Finally return the Task List (RG tasks may finish at different times as they are different calls)
         */
        if (!param.getPartial()) {
            Volume fullCopy = fullCopyVolumesInRequest.get(0);
            log.info("Full Copy operation requested for entire Application, Considering full copy {} in request.",
                    fullCopy.getLabel());
            fullCopyVolumesInRequest.clear();
            fullCopyVolumesInRequest.addAll(getClonesBySetName(fullCopy.getFullCopySetName(), volumeGroup.getId()));
        } else {
            log.info("Full Copy operation requested for subset of array replication groups in Application.");
        }

        Map<String, Volume> repGroupToFullCopyMap = groupFullCopiesByReplicationGroup(fullCopyVolumesInRequest);
        for (Map.Entry<String, Volume> entry : repGroupToFullCopyMap.entrySet()) {
            String replicationGroup = entry.getKey();
            Volume fullCopy = entry.getValue();
            log.info("Processing Array Replication Group {}, Full Copy {}", replicationGroup, fullCopy.getLabel());
            try {
                // get CG URI
                URI cgURI = getConsistencyGroupForFullCopy(fullCopy);

                // Resynchronize the full copy. Note that it will take into account the
                // fact that the volume is in a ReplicationGroup
                // and all volumes in that ReplicationGroup will be resynchronized.
                taskList.getTaskList().addAll(
                        _blockConsistencyGroupService.resynchronizeConsistencyGroupFullCopy(cgURI, fullCopy.getId())
                                .getTaskList());
            } catch (InternalException | APIException e) {
                String errMsg = String.format("Error resynchronizing Array Replication Group %s, Full Copy %s",
                        replicationGroup, fullCopy.getLabel());
                log.error(errMsg, e);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnVolume(_dbClient, fullCopy,
                        ResourceOperationTypeEnum.RESYNCHRONIZE_VOLUME_FULL_COPY, e);
                taskList.addTask(task);
            }
        }

        if (!param.getPartial()) {
            auditOp(OperationTypeEnum.RESYNCHRONIZE_VOLUME_GROUP_FULL_COPY, true, AuditLogManager.AUDITOP_BEGIN,
                    volumeGroup.getId().toString(), fullCopyVolumesInRequest.get(0).getLabel());
        }

        return taskList;
    }

    /**
     * Validate full copies in the request are members of the application in the request
     * 
     * @param fullCopyURIsInRequest
     *            the full copies in request
     * @param volumeGroupVolumes
     *            the volume group volumes
     * @return the full copy objects
     */
    private List<Volume> validateFullCopiesInRequest(final List<URI> fullCopyURIsInRequest, URI volumeGroupUri) {
        List<String> arrayGroupNames = new ArrayList<String>();
        List<Volume> fullCopyVolumesInRequest = new ArrayList<Volume>();
        Set<String> setNames = new HashSet<String>();
        for (URI fullCopyURI : fullCopyURIsInRequest) {
            ArgValidator.checkFieldUriType(fullCopyURI, Volume.class, "volume");
            // Get the full copy.
            Volume fullCopyVolume = (Volume) BlockFullCopyUtils.queryFullCopyResource(
                    fullCopyURI, uriInfo, false, _dbClient);

            // skip repeated array groups
            if (arrayGroupNames.contains(fullCopyVolume.getReplicationGroupInstance())) {
                log.info("Skipping repetitive request for Full Copy array group {}. Full Copy: {}",
                        fullCopyVolume.getReplicationGroupInstance(), fullCopyVolume.getLabel());
                continue;
            }
            arrayGroupNames.add(fullCopyVolume.getReplicationGroupInstance());

            verifyReplicaForCopyRequest(fullCopyVolume, volumeGroupUri);

            fullCopyVolumesInRequest.add(fullCopyVolume);
            setNames.add(fullCopyVolume.getFullCopySetName());
        }
        if (setNames.size() > 1) {
            throw APIException.badRequests.multipleSetNamesProvided(ReplicaTypeEnum.FULL_COPY.toString());
        }
        return fullCopyVolumesInRequest;
    }

    /**
     * Returns a map of replication group name to full copy.
     * 
     * @param fullCopies the full copies
     * @return the map of replication group to full copy
     */
    private Map<String, Volume> groupFullCopiesByReplicationGroup(List<Volume> fullCopies) {
        Map<String, Volume> repGroupToFullCopyMap = new HashMap<String, Volume>();
        for (Volume fullCopy : fullCopies) {
            String repGroupName = fullCopy.getReplicationGroupInstance();
            if (repGroupName == null && fullCopy.isVPlexVolume(_dbClient)) {
                // get backend source volume to get RG name
                Volume backedVol = VPlexUtil.getVPLEXBackendVolume(fullCopy, true, _dbClient);
                if (backedVol != null) {
                    repGroupName = backedVol.getReplicationGroupInstance();
                }
            }
            // duplicate group names will be overwritten
            repGroupToFullCopyMap.put(repGroupName, fullCopy);
        }
        return repGroupToFullCopyMap;
    }

    /**
     * Gets the consistency group for full copy.
     * 
     * @param fullCopy the full copy
     * @return the consistency group for full copy
     */
    private URI getConsistencyGroupForFullCopy(Volume fullCopy) {
        if (NullColumnValueGetter.isNullURI(fullCopy.getAssociatedSourceVolume())) {
            // Full Copy may already be Detached
            throw APIException.badRequests
                    .replicaOperationNotAllowedNotAReplica(ReplicaTypeEnum.FULL_COPY.toString(), fullCopy.getLabel());
        }
        Volume srcVolume = _dbClient.queryObject(Volume.class, fullCopy.getAssociatedSourceVolume());
        return srcVolume != null ? srcVolume.getConsistencyGroup() : null;
    }

    /**
     * allow replica operation only for COPY type VolumeGroup.
     * 
     * @param volumeGroup
     * @param replicaType
     */
    private void validateCopyOperationForVolumeGroup(VolumeGroup volumeGroup, ReplicaTypeEnum replicaType) {
        if (!volumeGroup.getRoles().contains(VolumeGroupRole.COPY.name())) {
            throw APIException.badRequests.replicaOperationNotAllowedForNonCopyTypeVolumeGroup(volumeGroup.getLabel(), replicaType.toString());
        }
    }

    /**
     * Creates and returns an instance of the block snapshot session manager to handle
     * a snapshot session creation request.
     * 
     * @return BlockSnapshotSessionManager
     */
    private BlockSnapshotSessionManager getSnapshotSessionManager() {
        BlockSnapshotSessionManager snapshotSessionManager = new BlockSnapshotSessionManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, sc, uriInfo, _request);
        return snapshotSessionManager;
    }

    /**
     * Creates and returns an instance of the block full copy manager to handle
     * a full copy request.
     * 
     * @return BlockFullCopyManager
     */
    private BlockFullCopyManager getFullCopyManager() {
        BlockFullCopyManager fcManager = new BlockFullCopyManager(_dbClient,
                _permissionsHelper, _auditMgr, _coordinator, _placementManager, sc, uriInfo,
                _request, null);
        return fcManager;
    }

    /**
     * 
     * Verifies that the passed replica URI and ensure that it represents a replica for a volume in volume group represented by
     * the passed
     * in volume group id.
     * 
     * @param replica
     *            the replica (Clone/Snapshot/Mirror)
     * @param volumeGroupUri
     * @return The URI of the replica's source.
     */
    private URI verifyReplicaForCopyRequest(BlockObject replica, URI volumeGroupUri) {
        URI sourceURI = getSourceIdForFullCopy(replica);

        if (NullColumnValueGetter.isNullURI(sourceURI)) {
            throw APIException.badRequests.replicaOperationNotAllowedNotAReplica(getReplicaType(replica),
                    replica.getLabel());
        }

        Volume sourceVol = _dbClient.queryObject(Volume.class, sourceURI);
        if (sourceVol != null && !sourceVol.getInactive() && sourceVol.getVolumeGroupIds() != null
                && sourceVol.getVolumeGroupIds().contains(volumeGroupUri.toString())) {
            return sourceURI;
        }

        throw APIException.badRequests.replicaOperationNotAllowedSourceNotInVolumeGroup(getReplicaType(replica),
                replica.getLabel());
    }

    /*
     * get all snapshot sets associated with the volume group
     */
    private VolumeGroupCopySetList getVolumeGroupSnapshotSets(VolumeGroup volumeGroup) {
        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        VolumeGroupCopySetList copySetList = new VolumeGroupCopySetList();
        Set<String> copySets = copySetList.getCopySets();

        // get snapshots for each volume in the group
        for (Volume volume : volumes) {
            if (volume.isVPlexVolume(_dbClient)) {
                volume = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient);
                if (volume == null || volume.getInactive()) {
                    log.warn("Cannot find backend volume for VPLEX volume {}", volume.getLabel());
                    continue;
                }
            }

            URIQueryResultList snapshotURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                    volume.getId()), snapshotURIs);
            if (!snapshotURIs.iterator().hasNext()) {
                continue;
            }

            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotURIs);
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot != null && !snapshot.getInactive()) {
                    String snapsetLabel = snapshot.getSnapsetLabel();
                    if (NullColumnValueGetter.isNotNullValue(snapsetLabel)) {
                        copySets.add(snapsetLabel);
                    }
                }
            }
        }

        return copySetList;
    }

    /**
     * Creates a volume group snapshot
     * Creates snapshot for all the array replication groups within this Application.
     * If partial flag is specified, it creates snapshot only for set of array replication groups.
     * A Volume from each array replication group can be provided to indicate which array replication
     * groups are required to take snapshot.
     *
     * @prereq none
     *
     * @param volumeGroupId the URI of the Volume Group
     *            - Volume group URI
     * @param param VolumeGroupSnapshotCreateParam
     *
     * @brief Create volume group snapshot
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createVolumeGroupSnapshot(@PathParam("id") final URI volumeGroupId,
            VolumeGroupSnapshotCreateParam param) {
        // Query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT);

        // validate name
        String name = param.getName();
        ArgValidator.checkFieldNotEmpty(name, NAME_FIELD);

        // snapsetLabel is normalized in RP, do it here too to avoid potential mismatch
        name = ResourceOnlyNameGenerator.removeSpecialCharsForName(name, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        if (StringUtils.isEmpty(name)) {
            // original name has special chars only
            throw APIException.badRequests.invalidCopySetName(param.getName(),
                    ReplicaTypeEnum.SNAPSHOT.toString());
        }

        // check name provided is not duplicate
        VolumeGroupCopySetList copySetList = getVolumeGroupSnapshotSets(volumeGroup);
        if (copySetList.getCopySets().contains(name)) {
            // duplicate name
            throw APIException.badRequests.duplicateCopySetName(param.getName(),
                    ReplicaTypeEnum.SNAPSHOT.toString());
        }

        // volumes to be processed
        List<Volume> volumes = null;

        if (param.getPartial()) {
            log.info("Snapshot requested for subset of array groups in Application.");

            // validate that at least one volume URI is provided
            ArgValidator.checkFieldNotEmpty(param.getVolumes(), VOLUMES_FIELD);

            volumes = new ArrayList<Volume>();
            // validate that provided volumes
            for (URI volumeURI : param.getVolumes()) {
                ArgValidator.checkFieldUriType(volumeURI, Volume.class, VOLUME_FIELD);
                // Get the volume
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                ArgValidator.checkEntity(volume, volumeURI, isIdEmbeddedInURL(volumeURI));

                // validate that provided volume is part of Volume Group
                if (!volume.getVolumeGroupIds().contains(volumeGroupId.toString())) {
                    throw APIException.badRequests
                            .replicaOperationNotAllowedVolumeNotInVolumeGroup(ReplicaTypeEnum.SNAPSHOT.toString(), volume.getLabel());
                }

                volumes.add(volume);
            }
        } else {
            log.info("Snapshot creation for entire Application");
            // get all volumes
            volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
            // validate that there should be some volumes in VolumeGroup
            if (volumes.isEmpty()) {
                throw APIException.badRequests.replicaOperationNotAllowedOnEmptyVolumeGroup(volumeGroup.getLabel(), ReplicaTypeEnum.SNAPSHOT.toString());
            }
        }

        auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP_SNAPSHOT, true, AuditLogManager.AUDITOP_BEGIN, volumeGroupId.toString(),
                name);
        TaskList taskList = new TaskList();

        /**
         * If there are VMAX3 volumes in the request, we need to create snap session for them.
         * For others, create snapshot.
         * 
         * vmax3Volumes - block VMAX3 or backend VMAX3 for VPLEX based on copy side requested
         * volumes - except volumes filtered out for above case
         */
        List<Volume> vmax3Volumes = getVMAX3Volumes(volumes, param.getCopyOnHighAvailabilitySide());

        // create snapshot
        Map<URI, List<URI>> cgToVolUris = ControllerUtils.groupVolumeURIsByCG(volumes);
        Set<Entry<URI, List<URI>>> entrySet = cgToVolUris.entrySet();
        for (Entry<URI, List<URI>> entry : entrySet) {
            URI cgUri = entry.getKey();
            log.info("Create snapshot with consistency group {}", cgUri);
            try {
                BlockConsistencyGroupSnapshotCreate cgSnapshotParam = new BlockConsistencyGroupSnapshotCreate(
                        name, entry.getValue(), param.getCreateInactive(), param.getReadOnly());
                TaskList cgTaskList = _blockConsistencyGroupService.createConsistencyGroupSnapshot(cgUri, cgSnapshotParam);
                List<TaskResourceRep> taskResourceRepList = cgTaskList.getTaskList();
                if (taskResourceRepList != null && !taskResourceRepList.isEmpty()) {
                    for (TaskResourceRep taskResRep : taskResourceRepList) {
                        taskList.addTask(taskResRep);
                    }
                }
            } catch (InternalException | APIException e) {
                log.error("Exception when creating snapshot with consistency group {}: {}", cgUri, e);
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnCG(_dbClient, cg,
                        ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT, e);
                taskList.addTask(task);
            } catch (Exception ex) {
                log.error("Unexpected Exception occurred while creating snapshot for consistency group {}: {}",
                        cgUri, ex);
            }
        }

        // create snapshot session for VMAX3
        Map<URI, List<URI>> cgToV3VolUris = ControllerUtils.groupVolumeURIsByCG(vmax3Volumes);
        Set<Entry<URI, List<URI>>> entrySetV3 = cgToV3VolUris.entrySet();
        for (Entry<URI, List<URI>> entry : entrySetV3) {
            URI cgUri = entry.getKey();
            log.info("Create snapshot session for consistency group {}, volumes {}",
                    cgUri, Joiner.on(',').join(entry.getValue()));
            try {
                // create snap session with No targets
                SnapshotSessionCreateParam cgSnapshotSessionParam = new SnapshotSessionCreateParam(
                        name, null, entry.getValue());
                taskList.getTaskList().addAll(
                        _blockConsistencyGroupService.createConsistencyGroupSnapshotSession(cgUri, cgSnapshotSessionParam)
                                .getTaskList());
            } catch (InternalException | APIException e) {
                log.error("Exception while creating snapshot session for consistency group {}: {}", cgUri, e);
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnCG(_dbClient, cg,
                        ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT_SESSION, e);
                taskList.addTask(task);
            } catch (Exception ex) {
                log.error("Unexpected Exception occurred while creating snapshot session for consistency group {}: {}",
                        cgUri, ex);
            }
        }

        auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP_SNAPSHOT, true, AuditLogManager.AUDITOP_END, volumeGroupId.toString(),
                name);

        return taskList;
    }

    /**
     * List snapshots for a volume group
     *
     * @prereq none
     * @param volumeGroupId The URI of the volume group.
     * @brief List snapshots for a volume group
     * @return SnapshotList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public SnapshotList getVolumeGroupSnapshots(@PathParam("id") final URI volumeGroupId) {
        // query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT);

        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        // get the snapshots for each volume in the group
        SnapshotList snapshotList = new SnapshotList();
        for (Volume volume : volumes) {
            if (volume.isVPlexVolume(_dbClient)) {
                volume = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient);
                if (volume == null || volume.getInactive()) {
                    log.warn("Cannot find backend volume for VPLEX volume {}", volume.getLabel());
                }
            }

            URIQueryResultList snapshotURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                    volume.getId()), snapshotURIs);
            if (!snapshotURIs.iterator().hasNext()) {
                continue;
            }

            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotURIs);
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot != null && !snapshot.getInactive()) {
                    snapshotList.getSnapList().add(toNamedRelatedResource(snapshot));
                }
            }
        }

        return snapshotList;
    }

   /**
     * Get the specified volume group snapshot
     *
     * @prereq none
     * @param volumeGroupId The URI of the volume group
     * @param snapshotId The URI of the snapshot
     * @brief Get the specified volume group snapshot
     * @return BlockSnapshotRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/{sid}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotRestRep getVolumeGroupSnapshot(@PathParam("id") final URI volumeGroupId,
            @PathParam("sid") URI snapshotId) {
        // query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT);

        // validate snapshot ID
        ArgValidator.checkFieldUriType(snapshotId, BlockSnapshot.class, SNAPSHOT_ID_FIELD);

        // get snapshot
        BlockSnapshot snapshot = _dbClient.queryObject(BlockSnapshot.class, snapshotId);
        ArgValidator.checkEntity(snapshot, snapshotId,isIdEmbeddedInURL(snapshotId), true);

        // validate that source of the provided snapshot is part of the volume group
        Volume volume = _dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
        if (volume == null || volume.getInactive() || !volume.getVolumeGroupIds().contains(volumeGroupId.toString())) {
            throw APIException.badRequests
                    .replicaOperationNotAllowedSourceNotInVolumeGroup(ReplicaTypeEnum.SNAPSHOT.toString(),
                            snapshot.getLabel());
        }

        return BlockMapper.map(_dbClient, snapshot);
    }

    /**
     * Get all snapshot set names for a volume group
     *
     * @prereq none
     * @param volumeGroupId The URI of the volume group
     * @brief List snapshot set names for a volume group
     * @return The list of snapshot set names for the volume group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/copy-sets")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public VolumeGroupCopySetList getVolumeGroupSnapshotSets(@PathParam("id") final URI volumeGroupId) {
        // query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        return getVolumeGroupSnapshotSets(volumeGroup);
    }

    /**
     * List snapshots in a snapshot set for a volume group
     *
     * @prereq none
     * @param volumeGroupId The URI of the volume group
     * @brief List snapshots in snapshot set for a volume group
     * @return SnapshotList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/copy-sets")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public SnapshotList getVolumeGroupSnapshotsForSet(@PathParam("id") final URI volumeGroupId, final VolumeGroupCopySetParam param) {
        // query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT);

        // validate copy set name
        String copySetName = param.getCopySetName();
        ArgValidator.checkFieldNotEmpty(copySetName, COPY_SET_NAME_FIELD);

        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);

        // get the snapshots for each volume in the group
        SnapshotList snapshotList = new SnapshotList();
        for (Volume volume : volumes) {
            if (volume.isVPlexVolume(_dbClient)) {
                volume = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient);
                if (volume == null || volume.getInactive()) {
                    log.warn("Cannot find backend volume for VPLEX volume {}", volume.getLabel());
                }
            }

            URIQueryResultList snapshotURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeSnapshotConstraint(
                    volume.getId()), snapshotURIs);
            if (!snapshotURIs.iterator().hasNext()) {
                continue;
            }

            List<BlockSnapshot> snapshots = _dbClient.queryObject(BlockSnapshot.class, snapshotURIs);
            for (BlockSnapshot snapshot : snapshots) {
                if (snapshot != null && !snapshot.getInactive()) {
                    if (copySetName.equals(snapshot.getSnapsetLabel())) {
                        snapshotList.getSnapList().add(toNamedRelatedResource(snapshot));
                    }
                }
            }
        }

        return snapshotList;
    }

    /**
     * Validate resources and group snapshots by snapsetLabel
     *
     * If partial, group snapshots in VolumeGroupSnapshotOperationParam by snapsetLabel
     * If full, find all the snapshots for each snapsetLabel that the snapshots in the param belong to
     *
     * @param volumeGroupId
     * @param param
     * @return map snapsetLabel to snapshots
     */
    private Map<String, List<BlockSnapshot>> getSnapshotsGroupedBySnapset(final URI volumeGroupId, VolumeGroupSnapshotOperationParam param) {
        // Query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT);

        // validate that at least one snapshot URI is provided
        ArgValidator.checkFieldNotEmpty(param.getSnapshots(), SNAPSHOTS_FIELD);

        // validate only one snapshot URI is provided for full request
        if (!param.getPartial() && param.getSnapshots().size() > 1) {
            throw APIException.badRequests.invalidNumberOfReplicas();
        }

        Map<String, List<BlockSnapshot>> snapsetToSnapshots = new HashMap<String, List<BlockSnapshot>>();
        for (URI snapshotURI : param.getSnapshots()) {
            ArgValidator.checkFieldUriType(snapshotURI, BlockSnapshot.class, SNAPSHOT_FIELD);

            // Get the snapshot
            BlockSnapshot snapshot = BlockServiceUtils.querySnapshotResource(snapshotURI, uriInfo, _dbClient);
            if (NullColumnValueGetter.isNullValue(snapshot.getReplicationGroupInstance())) {
                throw APIException.badRequests.noReplicationGroupForReplica(snapshot.getLabel());
            }

            // validate that source of the provided snapshot is part of the volume group
            if (!ControllerUtils.isSourceInVoumeGroup(snapshot, volumeGroupId, _dbClient)) {
                throw APIException.badRequests.replicaOperationNotAllowedSourceNotInVolumeGroup(ReplicaTypeEnum.SNAPSHOT.toString(),
                        snapshot.getLabel());
            }

            String snapsetLabel = snapshot.getSnapsetLabel();
            List<BlockSnapshot> snapshots = snapsetToSnapshots.get(snapsetLabel);
            if (snapshots == null) {
                if (param.getPartial()) {
                    snapshots = new ArrayList<BlockSnapshot>();
                    snapshots.add(snapshot);
                } else {
                    snapshots = ControllerUtils.getVolumeGroupSnapshots(volumeGroup.getId(), snapsetLabel, _dbClient);
                }

                snapsetToSnapshots.put(snapsetLabel, snapshots);
            } else if (param.getPartial()) {
                snapshots.add(snapshot);
            }
        }

        return snapsetToSnapshots;
    }

    /*
     * Wrapper of BlockConsistencyGroupService methods for snapshot operations
     *
     * @param volumeGroupId
     * @param param
     * @return a TaskList
     */
    private TaskList performVolumeGroupSnapshotOperation(final URI volumeGroupId, final VolumeGroupSnapshotOperationParam param, OperationTypeEnum opType) {
        Map<String, List<BlockSnapshot>> snapsetToSnapshots = getSnapshotsGroupedBySnapset(volumeGroupId, param);

        auditOp(opType, true, AuditLogManager.AUDITOP_BEGIN,
                volumeGroupId.toString(), param.getSnapshots());
        TaskList taskList = new TaskList();

        Set<Entry<String, List<BlockSnapshot>>> entrySet = snapsetToSnapshots.entrySet();
        for (Entry<String, List<BlockSnapshot>> entry : entrySet) {
            Table<URI, String, BlockSnapshot> storageRgToSnapshot = ControllerUtils.getSnapshotForStorageReplicationGroup(entry.getValue());
            for (Cell<URI, String, BlockSnapshot> cell : storageRgToSnapshot.cellSet()) {
                log.info("{} for replication group {}", opType.getDescription(), cell.getColumnKey());
                try {
                    BlockSnapshot snapshot = cell.getValue();
                    URI cgUri = snapshot.getConsistencyGroup();
                    URI snapshotUri = snapshot.getId();
                    switch (opType) {
                        case ACTIVATE_VOLUME_GROUP_SNAPSHOT:
                            taskList.addTask(_blockConsistencyGroupService.activateConsistencyGroupSnapshot(cgUri, snapshotUri));
                            break;
                        case RESTORE_VOLUME_GROUP_SNAPSHOT:
                            taskList.addTask(_blockConsistencyGroupService.restoreConsistencyGroupSnapshot(cgUri, snapshotUri));
                            break;
                        case RESYNCHRONIZE_VOLUME_GROUP_SNAPSHOT:
                            taskList.addTask(_blockConsistencyGroupService.resynchronizeConsistencyGroupSnapshot(cgUri, snapshotUri));
                            break;
                        case DEACTIVATE_VOLUME_GROUP_SNAPSHOT:
                            TaskList cgTaskList = _blockConsistencyGroupService.deactivateConsistencyGroupSnapshot(cgUri, snapshotUri);
                            List<TaskResourceRep> taskResourceRepList = cgTaskList.getTaskList();
                            if (taskResourceRepList != null && !taskResourceRepList.isEmpty()) {
                                for (TaskResourceRep taskResRep : taskResourceRepList) {
                                    taskList.addTask(taskResRep);
                                }
                            }
                            break;
                        default:
                            log.error("Unsupported operation {}", opType.getDescription());
                            break;
                    }
                } catch (InternalException | APIException e) {
                    log.error("Exception on {} for replication group {}: {}", opType.getDescription(), cell.getColumnKey(), e.getMessage());
                }
            }
        }

        auditOp(opType, true, AuditLogManager.AUDITOP_END, volumeGroupId.toString(), param.getSnapshots());
        return taskList;
    }

    /**
     * Activate the specified Volume group snapshot
     * Activates snapshot for all the array replication groups within this Application.
     * If partial flag is specified, it activates snapshot only for set of array replication groups.
     * A snapshot from each array replication group can be provided to indicate which array replication
     * groups's snapshots needs to be activated.
     *
     * @prereq Create volume group snapshot
     *
     * @param volumeGroupId The URI of the volume group
     * @param param VolumeGroupSnapshotOperationParam
     *
     * @brief Activate volume group snapshot
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/activate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList activateVolumeGroupSnapshot(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupSnapshotOperationParam param) {
        return performVolumeGroupSnapshotOperation(volumeGroupId, param, OperationTypeEnum.ACTIVATE_VOLUME_GROUP_SNAPSHOT);
    }

    /**
     * Deactivate the specified Volume group snapshot
     * Deactivates snapshot for all the array replication groups within this Application.
     * If partial flag is specified, it deactivates snapshot only for set of array replication groups.
     * A snapshot from each array replication group can be provided to indicate which array replication
     * groups's snapshots needs to be deactivated.
     *
     * @prereq Create volume group snapshot
     *
     * @param volumeGroupId The URI of the volume group
     * @param param VolumeGroupSnapshotOperationParam
     *
     * @brief Deactivate volume group snapshot
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateVolumeGroupSnapshot(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupSnapshotOperationParam param) {
        return performVolumeGroupSnapshotOperation(volumeGroupId, param, OperationTypeEnum.DEACTIVATE_VOLUME_GROUP_SNAPSHOT);
    }

    /**
     * Restore the specified Volume group snapshot
     * Restores snapshot for all the array replication groups within this Application.
     * If partial flag is specified, it restores snapshot only for set of array replication groups.
     * A snapshot from each array replication group can be provided to indicate which array replication
     * groups's snapshots needs to be restored.
     *
     * @prereq Create volume group snapshot
     *
     * @param volumeGroupId The URI of the volume group
     * @param param VolumeGroupSnapshotOperationParam
     *
     * @brief Restore volume group snapshot
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList restoreVolumeGroupSnapshot(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupSnapshotOperationParam param) {
        return performVolumeGroupSnapshotOperation(volumeGroupId, param, OperationTypeEnum.RESTORE_VOLUME_GROUP_SNAPSHOT);
    }

    /**
     * Resynchronize the specified Volume group snapshot
     * Resynchronizes snapshot for all the array replication groups within this Application.
     * If partial flag is specified, it resynchronizes snapshot only for set of array replication groups.
     * A snapshot from each array replication group can be provided to indicate which array replication
     * groups's snapshots needs to be resynchronized.
     *
     * @prereq Create volume group snapshot
     *
     * @param volumeGroupId The URI of the volume group
     * @param param VolumeGroupSnapshotOperationParam
     *
     * @brief Resynchronize volume group snapshot
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshots/resynchronize")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList resynchronizeVolumeGroupSnapshot(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupSnapshotOperationParam param) {
        return performVolumeGroupSnapshotOperation(volumeGroupId, param, OperationTypeEnum.RESYNCHRONIZE_VOLUME_GROUP_SNAPSHOT);
    }

    /**
     * Separates the VMAX3 volumes from the given list.
     * 
     * If there are VMAX3 volumes in the request, we need to create snap session for them.
     * For others, create snapshot.
     * VMAX could be either the 'block' or back-end for VPLEX virtual volume.
     *
     * @param vgVolumes the volume group volumes
     * @param copyOnHighAvailabilitySide side where to take snap in case of VPLEX Distributed volumes
     * @return the VMAX3 volumes
     */
    private List<Volume> getVMAX3Volumes(List<Volume> vgVolumes, boolean copyOnHighAvailabilitySide) {
        List<Volume> vmax3Volumes = new ArrayList<Volume>();
        Iterator<Volume> itr = vgVolumes.iterator();
        while (itr.hasNext()) {
            Volume volume = itr.next();
            URI systemURI = volume.getStorageController();
            if (volume.isVPlexVolume(_dbClient)) {
                Volume backedVol = null;
                if (copyOnHighAvailabilitySide) {
                    // get backend HA volume, copy is requested on HA side of VPLEX
                    backedVol = VPlexUtil.getVPLEXBackendVolume(volume, false, _dbClient);
                    if (backedVol == null || backedVol.getInactive()) {
                        throw APIException.badRequests.noHAVolumeFoundForVPLEX(volume.getLabel());
                    }
                } else {
                    // get backend source volume
                    backedVol = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient);
                }
                systemURI = backedVol.getStorageController();
            }
            StorageSystem system = _permissionsHelper.getObjectById(systemURI, StorageSystem.class);
            if (system.checkIfVmax3()) {
                vmax3Volumes.add(volume);
                // remove vmax3 volume from given list
                itr.remove();
            }
        }
        return vmax3Volumes;
    }

    /*
     * get all snapshot session set names associated with the volume group
     */
    private VolumeGroupCopySetList getVolumeGroupSnapsetSessionSets(VolumeGroup volumeGroup) {
        VolumeGroupCopySetList copySetList = new VolumeGroupCopySetList();
        Set<String> copySets = copySetList.getCopySets();

        // get all snapshot sessions for the volume group
        List<BlockSnapshotSession> volumeGroupSessions = getVolumeGroupSnapshotSessions(volumeGroup);

        for (BlockSnapshotSession session : volumeGroupSessions) {
            String sessionsetLabel = session.getSessionSetName();
            if (NullColumnValueGetter.isNotNullValue(sessionsetLabel)) {
                copySets.add(sessionsetLabel);
            }
        }

        return copySetList;
    }

    /**
     * Creates a volume group snapshot session
     * - Creates snapshot session for all the array replication groups within this Application.
     * - If partial flag is specified, it creates snapshot session only for set of array replication groups.
     * A Volume from each array replication group can be provided to indicate which array replication
     * groups are required to take snapshot session.
     *
     * @prereq none
     *
     * @param volumeGroupId the URI of the Volume Group
     *            - Volume group URI
     * @param param VolumeGroupSnapshotSessionCreateParam
     *
     * @brief Create volume group snapshot session
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN }, acls = { ACL.ANY })
    public TaskList createVolumeGroupSnapshotSession(@PathParam("id") final URI volumeGroupId,
            VolumeGroupSnapshotSessionCreateParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, ID_FIELD);
        // Query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT_SESSION);

        // validate name
        String name = param.getName();
        ArgValidator.checkFieldNotEmpty(name, NAME_FIELD);

        name = ResourceOnlyNameGenerator.removeSpecialCharsForName(name, SmisConstants.MAX_SNAPSHOT_NAME_LENGTH);
        if (StringUtils.isEmpty(name)) {
            // original name has special chars only
            throw APIException.badRequests.invalidCopySetName(param.getName(),
                    ReplicaTypeEnum.SNAPSHOT_SESSION.toString());
        }

        // check name provided is not duplicate
        VolumeGroupCopySetList sessionSet = getVolumeGroupSnapsetSessionSets(volumeGroup);
        if (sessionSet.getCopySets().contains(name)) {
            // duplicate name
            throw APIException.badRequests.duplicateCopySetName(param.getName(),
                    ReplicaTypeEnum.SNAPSHOT_SESSION.toString());
        }

        // volumes to be processed
        List<Volume> volumes = null;

        if (param.getPartial()) {
            log.info("Snapshot Session requested for subset of array groups in Application.");

            // validate that at least one volume URI is provided
            ArgValidator.checkFieldNotEmpty(param.getVolumes(), VOLUMES_FIELD);

            volumes = new ArrayList<Volume>();
            // validate the provided volumes
            for (URI volumeURI : param.getVolumes()) {
                ArgValidator.checkFieldUriType(volumeURI, Volume.class, VOLUME_FIELD);
                // Get the volume
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
                ArgValidator.checkEntity(volume, volumeURI, isIdEmbeddedInURL(volumeURI));

                // validate that provided volume is part of Volume Group
                if (!volume.getVolumeGroupIds().contains(volumeGroupId.toString())) {
                    throw APIException.badRequests
                            .replicaOperationNotAllowedVolumeNotInVolumeGroup(ReplicaTypeEnum.SNAPSHOT_SESSION.toString(),
                                    volume.getLabel());
                }

                volumes.add(volume);
            }
        } else {
            log.info("Snapshot Session creation for entire Application");
            // get all volumes
            volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
            // validate that there should be some volumes in VolumeGroup
            if (volumes.isEmpty()) {
                throw APIException.badRequests.replicaOperationNotAllowedOnEmptyVolumeGroup(volumeGroup.getLabel(),
                        ReplicaTypeEnum.SNAPSHOT_SESSION.toString());
            }
        }

        auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP_SNAPSHOT_SESSION, true, AuditLogManager.AUDITOP_BEGIN, volumeGroupId.toString(),
                name);
        TaskList taskList = new TaskList();

        Map<URI, List<URI>> cgToVolUris = ControllerUtils.groupVolumeURIsByCG(volumes);
        Set<Entry<URI, List<URI>>> entrySet = cgToVolUris.entrySet();
        for (Entry<URI, List<URI>> entry : entrySet) {
            URI cgUri = entry.getKey();
            log.info("Create snapshot session for consistency group {}, volumes {}",
                    cgUri, Joiner.on(',').join(entry.getValue()));
            try {
                SnapshotSessionCreateParam cgSnapshotSessionParam = new SnapshotSessionCreateParam(
                        name, param.getNewLinkedTargets(), entry.getValue());
                taskList.getTaskList().addAll(
                        _blockConsistencyGroupService.createConsistencyGroupSnapshotSession(cgUri, cgSnapshotSessionParam)
                                .getTaskList());
            } catch (InternalException | APIException e) {
                log.error("Exception while creating snapshot session for consistency group {}: {}", cgUri, e);
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgUri);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnCG(_dbClient, cg,
                        ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_SNAPSHOT_SESSION, e);
                taskList.addTask(task);
            } catch (Exception ex) {
                log.error("Unexpected Exception occurred while creating snapshot session for consistency group {}: {}",
                        cgUri, ex);
            }
        }

        auditOp(OperationTypeEnum.CREATE_VOLUME_GROUP_SNAPSHOT_SESSION, true, AuditLogManager.AUDITOP_END, volumeGroupId.toString(),
                name);

        return taskList;
    }

    /**
     * List snapshot sessions for a volume group
     *
     * @param volumeGroupId The URI of the volume group.
     * @brief List snapshot session for a volume group
     * @return BlockSnapshotSessionList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotSessionList getVolumeGroupSnapshotSessions(@PathParam("id") final URI volumeGroupId) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, ID_FIELD);
        // query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // get the snapshot sessions for each CG in the volume group
        BlockSnapshotSessionList snapshotSessionList = new BlockSnapshotSessionList();

        // get all snapshot sessions for the volume group
        List<BlockSnapshotSession> volumeGroupSessions = getVolumeGroupSnapshotSessions(volumeGroup);

        for (BlockSnapshotSession session : volumeGroupSessions) {
            snapshotSessionList.getSnapSessionRelatedResourceList().
                    add(toNamedRelatedResource(session));
        }

        return snapshotSessionList;
    }

    /**
     * Gets the volume group snapshot sessions.
     *
     * @param volumeGroup the volume group
     * @return the volume group snapshot sessions
     */
    private List<BlockSnapshotSession> getVolumeGroupSnapshotSessions(final VolumeGroup volumeGroup) {
        // get all volumes
        List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
        
        // get all RG names
        Set<String> rgNames = CopyVolumeGroupUtils.getReplicationGroupNames(volumeGroup, _dbClient);

        /**
         * Get all CGs involved
         * Query all snap sessions for CGs
         * Filter Sessions whose RG name does not match with volumes' RG names in VolumeGroup
         */
        Set<URI> cgIds = ControllerUtils.groupVolumeURIsByCG(volumes).keySet();
        List<BlockSnapshotSession> volumeGroupSessions = new ArrayList<BlockSnapshotSession>();
        for (URI cgId : cgIds) {
            BlockConsistencyGroup consistencyGroup = _permissionsHelper.getObjectById(cgId,
                    BlockConsistencyGroup.class);
            List<BlockSnapshotSession> cgSessions = getSnapshotSessionManager().
                    getSnapshotSessionsForCG(consistencyGroup);

            // filter Sessions for RGs which are not part of this application
            for (BlockSnapshotSession session : cgSessions) {
                if (!session.getInactive() &&
                        rgNames.contains(session.getReplicationGroupInstance())) {
                    volumeGroupSessions.add(session);
                }
            }
        }
        return volumeGroupSessions;
    }

    /**
     * Get the specified volume group snapshot session.
     *
     * @param volumeGroupId The URI of the volume group.
     * @param snapshotIsnapshotSessionIdd The URI of the snapshot session.
     * @brief Get the specified volume group snapshot session.
     * @return BlockSnapshotSessionRestRep.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/{sid}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotSessionRestRep getVolumeGroupSnapshotSession(@PathParam("id") final URI volumeGroupId,
            @PathParam("sid") final URI snapshotSessionId) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, ID_FIELD);
        // query Volume Group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT);

        // get snapshot session
        BlockSnapshotSession snapSession = BlockSnapshotSessionUtils.querySnapshotSession(snapshotSessionId, uriInfo, _dbClient, true);

        // validate that the provided snapshot session is part of the volume group
        validateSnapSessionBelongsToApplication(snapSession, volumeGroup);

        return map(_dbClient, snapSession);
    }

    /**
     * Get all snapshot session set names for a volume group.
     *
     * @param volumeGroupId The URI of the volume group
     *
     * @brief List snapsetLabels for a volume group
     * 
     * @return VolumeGroupCopySetList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/copy-sets")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public VolumeGroupCopySetList getVolumeGroupSnapsetSessionSets(@PathParam("id") final URI volumeGroupId) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, ID_FIELD);
        // query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);
        return getVolumeGroupSnapsetSessionSets(volumeGroup);
    }

    /**
     * List snapshot sessions in a session set for a volume group.
     *
     * @param volumeGroupId The URI of the volume group
     * @param param the VolumeGroupCopySetParam containing set name
     * @return BlockSnapshotSessionList
     * @brief List snapshot sessions in session set for a volume group
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/copy-sets")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public BlockSnapshotSessionList getVolumeGroupSnapshotSessionsByCopySet(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupCopySetParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, ID_FIELD);
        // query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate snap session set name
        String sessionsetName = param.getCopySetName();
        ArgValidator.checkFieldNotNull(sessionsetName, COPY_SET_NAME_FIELD);

        // get the snapshot sessions for the given set name in the volume group
        BlockSnapshotSessionList snapshotSessionList = new BlockSnapshotSessionList();

        // validate that the provided set name actually belongs to this Application
        VolumeGroupCopySetList copySetList = getVolumeGroupSnapsetSessionSets(volumeGroup);
        if (!copySetList.getCopySets().contains(sessionsetName)) {
            throw APIException.badRequests.
                    setNameDoesNotBelongToVolumeGroup("Snapshot Session Set name", sessionsetName, volumeGroup.getLabel());
        }

        // get the snapshot sessions for the volume group
        List<BlockSnapshotSession> volumeGroupSessions = getVolumeGroupSnapshotSessions(volumeGroup);

        for (BlockSnapshotSession session : volumeGroupSessions) {
            if (sessionsetName.equals(session.getSessionSetName())) {
                snapshotSessionList.getSnapSessionRelatedResourceList().
                        add(toNamedRelatedResource(session));
            }
        }

        return snapshotSessionList;
    }

    /**
     * Validate resources and return a list of snapshot sessions for set name
     *
     * If partial, validate snapshot sessions in VolumeGroupSnapshotOperationParam belonging to multiple sets
     * If full, find all snapshot sessions for the set name in the provided snapshot session
     *
     * @param volumeGroupId
     * @param param VolumeGroupSnapshotSessionOperationParam
     * @return list of snapshot sessions
     */
    private List<BlockSnapshotSession> getSnapshotSessionsGroupedBySnapSessionset(final URI volumeGroupId,
            VolumeGroupSnapshotSessionOperationParam param) {
        ArgValidator.checkFieldUriType(volumeGroupId, VolumeGroup.class, "id");
        // Query volume group
        final VolumeGroup volumeGroup = (VolumeGroup) queryResource(volumeGroupId);

        // validate replica operation for volume group
        validateCopyOperationForVolumeGroup(volumeGroup, ReplicaTypeEnum.SNAPSHOT_SESSION);

        // validate that at least one snapshot URI is provided
        ArgValidator.checkFieldNotEmpty(param.getSnapshotSessions(), SNAPSHOT_SESSIONS_FIELD);

        List<BlockSnapshotSession> snapSessions = new ArrayList<BlockSnapshotSession>();
        Set<String> setNames = new HashSet<String>();
        for (URI sessionURI : param.getSnapshotSessions()) {
            ArgValidator.checkFieldUriType(sessionURI, BlockSnapshotSession.class, SNAPSHOT_SESSION_FIELD);
            // Get the snapshot session
            BlockSnapshotSession session = BlockSnapshotSessionUtils.querySnapshotSession(sessionURI, uriInfo, _dbClient, true);

            // validate that source of the provided snapshot session is part of the volume group
            validateSnapSessionBelongsToApplication(session, volumeGroup);

            String setName = session.getSessionSetName();
            setNames.add(setName);

            if (param.getPartial()) {
                snapSessions.add(session);
            } else {
                log.info("Snapshot Session operation requested for entire Application, Considering session {} in request.",
                        session.getLabel());
                // get the snapshot sessions for the volume group
                List<BlockSnapshotSession> volumeGroupSessions = getVolumeGroupSnapshotSessions(volumeGroup);
                for (BlockSnapshotSession vgSession : volumeGroupSessions) {
                    if (setName.equals(vgSession.getSessionSetName())) {
                        snapSessions.add(vgSession);
                    }
                }
            }
        }

        if (setNames.size() > 1) {
            throw APIException.badRequests.multipleSetNamesProvided(ReplicaTypeEnum.SNAPSHOT_SESSION.toString());
        }
        return snapSessions;
    }

    /*
     * Wrapper of BlockConsistencyGroupService methods for snapshot session operations
     * 
     * @param volumeGroupId
     * 
     * @param param
     * 
     * @return a TaskList
     */
    private TaskList performVolumeGroupSnapshotSessionOperation(final URI volumeGroupId,
            final VolumeGroupSnapshotSessionOperationParam param,
            OperationTypeEnum opType) {

        List<BlockSnapshotSession> snapSessions = getSnapshotSessionsGroupedBySnapSessionset(volumeGroupId, param);

        auditOp(opType, true, AuditLogManager.AUDITOP_BEGIN,
                volumeGroupId.toString(), param.getSnapshotSessions());
        TaskList taskList = new TaskList();

        Table<URI, String, BlockSnapshotSession> storageRgToSnapshot = ControllerUtils.
                getSnapshotSessionForStorageReplicationGroup(snapSessions, _dbClient);
        for (Cell<URI, String, BlockSnapshotSession> cell : storageRgToSnapshot.cellSet()) {
            BlockSnapshotSession session = cell.getValue();
            log.info("{} for replication group {}", opType.getDescription(), cell.getColumnKey());
            ResourceOperationTypeEnum oprEnum = null;
            try {
                URI cgUri = session.getConsistencyGroup();  // should not be null
                URI sessionUri = session.getId();
                log.info("CG: {}, Session: {}", cgUri, session.getLabel());
                switch (opType) {
                    case RESTORE_VOLUME_GROUP_SNAPSHOT_SESSION:
                        oprEnum = ResourceOperationTypeEnum.RESTORE_SNAPSHOT_SESSION;
                        taskList.addTask(
                                _blockConsistencyGroupService.restoreConsistencyGroupSnapshotSession(cgUri, sessionUri));
                        break;
                    case DELETE_VOLUME_GROUP_SNAPSHOT_SESSION:
                        oprEnum = ResourceOperationTypeEnum.DELETE_CONSISTENCY_GROUP_SNAPSHOT_SESSION;
                        taskList.getTaskList().addAll(
                                _blockConsistencyGroupService.deactivateConsistencyGroupSnapshotSession(cgUri, sessionUri)
                                        .getTaskList());
                        break;
                    case LINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET:
                        oprEnum = ResourceOperationTypeEnum.LINK_SNAPSHOT_SESSION_TARGETS;
                        SnapshotSessionLinkTargetsParam linkParam = new SnapshotSessionLinkTargetsParam(
                                ((VolumeGroupSnapshotSessionLinkTargetsParam) param).getNewLinkedTargets());
                        taskList.getTaskList().addAll(
                                _blockConsistencyGroupService.linkTargetVolumes(cgUri, sessionUri, linkParam)
                                        .getTaskList());
                        break;
                    case RELINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET:
                        oprEnum = ResourceOperationTypeEnum.RELINK_CONSISTENCY_GROUP_SNAPSHOT_SESSION_TARGETS;
                        SnapshotSessionRelinkTargetsParam relinkParam = new SnapshotSessionRelinkTargetsParam(
                                getRelinkTargetIdsForSession((VolumeGroupSnapshotSessionRelinkTargetsParam) param, session));
                        taskList.getTaskList().addAll(
                                _blockConsistencyGroupService.relinkTargetVolumes(cgUri, sessionUri, relinkParam)
                                        .getTaskList());
                        break;
                    case UNLINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET:
                        oprEnum = ResourceOperationTypeEnum.UNLINK_SNAPSHOT_SESSION_TARGETS;
                        SnapshotSessionUnlinkTargetsParam unlinkParam = new SnapshotSessionUnlinkTargetsParam(
                                getUnlinkTargetIdsForSession((VolumeGroupSnapshotSessionUnlinkTargetsParam) param, session));
                        taskList.addTask(
                                _blockConsistencyGroupService.unlinkTargetVolumesForSession(cgUri, sessionUri, unlinkParam));
                        break;
                    default:
                        log.error("Unsupported operation {}", opType.getDescription());
                        break;
                }
            } catch (InternalException | APIException e) {
                String errMsg = String.format("Exception occurred while performing %s on Replication group %s",
                        opType.getDescription(), cell.getColumnKey());
                log.error(errMsg, e);
                TaskResourceRep task = BlockServiceUtils.createFailedTaskOnSnapshotSession(_dbClient, session, oprEnum, e);
                taskList.addTask(task);
            } catch (Exception ex) {
                String errMsg = String.format("Unexpected Exception occurred while performing %s on Replication group %s",
                        opType.getDescription(), cell.getColumnKey());
                log.error(errMsg, ex);
            }
        }

        auditOp(opType, true, AuditLogManager.AUDITOP_END, volumeGroupId.toString(), param.getSnapshotSessions());
        return taskList;
    }

    /**
     * Gets the relink target ids for the given session.
     *
     * @param param the VolumeGroupSnapshotSessionRelinkTargetsParam
     * @param session the snap session
     * @return the relink target ids for session
     */
    private List<URI> getRelinkTargetIdsForSession(final VolumeGroupSnapshotSessionRelinkTargetsParam param,
            BlockSnapshotSession session) {
        List<URI> targetIds = new ArrayList<URI>();
        StringSet sessionTargets = session.getLinkedTargets();
        for (URI snapURI : param.getLinkedTargetIds()) {
            // Snapshot session targets are represented by BlockSnapshot instances in ViPR.
            ArgValidator.checkFieldUriType(snapURI, BlockSnapshot.class, "id");
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapURI);
            ArgValidator.checkEntityNotNull(snap, snapURI, isIdEmbeddedInURL(snapURI));
            if (sessionTargets != null && sessionTargets.contains(snapURI.toString())) {
                targetIds.add(snapURI);
            }
        }
        log.info(String.format("Target ids for snapshot session %s : %s",
                session.getLabel(), Joiner.on(',').join(targetIds)));
        if (targetIds.isEmpty()) {
            // None of the provided target belong to this snapshot session.
            throw APIException.badRequests.snapshotSessionDoesNotHaveAnyTargets(session.getId().toString());
        }
        return targetIds;
    }

    /**
     * Gets the unlink target ids for the given session.
     *
     * @param param the VolumeGroupSnapshotSessionUnlinkTargetsParam
     * @param session the snap session
     * @return the unlink target id params for session
     */
    private List<SnapshotSessionUnlinkTargetParam> getUnlinkTargetIdsForSession(final VolumeGroupSnapshotSessionUnlinkTargetsParam param,
            BlockSnapshotSession session) {
        List<SnapshotSessionUnlinkTargetParam> targetIds = new ArrayList<SnapshotSessionUnlinkTargetParam>();
        List<URI> selectedURIs = new ArrayList<URI>();
        StringSet sessionTargets = session.getLinkedTargets();
        for (SnapshotSessionUnlinkTargetParam unlinkTarget : param.getLinkedTargets()) {
            URI snapURI = unlinkTarget.getId();
            // Snapshot session targets are represented by BlockSnapshot instances in ViPR.
            ArgValidator.checkFieldUriType(snapURI, BlockSnapshot.class, "id");
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapURI);
            ArgValidator.checkEntityNotNull(snap, snapURI, isIdEmbeddedInURL(snapURI));
            if (sessionTargets != null && sessionTargets.contains(snapURI.toString())) {
                targetIds.add(unlinkTarget);
                selectedURIs.add(snapURI);
            }
        }
        log.info(String.format("Target ids for snapshot session %s : %s",
                session.getLabel(), Joiner.on(',').join(selectedURIs)));
        if (targetIds.isEmpty()) {
            // None of the provided target belong to this snapshot session.
            throw APIException.badRequests.snapshotSessionDoesNotHaveAnyTargets(session.getId().toString());
        }
        return targetIds;
    }

    /**
     * Deactivate the specified Volume Group Snapshot Sessions
     * - Deactivates snapshot sessions for all the array replication groups within this Application.
     * - If partial flag is specified, it deactivates snapshot session only for set of array replication groups.
     * A snapshot session from each array replication group can be provided to indicate which array replication
     * groups's sessions needs to be deactivated.
     * 
     * @prereq Create volume group snapshot session.
     * 
     * @param volumeGroupId The URI of the volume group.
     * @param param VolumeGroupSnapshotSessionDeactivateParam.
     *
     * @brief Deactivate volume group snapshot sessions.
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList deactivateVolumeGroupSnapshotSession(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupSnapshotSessionDeactivateParam param) {
        return performVolumeGroupSnapshotSessionOperation(volumeGroupId, param,
                OperationTypeEnum.DELETE_VOLUME_GROUP_SNAPSHOT_SESSION);
    }

    /**
     * Restores the specified Volume Group Snapshot Sessions to the source object.
     * - Restores snapshot sessions for all the array replication groups within this Application.
     * - If partial flag is specified, it restores snapshot session only for set of array replication groups.
     * A snapshot session from each array replication group can be provided to indicate which array replication
     * groups's sessions needs to be restored.
     * 
     * @prereq None
     * 
     * @param volumeGroupId The URI of the volume group.
     * @param param VolumeGroupSnapshotSessionRestoreParam.
     *
     * @brief Restore volume group snapshot sessions to source.
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/restore")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList restoreVolumeGroupSnapshotSession(@PathParam("id") final URI volumeGroupId,
            final VolumeGroupSnapshotSessionRestoreParam param) {
        return performVolumeGroupSnapshotSessionOperation(volumeGroupId, param,
                OperationTypeEnum.RESTORE_VOLUME_GROUP_SNAPSHOT_SESSION);
    }

    /**
     * The method implements the API to create and link new target volumes
     * to an existing BlockSnapshotSession instances in the volume group.
     * - Links target volumes to snapshot sessions for all the array replication groups within this Application.
     * - If partial flag is specified, it links targets to snapshot session only for set of array replication groups.
     * A snapshot session from each array replication group can be provided to indicate which array replication
     * groups's sessions needs to be linked.
     * 
     * @prereq The block snapshot session has been created and the maximum
     *         number of targets has not already been linked to the snapshot sessions
     *         for the source object.
     * 
     * @param volumeGroupId The URI of the volume group.
     * @param param VolumeGroupSnapshotSessionLinkTargetsParam.
     *
     * @brief Link target volumes to volume group snapshot sessions
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/link-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList linkTargetVolumesForVolumeGroupSession(@PathParam("id") final URI volumeGroupId,
            VolumeGroupSnapshotSessionLinkTargetsParam param) {
        return performVolumeGroupSnapshotSessionOperation(volumeGroupId, param,
                OperationTypeEnum.LINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET);
    }

    /**
     * The method implements the API to re-link a target to either it's current snapshot sessions
     * or to a different snapshot sessions of the same source in the volume group.
     * - Re-links targets for all the array replication groups within this Application.
     * - If partial flag is specified, it re-links targets only for set of array replication groups.
     * A snapshot session from each array replication group can be provided to indicate which array replication
     * groups's sessions needs to be linked.
     * 
     * @prereq The target volumes are linked to a snapshot session of the same source object.
     * 
     * @param volumeGroupId The URI of the volume group.
     * @param param VolumeGroupSnapshotSessionRelinkTargetsParam.
     *
     * @brief Re-link target volumes to volume group snapshot sessions
     *
     * @return TaskList
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/relink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList relinkTargetVolumesForVolumeGroupSession(@PathParam("id") final URI volumeGroupId,
            VolumeGroupSnapshotSessionRelinkTargetsParam param) {
        return performVolumeGroupSnapshotSessionOperation(volumeGroupId, param,
                OperationTypeEnum.RELINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET);
    }
    
    /**
     * The method implements the API to unlink target volumes from an existing
     * BlockSnapshotSession instances and optionally delete those target volumes in the volume group.
     * - Unlinks target volumes from snapshot sessions for all the array replication groups within this Application.
     * - If partial flag is specified, it unlinks targets from snapshot session only for set of array replication groups.
     * A snapshot session from each array replication group can be provided to indicate which array replication
     * groups's sessions needs to be unlinked.
     * 
     * @prereq A snapshot session is created and target volumes have previously
     *         been linked to that snapshot session.
     * 
     * @param volumeGroupId The URI of the volume group.
     * @param param VolumeGroupSnapshotSessionUnlinkTargetsParam.
     *
     * @brief Unlink target volumes from volume group snapshot sessions
     *
     * @return TaskList
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/protection/snapshot-sessions/unlink-targets")
    @CheckPermission(roles = { Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public TaskList unlinkTargetVolumesForVolumeGroupSession(@PathParam("id") final URI volumeGroupId,
            VolumeGroupSnapshotSessionUnlinkTargetsParam param) {
        return performVolumeGroupSnapshotSessionOperation(volumeGroupId, param,
                OperationTypeEnum.UNLINK_VOLUME_GROUP_SNAPSHOT_SESSION_TARGET);
    }

    /**
     * Validates that the snap session belongs to the given application.
     *
     * @param session the snapshot session
     * @param volumeGroup the volume group
     */
    private void validateSnapSessionBelongsToApplication(BlockSnapshotSession session, VolumeGroup volumeGroup) {
        List<BlockSnapshotSession> volumeGroupSessions = getVolumeGroupSnapshotSessions(volumeGroup);
        Set<URI> sessionURIs = new HashSet<URI>();
        for (BlockSnapshotSession snapSession : volumeGroupSessions) {
            sessionURIs.add(snapSession.getId());
        }
        if (!sessionURIs.contains(session.getId())) {
            throw APIException.badRequests
                    .replicaOperationNotAllowedSourceNotInVolumeGroup(ReplicaTypeEnum.SNAPSHOT_SESSION.toString(),
                            session.getLabel());
        }
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
            validateParameters(Cluster.class, param.getAddClustersList(), ADD_CLUSTERS);
            validateParameters(Cluster.class, param.getRemoveClustersList(), REMOVE_CLUSTERS);

            validateParameters(Host.class, param.getAddHostsList(), ADD_HOSTS);
            validateParameters(Host.class, param.getRemoveHostsList(), REMOVE_HOSTS);

            if (param.getAddVolumesList() != null) {
                validateParameters(Volume.class, param.getAddVolumesList().getVolumes(), ADD_VOLUMES);
            }
            if (param.getRemoveVolumesList() != null) {
                validateParameters(Volume.class, param.getRemoveVolumesList().getVolumes(), REMOVE_VOLUMES);
            }
        }

        private void validateParameters(Class<? extends DataObject> clazz, List<URI> ids, String field) {
            if (ids != null) {
                for (URI id : ids) {
                    ArgValidator.checkFieldUriType(id, clazz, field);
                }
            }
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
         * gets the list of replication group names associated with this COPY type volume group
         * 
         * @return list of replication group names or empty list if the volume group is not COPY or no volumes exist in
         *         the volume group
         */
        public static Set<String> getReplicationGroupNames(VolumeGroup group, DbClient dbClient) {

            Set<String> groupNames = new HashSet<String>();
            if (group.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())) {
                List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(dbClient, group);
                if (volumes != null && !volumes.isEmpty()) {
                    BlockServiceApi serviceAPI = getBlockService(dbClient, volumes.iterator().next());
                    groupNames.addAll(serviceAPI.getReplicationGroupNames(group));
                }
            }
            return groupNames;
        }

        /**
         * return the list of virtual arrays for a volume group
         * 
         * @param group
         * @param dbClient
         * @return
         */
        public static Set<NamedRelatedResourceRep> getVirtualArrays(VolumeGroup group, DbClient dbClient) {

            Set<URI> varrayIds = new HashSet<URI>();
            if (group.getRoles().contains(VolumeGroup.VolumeGroupRole.COPY.toString())) {
                List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(dbClient, group);
                if (volumes != null && !volumes.isEmpty()) {
                    for (Volume volume : volumes) {
                        varrayIds.add(volume.getVirtualArray());
                    }
                }
            }
            Set<NamedRelatedResourceRep> virtualArrays = new HashSet<NamedRelatedResourceRep>();
            for (URI varrayId : varrayIds) {
                VirtualArray varray = dbClient.queryObject(VirtualArray.class, varrayId);
                if (varray != null && !varray.getInactive()) {
                    virtualArrays.add(DbObjectMapper.toNamedRelatedResource(varray));
                }
            }
            return virtualArrays;
        }

        /**
         * Validate the volumes to be added to the volume group.
         * For role COPY:
         * All volumes should be the same type (block, or RP, or VPLEX, or SRDF), and should be in consistency groups
         * 
         * @param volumes
         * @return The validated volumes
         */
        private List<Volume> validateAddVolumes(DbClient dbClient, VolumeGroupUpdateParam param, VolumeGroup volumeGroup,
                Set<URI> impactedCGs) {
            String addedVolType = null;
            String firstVolLabel = null;
            List<URI> addVolList = param.getAddVolumesList().getVolumes();
            List<Volume> volumes = new ArrayList<Volume>();
            for (URI volUri : addVolList) {
                ArgValidator.checkFieldUriType(volUri, Volume.class, "id");
                Volume volume = dbClient.queryObject(Volume.class, volUri);
                if (volume == null || volume.getInactive()) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(), "the volume has been deleted");
                }

                URI cgUri = volume.getConsistencyGroup();
                if (NullColumnValueGetter.isNullURI(cgUri)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "Volume is not in a consistency group");
                }

                // check mirrors
                StringSet mirrors = volume.getMirrors();
                if (mirrors != null && !mirrors.isEmpty()) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "Volume has mirror");
                }

                URI systemUri = volume.getStorageController();
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
                String type = system.getSystemType();
                if (!ALLOWED_SYSTEM_TYPES.contains(type)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(volume.getLabel(),
                            "The storage system type that the volume created in is not allowed ");
                }
                String volType = getVolumeType(volume, dbClient);
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
                                        String.format("The volume is already a member of an application: %s", vg.getLabel()));
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
                impactedCGs.add(volume.getConsistencyGroup());
            }
            // Check if the to-add volumes are the same volume type as existing volumes in the application
            List<Volume> existingVols = ControllerUtils.getVolumeGroupVolumes(dbClient, volumeGroup);
            if (!existingVols.isEmpty()) {
                Volume firstVolume = existingVols.get(0);

                String existingType = getVolumeType(firstVolume, dbClient);
                if (!existingType.equals(addedVolType)) {
                    throw APIException.badRequests.volumeCantBeAddedToVolumeGroup(firstVolLabel,
                            "The volume type is not same as existing volumes in the application");
                }
            }

            // Check to make sure the replication group name is not used in a CG that is not part of an application
            // or part of another application
            // Check to make sure to be added volumes are in the same CG if the backend volumes are in the same backend array
            // All volumes in the same replication group should belong to the same CG.
            if (param.getAddVolumesList().getReplicationGroupName() != null) {
                String replicationGroupName = param.getAddVolumesList().getReplicationGroupName();
                List<Volume> volumesInReplicationGroup = CustomQueryUtility.queryActiveResourcesByConstraint(
                        dbClient, Volume.class, AlternateIdConstraint.Factory.getVolumeByReplicationGroupInstance(replicationGroupName));
                List<URI> toAddVolumes = param.getAddVolumesList().getVolumes();
                // Get the backend volumes not in replication group and sort them in storage system and CG
                Map<URI, URI> backendVolSystemCGMap = new HashMap<URI, URI>();
                for (URI volUri : toAddVolumes) {
                    Volume volToAdd = dbClient.queryObject(Volume.class, volUri);
                    URI cgURI = volToAdd.getConsistencyGroup();
                    StringSet backendVols = volToAdd.getAssociatedVolumes();
                    if (backendVols != null && !backendVols.isEmpty()) {
                        for (String backendUri : backendVols) {
                            Volume backendVol = dbClient.queryObject(Volume.class, URI.create(backendUri));
                            if (backendVol != null && NullColumnValueGetter.isNullValue(backendVol.getReplicationGroupInstance())) {
                                URI storage = backendVol.getStorageController();
                                URI sortCG = backendVolSystemCGMap.get(storage);
                                if (sortCG != null && !cgURI.equals(sortCG)) {
                                    // there are at least two volumes backend volumes are from the same storage system,
                                    // but their CGs are different, throw error
                                    throw APIException.badRequests
                                            .volumeCantBeAddedToVolumeGroup(volToAdd.getLabel(),
                                                    "the volumes in the request are from different consistency group, they could not be added into the same replication group.");
                                } else if (sortCG == null) {
                                    backendVolSystemCGMap.put(storage, cgURI);
                                }
                            }
                        }
                    }
                }
                if (volumesInReplicationGroup != null && !volumesInReplicationGroup.isEmpty()) {
                    for (Volume volumeInRepGrp : volumesInReplicationGroup) {
                        URI storage = volumeInRepGrp.getStorageController();
                        URI addingCG = backendVolSystemCGMap.get(storage);
                        if (addingCG != null) {
                            URI existingVolCG = volumeInRepGrp.getConsistencyGroup();
                            if (!addingCG.equals(existingVolCG)) {
                                throw APIException.badRequests
                                        .volumeCantBeAddedToVolumeGroup(
                                                firstVolLabel,
                                                String.format(
                                                        "the replication group %s is existing, but the volumes in the request are from different consistency group",
                                                        replicationGroupName));
                            }

                            Volume volToCheck = volumeInRepGrp;

                            // if this is a vplex backing volume, get the parent virtual voume
                            if (VPlexUtil.isVplexBackendVolume(volumeInRepGrp, dbClient)) {
                                List<Volume> vplexVolumes = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Volume.class,
                                        getVolumesByAssociatedId(volumeInRepGrp.getId().toString()));
                                if (vplexVolumes != null && !vplexVolumes.isEmpty()) {
                                    // we expect just one parent virtual volume for each backing volume
                                    volToCheck = vplexVolumes.get(0);
                                }
                            }

                            // check to see if the volume is part of another application or not part of an application
                            VolumeGroup grp = volToCheck.getApplication(dbClient);
                            if (grp == null) {
                                throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(),
                                        String.format("a volume, %s is part of the volume group %s but is not part of any application",
                                                volToCheck.getLabel(), replicationGroupName));
                            } else if (!grp.getId().equals(volumeGroup.getId())) {
                                throw APIException.badRequests.volumeGroupCantBeUpdated(volumeGroup.getLabel(),
                                        String.format("a volume, %s is part of the volume group %s and is part of another application: %s",
                                                volToCheck.getLabel(), replicationGroupName, grp.getLabel()));
                            }
                        }
                    }
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
                    log.warn(String.format(
                            "The volume [%s] will not be removed from application %s because it does not exist or has been deleted",
                            voluri.toString(), volumeGroup.getLabel()));
                    continue;
                }
                StringSet volumeGroups = vol.getVolumeGroupIds();
                if (volumeGroups == null || !volumeGroups.contains(volumeGroup.getId().toString())) {
                    log.warn(String.format(
                            "The volume %s will not be removed from application %s because it is not assigned to the application",
                            vol.getLabel(), volumeGroup.getLabel()));
                    continue;
                }

                if (vol.isInCG() && !vol.isVPlexVolume(dbClient)) {
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

        private static String getVolumeType(Volume volume, DbClient dbClient) {
            if (!isNullURI(volume.getProtectionController())
                    && volume.checkForRp()) {
                return DiscoveredDataObject.Type.rp.name();
            }
            if (Volume.checkForSRDF(dbClient, volume.getId())) {
                return DiscoveredDataObject.Type.srdf.name();
            }
            if (volume.isVPlexVolume(dbClient)) {
                return DiscoveredDataObject.Type.vplex.name();
            }

            return BLOCK;

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

        private static BlockServiceApi getBlockService(DbClient dbClient, final Volume volume) {
            if (!isNullURI(volume.getProtectionController())
                    && volume.checkForRp()) {
                return getBlockServiceImpl(DiscoveredDataObject.Type.rp.name());
            }

            if (Volume.checkForSRDF(dbClient, volume.getId())) {
                return getBlockServiceImpl(DiscoveredDataObject.Type.srdf.name());
            }

            String volType = getVolumeType(volume, dbClient);
            return getBlockServiceImpl(volType);
        }

    }

    /**
     * Get volume group hosts
     * 
     * @param volumeGroup
     * @return The list of hosts in volume group
     */
    private static List<Host> getVolumeGroupHosts(DbClient dbClient, VolumeGroup volumeGroup) {
        final List<Host> hosts = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Host.class,
                        AlternateIdConstraint.Factory.getHostsByVolumeGroupId(volumeGroup.getId().toString()));
        return hosts;
    }

    /**
     * get the children for this volume group
     * 
     * @param dbClient
     *            db client for db queries
     * @param volumeGroup
     *            volume group to get children for
     * @return a list of volume groups
     */
    private static List<VolumeGroup> getVolumeGroupChildren(DbClient dbClient, VolumeGroup volumeGroup) {
        List<VolumeGroup> result = new ArrayList<VolumeGroup>();
        final List<VolumeGroup> volumeGroups = CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, VolumeGroup.class,
                ContainmentConstraint.Factory.getVolumesGroupsByVolumeGroupId(volumeGroup.getId()));
        for (VolumeGroup volGroup : volumeGroups) {
            result.add(volGroup);
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
        return clusters;
    }

    /**
     * Check if the application has any pending task
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

    private String setParent(VolumeGroup volumeGroup, String parent) {
        String errorMsg = null;
        // add parent if specified
        if (parent != null && !parent.isEmpty()) {
            if (URIUtil.isValid(parent)) {
                URI parentId = URI.create(parent);
                ArgValidator.checkFieldUriType(parentId, VolumeGroup.class, "parent");
                VolumeGroup parentVG = _dbClient.queryObject(VolumeGroup.class, parentId);
                if (parentVG == null || parentVG.getInactive()) {
                    errorMsg = "The parent volume group does not exist";
                } else {
                    volumeGroup.setParent(parentId);
                }
            } else if (NullColumnValueGetter.isNullValue(parent)) {
                volumeGroup.setParent(NullColumnValueGetter.getNullURI());
            } else {
                List<VolumeGroup> parentVg = CustomQueryUtility
                        .queryActiveResourcesByConstraint(_dbClient, VolumeGroup.class,
                                PrefixConstraint.Factory.getLabelPrefixConstraint(VolumeGroup.class, parent));
                if (parentVg == null || parentVg.isEmpty()) {
                    errorMsg = "The parent volume group does not exist";
                } else {
                    volumeGroup.setParent(parentVg.iterator().next().getId());
                }
            }
        }
        return errorMsg;
    }

    /**
     * Check if the volume is a vplex volume
     * 
     * @param volume The volume to be checked
     * @return true or false
     */
    static private boolean isVPlexVolume(Volume volume, DbClient dbClient) {
        boolean result = false;
        URI storageUri = volume.getStorageController();
        StorageSystem storage = dbClient.queryObject(StorageSystem.class, storageUri);
        String systemType = storage.getSystemType();
        if (systemType.equals(DiscoveredDataObject.Type.vplex.name())) {
            result = true;
        }
        return result;
    }

    /**
     * Gets all clones for the given set name and volume group.
     * 
     * @param cloneSetName
     * @param volumeGroupId
     * @param dbClient
     * @return
     */
    private List<Volume> getClonesBySetName(String cloneSetName, URI volumeGroupId) {
        List<Volume> setClones = new ArrayList<Volume>();
        if (cloneSetName != null) {
            URIQueryResultList list = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFullCopiesBySetName(cloneSetName), list);
            Iterator<Volume> iter = _dbClient.queryIterativeObjects(Volume.class, list);
            while (iter.hasNext()) {
                Volume vol = iter.next();
                URI sourceId = getSourceIdForFullCopy(vol);
                if (sourceId != null) {
                    Volume sourceVol = _dbClient.queryObject(Volume.class, sourceId);
                    if (sourceVol != null && !sourceVol.getInactive() && sourceVol.getVolumeGroupIds() != null
                            && sourceVol.getVolumeGroupIds().contains(volumeGroupId.toString())) {
                        setClones.add(vol);
                    }
                }
            }
        }
        return setClones;
    }

    /**
     * gets the source URI for a replica
     * 
     * @param replica
     * @return
     */
    private URI getSourceIdForFullCopy(BlockObject replica) {
        URI sourceURI = null;
        if (replica instanceof BlockSnapshot) {
            sourceURI = ((BlockSnapshot) replica).getParent().getURI();
        } else if (replica instanceof BlockMirror) {
            sourceURI = ((BlockMirror) replica).getSource().getURI();
        } else if (replica instanceof Volume) {
            sourceURI = ((Volume) replica).getAssociatedSourceVolume();
        }
        return sourceURI;
    }

    /**
     * gets the replica type for a replica
     * 
     * @param replica
     * @return
     */
    private String getReplicaType(BlockObject replica) {
        String replicaType = null;
        if (replica instanceof BlockSnapshot) {
            replicaType = ReplicaTypeEnum.SNAPSHOT.toString();
        } else if (replica instanceof BlockMirror) {
            replicaType = ReplicaTypeEnum.MIRROR.toString();
        } else if (replica instanceof Volume) {
            replicaType = ReplicaTypeEnum.FULL_COPY.toString();
        }
        return replicaType;
    }
}
