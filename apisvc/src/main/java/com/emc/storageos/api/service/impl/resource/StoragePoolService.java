/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.toVirtualPoolResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toTypedRelatedResource;
import static com.emc.storageos.api.mapper.SystemsMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.PurgeRunnable;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.TypedRelatedResourceRep;
import com.emc.storageos.model.block.tier.StorageTierList;
import com.emc.storageos.model.pools.StoragePoolBulkRep;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.pools.StoragePoolResources;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.pools.StoragePoolUpdate;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.google.common.base.Function;

/**
 * StoragePool resource implementation
 */
@Path("/vdc/storage-pools")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StoragePoolService extends TaggedResource {

    private Logger _logger = LoggerFactory.getLogger(StoragePoolService.class);
    private static final String EVENT_SERVICE_TYPE = "StoragePool";

    protected static final String EVENT_SERVICE_SOURCE = "StoragePoolService";
    protected static final String STORAGEPOOL_UPDATED_DESCRIPTION = "Storage pool Updated";
    protected static final String STORAGEPOOL_DEREGISTERED_DESCRIPTION = "Storage Pool Unregistered";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    // how many times to retry a procedure before returning failure to the user.
    // Is used with "system delete" operation.
    private int _retry_attempts;

    private static final Logger _log = LoggerFactory.getLogger(StoragePoolService.class);

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    public void setRetryAttempts(int retries) {
        _retry_attempts = retries;
    }

    /**
     * Gets the storage pool with the passed id from the database.
     * 
     * @param id the URN of a ViPR storage pool.
     * 
     * @return A reference to the registered StoragePool.
     * 
     * @throws BadRequestException When the storage pool is not registered.
     */
    protected StoragePool queryRegisteredResource(URI id) {
        ArgValidator.checkUri(id);
        StoragePool pool = _dbClient.queryObject(StoragePool.class, id);
        ArgValidator.checkEntityNotNull(pool, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                pool.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(StoragePool.class.getSimpleName(), id);
        }

        return pool;
    }

    @Override
    protected StoragePool queryResource(URI id) {
        ArgValidator.checkUri(id);
        StoragePool pool = _dbClient.queryObject(StoragePool.class, id);
        ArgValidator.checkEntity(pool, id, isIdEmbeddedInURL(id));
        return pool;
    }

    /**
     * Gets the ids and self links for all storage pools.
     * 
     * @brief List storage pools
     * @return A StoragePoolList reference specifying the ids and self links for
     *         the storage pools.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePoolList getStoragePools() {

        StoragePoolList storagePools = new StoragePoolList();
        List<URI> ids = _dbClient.queryByType(StoragePool.class, true);
        for (URI id : ids) {
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, id);
            if (storagePool != null) {
                storagePools.getPools().add(toNamedRelatedResource(storagePool, storagePool.getNativeGuid()));
            }
        }

        return storagePools;
    }

    /**
     * Gets the ids and self links for all matched VirtualPools for a given storage pool.
     * 
     * @brief List matching VirtualPools for specified storage pool
     * @return A VirtualPoolList reference specifying the ids and self links for
     *         the matched VirtualPool.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/matched-vpools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VirtualPoolList getMatchedVirtualPoolForPool(@PathParam("id") URI id) {
        VirtualPoolList vpools = new VirtualPoolList();
        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        StoragePool storagePool = queryRegisteredResource(id);
        ArgValidator.checkEntity(storagePool, id, isIdEmbeddedInURL(id));
        URIQueryResultList cosResultList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getMatchedPoolVirtualPoolConstraint(id), cosResultList);
        Iterator<URI> cosListItr = cosResultList.iterator();
        while (cosListItr.hasNext()) {
            VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, cosListItr.next());
            vpools.getVirtualPool().add(toVirtualPoolResource(vpool));
        }
        return vpools;
    }

    /**
     * Gets the data for a storage pool.
     * 
     * @param id the URN of a ViPR storage pool.
     * 
     * @brief Show storage pool
     * @return A StoragePoolRestRep reference specifying the data for the
     *         storage pool with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePoolRestRep getStoragePool(@PathParam("id") URI id) {

        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        StoragePool storagePool = queryResource(id);
        ArgValidator.checkEntity(storagePool, id, isIdEmbeddedInURL(id));

        StoragePoolRestRep restRep = toStoragePoolRep(storagePool, _dbClient, _coordinator);
        restRep.setNumResources(getNumResources(storagePool, _dbClient));
        return restRep;
    }

    /**
     * Get Storage tiers associated with given Pool
     * Vmax pools, only one tier will be present always
     * Vnx pools can have multiple tiers.
     * 
     * @param id the URN of a ViPR storage pool.
     * 
     * @brief List storage pool storage tiers
     * @return A StorageTierList reference specifying the data for the
     *         storage tier with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-tiers")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StorageTierList getStorageTiers(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        StoragePool storagePool = queryRegisteredResource(id);
        ArgValidator.checkEntity(storagePool, id, isIdEmbeddedInURL(id));
        if (storagePool.getTiers() == null) {
            throw APIException.badRequests.invalidParameterStoragePoolHasNoTiers(id);
        }
        StorageTierList storageTierList = new StorageTierList();

        for (String tierUri : storagePool.getTiers()) {
            StorageTier tier = _dbClient.queryObject(StorageTier.class, URI.create(tierUri));
            if (null != tier) {
                storageTierList.getStorageTiers().add(toNamedRelatedResource(tier, tier.getNativeGuid()));
            }
        }
        return storageTierList;
    }

    /**
     * Allows the user to deregister a registered storage pool so that it is no
     * longer used by the system. This simply sets the registration_status of
     * the storage pool to UNREGISTERED.
     * 
     * @param id the URN of a ViPR storage pool to deregister.
     * 
     * @brief Unregister storage pool
     * @return Status indicating success or failure.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolRestRep deregisterStoragePool(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        StoragePool pool = queryResource(id);
        StringBuffer errorMessage = new StringBuffer();
        if (RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                pool.getRegistrationStatus())) {
            pool.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            // run implicit pool matcher algorithm to update the matched pools in VirtualPool.
            if (null == pool.getConnectedVirtualArrays() || pool.getConnectedVirtualArrays().isEmpty()) {
                ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(Arrays.asList(pool), _dbClient, _coordinator,errorMessage);
            }
            _dbClient.persistObject(pool);
            // Record the storage pool deregister event.
            recordStoragePoolEvent(OperationTypeEnum.STORAGE_POOL_DEREGISTER,
                    STORAGEPOOL_DEREGISTERED_DESCRIPTION, pool.getId());

            auditOp(OperationTypeEnum.DEREGISTER_STORAGE_POOL, true, null, id.toString());
        }

        return toStoragePoolRep(pool, _dbClient, _coordinator);
    }

    /**
     * This API call only allows user to update virtual array & virtual pool
     * assignments for the registered storage pool.
     * <p>
     * A pool can be associated with a virtual array either implicitly or explicitly. A pool is implicitly associated with a virtual array
     * when the pool's storage system has one or more ports in the virtual array (see {@link StoragePool#getConnectedVirtualArrays()}). the
     * pool's implicit virtual arrays are the union of all the tagged virtual arrays of the storage array ports. This implicit association
     * cannot be changed or removed, it can only be overridden by an explicit assignment (see {@link StoragePool#getAssignedVirtualArrays()}
     * ). A pool's effective virtual array association is {@link StoragePool#getTaggedVirtualArrays()})
     * <p>
     * Managing pools associated virtual arrays requires planning. In general, pools should be assigned to virtual arrays only when it is
     * desired to limit the virtual arrays where they can be used.
     * 
     * @param id the URN of a ViPR storage pool.
     * @param storagePoolUpdates Specifies the updates to be made to the storage
     *            pool.
     * 
     * @brief Update storage pool
     * @return A StoragePoolRestRep specifying the updated storage pool info.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolRestRep updateStoragePool(@PathParam("id") URI id,
            StoragePoolUpdate storagePoolUpdates) {
        // Get the storage pool with the passed id.
        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        StoragePool storagePool = queryRegisteredResource(id);
        ArgValidator.checkEntity(storagePool, id, isIdEmbeddedInURL(id));

        boolean neighborhoodChange = false;

        // Validate that the neighborhoods to be assigned to the storage pool
        // reference existing neighborhoods in the database and add them to
        // the storage pool.
        if (storagePoolUpdates.getVarrayChanges() != null) {
            VirtualArrayAssignments addedNH = storagePoolUpdates.getVarrayChanges().getAdd();
            if ((addedNH != null) && (!addedNH.getVarrays().isEmpty())) {
                VirtualArrayService.checkVirtualArrayURIs(addedNH.getVarrays(), _dbClient);
                storagePool.addAssignedVirtualArrays(addedNH.getVarrays());
            }

            // Validate that the neighborhoods to be unassigned from the storage
            // pool reference existing neighborhoods in the database and remove
            // them from the storage pool.
            VirtualArrayAssignments removedNH = storagePoolUpdates.getVarrayChanges().getRemove();
            if ((removedNH != null) && (!removedNH.getVarrays().isEmpty())) {
                VirtualArrayService.checkVirtualArrayURIs(removedNH.getVarrays(), _dbClient);
                storagePool.removeAssignedVirtualArrays(removedNH.getVarrays());
            }
            verifyPoolNoInUseInVarrays(storagePool);
            neighborhoodChange = true;
        }

        // If there is change in varray, then update new matched pools
        // for all VirtualPool.
        if (neighborhoodChange) {
            StringBuffer errorMessage = new StringBuffer();
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(Arrays.asList(storagePool), _dbClient, _coordinator,
                    errorMessage);
        }

        Integer currentMaxSubscriptionPercentFromArray = storagePool.getMaxThinPoolSubscriptionPercentageFromArray();
        _logger.info(String.format("Current maximum subscription percent in storage pool from array : %s ",
                currentMaxSubscriptionPercentFromArray));

        if (null != storagePoolUpdates.getMaxPoolUtilizationPercentage()) {

            if (storagePoolUpdates.getMaxPoolUtilizationPercentage() < 0 || storagePoolUpdates.getMaxPoolUtilizationPercentage() > 100) {
                throw APIException.badRequests.invalidParameterPercentageExpected("max_pool_utilization_percentage",
                        storagePoolUpdates.getMaxPoolUtilizationPercentage());
            }

            // check that a new value does not exceed array limit
            if (currentMaxSubscriptionPercentFromArray != null
                    && storagePoolUpdates.getMaxPoolUtilizationPercentage() > currentMaxSubscriptionPercentFromArray) {
                throw APIException.badRequests.invalidParameterValueExceedsArrayLimit("max_pool_utilization_percentage",
                        storagePoolUpdates.getMaxPoolUtilizationPercentage(), currentMaxSubscriptionPercentFromArray);
            }
            storagePool
                    .setMaxPoolUtilizationPercentage(storagePoolUpdates.getMaxPoolUtilizationPercentage());
        }
        if (null != storagePoolUpdates.getMaxThinPoolSubscriptionPercentage()) {

            ArgValidator.checkFieldMinimum(storagePoolUpdates.getMaxThinPoolSubscriptionPercentage(), 0,
                    "max_thin_pool_subscription_percentage");

            if (!validateMaxThinPoolSubscriptionInput(storagePool, storagePoolUpdates.getMaxThinPoolSubscriptionPercentage())) {
                throw APIException.badRequests.parameterIsOnlyApplicableTo("max_thin_pool_subscription_percentage", "Thin Pool");
            }

            // check that a new value does not exceed array limit
            if (currentMaxSubscriptionPercentFromArray != null
                    && storagePoolUpdates.getMaxThinPoolSubscriptionPercentage() > currentMaxSubscriptionPercentFromArray) {
                throw APIException.badRequests.invalidParameterValueExceedsArrayLimit("max_thin_pool_subscription_percentage",
                        storagePoolUpdates.getMaxThinPoolSubscriptionPercentage(), currentMaxSubscriptionPercentFromArray);
            }

            storagePool
                    .setMaxThinPoolSubscriptionPercentage(storagePoolUpdates.getMaxThinPoolSubscriptionPercentage());
        }

        // If unlimited resources is specified and set to true, then no need to look at max resources
        // If unlimited resources is set to false, then max resources should also be specified. If not specified, throw error
        if (null != storagePoolUpdates.getIsUnlimitedResourcesSet()) {
            if (storagePoolUpdates.getIsUnlimitedResourcesSet()) {
                storagePool.setIsResourceLimitSet(false);
            } else {
                if (null != storagePoolUpdates.getMaxResources()) {
                    storagePool.setIsResourceLimitSet(true);
                    storagePool.setMaxResources(storagePoolUpdates.getMaxResources());
                } else {
                    throw APIException.badRequests.parameterMaxResourcesMissing();
                }
            }

        } else if (null != storagePoolUpdates.getMaxResources()) {
            storagePool.setMaxResources(storagePoolUpdates.getMaxResources());
            storagePool.setIsResourceLimitSet(true);
        }
        // Persist the changes and return a successful response.
        _dbClient.updateAndReindexObject(storagePool);

        // Record the storage pool update event.
        recordStoragePoolEvent(OperationTypeEnum.STORAGE_POOL_UPDATE,
                STORAGEPOOL_UPDATED_DESCRIPTION, storagePool.getId());

        auditOp(OperationTypeEnum.UPDATE_STORAGE_POOL, true, null, id.toString());

        return toStoragePoolRep(storagePool, _dbClient, _coordinator);
    }

    /**
     * Remove a storage pool. The method would remove the deregistered storage pool and all resources
     * associated with the storage pool from the database.
     * Note they are not removed from the storage system physically,
     * but become unavailable for the user.
     * 
     * @param id the URN of a ViPR storage pool to be removed.
     * 
     * @brief Remove storage pool from ViPR
     * @return Status indicating success or failure.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteStoragePool(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        StoragePool pool = queryResource(id);

        if (!RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                pool.getRegistrationStatus()) ||
                DiscoveryStatus.VISIBLE.name().equalsIgnoreCase(
                        pool.getDiscoveryStatus())) {
            throw APIException.badRequests.cannotDeactivateStoragePool();
        }

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(StoragePool.class, id,
                taskId, ResourceOperationTypeEnum.DELETE_STORAGE_POOL);

        PurgeRunnable.executePurging(_dbClient, _dbPurger,
                _asynchJobService.getExecutorService(), pool,
                _retry_attempts, taskId, 60);
        return toTask(pool, taskId, op);
    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param type
     * @param type
     * @param description
     * @param storagePort
     */
    private void recordStoragePoolEvent(OperationTypeEnum opType, String description,
            URI storagePool) {

        String evType;
        evType = opType.getEvType(true);

        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */evType,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* VirtualPool */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */storagePool,
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */"",
                /* native guid */null,
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",
                    description, ex);
        }
    }

    /**
     * Verify whether thinPoolSubscriptionPercentageLimit is applicable to this pool or not.
     * 
     * @param pool
     * @param thinPoolSubscriptionPercentageLimit
     * @return
     */
    private boolean validateMaxThinPoolSubscriptionInput(StoragePool pool,
            Integer thinPoolSubscriptionPercentageLimit) {

        if (null != thinPoolSubscriptionPercentageLimit) {
            String resType = pool.getSupportedResourceTypes();
            if (null == resType) {
                _log.error("Supported reousrce type for the storage pool was not set.");
                return false;
            }

            _log.debug("validate pool of type {} for limit of {}.", resType, thinPoolSubscriptionPercentageLimit);
            if (resType.equals(StoragePool.SupportedResourceTypes.THICK_ONLY.name())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Retrieves the id, name, and type of the resources in the registered
     * storage pool. with the passed id.
     * 
     * @param id the URN of a ViPR storage pool.
     * 
     * @brief List storage pool resources
     * @return A list of the resources in the storage pool.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resources")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePoolResources getStoragePoolResources(@PathParam("id") URI id) {
        // Make sure the storage pool is registered.
        ArgValidator.checkFieldUriType(id, StoragePool.class, "id");
        queryRegisteredResource(id);

        // Create the storage pools resources to be returned.
        StoragePoolResources resources = new StoragePoolResources();

        // Get the active volumes in the storage pool and add them to
        // the storage pool resources
        URIQueryResultList volumeURIList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStoragePoolVolumeConstraint(id),
                volumeURIList);
        Iterator<URI> volumeURIIter = volumeURIList.iterator();
        while (volumeURIIter.hasNext()) {
            URI volumeURI = volumeURIIter.next();
            Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
            if ((volume != null) && (!volume.getInactive())) {
                TypedRelatedResourceRep resource = toTypedRelatedResource(volume);
                resources.getResources().add(resource);
            }
        }

        // Get the active file shares in the storage pool and add them to the
        // storage pools resources.
        URIQueryResultList fsURIList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStoragePoolFileshareConstraint(id),
                fsURIList);
        Iterator<URI> fsURIIter = fsURIList.iterator();
        while (fsURIIter.hasNext()) {
            URI fsURI = fsURIIter.next();
            FileShare fs = _dbClient.queryObject(FileShare.class, fsURI);
            if ((fs != null) && (!fs.getInactive())) {
                TypedRelatedResourceRep resource = toTypedRelatedResource(fs);
                resources.getResources().add(resource);
            }
        }

        return resources;
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of storage pool resources
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public StoragePoolBulkRep getBulkResources(BulkIdParam param) {
        return (StoragePoolBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StoragePool> getResourceClass() {
        return StoragePool.class;
    }

    @Override
    public StoragePoolBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<StoragePool> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new StoragePoolBulkRep(BulkList.wrapping(_dbIterator, new Function<StoragePool, StoragePoolRestRep>() {
            @Override
            public StoragePoolRestRep apply(StoragePool storagePool) {
                StoragePoolRestRep restRep = toStoragePoolRep(storagePool, _dbClient, _coordinator);
                restRep.setNumResources(getNumResources(storagePool, _dbClient));
                return restRep;
            }
        }));
    }

    @Override
    public StoragePoolBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    public static StoragePoolRestRep toStoragePoolRep(StoragePool pool, DbClient dbClient, CoordinatorClient coordinator) {
        boolean isBlockStoragePool = StoragePool.PoolServiceType.block.name().
                equalsIgnoreCase(pool.getPoolServiceType());
        Map<String, BigInteger> rawCapacityMetrics = CapacityUtils.getPoolCapacityMetrics(pool);
        Map<String, Long> capacityMetrics = CapacityUtils.preparePoolCapacityMetrics(rawCapacityMetrics);
        return map(pool, capacityMetrics, isBlockStoragePool, coordinator);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_POOL;
    }

    // Counts and returns the number of resources in a pool
    public static Integer getNumResources(StoragePool pool, DbClient dbClient) {
        String serviceType = pool.getPoolServiceType();
        if (StoragePool.PoolServiceType.file.name().equals(serviceType)) {
            return dbClient.countObjects(FileShare.class, "pool", pool.getId());
        }
        if (StoragePool.PoolServiceType.block.name().equals(serviceType)) {
            return dbClient.countObjects(Volume.class, "pool", pool.getId());
        }
        // We don't do anything if it's of type object
        return 0;
    }

    /**
     * Checks that the pool does not have any volumes in the varrays from which it is being removed
     * 
     * @param storagePool
     */
    private void verifyPoolNoInUseInVarrays(StoragePool storagePool) {
        _log.debug("Checking virtual array changes allowed for pool {}.", storagePool.getNativeGuid());
        List<Volume> volumes = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient,
                storagePool.getId(), Volume.class, "pool");
        for (Volume volume : volumes) {
            // only error if the pool ends up in a state where it can no longer be used in the varray
            // if removing the varrays reverts the pool to using implicit varrays which contains the
            // volumes, then it is all good.
            if (!storagePool.getTaggedVirtualArrays().contains(volume.getVirtualArray().toString())) {
                _log.debug("The pool is in use by volume {} in varray {} which will no longer in the pool's tagged varray",
                        volume.getLabel(), volume.getVirtualArray().toString());
                throw APIException.badRequests.cannotChangePoolVarraysVolumeExists(
                        storagePool.getNativeGuid(), volume.getVirtualArray().toString(), volume.getLabel());
            }
        }
    }

}
