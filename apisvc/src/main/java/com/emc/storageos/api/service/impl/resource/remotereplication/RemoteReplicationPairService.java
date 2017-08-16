/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByRelation;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapRemoteReplicationPair;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockConsistencyGroupService;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationModeChangeParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairBulkRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairRestRep;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationController;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.model.StatusCoded;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

@Path("/vdc/block/remote-replication-pairs")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class RemoteReplicationPairService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationSetService.class);
    public static final String SERVICE_TYPE = "remote_replication";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;

    private BlockService blockService;

    BlockConsistencyGroupService blockConsistencyGroupService;

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

    public BlockConsistencyGroupService getBlockConsistencyGroupService() {
        return blockConsistencyGroupService;
    }

    public void setBlockConsistencyGroupService(BlockConsistencyGroupService blockConsistencyGroupService) {
        this.blockConsistencyGroupService = blockConsistencyGroupService;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<RemoteReplicationPair> getResourceClass() {
        return RemoteReplicationPair.class;
    }

    @Override
    public RemoteReplicationPairBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<RemoteReplicationPair> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new RemoteReplicationPairBulkRep(BulkList.wrapping(_dbIterator, MapRemoteReplicationPair.getInstance()));
    }

    /**
     * Gets the id, name, and self link for all remote replication pairs
     *
     * @brief List remote replication pairs
     * @return A reference to a RemoteReplicationPairList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairList getRemoteReplicationPairs(@QueryParam("storageElement") URI storageElementURI) {
        RemoteReplicationPairList rrPairList = null;
        if (storageElementURI != null) {
            _log.info("Called: getRemoteReplicationPairs() for storage element {}", storageElementURI);
            rrPairList = getRemoteReplicationPairsForStorageElement(storageElementURI, _dbClient);
        } else {
            _log.info("Called: getRemoteReplicationPairs()");
            rrPairList = new RemoteReplicationPairList();

            List<URI> ids = _dbClient.queryByType(RemoteReplicationPair.class, true);
            _log.info("Found pairs: {}", ids);
            Iterator<RemoteReplicationPair> iter = _dbClient.queryIterativeObjects(RemoteReplicationPair.class, ids);
            while (iter.hasNext()) {
                rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(iter.next()));
            }
        }
        return rrPairList;
    }

    /**
     * Get information about the remote replication pair with the passed id.
     *
     * @param id the URN of remote replication pair.
     *
     * @brief Show remote replication pair
     * @return A reference to a RemoteReplicationPairRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairRestRep getRemoteReplicationPair(@PathParam("id") URI id) {
        _log.info("Called: getRemoteReplicationPair() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);
        RemoteReplicationPairRestRep restRep = map(rrPair);
        return restRep;
    }

    /**
     * Get remote replication pairs for a given storage element (e.g.: a Volume).
     * Returns all pairs where the storage element is source or target element.
     *
     * @param storageElementURI uri of a storage element (e.g.: a Volume)
     * @return
     */
    public static RemoteReplicationPairList getRemoteReplicationPairsForStorageElement(URI storageElementURI, DbClient dbClient) {
        _log.info("Called: getRemoteReplicationPairsForStorageElement() for for storage element {}", storageElementURI);

        ArgValidator.checkUri(storageElementURI);
        Class modelType = URIUtil.getModelClass(storageElementURI);
        DataObject storageElement = dbClient.queryObject(modelType, storageElementURI);
        ArgValidator.checkEntity(storageElement, storageElementURI, false);

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairs =
                queryActiveResourcesByRelation(dbClient, storageElementURI,
                        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.class,
                        "sourceElement");

        List<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> rrPairsForTarget =
                queryActiveResourcesByRelation(dbClient, storageElementURI,
                        com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair.class,
                        "targetElement");
        rrPairs.addAll(rrPairsForTarget);
        _log.info("Found pairs: {}", rrPairs);

        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();
        Iterator<com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair> iter = rrPairs.iterator();
        while (iter.hasNext()) {
            rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(iter.next()));
        }
        return rrPairList;
    }

    public TaskList failoverRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: failoverRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_OVER);
        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.FAILOVER_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.FAILOVER_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.FAILOVER_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failover")
    public TaskResourceRep failoverRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failoverRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return failoverRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep failoverRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: failoverRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);
        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_OVER);

        // If we here, pair is not in CG.
        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.FAILOVER_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        // Create a task for the failover remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.FAILOVER_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId,  e);
        }

        auditOp(OperationTypeEnum.FAILOVER_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    public TaskList failbackRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: failbackRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_BACK);

        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failbackRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.FAILBACK_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failback")
    public TaskResourceRep failbackRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failbackRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return failbackRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep failbackRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: failbackRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_BACK);


        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        // Create a task for the failback remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failbackRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.FAILBACK_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    public TaskList establishRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: establishRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.ESTABLISH);

        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();

        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.establishRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/establish")
    public TaskResourceRep establishRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: establishRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return establishRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep establishRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: establishRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // SRDF integration logic
        Volume sourceVolume = _dbClient.queryObject(Volume.class, rrPair.getSourceElement());
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.ESTABLISH);

        // Create a task for the establish remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.establishRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    public TaskList splitRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: splitRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SPLIT);

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.splitRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SPLIT_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/split")
    public TaskResourceRep splitRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: splitRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return splitRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep splitRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: splitRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SPLIT);

        // Create a task for the split remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.splitRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SPLIT_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    public TaskList suspendRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: suspendRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SUSPEND);

        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.suspendRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SUSPEND_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/suspend")
    public TaskResourceRep suspendRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: suspendRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return suspendRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep suspendRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: suspendRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SUSPEND);

        // Create a task for the suspend remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.suspendRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SUSPEND_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    public TaskList resumeRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: resumeRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.RESUME);

        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.resumeRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.RESUME_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resume")
    public TaskResourceRep resumeRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: resumeRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return resumeRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep resumeRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: resumeRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.RESUME);

        // Create a task for the resume remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.resumeRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.RESUME_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    public TaskList swapRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: swapRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_OVER);
        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.swapRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/swap")
    public TaskResourceRep swapRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: swapRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return swapRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep swapRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: swapRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SWAP);

        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        // Create a task for the swap remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.swapRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    public TaskList stopRemoteReplicationCGLink(List<URI> ids) throws InternalException {
        _log.info("Called: stopRemoteReplicationCGLink() with ids {}", ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_OVER);
        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfGroupLinkRequest(rrPairs.get(0), ResourceOperationTypeEnum.STOP_REMOTE_REPLICATION_CG_LINK);
            return taskList;
        }

        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.STOP_REMOTE_REPLICATION_CG_LINK);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.stopRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.STOP_REMOTE_REPLICATION_CG_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/stop")
    public TaskResourceRep stopRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: stopRemoteReplicationPairLink() with id {}", id);

        String taskId = UUID.randomUUID().toString();
        return stopRemoteReplicationPairLink(id, taskId);
    }

    public TaskResourceRep stopRemoteReplicationPairLink(URI id, String taskId) throws InternalException {
        _log.info("Called: stopRemoteReplicationPairLink() with id {} and task {}", id, taskId);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.STOP);

        // SRDF integration logic
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            // delegate to SRDF support
            TaskList taskList = processSrdfVolumeLinkRequest(rrPair, ResourceOperationTypeEnum.STOP_REMOTE_REPLICATION_PAIR_LINK);
            return taskList.getTaskList().get(0);
        }

        // Create a task for the stop remote replication Pair operation
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.STOP_REMOTE_REPLICATION_PAIR_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.stopRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.STOP_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }



    public TaskList changeRemoteReplicationModeForPairsInCG(List<URI> ids, String newMode) throws InternalException {
        _log.info("Called: changeRemoteReplicationCGMode() with new mode {} and with ids {}", newMode, ids);
        for (URI id : ids) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        }
        ArgValidator.checkFieldNotEmpty(newMode, "replication_mode");
        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, ids);
        URI sourceElementURI = rrPairs.get(0).getSourceElement().getURI();
        ArgValidator.checkFieldUriType(sourceElementURI, Volume.class, "id");
        Volume sourceElement = _dbClient.queryObject(Volume.class, sourceElementURI);
        URI cgURI = sourceElement.getConsistencyGroup();
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(RemoteReplicationSet.ElementType.CONSISTENCY_GROUP, cgURI);

        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.CHANGE_REPLICATION_MODE);

        _log.info("Execute operation for {} array type.", sourceElement.getSystemType());
        // VMAX SRDF integration logic
        // This operation is not supported in current native srdf support.
        if (RemoteReplicationUtils.isVmaxPair(rrPairs.get(0), _dbClient)) {
                throw APIException.badRequests.unsupportedSystemType(sourceElement.getSystemType());
        }

        // this validation is not applicable to SRDF volumes
        RemoteReplicationUtils.validateRemoteReplicationModeChange(_dbClient, rrElement, newMode);
        String taskId = UUID.randomUUID().toString();
        TaskList taskList = new TaskList();
        Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, cg.getId(),
                taskId, ResourceOperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE);
        TaskResourceRep volumeTaskResourceRep = toTask(cg, taskId, op);
        taskList.getTaskList().add(volumeTaskResourceRep);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.changeRemoteReplicationMode(rrElement, newMode, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(BlockConsistencyGroup.class, cg.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE, true, AuditLogManager.AUDITOP_BEGIN,
                cg.getLabel());

        return taskList;
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/change-replication-mode")
    public TaskResourceRep changeRemoteReplicationPairMode(@PathParam("id") URI id,
                                                           final RemoteReplicationModeChangeParam param) throws InternalException {
        _log.info("Called: changeRemoteReplicationPairMode() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        // Validate a copy mode was passed
        ArgValidator.checkFieldNotEmpty(param.getNewMode(), "replication_mode");
        String taskId = UUID.randomUUID().toString();
        return changeRemoteReplicationPairMode(id, param.getNewMode(), taskId);
    }

    public TaskResourceRep changeRemoteReplicationPairMode(URI id, String newMode, String taskId) throws InternalException {
        _log.info("Called: changeRemoteReplicationPairMode() with id {} and task {}, new mode: {} .", id, taskId, newMode);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.CHANGE_REPLICATION_MODE);

        // SRDF integration logic
        // This operation is not supported in current native srdf support.
        if (RemoteReplicationUtils.isVmaxPair(rrPair, _dbClient)) {
            String systemType = _dbClient.queryObject(Volume.class, rrPair.getSourceElement()).getSystemType();
            throw APIException.badRequests.unsupportedSystemType(systemType);
        }

        // this validation is not applicable to SRDF volumes
        RemoteReplicationUtils.validateRemoteReplicationModeChange(_dbClient, rrElement, newMode);
        // Create a task for the remote replication mode change
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.changeRemoteReplicationMode(rrElement, newMode, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    /**
     * Move remote replication pair from current parent group to different group in the same replication set.
     *
     * @param id
     * @param param new remote replication group for the pair
     * @return
     * @throws InternalException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/change-group")
    public TaskResourceRep moveRemoteReplicationPair(@PathParam("id") URI id,
                                                           final RemoteReplicationGroupParam param) throws InternalException {
        _log.info("Called: moveRemoteReplicationPair() with id {} and new group {}", id, param.getRemoteReplicationGroup());
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);
        URI newGroup = param.getRemoteReplicationGroup();

        validateMovePairOperation(rrPair, newGroup);
        // Create task for the move remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.MOVE_REMOTE_REPLICATION_PAIR);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.moveRemoteReplicationPair(id, newGroup, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationPair.class, rrPair.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.MOVE_REMOTE_REPLICATION_PAIR, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    /**
     * Validate if pair can be moved to a new group
     * @param rrPair remote replication pair
     * @param targetGroup new group
     */
    public void validateMovePairOperation(RemoteReplicationPair rrPair, URI targetGroup) {
        //   if pair is in a group
        //   if new group has the same source system, the same target system as current group of the pair
        URI currentGroupId = rrPair.getReplicationGroup();
        if (currentGroupId == null) {
            throw APIException.badRequests.remoteReplicationPairMoveOperationIsNotAllowed(rrPair.getNativeId(), targetGroup.toString(),
                    "current remote replication group is null");
        }
        RemoteReplicationGroup currentGroup = _dbClient.queryObject(RemoteReplicationGroup.class, currentGroupId);
        ArgValidator.checkFieldUriType(targetGroup, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup newGroup = _dbClient.queryObject(RemoteReplicationGroup.class, targetGroup);
        if (newGroup == null) {
            throw APIException.badRequests.invalidURI(targetGroup);
        }
        if (URIUtil.uriEquals(newGroup.getId(), currentGroupId)) {
            throw APIException.badRequests.remoteReplicationPairMoveOperationIsNotAllowed(rrPair.getNativeId(),
                    newGroup.getNativeId(), "target remote replication group can not be the same as the current one");
        }
        if (!URIUtil.uriEquals(currentGroup.getSourceSystem(), newGroup.getSourceSystem()) ||
                !URIUtil.uriEquals(currentGroup.getTargetSystem(), newGroup.getTargetSystem())) {
            throw APIException.badRequests.remoteReplicationPairMoveOperationIsNotAllowed(rrPair.getNativeId(), targetGroup.toString(),
                    "new remote replication group's source or target system is not the same as the old one");
        }
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.REMOTE_REPLICATION_PAIR;
    }

    @Override
    protected RemoteReplicationPair queryResource(URI id) {
        ArgValidator.checkUri(id);
        RemoteReplicationPair replicationPair = _dbClient.queryObject(RemoteReplicationPair.class, id);
        ArgValidator.checkEntityNotNull(replicationPair, id, isIdEmbeddedInURL(id));
        if (replicationPair.getInactive()) {
            throw APIException.badRequests.unableToFindEntity(id);
        }
        return replicationPair;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }


    /**
     * Process srdf consistency group link operation request for srdf protected cg.
     *
     * @param systemPair remote replication pair with volumes in srdf protected consistency group
     * @param operationType link operation type
     * @return
     */
    private TaskList processSrdfGroupLinkRequest(RemoteReplicationPair systemPair, ResourceOperationTypeEnum operationType) {
        TaskList taskList = null;
        String taskId = UUID.randomUUID().toString();

        URI sourceElementURI = systemPair.getSourceElement().getURI();
        URI targetElementURI = systemPair.getTargetElement().getURI();
        Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceElementURI);
        if (!sourceVolume.checkForSRDF()) {
            // not srdf volume --- not supported
            throw APIException.badRequests.volumeMustBeSRDFProtected(sourceVolume.getId());
        }

        Volume targetVolume = _dbClient.queryObject(Volume.class, targetElementURI);
        // check for swap: srdf changes srdf personalities of volumes after swap. rr pair source volume has srdf target personality and
        // rr pair target volume has srdf source personality after srdf swap.
        if(RemoteReplicationUtils.isSwapped(systemPair, _dbClient)) {
            Volume temp = sourceVolume;
            sourceVolume = targetVolume;
            targetVolume = temp;
        }

        String type = BlockSnapshot.TechnologyType.SRDF.toString();
        Copy copy = new Copy();
        copy.setType(type);
        CopiesParam param = new CopiesParam();
        URI cgURI = sourceVolume.getConsistencyGroup();
        switch (operationType) {
            case FAILOVER_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getVirtualArray());
                param.getCopies().add(copy);
                taskList = blockConsistencyGroupService.failoverProtection(cgURI, param);
                break;
            case FAILBACK_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getVirtualArray());
                param.getCopies().add(copy);
                taskList = blockConsistencyGroupService.failoverProtection(cgURI, param);
                break;
            case SWAP_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getVirtualArray());
                param.getCopies().add(copy);
                taskList = blockConsistencyGroupService.swap(cgURI, param);
                break;
            case RESUME_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getId());
                param.getCopies().add(copy);
                // Specific to srdf implementation of resume for cg: block service will handle volume in cg by executing
                // operation for complete cg.
                taskList = blockService.resumeContinuousCopies(sourceVolume.getId(), param);
                break;
            case SUSPEND_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getId());
                copy.setSync("false"); // srdf does suspend for sync == false, split otherwise,
                // when called pauseContinuousCopies()
                param.getCopies().add(copy);
                // Specific to srdf implementation of suspend for cg: block service will handle volume in cg by executing
                // operation for complete cg.
                taskList = blockService.pauseContinuousCopies(sourceVolume.getId(), param);
                break;
            case ESTABLISH_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getId());
                param.getCopies().add(copy);
                // Specific to srdf implementation of establish for cg: block service will handle volume in cg by executing
                // operation for complete cg.
                // Call srdf resume method (srdf start is internal api method used by service catalog for ingestion use case).
                taskList = blockService.resumeContinuousCopies(sourceVolume.getId(), param);
                break;
            case SPLIT_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getId());
                copy.setSync("true"); // srdf does split for sync == true when called pauseContinuousCopies()
                param.getCopies().add(copy);
                // Specific to srdf implementation of split for cg: block service will handle volume in cg by executing
                // operation for complete cg.
                taskList = blockService.pauseContinuousCopies(sourceVolume.getId(), param);
                break;
            case STOP_REMOTE_REPLICATION_CG_LINK:
                copy.setCopyID(targetVolume.getId());
                param.getCopies().add(copy);
                // Specific to srdf implementation of stop for cg: block service will handle volume in cg by executing
                // operation for complete cg.
                taskList = blockService.stopContinuousCopies(sourceVolume.getId(), param);
                break;

            default:
                throw APIException.badRequests.operationNotSupportedForSystemType(operationType.toString(), StorageSystem.Type.vmax.toString());
        }
        return taskList;
    }


    /**
     * Process srdf link operation request for source volume in replication pair.
     *
     * @param systemPair replication pair
     * @param operationType link operation type
     * @return
     */
    private TaskList processSrdfVolumeLinkRequest(RemoteReplicationPair systemPair, ResourceOperationTypeEnum operationType) {
        TaskList taskList = null;
        String taskId = UUID.randomUUID().toString();
        // We follow loose integration with SRDF link operations.
        // We delegate to BlockService method for srdf volume link operation
        Volume sourceVolume = _dbClient.queryObject(Volume.class, systemPair.getSourceElement());
        if (!sourceVolume.checkForSRDF()) {
            // not srdf volume --- not supported
            _log.error("Bad request --- VMAX volume is not SRDF volume.");
            throw  APIException.badRequests.volumeMustBeSRDFProtected(sourceVolume.getId());
        }

        String type = BlockSnapshot.TechnologyType.SRDF.toString();
        Copy copy = new Copy();
        copy.setType(type);
        boolean isSwapped = RemoteReplicationUtils.isSwapped(systemPair, _dbClient);
        NamedURI sourceElement;
        NamedURI targetElement;
        // check for swap: srdf changes srdf personalities of volumes after swap. rr pair source volume has srdf target personality and
        // rr pair target volume has srdf source personality after srdf swap.
        if (isSwapped){
            sourceElement = systemPair.getTargetElement();
            targetElement = systemPair.getSourceElement();
        } else {
            sourceElement = systemPair.getSourceElement();
            targetElement = systemPair.getTargetElement();
        }
        copy.setCopyID(targetElement.getURI());
        CopiesParam param = new CopiesParam();
        URI sourceVolumeURI = sourceElement.getURI();
        switch (operationType) {
            case FAILOVER_REMOTE_REPLICATION_PAIR_LINK:
                param.getCopies().add(copy);
                taskList = blockService.failoverProtection(sourceVolumeURI, param);
                break;
            case FAILBACK_REMOTE_REPLICATION_PAIR_LINK:
                param.getCopies().add(copy);
                taskList = blockService.failoverProtection(sourceVolumeURI, param);
                break;
            case SWAP_REMOTE_REPLICATION_PAIR_LINK:
                param.getCopies().add(copy);
                taskList = blockService.swap(sourceVolumeURI, param);
                break;
            case RESUME_REMOTE_REPLICATION_PAIR_LINK:
                param.getCopies().add(copy);
                taskList = blockService.resumeContinuousCopies(sourceVolumeURI, param);
                break;
            case SUSPEND_REMOTE_REPLICATION_PAIR_LINK:
                copy.setSync("false"); // srdf does suspend for sync == false, split otherwise,
                                       // when called pauseContinuousCopies()
                param.getCopies().add(copy);
                taskList = blockService.pauseContinuousCopies(sourceVolumeURI, param);
                break;
            case ESTABLISH_REMOTE_REPLICATION_PAIR_LINK:
                param.getCopies().add(copy);
                // Call srdf resume method (srdf start is internal api method used by service catalog for ingestion use case).
                taskList = blockService.resumeContinuousCopies(sourceVolumeURI, param);
                break;
            case SPLIT_REMOTE_REPLICATION_PAIR_LINK:
                copy.setSync("true"); // srdf does split for sync == true when called pauseContinuousCopies()
                param.getCopies().add(copy);
                taskList = blockService.pauseContinuousCopies(sourceVolumeURI, param);
                break;
            case STOP_REMOTE_REPLICATION_PAIR_LINK:
                param.getCopies().add(copy);
                taskList = blockService.stopContinuousCopies(sourceVolumeURI, param);
                break;

            default:
                throw APIException.badRequests.operationNotSupportedForSystemType(operationType.toString(), StorageSystem.Type.vmax.toString());
        }
        return taskList;
    }


    private TaskResourceRep processSrdfAPIError(Volume sourceVolume, String taskId, ResourceOperationTypeEnum operationType, StatusCoded ex) {
        _log.info("Processing srdf api error: resource: {}, taskId: {}", sourceVolume.getLabel(), taskId);
        Operation op = _dbClient.createTaskOpStatus(Volume.class, sourceVolume.getId(), taskId, operationType);
        _dbClient.error(Volume.class, sourceVolume.getId(), taskId,  ex);
        return toTask(sourceVolume, taskId, op);
    }

    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public RemoteReplicationPairBulkRep getBulkResources(BulkIdParam param) {
        return (RemoteReplicationPairBulkRep) super.getBulkResources(param);
    }
}


