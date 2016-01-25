/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualPoolMapper.toObjectVirtualPool;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.volumecontroller.AttributeMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.VirtualPoolMapper;
import com.emc.storageos.api.mapper.functions.MapObjectVirtualPool;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.model.vpool.ObjectVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolParam;
import com.emc.storageos.model.vpool.ObjectVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ObjectVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Function;

@Path("/object/vpools")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ObjectVirtualPoolService extends VirtualPoolService {

    private static final Logger _log = LoggerFactory.getLogger(ObjectVirtualPoolService.class);

    /**
     * Create Object Store VirtualPool
     * 
     * @param param VirtualPool parameters
     * @brief Create VirtualPool for a Object store
     * @return VirtualPool details
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ObjectVirtualPoolRestRep createObjectVirtualPool(ObjectVirtualPoolParam param) {

        ArgValidator.checkFieldNotEmpty(param.getName(), VPOOL_NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), VPOOL_DESCRIPTION);
        VirtualPoolUtil.validateObjectVirtualPoolCreateParams(param, _dbClient);
        VirtualPool cos = prepareVirtualPool(param);

        // update the implicit pools matching with this VirtualPool.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator);
        _dbClient.createObject(cos);

        recordOperation(OperationTypeEnum.CREATE_VPOOL, VPOOL_CREATED_DESCRIPTION, cos);

        return toObjectVirtualPool(cos);
    }

    /**
     * List VirtualPool for Object Store
     * 
     * @brief List classes of service for a Object store
     * @return Returns the VirtualPool user is authorized to see
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualPoolList listObjectVirtualPool(@DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        return getVirtualPoolList(VirtualPool.Type.object, shortVdcId);
    }

    /**
     * Get info for Object Store VirtualPool
     * 
     * @param id the URN of a ViPR VirtualPool
     * @brief Show Object store VirtualPool
     * @return VirtualPool details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ObjectVirtualPoolRestRep getObjectVirtualPool(@PathParam("id") URI id) {
        VirtualPool vpool = getVirtualPool(VirtualPool.Type.object, id);
        ObjectVirtualPoolRestRep restRep = toObjectVirtualPool(vpool);
        restRep.setNumResources(getNumResources(vpool, _dbClient));
        if (null != vpool.getMaxRetention()) {
            restRep.setMaxRetention(vpool.getMaxRetention());
        }
        if (null != vpool.getMinDataCenters()) {
            restRep.setMinDataCenters(vpool.getMinDataCenters());
        }
        return restRep;
    }

    /**
     * Deactivate Object Store VirtualPool, this will move the Cos to a "marked-for-deletion" state,
     * and no more resource may be created using it.
     * The VirtualPool will be deleted when all references to this VirtualPool of type ObjectShare are deleted
     * 
     * @param id the URN of a ViPR VirtualPool
     * @brief Delete Object store VirtualPool
     * @return VirtualPool details
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteObjectVirtualPool(@PathParam("id") URI id) {
        return deleteVirtualPool(VirtualPool.Type.object, id);
    }

    /**
     * Return the matching pools for a given set of VirtualPool attributes.
     * This API is useful for user to find the matching pools before creating a VirtualPool.
     * 
     * @param param : VirtualPoolAttributeParam
     * @brief List pools matching specified properties in Object store VirtualPool
     * @return : matching pools.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/matching-pools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolList getMatchingPoolsForVirtualPoolAttributes(ObjectVirtualPoolParam param) {
        StoragePoolList poolList = new StoragePoolList();
        VirtualPool vpool = prepareVirtualPool(param);
        List<URI> poolURIs = _dbClient.queryByType(StoragePool.class, true);
        List<StoragePool> allPools = _dbClient.queryObject(StoragePool.class, poolURIs);

        List<StoragePool> matchedPools = ImplicitPoolMatcher.getMatchedPoolWithStoragePools(vpool,
                allPools,
                null,
                null,
                null,
                _dbClient,
                _coordinator, AttributeMatcher.VPOOL_MATCHERS);
        for (StoragePool pool : matchedPools) {
            poolList.getPools().add(toNamedRelatedResource(pool, pool.getNativeGuid()));
        }
        return poolList;
    }

    /**
     * Get Object Store VirtualPool ACL
     * 
     * @param id the URN of a ViPR VirtualPool
     * @brief Show ACL entries for Object store VirtualPool
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnVirtualPool(VirtualPool.Type.object, id);
    }

    /**
     * Add or remove individual Object Store VirtualPool ACL entry(s). Request body must include at least one add or remove operation.
     * 
     * @param id the URN of a ViPR VirtualPool
     * @param changes ACL assignment changes
     * @brief Add or remove ACL entries from Object store VirtualPool
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        return updateAclsOnVirtualPool(VirtualPool.Type.object, id, changes);
    }

    /**
     * Returns list of computed id's for all storage pools matching with the VirtualPool.
     * This list of pools will be used to do create Object.
     * 
     * @param id the URN of a ViPR VirtualPool.
     * 
     * @brief List storage pools in Object store VirtualPool
     * @return The ids for all storage pools that satisfy the VirtualPool.
     */
    @GET
    @Path("/{id}/storage-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePoolList getStoragePools(@PathParam("id") URI id) {
        return getStoragePoolsForVirtualPool(id);
    }

    /**
     * This method re-computes the matched pools for this VirtualPool and returns this information.
     * 
     * Where as getStoragePools {id}/storage-pools returns whatever is already computed, for matched pools.
     * 
     * @param id : the URN of a ViPR Block VirtualPool.
     * @brief Refresh list of storage pools in Object store VirtualPool
     * @return : List of Pool Ids matching with this VirtualPool.
     */
    @GET
    @Path("/{id}/refresh-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolList refreshMatchedStoragePools(@PathParam("id") URI id) {
        return refreshMatchedPools(VirtualPool.Type.object, id);
    }

    /**
     * Update Object VirtualPool only allows if there are no resources associated and
     * list of attributes changed not changed.
     * 
     * List of attributes can updated if it satisfies above constraint:
     * assignedStoragePools & useMatchedStoragePools flag.
     * 
     * @param param
     *            VirtualPool parameters
     * @brief Update description of Object store VirtualPool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ObjectVirtualPoolRestRep updateObjectVirtualPool(@PathParam("id") URI id, ObjectVirtualPoolUpdateParam param) {
        VirtualPool cos = null;
        ArgValidator.checkFieldUriType(id, VirtualPool.class, "id");
        cos = _dbClient.queryObject(VirtualPool.class, id);
        ArgValidator.checkEntity(cos, id, isIdEmbeddedInURL(id));
        if (!cos.getType().equals(VirtualPool.Type.object.name())) {
            throw APIException.badRequests.unexpectedValueForProperty("VPool type", VirtualPool.Type.object.name(), cos.getType());
        }
        VirtualPoolUtil.validateObjectVirtualPoolUpdateParams(cos, param, _dbClient);
        
        // Validate the attributes that could be change if resource is created.
        if (getNumResources(cos, _dbClient) > 0 && checkAttributeValuesChanged(param, cos)) {
            throw APIException.badRequests.vPoolUpdateNotAllowed("Bucket");
        }

        // set common update VirtualPool Params here.
        populateCommonVirtualPoolUpdateParams(cos, param);
        if (null != param.getMaxRetention()) {
            cos.setMaxRetention(param.getMaxRetention());
        }
        if (null != param.getMinDataCenters()) {
            cos.setMinDataCenters(param.getMinDataCenters());
        }
        
        if (null != param.getSystemType()) {
            if (cos.getArrayInfo().containsKey(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                for (String systemType : cos.getArrayInfo().get(
                        VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                    cos.getArrayInfo().remove(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE,
                            systemType);
                }
            }

            if (!(VirtualPool.SystemType.NONE.name().equalsIgnoreCase(param.getSystemType())
            || VirtualPool.SystemType.isObjectTypeSystem(param.getSystemType()))) {
                throw APIException.badRequests.invalidSystemType("Object");
            }
            cos.getArrayInfo()
                    .put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
        }

        // invokes implicit pool matching algorithm.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator);

        _dbClient.updateAndReindexObject(cos);

        recordOperation(OperationTypeEnum.UPDATE_VPOOL, VPOOL_UPDATED_DESCRIPTION, cos);
        return toObjectVirtualPool(cos);
    }

    /**
     * Update Object VirtualPool only allows user to assign matching storage pools.
     * 
     * @param param
     *            VirtualPool parameters
     * @brief Update storage pools in Object store VirtualPool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/assign-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public ObjectVirtualPoolRestRep updateObjectVirtualPoolWithAssignedPools(@PathParam("id") URI id,
            VirtualPoolPoolUpdateParam param) {
        return toObjectVirtualPool(updateVirtualPoolWithAssignedStoragePools(id, param));
    }

    /**
     * Gets storage capacity information for specified VirtualPool and Neighborhood instances.
     * 
     * The method returns set of metrics for capacity available for Object storage provisioning:
     * - usable_gb : total storage capacity
     * - free_gb : free storage capacity
     * - used_gb : used storage capacity
     * - percent_used : percent of usable capacity which is used
     * 
     * @param id the URN of a ViPR VirtualPool.
     * @param varrayId The id of varray.
     * @brief Show storage capacity for a VirtualPool and varray
     * @return Capacity metrics in GB and percent indicator for used capacity.
     */
    @GET
    @Path("/{id}/varrays/{varrayId}/capacity")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public CapacityResponse getCapacity(@PathParam("id") URI id,
            @PathParam("varrayId") URI varrayId) {
        return getCapacityForVirtualPoolAndVirtualArray(getVirtualPool(Type.object, id), varrayId);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected Type getVirtualPoolType() {
        return Type.object;
    }

    /**
     * @brief List all instances of Object VirtualPools
     * 
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ObjectVirtualPoolBulkRep getBulkResources(BulkIdParam param) {
        return (ObjectVirtualPoolBulkRep) super.getBulkResources(param);
    }

    /**
     * Gets Quota information.
     * 
     * @param id the URN of a ViPR VirtualPool.
     * @brief Show quota and available capacity before quota is exhausted
     * @return QuotaInfo Quota metrics.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo getQuota(@PathParam("id") URI id) throws DatabaseException {
        return getQuota(getVirtualPool(Type.object, id));
    }

    /**
     * Update Quota information.
     * 
     * @param id the URN of a ViPR VirtualPool.
     * @param param new values for the quota
     * @brief Updates quota and available capacity before quota is exhausted
     * @return QuotaInfo Quota metrics.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo updateQuota(@PathParam("id") URI id,
            QuotaUpdateParam param) throws DatabaseException {
        return updateQuota(getVirtualPool(Type.object, id), param);
    }

    private class mapObjectVirtualPoolWithResources implements Function<VirtualPool, ObjectVirtualPoolRestRep> {
        @Override
        public ObjectVirtualPoolRestRep apply(VirtualPool vpool) {
            ObjectVirtualPoolRestRep resp = VirtualPoolMapper.toObjectVirtualPool(vpool);
            resp.setNumResources(getNumResources(vpool, _dbClient));
            return resp;
        }
    }

    /**
     * Gets list of all Object Virtual pool IDs
     */
    @Override
    public ObjectVirtualPoolBulkRep queryBulkResourceReps(List<URI> ids) {

        if (!ids.iterator().hasNext()) {
            return new ObjectVirtualPoolBulkRep();
        }

        // get vdc id from the first id; assume all id's are from the same vdc
        String shortVdcId = VdcUtil.getVdcId(VirtualArray.class, ids.iterator().next()).toString();

        Iterator<VirtualPool> dbIterator;
        if (shortVdcId.equals(VdcUtil.getLocalShortVdcId())) {
            dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                dbIterator = geoClient.queryObjects(getResourceClass(), ids);
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving bulk virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote virtual pool", ex);
            }
        }
        return new ObjectVirtualPoolBulkRep(BulkList.wrapping(dbIterator, new mapObjectVirtualPoolWithResources(),
                new BulkList.VirtualPoolFilter(Type.object)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected ObjectVirtualPoolBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        if (isSystemAdmin()) {
            return queryBulkResourceReps(ids);
        }

        if (!ids.iterator().hasNext()) {
            return new ObjectVirtualPoolBulkRep();
        }

        // get vdc id from the first id; assume all id's are from the same vdc
        String shortVdcId = VdcUtil.getVdcId(VirtualArray.class, ids.iterator().next()).toString();

        Iterator<VirtualPool> dbIterator;
        if (shortVdcId.equals(VdcUtil.getLocalShortVdcId())) {
            dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                dbIterator = geoClient.queryObjects(getResourceClass(), ids);
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving bulk virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote virtual pool", ex);
            }
        }
        BulkList.ResourceFilter filter = new BulkList.VirtualPoolFilter(Type.object, getUserFromContext(), _permissionsHelper);
        return new ObjectVirtualPoolBulkRep(BulkList.wrapping(dbIterator, MapObjectVirtualPool.getInstance(), filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.OBJECT_VPOOL;
    }

    // this method must not persist anything to the DB.
    private VirtualPool prepareVirtualPool(ObjectVirtualPoolParam param) {
        VirtualPool vPool = new VirtualPool();
        vPool.setType(VirtualPool.Type.object.name());
        // set common VirtualPool parameters.
        populateCommonVirtualPoolCreateParams(vPool, param);
        StringSetMap arrayInfo = new StringSetMap();
        if (null != param.getSystemType()) {
            if (!VirtualPool.SystemType.NONE.toString().equals(param.getSystemType())
                    && !VirtualPool.SystemType.isObjectTypeSystem(param.getSystemType())) {
                throw APIException.badRequests.invalidParameter("system_type", param.getSystemType());
            }
            arrayInfo.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
            vPool.addArrayInfoDetails(arrayInfo);
        }
        if (null != param.getMaxRetention()) {
            vPool.setMaxRetention(param.getMaxRetention());
        }
        
        if (null != param.getMinDataCenters()) {
            vPool.setMinDataCenters(param.getMinDataCenters());
        }

        return vPool;
    }
    
    private static Integer getNumResources(VirtualPool vpool, DbClient dbClient) {
        return dbClient.countObjects(Bucket.class, "virtualPool", vpool.getId());
    }
}
