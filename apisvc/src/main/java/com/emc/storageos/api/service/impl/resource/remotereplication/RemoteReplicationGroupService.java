package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;


@Path("/vdc/block/remotereplicationgroups")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class RemoteReplicationGroupService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationGroupService.class);


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


