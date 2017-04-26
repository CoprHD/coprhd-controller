/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;

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

import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationController;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.security.authorization.ACL;

import com.emc.storageos.storagedriver.storagecapabilities.RemoteReplicationAttributes;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreate;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationModeChangeParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyGroupUtils;


@Path("/vdc/block/remotereplicationgroups")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class RemoteReplicationGroupService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationGroupService.class);
    public static final String SERVICE_TYPE = "remote_replication";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;

    private BlockService blockService;

    public RemoteReplicationBlockServiceApiImpl getRemoteReplicationServiceApi() {
        return remoteReplicationServiceApi;
    }

    public void setRemoteReplicationServiceApi(RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi) {
        this.remoteReplicationServiceApi = remoteReplicationServiceApi;
    }

    public BlockService getBlockService() {
        return blockService;
    }

    public void setBlockService(BlockService blockService) {
        this.blockService = blockService;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    /**
     * Gets the id, name, and self link for all remote replication groups
     *
     * @brief List remote replication groups
     * @return A reference to a RemoteReplicationGroupList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationGroupList getRemoteReplicationGroups() {
        _log.info("Called: getRemoteReplicationGroups()");
        RemoteReplicationGroupList rrGroupList = new RemoteReplicationGroupList();

        List<URI> ids = _dbClient.queryByType(RemoteReplicationGroup.class, true);
        _log.info("Found groups: {}", ids);
        Iterator<RemoteReplicationGroup> iter = _dbClient.queryIterativeObjects(RemoteReplicationGroup.class, ids);
        while (iter.hasNext()) {
            rrGroupList.getRemoteReplicationGroups().add(toNamedRelatedResource(iter.next()));
        }
        return rrGroupList;
    }

    /**
     * Gets the id, name, and self link for all valid remote replication groups
     *
     * Valid groups are reachable, and have source & target systems
     *
     * @brief List valid remote replication groups
     * @return A reference to a RemoteReplicationGroupList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/valid")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationGroupList getValidRemoteReplicationGroups() {
        _log.info("Called: getValidRemoteReplicationGroups()");
        RemoteReplicationGroupList rrGroupList = new RemoteReplicationGroupList();
        List<URI> ids = _dbClient.queryByType(RemoteReplicationGroup.class, true);
        _log.info("Found groups: {}", ids);
        Iterator<RemoteReplicationGroup> iter = _dbClient.queryIterativeObjects(RemoteReplicationGroup.class, ids);
        while (iter.hasNext()) {
            RemoteReplicationGroup grp = iter.next();
            if (grp.getReachable() && !NullColumnValueGetter.isNullURI(grp.getSourceSystem()) &&
                    !NullColumnValueGetter.isNullURI(grp.getTargetSystem())) {
                rrGroupList.getRemoteReplicationGroups().add(toNamedRelatedResource(grp));
            }
        }
        return rrGroupList;
    }

    /**
     * Get remote replication groups for a given consistency group.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/consistency-group/groups")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationGroupList getRemoteReplicationGroupsForCG(@QueryParam("consistencyGroup") URI uri) {
        ArgValidator.checkUri(uri);
        ArgValidator.checkFieldUriType(uri, BlockConsistencyGroup.class, "id");
        BlockConsistencyGroup cGroup = ConsistencyGroupUtils.findConsistencyGroupById(uri, _dbClient);
        if (ConsistencyGroupUtils.isConsistencyGroupEmpty(cGroup)) {
            // If CG is empty (storageDevice is null) any remote replication group is a match.
            return getRemoteReplicationGroups();
        }
        RemoteReplicationGroupList result = new RemoteReplicationGroupList();
        if (!ConsistencyGroupUtils.isConsistencyGroupSupportRemoteReplication(cGroup)) {
            return result;
        }
        Set<String> targetCGSystemsSet = ConsistencyGroupUtils
                .findAllRRConsistencyGrroupSystemsByAlternateLabel(cGroup.getLabel(), _dbClient);
        Iterator<RemoteReplicationGroup> groups = RemoteReplicationUtils.findAllRemoteRepliationGroupsIteratively(_dbClient);
        while (groups.hasNext()) {
            RemoteReplicationGroup rrGroup = groups.next();
            StorageSystem cgSystem = _dbClient.queryObject(StorageSystem.class, cGroup.getStorageController());
            if (!StringUtils.equals(cgSystem.getSystemType(), rrGroup.getStorageSystemType())) {
                // Pass ones whose storage system type is not aligned with consistency group
                continue;
            }
            if (!URIUtil.uriEquals(rrGroup.getSourceSystem(), cGroup.getStorageController())) {
                // Pass ones whose source systems isn't equal with source CG's storage system
                continue;
            }
            if (!targetCGSystemsSet.contains(URIUtil.toString(rrGroup.getTargetSystem()))) {
                // Pass ones whose target system is not covered by ones of given CG
                continue;
            }
            result.getRemoteReplicationGroups().add(toNamedRelatedResource(rrGroup));
        }
        return result;
    }

    /**
     * Get information about the remote replication group with the passed id.
     *
     * @param id the URN of remote replication group.
     *
     * @brief Show remote replication group
     * @return A reference to a RemoteReplicationGroupRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationGroupRestRep getRemoteReplicationGroup(@PathParam("id") URI id) {
        _log.info("Called: getRemoteReplicationGroups() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);
        RemoteReplicationGroupRestRep restRep = map(rrGroup);
        return restRep;
    }

    /**
     * Get remote replication pairs in the remote replication group
     * @return pairs in the group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/pairs")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairList getRemoteReplicationPairs(@PathParam("id") URI id) {
        _log.info("Called: get" +
                "RemoteReplicationPairs() for replication group {}", id);
        ArgValidator.checkFieldUriType(id, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class, "id");
        List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, id, RemoteReplicationPair.class, "replicationGroup");
        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();
        if (rrPairs != null) {
            _log.info("Found pairs: {}", rrPairs);
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
    @Path("/create-group")
    public TaskResourceRep createRemoteReplicationGroup(final RemoteReplicationGroupCreate param) throws InternalException {

        _log.info("Called: createRemoteReplicationGroup()");
        URI sourceSystem = param.getSourceSystem();
        URI targetSystem = param.getTargetSystem();
        precheckStorageSystem(sourceSystem, "source system");
        precheckStorageSystem(targetSystem, "target system");

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup> rrGroupsForSystem =
                queryActiveResourcesByRelation(_dbClient, sourceSystem, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class,
                        "sourceSystem");

        if (rrGroupsForSystem != null) {
            for (RemoteReplicationGroup group : rrGroupsForSystem) {
                if (group.getLabel() != null && group.getLabel().equalsIgnoreCase(param.getDisplayName())) {
                    throw APIException.badRequests.duplicateLabel(param.getDisplayName());
                }
            }
        }

        List<URI> sourcePortIds = precheckPorts(param.getSourcePorts(), sourceSystem, "source ports");
        List<URI> targetPortIds = precheckPorts(param.getTargetPorts(), targetSystem, "target ports");

        RemoteReplicationGroup rrGroup = prepareRRGroup(param);
        _dbClient.createObject(rrGroup);

        // Create a task for the create remote replication group operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.CREATE_REMOTE_REPLICATION_GROUP);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.createRemoteReplicationGroup(rrGroup.getId(), sourcePortIds, targetPortIds, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            rrGroup.setInactive(true);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.CREATE_REMOTE_REPLICATION_GROUP, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);

    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failover")
    public TaskResourceRep failoverRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failoverRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_OVER);

        // Create a task for the create remote replication group operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.FAILOVER_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.FAILOVER_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failback")
    public TaskResourceRep failbackRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failbackRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_BACK);

        // Create a task for the create remote replication group operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failbackRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.FAILBACK_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/establish")
    public TaskResourceRep establishRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: establishRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.ESTABLISH);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.establishRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/split")
    public TaskResourceRep splitRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: splitRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SPLIT);

        // Create a task for split remote replication group operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.splitRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.SPLIT_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/suspend")
    public TaskResourceRep suspendRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: suspendRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SUSPEND);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.suspendRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.SUSPEND_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resume")
    public TaskResourceRep resumeRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: resumeRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.RESUME);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.resumeRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.RESUME_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/swap")
    public TaskResourceRep swapRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: swapRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);


        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SWAP);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.swapRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/change-replication-mode")
    public TaskResourceRep changeRemoteReplicationGroupMode(@PathParam("id") URI id,
                                                            final RemoteReplicationModeChangeParam param) throws InternalException {
        _log.info("Called: changeRemoteReplicationGroupMode() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        String newMode = param.getNewMode();

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationModeChange(_dbClient, rrElement, newMode);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.changeRemoteReplicationMode(rrElement, newMode, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            _dbClient.updateObject(rrGroup);
        }

        auditOp(OperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    private RemoteReplicationGroup prepareRRGroup(RemoteReplicationGroupCreate param) {

        RemoteReplicationGroup remoteReplicationGroup = new RemoteReplicationGroup();
        remoteReplicationGroup.setId(URIUtil.createId(RemoteReplicationGroup.class));
        remoteReplicationGroup.setIsDriverManaged(true);
        remoteReplicationGroup.setReachable(true);
        remoteReplicationGroup.setLabel(param.getDisplayName());
        remoteReplicationGroup.setOpStatus(new OpStatusMap());

        remoteReplicationGroup.setDisplayName(param.getDisplayName());
        if (param.getReplicationState() != null ) {
            remoteReplicationGroup.addProperty(RemoteReplicationAttributes.PROPERTY_NAME.CREATE_STATE.toString(), param.getReplicationState());
        }
        remoteReplicationGroup.setIsGroupConsistencyEnforced(param.getIsGroupConsistencyEnforced());

        // set replication mode
        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet> rrSets =
                queryActiveResourcesByAltId(_dbClient, com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet.class,
                        "storageSystemType", param.getStorageSystemType());
        if (!rrSets.isEmpty()) {
            com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet rrSet = rrSets.get(0);
            StringSet rrSetSupportedReplicationModes = rrSet.getSupportedReplicationModes();
            String rrGroupReplicationMode = param.getReplicationMode();
            if (rrGroupReplicationMode != null && rrSetSupportedReplicationModes.contains(rrGroupReplicationMode)) {
                remoteReplicationGroup.setReplicationMode(rrGroupReplicationMode);
            } else {
                throw APIException.badRequests.invalidReplicationMode(param.getReplicationMode());
            }
            // verify that isGroupConsistencyEnforced settings for this group comply with parent set settings
            StringSet rrModesNoGroupConsistency = rrSet.getReplicationModesNoGroupConsistency();
            StringSet rrModeGroupConsistencyEnforced = rrSet.getReplicationModesGroupConsistencyEnforced();
            if (param.getIsGroupConsistencyEnforced() != null && rrModeGroupConsistencyEnforced.contains(rrGroupReplicationMode) &&
                    !param.getIsGroupConsistencyEnforced()) {
                throw APIException.badRequests.invalidIsGroupConsistencyEnforced(param.getIsGroupConsistencyEnforced().toString());
            } else if (param.getIsGroupConsistencyEnforced() != null && rrModesNoGroupConsistency.contains(rrGroupReplicationMode) &&
                    param.getIsGroupConsistencyEnforced()) {
                throw APIException.badRequests.invalidIsGroupConsistencyEnforced(param.getIsGroupConsistencyEnforced().toString());
            }
        }

        remoteReplicationGroup.setStorageSystemType(param.getStorageSystemType());
        remoteReplicationGroup.setSourceSystem(param.getSourceSystem());
        remoteReplicationGroup.setTargetSystem(param.getTargetSystem());

        return remoteReplicationGroup;
    }

    /* Pre-check methods */

    private void precheckStorageSystem(URI systemId, String fieldName) {
        if (systemId == null) {
            throw APIException.badRequests.parameterIsNullOrEmpty(fieldName);
        }
        ArgValidator.checkFieldUriType(systemId, StorageSystem.class, fieldName);
    }

    /**
     * Convert ports from portNetworkIds to URIs, and check if it belongs to the
     * specified storage system, and throw exception if not.
     *
     * @param portNetworkIds
     *            ports that are specified by portNetworkIds
     * @param deviceId
     *            the storage system URI that ports should belong to
     * @param fieldName
     *            field name string for error message display
     * @return converted URI list
     */
    private List<URI> precheckPorts(List<String> portNetworkIds, URI deviceId, String fieldName) {
        if (portNetworkIds == null || portNetworkIds.isEmpty()) {
            throw APIException.badRequests.parameterIsNullOrEmpty(fieldName);
        }

        List<URI> ports = new ArrayList<>();
        for (String endpoint : portNetworkIds) {
            StoragePort port = NetworkUtil.getStoragePort(endpoint, _dbClient);
            if (port == null || !deviceId.equals(port.getStorageDevice())) {
                throw APIException.badRequests.invalidParameterNoStoragePort(endpoint, deviceId);
            }
            ports.add(port.getId());
        }
        return ports;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.REMOTE_REPLICATION_GROUP;
    }

    @Override
    protected RemoteReplicationGroup queryResource(URI id) {
        ArgValidator.checkUri(id);
        RemoteReplicationGroup replicationGroup = _dbClient.queryObject(RemoteReplicationGroup.class, id);
        ArgValidator.checkEntityNotNull(replicationGroup, id, isIdEmbeddedInURL(id));
        return replicationGroup;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }
}


