/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.remotereplication;


import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.model.remotereplication.RemoteReplicationModeChangeParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.BlockService.ProtectionOp;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationOperationParam.OperationContext;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

@Path("/vdc/block/remotereplicationmanagement")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class RemoteReplicationManagementService extends TaskResourceService {

    private static final String MULTI_PAIR_VMAX_ERR_MSG = "Multiple pairs in the request. For VMAX arrays, " +
            "operations with context %s are supported only for a single pair in the request";
    private static final String UNSUPPORTTED_CONTEXT_VMAX_ERR_MSG = "For VMAX arrays, operations with context " +
            "%s are not supported. Use %s or %s context";
    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationManagementService.class);
    public static final String SERVICE_TYPE = "remote_replication_management";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;

    private RemoteReplicationGroupService rrGroupService;
    private RemoteReplicationPairService rrPairService;
    private RemoteReplicationSetService rrSetService;

    public RemoteReplicationBlockServiceApiImpl getRemoteReplicationServiceApi() {
        return remoteReplicationServiceApi;
    }

    public void setRemoteReplicationServiceApi(RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi) {
        this.remoteReplicationServiceApi = remoteReplicationServiceApi;
    }

    public RemoteReplicationGroupService getRrGroupService() {
        return rrGroupService;
    }

    public void setRrGroupService(RemoteReplicationGroupService groupService) {
        this.rrGroupService = groupService;
    }

    public RemoteReplicationPairService getRrPairService() {
        return rrPairService;
    }

    public void setRrPairService(RemoteReplicationPairService rrPairService) {
        this.rrPairService = rrPairService;
    }

    public RemoteReplicationSetService getRrSetService() {
        return rrSetService;
    }

    public void setRrSetService(RemoteReplicationSetService rrSetService) {
        this.rrSetService = rrSetService;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.REMOTE_REPLICATION_MANAGEMENT;
    }

    @Override
    protected RemoteReplicationPair queryResource(URI id) {
       return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/split")
    public TaskList splitRemoteReplicationLink(RemoteReplicationOperationParam operationParam) throws InternalException {
        validateOperationParam(operationParam);
        _log.info("Called: splitRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.SPLIT);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.splitRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_GROUP_CG:
            case RR_SET_CG:
                taskList = rrPairService.splitRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.splitRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task = rrSetService.splitRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/establish")
    public TaskList establishRemoteReplicationLink(RemoteReplicationOperationParam operationParam) throws InternalException {
        validateOperationParam(operationParam);
        _log.info("Called: establishRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();

        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));
        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.START);

        switch (operationContext) {
            case RR_PAIR:
                // for individual pairs send one request for each pair
                // call pair service for each pair and add task to the taskList, return task list.
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.establishRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;
            case RR_SET_CG:
            case RR_GROUP_CG:
                taskList =  rrPairService.establishRemoteReplicationCGLink(operationParam.getIds());
                break;
            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.establishRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task =  rrSetService.establishRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/suspend")
    public TaskList suspendRemoteReplicationLink(RemoteReplicationOperationParam operationParam) {
        validateOperationParam(operationParam);
        _log.info("Called: suspendRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.SUSPEND);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.suspendRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_GROUP_CG:
            case RR_SET_CG:
                taskList = rrPairService.suspendRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.suspendRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task = rrSetService.suspendRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/resume")
    public TaskList resumeRemoteReplicationLink(RemoteReplicationOperationParam operationParam) {
        validateOperationParam(operationParam);
        _log.info("Called: resumeRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.RESUME);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.resumeRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_GROUP_CG:
            case RR_SET_CG:
                taskList = rrPairService.resumeRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.resumeRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task = rrSetService.resumeRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/failback")
    public TaskList failbackRemoteReplicationLink(RemoteReplicationOperationParam operationParam) throws InternalException {
        validateOperationParam(operationParam);
        _log.info("Called: failbackRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.FAILBACK);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.failbackRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_SET_CG:
            case RR_GROUP_CG:
                taskList = rrPairService.failbackRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.failbackRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task = rrSetService.failbackRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/failover")
    public TaskList failoverRemoteReplicationLink(RemoteReplicationOperationParam operationParam) throws InternalException {
        validateOperationParam(operationParam);
        _log.info("Called: failoverRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.FAILOVER);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.failoverRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_SET_CG:
            case RR_GROUP_CG:
                taskList = rrPairService.failoverRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.failoverRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task = rrSetService.failoverRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/swap")
    public TaskList swapRemoteReplicationLink(RemoteReplicationOperationParam operationParam) throws InternalException {
        validateOperationParam(operationParam);
        _log.info("Called: swapRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.SWAP);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.swapRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_SET_CG:
            case RR_GROUP_CG:
                taskList =  rrPairService.swapRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.swapRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task =  rrSetService.swapRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/stop")
    public TaskList stopRemoteReplicationLink(RemoteReplicationOperationParam operationParam) {
        validateOperationParam(operationParam);
        _log.info("Called: stopRemoteReplicationLink() with context {} and ids {}",
                operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.STOP);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.stopRemoteReplicationPairLink(rrPairURI, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_GROUP_CG:
            case RR_SET_CG:
                taskList = rrPairService.stopRemoteReplicationCGLink(operationParam.getIds());
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, groupURI);
                task =  rrGroupService.stopRemoteReplicationGroupLink(rrGroup.getId());
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, setURI);
                task = rrSetService.stopRemoteReplicationSetLink(rrSet.getId());
                taskList.addTask(task);
                break;
        }
        return taskList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/change-replication-mode")
    public TaskList changeRemoteReplicationMode(RemoteReplicationOperationParam operationParam) throws InternalException {
        validateOperationParam(operationParam);
        // Validate a copy mode was passed
        ArgValidator.checkFieldNotEmpty(operationParam.getNewReplicationMode(), "replication_mode");
        _log.info("Called: changeRemoteReplicationMode() with new mode {}, context {} and ids {}",
                operationParam.getNewReplicationMode(), operationParam.getOperationContext(), operationParam.getIds());

        validateContainmentForContext(operationParam);

        RemoteReplicationOperationParam.OperationContext operationContext =
                RemoteReplicationOperationParam.OperationContext.valueOf(operationParam.getOperationContext());

        TaskResourceRep task = null;
        TaskList taskList = new TaskList();
        String newReplicationMode = operationParam.getNewReplicationMode();
        RemoteReplicationPair rrPair = _dbClient.queryObject(RemoteReplicationPair.class, operationParam.getIds().get(0));

        precheckVmaxOperation(rrPair, operationContext, operationParam, ProtectionOp.CHANGE_COPY_MODE);

        RemoteReplicationModeChangeParam param = new RemoteReplicationModeChangeParam();
        param.setNewMode(newReplicationMode);

        switch (operationContext) {
            case RR_PAIR:
                String taskID = UUID.randomUUID().toString();
                for (URI rrPairURI : operationParam.getIds()) {
                    TaskResourceRep rrPairTaskResourceRep = rrPairService.changeRemoteReplicationPairMode(rrPairURI, newReplicationMode, taskID);
                    taskList.addTask(rrPairTaskResourceRep);
                }
                break;

            case RR_SET_CG:
            case RR_GROUP_CG:
                taskList = rrPairService.changeRemoteReplicationCGMode(operationParam.getIds(), newReplicationMode);
                break;

            case RR_GROUP:
                URI groupURI = rrPair.getReplicationGroup();
                task =  rrGroupService.changeRemoteReplicationGroupMode(groupURI, param);
                taskList.addTask(task);
                break;

            case RR_SET:
                URI setURI = rrPair.getReplicationSet();
                task =  rrSetService.changeRemoteReplicationSetMode(setURI, param);
                taskList.addTask(task);
                break;
        }
        return taskList;
    }



    private void validateOperationParam(RemoteReplicationOperationParam param) {
        ArgValidator.checkFieldNotNull(param, "remote replication operation parameter");
        ArgValidator.checkFieldNotNull(param.getOperationContext(), "remote replication operation context");
        ArgValidator.checkFieldNotEmpty(param.getIds(), "remote replication operation pair ids");
        for (URI id : param.getIds()) {
            ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
            if (_dbClient.queryObject(id) == null) {
                throw APIException.badRequests.invalidURI(id);
            }
        }
    }

    /**
     * VMAX pairs precheck rules:
     *   1. Support RR_PAIR context operations, but only on a single pair at a time;
     *   2. Not support RR_GROUP, RR_SET context or RR_SET_CG operation;
     *   3. Support all RR_GROUP_CG context operations.
     */
    private void precheckVmaxOperation(RemoteReplicationPair pair, OperationContext context,
            RemoteReplicationOperationParam operationParam, ProtectionOp op) {
        if (!RemoteReplicationUtils.isVmaxPair(pair, _dbClient)) {
            return;
        }
        if (context == OperationContext.RR_PAIR) {
            if (operationParam.getIds().size() > 1) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(
                        String.format(MULTI_PAIR_VMAX_ERR_MSG, context.toString()));
            }
        } else if (context == OperationContext.RR_GROUP || context == OperationContext.RR_SET
                || context == OperationContext.RR_SET_CG) {
            throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(
                    String.format(UNSUPPORTTED_CONTEXT_VMAX_ERR_MSG, context.toString(), OperationContext.RR_PAIR,
                            OperationContext.RR_GROUP_CG));
        }
    }


    private Set<URI> getSetPairIdsByRrSet(URI rrSetId) {
        List<RemoteReplicationPair> pairs = RemoteReplicationUtils.findAllRemoteReplicationPairsByRrSet(rrSetId, _dbClient);
        Set<URI> setPairIds = new HashSet<>();
        for (RemoteReplicationPair pair : pairs) {
            if (!pair.isGroupPair()) {
                setPairIds.add(pair.getId());
            }
        }
        return setPairIds;
    }

    private Set<URI> getGroupPairIdsByRrGroup(URI rrGroupId) {
        List<RemoteReplicationPair> pairs = RemoteReplicationUtils.findAllRemoteReplicationPairsByRrGroup(rrGroupId, _dbClient);
        Set<URI> groupPairIds = new HashSet<>();
        for (RemoteReplicationPair pair : pairs) {
            if (pair.isGroupPair()) {
                groupPairIds.add(pair.getId());
            }
        }
        return groupPairIds;
    }

    /**
     * Validate parameters according to context of the operation.
     * @param operationParam
     */
    private void validateContainmentForContext(RemoteReplicationOperationParam operationParam) {
        OperationContext context = validateOperationContext(operationParam.getOperationContext());
        if (operationParam.getIds() == null || operationParam.getIds().isEmpty()) {
            throw APIException.badRequests.remoteReplicationOperationPrecheckFailed("No remote replication pairs are specified.");
        }

        Set<URI> rrPairIds = new HashSet<>(operationParam.getIds());
        for (URI rrPairId : rrPairIds) {
            ArgValidator.checkFieldUriType(rrPairId, RemoteReplicationPair.class, "id");
        }

        List<RemoteReplicationPair> rrPairs =_dbClient.queryObject(RemoteReplicationPair.class, rrPairIds);
        if (rrPairs.isEmpty()) {
            throw APIException.badRequests.remoteReplicationOperationPrecheckFailed("remote replication pair IDs are not found");
        }
        

        switch (context) {
            case RR_SET:
                checkRRSetContainment(rrPairs);
                URI rrSetId = rrPairs.get(0).getReplicationSet();
                Set<URI> setPairIds = getSetPairIdsByRrSet(rrSetId);
                if (!rrPairIds.containsAll(setPairIds)) {
                    throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(
                            String.format("Given remote replication pair ids should contain all pairs of " +
                                    "remote replication set %s Pair IDs: %s  Set Pairs: %s",
                                    rrSetId,rrPairs,setPairIds));
                }
                break;
            case RR_GROUP:
                checkRRGroupContainment(rrPairs);
                URI rrGroupId = rrPairs.get(0).getReplicationGroup();
                Set<URI> groupPairIds =getGroupPairIdsByRrGroup(rrGroupId);
                if (!rrPairIds.containsAll(groupPairIds)) {
                    throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(
                            String.format("Given remote replication pair ids should contain all pairs of " +
                                    "remote replication group %s Pair IDs: %s  Set Pairs: %s",
                                    rrGroupId,rrPairs,groupPairIds));
                }
                break;
            case RR_PAIR:
                // No additional validation needed
                break;
            case RR_SET_CG:
                checkRRSetCGContainment(rrPairs);
                break;
            case RR_GROUP_CG:
                checkRRGroupCGContainment(rrPairs);
                break;
        }
    }

    private OperationContext validateOperationContext(String contextStr) {
        try {
            return OperationContext.valueOf(contextStr);
        } catch (Exception e) {
            throw APIException.badRequests.invalidRemoteReplicationContext(contextStr);
        }
    }

    /**
     * Validation criteria: all rr pairs are contained in the same rr set
     * @param rrPairs
     */
    private void checkRRSetContainment(List<RemoteReplicationPair> rrPairs) {
        URI uniqueSet = null;
        for (RemoteReplicationPair rrPair : rrPairs) {
            URI currentSet = rrPair.getReplicationSet();
            if (currentSet == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication set of remote replication pair %s is null, which is not allowed",
                        rrPair.getNativeId()));
            } else if (uniqueSet == null) {
                uniqueSet = currentSet;
            } else if (!uniqueSet.equals(currentSet)) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication set of remote replication pair %s is not the same as the others",
                        rrPair.getNativeId()));
            }
        }
    }

    /**
     * Validation criteria: all rr pairs are contained in the same rr group
     * @param rrPairs
     */
    private void checkRRGroupContainment(List<RemoteReplicationPair> rrPairs) {
        URI uniqueGroup = null;
        for (RemoteReplicationPair rrPair : rrPairs) {
            URI currentGroup = rrPair.getReplicationGroup();
            if (currentGroup == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication group of remote replication pair %s is null, which is not allowed",
                        rrPair.getNativeId()));
            } else if (uniqueGroup == null) {
                uniqueGroup = currentGroup;
            } else if (!uniqueGroup.equals(currentGroup)) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication group of remote replication pair %s is not the same as the others",
                        rrPair.getNativeId()));
            }
        }
    }

    /**
     * Validation criteria:
     *   1. All rr pairs are directly contained in the same rr set;
     *   2. All source volumes are contained in the same source cg
     *      and have to contain all volumes from the cg (We do not
     *      allow operations on subset of volumes in cg).
     *   3. Target volumes can be in one or more cg(s).
     * @param rrPairs
     */
    private void checkRRSetCGContainment(List<RemoteReplicationPair> rrPairs) {
        URI uniqueSet = null;
        URI uniqueSourceCG = null;
        Set<URI> sourceVolIds = new HashSet<URI>();
        for (RemoteReplicationPair rrPair : rrPairs) {
            if (rrPair.isGroupPair()) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %s is not directly contained in remote replication set, which is not allowed",
                        rrPair.getNativeId()));
            }

            URI currentSet = rrPair.getReplicationSet();
            if (currentSet == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication set of remote replication pair %s is null, which is not allowed",
                        rrPair.getNativeId()));
            } else if (uniqueSet == null) {
                uniqueSet = currentSet;
            } else if (!uniqueSet.equals(currentSet)) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication set of remote replication pair %s is not the same as the others",
                        rrPair.getNativeId()));
            }

            Volume source = _dbClient.queryObject(Volume.class, rrPair.getSourceElement());
            sourceVolIds.add(source.getId());
            URI currentSourceCG = source.getConsistencyGroup();
            if (currentSourceCG == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %'s source volume has no consistency group", rrPair.getNativeId()));
            } else if (uniqueSourceCG == null) {
                uniqueSourceCG = currentSourceCG;
            } else if (!uniqueSourceCG.equals(currentSourceCG)) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %s's source volume's consistency group is not the same as others",
                        rrPair.getNativeId()));
            }

            URI currentTargetCG = _dbClient.queryObject(Volume.class, rrPair.getTargetElement()).getConsistencyGroup();
            if (currentTargetCG == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %'s target volume has no consistency group", rrPair.getNativeId()));
            }
        }

        if (!containAllCgVolumes(sourceVolIds, uniqueSourceCG)) {
            throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                    "source volumes of given remote replication pairs don't contain all volumes in consistency group %s",
                    uniqueSourceCG));
        }
    }

    /**
     * Validation criteria:
     *   1. All rr pairs are contained in rr group (not necessarily the same one);
     *   2. All source volumes are contained in the same cg and it contains all volumes in the source cg
     *   3. All target volumes are contained in the same cg;
     * @param rrPairs
     */
    private void checkRRGroupCGContainment(List<RemoteReplicationPair> rrPairs) {
        URI uniqueSourceCG = null;
        URI uniqueTargetCG = null;
        Set<URI> sourceVolIds = new HashSet<>();
        for (RemoteReplicationPair rrPair : rrPairs) {
            if (!rrPair.isGroupPair()) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %s is directly contained in remote replication set, which is not allowed",
                        rrPair.getNativeId()));
            }

            Volume source = _dbClient.queryObject(Volume.class, rrPair.getSourceElement());
            sourceVolIds.add(source.getId());
            URI currentSourceCG = source.getConsistencyGroup();
            if (currentSourceCG == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %'s source volume has no consistency group", rrPair.getNativeId()));
            } else if (uniqueSourceCG == null) {
                uniqueSourceCG = currentSourceCG;
            } else if (!uniqueSourceCG.equals(currentSourceCG)) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %s's source volume's consistency group is not the same as others",
                        rrPair.getNativeId()));
            }

            URI currentTargetCG = _dbClient.queryObject(Volume.class, rrPair.getTargetElement()).getConsistencyGroup();
            if (currentTargetCG == null) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %'s target volume has no consistency group", rrPair.getNativeId()));
            } else if (uniqueTargetCG == null) {
                uniqueTargetCG = currentTargetCG;
            } else if (!uniqueTargetCG.equals(currentTargetCG)) {
                throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                        "remote replication pair %s's target volume's consistency group is not the same as others",
                        rrPair.getNativeId()));
            }
        }

        if (!containAllCgVolumes(sourceVolIds, uniqueSourceCG)) {
            throw APIException.badRequests.remoteReplicationOperationPrecheckFailed(String.format(
                    "source volumes of given remote replication pairs don't contain all volumes in consistency group %s",
                    uniqueSourceCG));
        }
    }

    /**
     * @param vols volume URI collection
     * @param cgId consistency group URI
     * @return true if given volume collection contains all volumes in the
     *         given consistency group, otherwise return false
     */
    private boolean containAllCgVolumes(Set<URI> vols, URI cgId) {
        BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgId);
        for (Volume vol : BlockConsistencyGroupUtils.getActiveVolumesInCG(cg, _dbClient, null)) {
            if (!vols.contains(vol.getId())) {
                return false;
            }
        }
        return true;
    }

}
