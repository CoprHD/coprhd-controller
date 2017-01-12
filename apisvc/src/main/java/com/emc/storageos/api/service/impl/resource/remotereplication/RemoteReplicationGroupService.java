package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

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
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;


@Path("/vdc/block/remotereplicationgroups")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class RemoteReplicationGroupService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationGroupService.class);

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;
    public RemoteReplicationBlockServiceApiImpl getRemoteReplicationServiceApi() {
        return remoteReplicationServiceApi;
    }

    public void setRemoteReplicationServiceApi(RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi) {
        this.remoteReplicationServiceApi = remoteReplicationServiceApi;
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
        _log.info("Found {} groups: {}", ids.size(), ids);
        Iterator<RemoteReplicationGroup> iter = _dbClient.queryIterativeObjects(RemoteReplicationGroup.class, ids);
        while (iter.hasNext()) {
            rrGroupList.getRemoteReplicationGroups().add(toNamedRelatedResource(iter.next()));
        }
        return rrGroupList;
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

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failover")
    public TaskResourceRep failoverRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failoverRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on groups, if group state is valid for the operation, etc.
        // Create a task for the create remote replication group operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.FAILOVER_REMOTE_REPLICATION_GROUP_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationElement.ReplicationElementType.REPLICATION_GROUP, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrGroup.getOpStatus().get(taskId);
            op.error(e);
            rrGroup.getOpStatus().updateTaskStatus(taskId, op);
            rrGroup.setInactive(true);
            _dbClient.updateObject(rrGroup);

            throw e;
        }

        auditOp(OperationTypeEnum.FAILOVER_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }



    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.REMOTE_REPLICATION_SET;
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


