package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreate;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationModeChangeParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

@Path("/vdc/block/remotereplicationsets")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class RemoteReplicationSetService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationSetService.class);
    public static final String SERVICE_TYPE = "remote_replication";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;

    public RemoteReplicationBlockServiceApiImpl getRemoteReplicationServiceApi() {
        return remoteReplicationServiceApi;
    }

    public void setRemoteReplicationServiceApi(RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi) {
        this.remoteReplicationServiceApi = remoteReplicationServiceApi;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    /**
     * Gets the id, name, and self link for all remote replication sets
     *
     * @brief List remote replication sets
     * @return A reference to a RemoteReplicationSetList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationSetList getRemoteReplicationSets() {
        _log.info("Called: getRemoteReplicationSets()");
        RemoteReplicationSetList rrSetList = new RemoteReplicationSetList();

        List<URI> ids = _dbClient.queryByType(RemoteReplicationSet.class, true);
        _log.info("Found sets: {}", ids);
        Iterator<RemoteReplicationSet> iter = _dbClient.queryIterativeObjects(RemoteReplicationSet.class, ids);
        while (iter.hasNext()) {
            rrSetList.getRemoteReplicationSets().add(toNamedRelatedResource(iter.next()));
        }
        return rrSetList;
    }

    /**
     * Get information about the remote replication set with the passed id.
     *
     * @param id the URN of remote replication set.
     *
     * @brief Show remote replication set
     * @return A reference to a RemoteReplicationSetRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationSetRestRep getRemoteReplicationSet(@PathParam("id") URI id) {
        _log.info("Called: getRemoteReplicationSet() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);
        return map(rrSet);
    }

    /**
     * Get remote replication groups associated to remote replication set
     * @return groups associated to the set through storage systems
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/groups")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationGroupList getRemoteReplicationGroups(@PathParam("id") URI id) {
        _log.info("Called: getRemoteReplicationGroups() for replication set {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);
        Set<String> sourceSystems = rrSet.getSourceSystems();
        Set<String> targetSystems = rrSet.getTargetSystems();
        Set<String> supportedReplicationModes = rrSet.getSupportedReplicationModes();

        // get groups with these source systems and filter in groups with target system in target systems
        // check that each group in result set has replication mode as supported in the set
        List<RemoteReplicationGroup> setGroups = new ArrayList<>();
        for (String system : sourceSystems) {
            URI systemURI = URIUtil.uri(system);
            List<RemoteReplicationGroup> rrGroups = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient,
                    systemURI, RemoteReplicationGroup.class, "sourceSystem");
            for (RemoteReplicationGroup rrGroup : rrGroups) {
                if (targetSystems.contains(rrGroup.getTargetSystem().toString()) &&
                        supportedReplicationModes.contains(rrGroup.getReplicationMode())) {
                    setGroups.add(rrGroup);
                }
            }
            setGroups.addAll(rrGroups);
        }

        RemoteReplicationGroupList rrGroupList = new RemoteReplicationGroupList();
        if (!setGroups.isEmpty()) {
            _log.info("Found groups: {}", setGroups);
            Iterator<RemoteReplicationGroup> iter = setGroups.iterator();
            while (iter.hasNext()) {
                rrGroupList.getRemoteReplicationGroups().add(toNamedRelatedResource(iter.next()));
            }
        }
        return rrGroupList;
    }

    /**
     * Get remote replication sets for a given storage type
     *
     * @param storageTypeURI uri of a storage type
     * @return
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/storage-type/sets")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationSetList getRemoteReplicationSetsForStorageType(@QueryParam("storageType") URI storageTypeURI) {
        _log.info("Called: getRemoteReplicationSetsForStorageType() for type {}", storageTypeURI);

        ArgValidator.checkFieldUriType(storageTypeURI, StorageSystemType.class, "id");
        StorageSystemType storageSystemType = _dbClient.queryObject(StorageSystemType.class, storageTypeURI);
        ArgValidator.checkEntity(storageSystemType, storageTypeURI, false);

        List<RemoteReplicationSet> rrSets =
                queryActiveResourcesByAltId(_dbClient, RemoteReplicationSet.class, "storageSystemType", storageSystemType.getStorageTypeName());
        RemoteReplicationSetList rrSetList = new RemoteReplicationSetList();

        if (rrSets != null) {
            _log.info("Found sets: {}", rrSets);
            Iterator<RemoteReplicationSet> iter = rrSets.iterator();
            while (iter.hasNext()) {
                rrSetList.getRemoteReplicationSets().add(toNamedRelatedResource(iter.next()));
            }
        }
        return rrSetList;
    }

    /**
     * Get remote replication pairs which belong directly to the replication set (no group container)
     * @return pairs in the set
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/set-pairs")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairList getRemoteReplicationSetPairs(@PathParam("id") URI id) {
        _log.info("Called: get" +
                "getRemoteReplicationSetPairs() for replication set {}", id);
        ArgValidator.checkFieldUriType(id, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class, "id");
        List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, id, RemoteReplicationPair.class, "replicationSet");
        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();
        if (rrPairs != null) {
            _log.info("Found total pairs: {}", rrPairs.size());
            int size = 0;
            for (RemoteReplicationPair rrPair : rrPairs) {
                if(rrPair.getReplicationGroup() == null) {
                    // return only pairs directly in replication set
                    rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(rrPair));
                    size++;
                }
                _log.info("Found pairs: {} directly in the set", size);
            }
        }
        return rrPairList;
    }

    /**
     * Get all remote replication pairs in the remote replication set, including pairs directly in the set and
     * pairs in the groups, which belong to the set.
     * @return pairs in the group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/pairs")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairList getRemoteReplicationPairs(@PathParam("id") URI id) {
        _log.info("Called: get" +
                "RemoteReplicationPairs() for replication set {}", id);
        ArgValidator.checkFieldUriType(id, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class, "id");
        List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, id, RemoteReplicationPair.class, "replicationSet");
        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();
        if (rrPairs != null) {
            _log.info("Found total pairs: {}", rrPairs.size());
            Iterator<RemoteReplicationPair> iter = rrPairs.iterator();
            while (iter.hasNext()) {
                rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(iter.next()));
            }
        }
        return rrPairList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failover")
    public TaskResourceRep failoverRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failoverRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on Sets, if Set state is valid for the operation, if the Set is reachable, etc.
        // Create a task for the failover remote replication Set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.FAILOVER_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failback")
    public TaskResourceRep failbackRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failbackRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the failback remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failbackRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.FAILBACK_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/establish")
    public TaskResourceRep establishRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: establishRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the establish remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.establishRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/split")
    public TaskResourceRep splitRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: splitRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the split remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.splitRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.SPLIT_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/suspend")
    public TaskResourceRep suspendRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: suspendRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the suspend remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.suspendRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.SUSPEND_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resume")
    public TaskResourceRep resumeRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: resumeRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the resume remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.resumeRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.RESUME_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/swap")
    public TaskResourceRep swapRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: swapRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the swap remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_SET_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.swapRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/change-replication-mode")
    public TaskResourceRep changeRemoteReplicationSetMode(@PathParam("id") URI id,
                                                            final RemoteReplicationModeChangeParam param) throws InternalException {
        _log.info("Called: changeRemoteReplicationSetMode() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        String newMode = param.getNewMode();

        // todo: validate that this operation is valid: if operations are allowed on sets, if set state is valid for the operation, if the set is reachable, etc.
        // Create a task for the create remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.changeRemoteReplicationMode(rrElement, newMode, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrSet.getOpStatus().get(taskId);
            op.error(e);
            rrSet.getOpStatus().updateTaskStatus(taskId, op);
            rrSet.setInactive(true);
            _dbClient.updateObject(rrSet);

            throw e;
        }

        auditOp(OperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.REMOTE_REPLICATION_SET;
    }

    @Override
    protected RemoteReplicationSet queryResource(URI id) {
        ArgValidator.checkUri(id);
        RemoteReplicationSet replicationSet = _dbClient.queryObject(RemoteReplicationSet.class, id);
        ArgValidator.checkEntityNotNull(replicationSet, id, isIdEmbeddedInURL(id));
        return replicationSet;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }


}
