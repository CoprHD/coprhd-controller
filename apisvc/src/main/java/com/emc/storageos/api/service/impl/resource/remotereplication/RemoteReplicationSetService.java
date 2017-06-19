/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockConsistencyGroupList;
import com.emc.storageos.model.block.NamedRelatedBlockConsistencyGroupRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationGroupList;
import com.emc.storageos.model.remotereplication.RemoteReplicationModeChangeParam;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetList;
import com.emc.storageos.model.remotereplication.RemoteReplicationSetRestRep;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationController;
import com.emc.storageos.remotereplicationcontroller.RemoteReplicationUtils;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;
import com.emc.storageos.volumecontroller.impl.utils.ConsistencyGroupUtils;

@Path("/vdc/block/remotereplicationsets")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, readAcls = {
        ACL.OWN, ACL.ALL }, writeRoles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN }, writeAcls = { ACL.OWN,
        ACL.ALL })
public class RemoteReplicationSetService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationSetService.class);
    public static final String SERVICE_TYPE = "remote_replication";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;
    private RemoteReplicationGroupService rrGroupService;

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

    public RemoteReplicationGroupService getRrGroupService() {
        return rrGroupService;
    }

    public void setRrGroupService(RemoteReplicationGroupService groupService) {
        this.rrGroupService = groupService;
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

        Iterator<RemoteReplicationSet> iter = RemoteReplicationUtils.findAllRemoteReplicationSetsIteratively(_dbClient);
        while (iter.hasNext()) {
            rrSetList.getRemoteReplicationSets().add(toNamedRelatedResource(iter.next()));
        }
        return rrSetList;
    }

    /**
     * @return all remote replication sets with storage in specified varray and vpool
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/varray/{varray}/vpool/{vpool}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationSetList getRemoteReplicationSetsForVarrayVpool(@PathParam("varray") URI varrayURI,
            @PathParam("vpool") URI vpoolURI) {
        _log.info("Called: getRemoteReplicationSetsForVarrayVpool() with params: (varray: {}, vpool: {})", varrayURI, vpoolURI);
        ArgValidator.checkFieldUriType(varrayURI, VirtualArray.class, "virtual array id");
        ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "virtual pool id");
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, vpoolURI);
        if (vpool == null || vpool.getRemoteReplicationProtectionSettings() == null) {
            throw APIException.badRequests.invalidVirtualPoolUriOrNotSupportRemoteReplication(vpoolURI);
        }
        if (!vpool.getVirtualArrays().contains(varrayURI.toString())) {
            throw APIException.badRequests.vpoolVarrayMismatch(vpoolURI, varrayURI);
        }

        RemoteReplicationSetList result = new RemoteReplicationSetList();

        Set<String> sourceDevices = RemoteReplicationUtils.getStorageSystemsForVarrayVpool(varrayURI, vpoolURI,
                _dbClient, _coordinator);

        // Hold storage systems belonging to every target varray/vpool pair separately.
        Set<Set<String>> targetSystemsForAllPairs = new HashSet<>();
        // Hold all storage systems of all target varray/vpool pairs together.
        Set<String> allTargetSystems = new HashSet<String>();
        for (Entry<String, String> pair : vpool.getRemoteReplicationProtectionSettings().entrySet()) {
            URI targetvArrayURI = URI.create(pair.getKey());
            URI targetvPoolURI = URI.create(pair.getValue());
            Set<String> targetDevices = RemoteReplicationUtils.getStorageSystemsForVarrayVpool(targetvArrayURI,
                    targetvPoolURI, _dbClient, _coordinator);
            targetSystemsForAllPairs.add(targetDevices);
            allTargetSystems.addAll(targetDevices);
        }

        Iterator<RemoteReplicationSet> it = RemoteReplicationUtils.findAllRemoteReplicationSetsIteratively(_dbClient);
        outloop:
        while (it.hasNext()) {
            RemoteReplicationSet rrSet = it.next();
            Set<String> sourcesOfrrSet = rrSet.getSourceSystems();

            // Temporarily relax the filtering condition, check comment under COP-28147 for details
            // if (sourcesOfrrSet.size() > sourceDevices.size() ||!sourceDevices.containsAll(sourcesOfrrSet)) {
            if (!CollectionUtils.containsAny(sourcesOfrrSet, sourceDevices)) {
                continue;
            }

            Set<String> targetsOfrrSet = rrSet.getTargetSystems();
            // Temporarily commented out, check comment under COP-28147 for details
            // if (targetsOfrrSet.size() > allTargetSystems.size() || !allTargetSystems.containsAll(targetsOfrrSet)) {
            //     continue; // filter out rr sets that have target systems outside of target devices
            //}

            for (Set<String> targetDevices : targetSystemsForAllPairs) {
                if (!CollectionUtils.containsAny(targetsOfrrSet, targetDevices)) {
                    continue outloop; 
                    // filter out rr sets whose target systems have no overlap with target devices of current pair
                }
            }

            /* Finally, a rr set is qualified and put in result collection only if it meets following conditions:
               - Its source systems collection is the subset of ones filtered by given varray and vpool;
               - Its target systems collection is subset of ones of all target varray/vpool pairs;
               - Its target systems contain at least one storage system for each target varray/vpool pair.
             */
            result.getRemoteReplicationSets().add(toNamedRelatedResource(rrSet));
        }
        return result;
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
        String storageSystemType = rrSet.getStorageSystemType();
        Set<String> supportedReplicationModes = rrSet.getSupportedReplicationModes();

        // get groups with these source systems and filter in groups with target system in target systems
        // check that each group in result set has replication mode as supported in the set
        List<RemoteReplicationGroup> setGroups = new ArrayList<>();
        for (String system : sourceSystems) {
            URI systemURI = URIUtil.uri(system);
            List<RemoteReplicationGroup> rrGroups = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient,
                    systemURI, RemoteReplicationGroup.class, "sourceSystem");
            for (RemoteReplicationGroup rrGroup : rrGroups) {
                if (targetSystems.contains(rrGroup.getTargetSystem().toString())) {
                    if (storageSystemType != null &&
                            (storageSystemType.equalsIgnoreCase(DiscoveredDataObject.Type.vmax.toString()) ||
                                    storageSystemType.equalsIgnoreCase(DiscoveredDataObject.Type.vmax3.toString()))) {
                        // Do not check replication mode for vmax groups.
                        // VMAX groups may have stale replication mode
                        setGroups.add(rrGroup);
                    } else if (supportedReplicationModes.contains(rrGroup.getReplicationMode())) {
                        setGroups.add(rrGroup);
                    }
                }
            }
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
     * Get remote replication sets for a given consistency group.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/consistency-group/sets")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationSetList getRemoteReplicationSetsForCG(@QueryParam("consistencyGroup") URI uri) {
        ArgValidator.checkUri(uri);
        ArgValidator.checkFieldUriType(uri, BlockConsistencyGroup.class, "id");
        BlockConsistencyGroup cGroup = ConsistencyGroupUtils.findConsistencyGroupById(uri, _dbClient);
        if (ConsistencyGroupUtils.isConsistencyGroupEmpty(cGroup)) {
            // If CG is empty (storageDevice is null) any remote replication set is a match.
            return getRemoteReplicationSets();
        }
        RemoteReplicationSetList result = new RemoteReplicationSetList();
        if (!ConsistencyGroupUtils.isConsistencyGroupSupportRemoteReplication(cGroup)) {
            return result;
        }
        Set<String> targetCGSystemsSet = ConsistencyGroupUtils
                .findAllRRConsistencyGroupSystemsByAlternateLabel(cGroup.getLabel(), _dbClient);
        Iterator<RemoteReplicationSet> sets = RemoteReplicationUtils.findAllRemoteReplicationSetsIteratively(_dbClient);
        StorageSystem cgSystem = _dbClient.queryObject(StorageSystem.class, cGroup.getStorageController());
        while (sets.hasNext()) {
            RemoteReplicationSet rrSet = sets.next();
            if (!StringUtils.equals(cgSystem.getSystemType(), rrSet.getStorageSystemType())) {
                // Pass ones whose storage system type is not aligned with consistency group
                continue;
            }
            if (!rrSet.getSourceSystems().contains(URIUtil.toString(cGroup.getStorageController()))) {
                // Pass ones whose source systems can't cover source CG
                continue;
            }
            if (!rrSet.getTargetSystems().containsAll(targetCGSystemsSet)) {
                // Pass ones whose target systems can't cover target CGs
                continue;
            }
            result.getRemoteReplicationSets().add(toNamedRelatedResource(rrSet));
        }
        return result;
    }


    /**
     * Get consistency groups for a given remote replication set
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("{id}/consistency-groups")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public BlockConsistencyGroupList getRemoteReplicationSetCGs(@PathParam("id") URI id) {

        _log.info("Called: getRemoteReplicationSetCGs() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        BlockConsistencyGroupList result = new BlockConsistencyGroupList();
        RemoteReplicationSet rrSet = queryResource(id);

        List<URI> srcStorageSystemsForSet = URIUtil.toURIList(rrSet.getSourceSystems());
        if (srcStorageSystemsForSet == null) {
            _log.info("No storage systems found for Remote Replication Set while getting CGs for set '" +
                    rrSet.getLabel() + "' [" + rrSet.getId() + "]");
            return result;
        }

        List<URI> cgs = new ArrayList<>();
        for(URI storageSystemUri : srcStorageSystemsForSet ) {
            URIQueryResultList cgsForStorageSystem = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                    getStorageSystemConsistencyGroupConstraint(storageSystemUri),cgsForStorageSystem);
            Iterator<URI> itr = cgsForStorageSystem.iterator();
            while (itr.hasNext()) {
                cgs.add(itr.next());
            }
        }

        for (URI uri : cgs) {
            BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, uri);

            // skip CGs whose targets' systems are not all in the RR Set's target systems
            Set<String> targetCGSystemsSet = ConsistencyGroupUtils
                    .findAllRRConsistencyGroupSystemsByAlternateLabel(cg.getLabel(), _dbClient);
            if (!rrSet.getTargetSystems().containsAll(targetCGSystemsSet)) {
                _log.info("Removing CG from list for Remote Replication Set '" + rrSet.getLabel() +
                        "' [" + rrSet.getId() + "]. CGs target systems not all in RR Set's target " +
                        "systems for CG '" + cg.getLabel() + "' [" + cg.getId() + "]");
                continue;
            }

            // skip CG if volumes not all in pairs that are in same set
            QueryResultList<URI> volsInCg = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                    getVolumesByConsistencyGroup(cg.getId()), volsInCg);
            QueryResultList<URI> pairsInSet = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                    getRemoteReplicationPairSetConstraint(rrSet.getId()),pairsInSet);
            List<URI> srcVolsInPairs = new ArrayList<>();
            for ( URI pairId : pairsInSet) {
                RemoteReplicationPair pair = _dbClient.queryObject(RemoteReplicationPair.class, pairId);
                srcVolsInPairs.add(pair.getSourceElement().getURI());
            }
            if(!srcVolsInPairs.containsAll(volsInCg)) {
                _log.info("Consistency Group disqualified from RemoteReplicationSet because not all " +
                        "volumes in CG are in Set. CG:'" + cg.getLabel() + "' [" + cg.getId() + "] " +
                        "RR Set:'" + rrSet.getLabel() + "' [" + rrSet.getId() + "]. Volumes in CG: " +
                        srcVolsInPairs + "  RR Pairs in set: " + pairsInSet);
                continue;
            }
            result.getConsistencyGroupList().add(
                    new NamedRelatedBlockConsistencyGroupRep(cg.getId(), DbObjectMapper.toLink(cg),
                            cg.getLabel(), null));
        }
        return result;
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
        List<RemoteReplicationPair> rrPairs = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, id,
                RemoteReplicationPair.class, "replicationSet");
        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();
        if (rrPairs != null) {
            _log.info("Found total pairs: {}", rrPairs.size());
            for (RemoteReplicationPair rrPair : rrPairs) {
                if((rrPair.getReplicationGroup() == null) && !rrPair.isInCG(_dbClient)) {
                    // return only pairs directly in replication set
                    rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(rrPair));
                }
                _log.info("Found pairs: {} directly in the set", rrPairList.getRemoteReplicationPairs().size());
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_OVER);

        // Create a task for the failover remote replication Set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.FAIL_BACK);

        // Create a task for the failback remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failbackRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.ESTABLISH);

        // Create a task for the establish remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.establishRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SPLIT);

        // Create a task for the split remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.splitRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SUSPEND);

        // Create a task for the suspend remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.suspendRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.RESUME);

        // Create a task for the resume remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.resumeRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.SWAP);

        // Create a task for the swap remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.swapRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrSet.getDeviceLabel(), rrSet.getStorageSystemType());

        return toTask(rrSet, taskId, op);
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/stop")
    public TaskResourceRep stopRemoteReplicationSetLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: stopRemoteReplicationSetLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationSet.class, "id");
        RemoteReplicationSet rrSet = queryResource(id);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationOperation(_dbClient, rrElement, RemoteReplicationController.RemoteReplicationOperations.STOP);

        // Create a task for the stop remote replication set operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.STOP_REMOTE_REPLICATION_SET_LINK);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.stopRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
        }

        auditOp(OperationTypeEnum.STOP_REMOTE_REPLICATION_SET_LINK, true, AuditLogManager.AUDITOP_BEGIN,
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

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_SET, id);
        RemoteReplicationUtils.validateRemoteReplicationModeChange(_dbClient, rrElement, newMode);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationSet.class, rrSet.getId(),
                taskId, ResourceOperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE);

        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.changeRemoteReplicationMode(rrElement, newMode, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            _dbClient.error(RemoteReplicationSet.class, rrSet.getId(), taskId, e);
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
