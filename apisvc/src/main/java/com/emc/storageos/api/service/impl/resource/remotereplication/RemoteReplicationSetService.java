package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.model.block.BlockConsistencyGroupCreate;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreate;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupRestRep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/block/remotereplicationsets")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class RemoteReplicationSetService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationSetService.class);

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public RemoteReplicationGroupRestRep createRemoteReplicationGroup(@PathParam("id") URI id,
            final RemoteReplicationGroupCreate param) {

        checkForDuplicateName(param.getDisplayName(), RemoteReplicationGroup.class, id, "replicationSet", _dbClient);

        RemoteReplicationGroup rrGroup = prepareRRGroup(param, id);
        // Validate name
//        ArgValidator.checkFieldNotEmpty(param.getName(), "name");
//
//        // Validate name not greater than 64 characters
//        ArgValidator.checkFieldLengthMaximum(param.getName(), CG_MAX_LIMIT, "name");
//
//        // Validate project
//        ArgValidator.checkFieldUriType(param.getProject(), Project.class, "project");
//        final Project project = _dbClient.queryObject(Project.class, param.getProject());
//        ArgValidator
//                .checkEntity(project, param.getProject(), isIdEmbeddedInURL(param.getProject()));
//        // Verify the user is authorized.
//        verifyUserIsAuthorizedForRequest(project);
//
//        // Create Consistency Group in db
//        final BlockConsistencyGroup consistencyGroup = new BlockConsistencyGroup();
//        consistencyGroup.setId(URIUtil.createId(BlockConsistencyGroup.class));
//        consistencyGroup.setLabel(param.getName());
//        consistencyGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
//        consistencyGroup.setTenant(project.getTenantOrg());
//        // disable array consistency if user has selected not to create backend replication group
//        consistencyGroup.setArrayConsistency(param.getArrayConsistency());
//
//        _dbClient.createObject(consistencyGroup);
//
//        return BlockMapper.map(consistencyGroup, null, _dbClient);
        RemoteReplicationGroupRestRep restRep = map(rrGroup);
        return restRep;

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
        RemoteReplicationSetRestRep restRep = map(rrSet);
        return restRep;
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

    private RemoteReplicationGroup prepareRRGroup(RemoteReplicationGroupCreate param, URI parentRRSetURI) {

        RemoteReplicationGroup remoteReplicationGroup = new RemoteReplicationGroup();
        RemoteReplicationSet remoteReplicationSet = _dbClient.queryObject(RemoteReplicationSet.class, parentRRSetURI);
        remoteReplicationGroup.setDisplayName(param.getDisplayName());
        remoteReplicationGroup.setReplicationState(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ReplicationState.valueOf(param.getReplicationState()));
        remoteReplicationGroup.setIsGroupConsistencyEnforced(param.getIsGroupConsistencyEnforced());

        // set supported replication link granularity for this group
        StringSet rrSetLinkGranularity = remoteReplicationSet.getSupportedReplicationLinkGranularity();
        StringSet granularityOfLinkOperations = new StringSet();
        if (param.getIsGroupConsistencyEnforced() != null && param.getIsGroupConsistencyEnforced()) {
            // if group requires to enforce group consistency, only group level link operations supported
            granularityOfLinkOperations.add(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_GROUP.toString());
        } else if (rrSetLinkGranularity.contains(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_GROUP.toString())){
            // if group consistency is not enforced, check if parent set supports group operations and if group allow operations on pairs
            granularityOfLinkOperations.add(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_GROUP.toString());
            if (param.getPairLinkOperationsSupported() != null && param.getPairLinkOperationsSupported()) {
                granularityOfLinkOperations.add(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR.toString());
            }
        } else if (param.getPairLinkOperationsSupported() != null && param.getPairLinkOperationsSupported()) {
                granularityOfLinkOperations.add(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR.toString());
        }
        remoteReplicationGroup.setSupportedReplicationLinkGranularity(granularityOfLinkOperations);



        return null;
    }
}
