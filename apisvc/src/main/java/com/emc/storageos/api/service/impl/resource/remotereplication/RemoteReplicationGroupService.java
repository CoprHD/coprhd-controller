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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;

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

import com.emc.storageos.services.util.StorageDriverManager;
import com.emc.storageos.storagedriver.storagecapabilities.RemoteReplicationAttributes;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapRemoteReplicationGroup;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.storageos.model.block.NamedRelatedBlockConsistencyGroupRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupBulkRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupCreateParams;
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


@Path("/vdc/block/remote-replication-groups")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class RemoteReplicationGroupService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationGroupService.class);
    public static final String SERVICE_TYPE = "remote_replication";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;

    private BlockService blockService;
    private RemoteReplicationPairService rrPairService;
    private RemoteReplicationSetService rrSetService;

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

    @SuppressWarnings("unchecked")
    @Override
    public Class<RemoteReplicationGroup> getResourceClass() {
        return RemoteReplicationGroup.class;
    }

    @Override
    public RemoteReplicationGroupBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<RemoteReplicationGroup> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new RemoteReplicationGroupBulkRep(BulkList.wrapping(_dbIterator, MapRemoteReplicationGroup.getInstance()));
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
     * @return all remote replication groups with storage in specified varray and vpool
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/varray/{varray}/vpool/{vpool}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationGroupList getRemoteReplicationGroupsForVarrayVpool(@PathParam("varray") URI varrayURI,
            @PathParam("vpool") URI vpoolURI) {
        _log.info("Called: getRemoteReplicationGroupsForVarrayVpool() with params: (varray: {}, vpool: {})", varrayURI, vpoolURI);
        ArgValidator.checkFieldUriType(varrayURI, VirtualArray.class, "virtual array id");
        ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "virtual pool id");
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, vpoolURI);
        if (vpool == null || vpool.getRemoteReplicationProtectionSettings() == null) {
            throw APIException.badRequests.invalidVirtualPoolUriOrNotSupportRemoteReplication(vpoolURI);
        }
        if (!vpool.getVirtualArrays().contains(varrayURI.toString())) {
            throw APIException.badRequests.vpoolVarrayMismatch(vpoolURI, varrayURI);
        }
        RemoteReplicationGroupList result = new RemoteReplicationGroupList();
        Set<String> sourceDevices = RemoteReplicationUtils.getStorageSystemsForVarrayVpool(varrayURI, vpoolURI, _dbClient, _coordinator);
        Set<String> allTargetSystems = new HashSet<String>();
        for (Entry<String, String> pair : vpool.getRemoteReplicationProtectionSettings().entrySet()) {
            URI targetvArrayURI = URI.create(pair.getKey());
            URI targetvPoolURI = URI.create(pair.getValue());
            Set<String> targetDevices = RemoteReplicationUtils.getStorageSystemsForVarrayVpool(targetvArrayURI,
                    targetvPoolURI, _dbClient, _coordinator);
            allTargetSystems.addAll(targetDevices);
        }
        Iterator<RemoteReplicationGroup> it = RemoteReplicationUtils.findAllRemoteReplicationGroupsIteratively(_dbClient);
        while (it.hasNext()) {
            RemoteReplicationGroup rrGroup = it.next();

            // Bypass rr groups whose source/target device is not assigned
            String groupSourceDevice = rrGroup.getSourceSystem() != null ? rrGroup.getSourceSystem().toString() : null;
            String groupTargetDevice = rrGroup.getTargetSystem() != null ? rrGroup.getTargetSystem().toString() : null;
            if (groupSourceDevice == null || groupTargetDevice == null) {
                continue;
            }

            // rr group's source device should be contained by source varray/vpool pair's devices
            if (!sourceDevices.contains(groupSourceDevice)) {
                continue;
            }

            // rr group's target device should be contained in at least one varray/vpool pair's devices
            if (!allTargetSystems.contains(groupTargetDevice)) {
                continue;
            }

            result.getRemoteReplicationGroups().add(toNamedRelatedResource(rrGroup));
        }
        return result;
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
                .findAllRRConsistencyGroupSystemsByAlternateLabel(cGroup.getLabel(), _dbClient);
        Iterator<RemoteReplicationGroup> rrGroups = RemoteReplicationUtils.findAllRemoteReplicationGroupsIteratively(_dbClient);
        while (rrGroups.hasNext()) {
            RemoteReplicationGroup rrGroup = rrGroups.next();
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

            // check that all vols in CG are in RRPairs of RRGroup:
            QueryResultList<URI> volsInCg = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumesByConsistencyGroup(cGroup.getId()), volsInCg);
            RemoteReplicationPairList pairsInRrGroup = getGroupPairs(rrGroup.getId(),true);
            List<URI> pairIds = new ArrayList<>();
            for (NamedRelatedResourceRep pair : pairsInRrGroup.getRemoteReplicationPairs()) {
                // get ids of pairs in RR group
                pairIds.add(pair.getId());
            }
            List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, pairIds);
            List<URI> srcVolIdsInRrGroup = new ArrayList<>();
            for (RemoteReplicationPair pair : rrPairs) {
                // get ids of all source volumes in each RR pair
                srcVolIdsInRrGroup.add(pair.getSourceElement().getURI());
            }
            if(!srcVolIdsInRrGroup.containsAll(volsInCg)) {
                _log.info("Skipping Remote Replication Group '" + rrGroup.getLabel() + "' [" +
                        rrGroup.getId() + "] for CG '" + cGroup.getLabel() + "' [" + cGroup.getId() +
                        "] since CG volumes are not all in Remote Replication Group.  CG contains " +
                        volsInCg + " and source volumes in RR Group pairs are " + srcVolIdsInRrGroup);
                continue;
            }
            _log.info("Remote Replication Group '" + rrGroup.getLabel() + "' [" + rrGroup.getId() +
                    "] contains CG '" + cGroup.getLabel() + "' [" + cGroup.getId() + "].  CG contains " +
                    volsInCg + " and source volumes in RR Group pairs are " + srcVolIdsInRrGroup);
            result.getRemoteReplicationGroups().add(toNamedRelatedResource(rrGroup));
        }
        return result;
    }

    /**
     * Get consistency groups for given remote replication group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{groupId}/consistency-groups")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public BlockConsistencyGroupList getConsistencyGroups(@PathParam("groupId") URI groupId) {
        ArgValidator.checkUri(groupId);
        ArgValidator.checkFieldUriType(groupId, RemoteReplicationGroup.class, "id");

        // get ids of pairs in group
        RemoteReplicationPairList pairsInGroup = getGroupPairs(groupId,true);
        List<URI> pairIds = new ArrayList<>();
        for (NamedRelatedResourceRep pair : pairsInGroup.getRemoteReplicationPairs()) {
            pairIds.add(pair.getId());
        }
        // get ids of all source volumes in each pair
        List<RemoteReplicationPair> rrPairs = _dbClient.queryObject(RemoteReplicationPair.class, pairIds);
        List<URI> srcVolIds = new ArrayList<>();
        for (RemoteReplicationPair pair : rrPairs) {
            srcVolIds.add(pair.getSourceElement().getURI());
        }
        // map vols by CG
        List<Volume> srcVols = _dbClient.queryObject(Volume.class, srcVolIds);
        Map<URI,List<URI>> cgToVolMap = new HashMap<>();
        for (Volume vol : srcVols) {
            if (vol.hasConsistencyGroup()) {
                if (!cgToVolMap.containsKey(vol.getConsistencyGroup())) {
                    cgToVolMap.put(vol.getConsistencyGroup(),new ArrayList<URI>());
                }
                cgToVolMap.get(vol.getConsistencyGroup()).add(vol.getId());
            }
        }
        // return CGs if RR grp has all vols in that CG
        BlockConsistencyGroupList result = new BlockConsistencyGroupList();
        List<BlockConsistencyGroup> cgs =
                _dbClient.queryObject(BlockConsistencyGroup.class, cgToVolMap.keySet());
        consistencyGroupLoop:
            for (BlockConsistencyGroup cg : cgs) {
                List<Volume> volsInCg = BlockConsistencyGroupUtils.getActiveVolumesInCG(cg, _dbClient, null);
                if (volsInCg.size() != cgToVolMap.get(cg.getId()).size()) {
                    continue; // number of vols doesn't match
                }
                for (Volume volInCg : volsInCg) {
                    if (!cgToVolMap.get(cg.getId()).contains(volInCg.getId())) {
                        continue consistencyGroupLoop; // IDs of vols don't match
                    }
                }
                // vols match, return this CG
                RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), cg.getId()));
                result.getConsistencyGroupList().add(
                        new NamedRelatedBlockConsistencyGroupRep(cg.getId(), selfLink, cg.getLabel(), null));
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
        return getGroupPairs(id,true);
    }

    /**
     * Get remote replication pairs in the remote replication group but not CG
     * @return pairs in the group but not in a consistency group
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/pairs-not-in-cg")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairList getRemoteReplicationPairsNotInCg(@PathParam("id") URI id) {
        _log.info("Called: get" +
                "getRemoteReplicationPairsNotInCg() for replication group {}", id);
        return getGroupPairs(id,false);
    }

    /*
     * Get pairs in group.  Flag includes pairs in a CG
     */
    private  RemoteReplicationPairList getGroupPairs(URI id, boolean includePairsInCg){
        ArgValidator.checkFieldUriType(id,
                com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup.class,
                "id");
        List<RemoteReplicationPair> rrPairs =
                CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, id,
                        RemoteReplicationPair.class, "replicationGroup");
        _log.info("Found pairs: {}", rrPairs);
        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();
        Iterator<RemoteReplicationPair> iter = rrPairs.iterator();
        while ((iter != null) && iter.hasNext()) {
            RemoteReplicationPair rrPair = iter.next();
            if(includePairsInCg || !rrPair.isInCG(_dbClient)) {
                rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(rrPair));
            }
        }
        return rrPairList;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/create-group")
    public TaskResourceRep createRemoteReplicationGroup(final RemoteReplicationGroupCreateParams param) throws InternalException {

        _log.info("Called: createRemoteReplicationGroup()");
        URI sourceSystem = param.getSourceSystem();
        URI targetSystem = param.getTargetSystem();
        precheckStorageSystem(sourceSystem, "source system");
        precheckStorageSystem(targetSystem, "target system");

        StorageSystem sourceSystemObject = _dbClient.queryObject(StorageSystem.class, sourceSystem);
        StorageDriverManager storageDriverManager = (StorageDriverManager) StorageDriverManager.getApplicationContext().getBean(
                StorageDriverManager.STORAGE_DRIVER_MANAGER);
        if (storageDriverManager != null && !storageDriverManager.isDriverManaged(sourceSystemObject.getSystemType())) {
            throw APIException.badRequests.unsupportedSystemType(sourceSystemObject.getSystemType());
        }

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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
            _dbClient.markForDeletion(rrGroup);
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.RESUME_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/restore")
    public TaskResourceRep restoreRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: restoreRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.RESTORE);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.RESTORE_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.restoreRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.RESTORE_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/stop")
    public TaskResourceRep stopRemoteReplicationGroupLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: stopRemoteReplicationGroupLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationGroup.class, "id");
        RemoteReplicationGroup rrGroup = queryResource(id);


        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_GROUP, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.STOP);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationGroup.class, rrGroup.getId(),
                taskId, ResourceOperationTypeEnum.STOP_REMOTE_REPLICATION_GROUP_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.stopRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.STOP_REMOTE_REPLICATION_GROUP_LINK, true, AuditLogManager.AUDITOP_BEGIN,
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
            _dbClient.error(RemoteReplicationGroup.class, rrGroup.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE, true, AuditLogManager.AUDITOP_BEGIN,
                rrGroup.getDisplayName(), rrGroup.getStorageSystemType(), rrGroup.getReplicationMode());

        return toTask(rrGroup, taskId, op);
    }

    private RemoteReplicationGroup prepareRRGroup(RemoteReplicationGroupCreateParams param) {

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
            // supported replication modes and group consistency properties for all rr sets of the same storage type are the same
            com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet rrSet = rrSets.get(0);
            StringSet rrSetSupportedReplicationModes = rrSet.getSupportedReplicationModes();
            String rrGroupReplicationMode = param.getReplicationMode();
            if (rrGroupReplicationMode != null && rrSetSupportedReplicationModes.contains(rrGroupReplicationMode)) {
                remoteReplicationGroup.setReplicationMode(rrGroupReplicationMode);
            } else {
                throw APIException.badRequests.invalidReplicationMode(param.getReplicationMode());
            }
            // verify that isGroupConsistencyEnforced settings for this group comply with parent set settings
            if (param.getIsGroupConsistencyEnforced() != null) {
                StringSet rrModesNoGroupConsistency = rrSet.getReplicationModesNoGroupConsistency();
                StringSet rrModeGroupConsistencyEnforced = rrSet.getReplicationModesGroupConsistencyEnforced();
                if (!param.getIsGroupConsistencyEnforced() && (rrModeGroupConsistencyEnforced != null) &&
                        rrModeGroupConsistencyEnforced.contains(rrGroupReplicationMode)) {
                    throw APIException.badRequests.invalidIsGroupConsistencyEnforced(param.getIsGroupConsistencyEnforced().toString());
                } else if (param.getIsGroupConsistencyEnforced() && (rrModesNoGroupConsistency != null) &&
                        rrModesNoGroupConsistency.contains(rrGroupReplicationMode)) {
                    throw APIException.badRequests.invalidIsGroupConsistencyEnforced(param.getIsGroupConsistencyEnforced().toString());
                }
            }
        } else {
            // this is error condition
            throw APIException.badRequests.noRRSetsForStorageType(param.getStorageSystemType());
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

    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public RemoteReplicationGroupBulkRep getBulkResources(BulkIdParam param) {
        return (RemoteReplicationGroupBulkRep) super.getBulkResources(param);
    }

}
