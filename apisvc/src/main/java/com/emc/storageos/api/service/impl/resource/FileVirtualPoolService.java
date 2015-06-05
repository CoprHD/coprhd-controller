/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualPoolMapper.toFileVirtualPool;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.emc.storageos.model.BulkIdParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.emc.storageos.api.mapper.functions.MapFileVirtualPool;
import com.emc.storageos.api.mapper.VirtualPoolMapper;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.*;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitUnManagedObjectsMatcher;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

@Path("/file/vpools")
@DefaultPermissions( read_roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR},
        read_acls = {ACL.USE},
        write_roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
public class FileVirtualPoolService extends VirtualPoolService {

    private static final Logger _log = LoggerFactory.getLogger(FileVirtualPoolService.class);

    /**     
     * Create File Store VirtualPool
     * @param param VirtualPool parameters
     * @brief Create VirtualPool for a file store
     * @return VirtualPool details
     */
    @POST
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public FileVirtualPoolRestRep createFileVirtualPool(FileVirtualPoolParam param) {

        ArgValidator.checkFieldNotEmpty(param.getName(), VPOOL_NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), VPOOL_DESCRIPTION);
        VirtualPoolUtil.validateFileVirtualPoolCreateParams(param, _dbClient);
        VirtualPool cos = prepareVirtualPool(param);
        if (null != param.getLongTermRetention()) {
            cos.setLongTermRetention(param.getLongTermRetention());
        }

            // update the implicit pools matching with this VirtualPool.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator);
        
        if (null != cos.getMatchedStoragePools() || null != cos.getInvalidMatchedPools()) {
            ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedFileSystems(cos,
                    _dbClient);
        }
        _dbClient.createObject(cos);

        recordOperation(OperationTypeEnum.CREATE_VPOOL, VPOOL_CREATED_DESCRIPTION, cos);

        return toFileVirtualPool(cos);
    }

    /**     
     * List VirtualPool for File Store
     * @brief List classes of service for a file store
     * @return Returns the VirtualPool user is authorized to see
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public VirtualPoolList listFileVirtualPool(@DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);             
        return getVirtualPoolList(VirtualPool.Type.file, shortVdcId);
    }

    /**     
     * Get info for File Store VirtualPool
     * @param id the URN of a ViPR VirtualPool
     * @brief Show file store VirtualPool
     * @return VirtualPool details
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{id}")
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR}, acls = {ACL.USE})
    public FileVirtualPoolRestRep getFileVirtualPool(@PathParam("id") URI id) {
        VirtualPool vpool = getVirtualPool(VirtualPool.Type.file, id);
        FileVirtualPoolRestRep restRep = toFileVirtualPool(vpool);
        restRep.setNumResources(getNumResources(vpool, _dbClient));
        return restRep;
    }

    /**     
     * Deactivate File Store VirtualPool, this will move the Cos to a "marked-for-deletion" state,
     * and no more resource may be created using it.
     * The VirtualPool will be deleted when all references to this VirtualPool of type FileShare are deleted
     *
     * @param id the URN of a ViPR VirtualPool
     * @brief Delete file store VirtualPool
     * @return VirtualPool details
     */
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{id}/deactivate")
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public Response deleteFileVirtualPool(@PathParam("id") URI id) {
        return deleteVirtualPool(VirtualPool.Type.file, id);
    }

    /**     
     * Return the matching pools for a given set of VirtualPool attributes.
     * This API is useful for user to find the matching pools before creating a VirtualPool.
     * @param param : VirtualPoolAttributeParam
     * @brief List pools matching specified properties in file store VirtualPool
     * @return : matching pools.
     */
    @POST
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/matching-pools")
    @CheckPermission( roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public StoragePoolList getMatchingPoolsForVirtualPoolAttributes(FileVirtualPoolParam param) {
        StoragePoolList poolList = new StoragePoolList();
        VirtualPool vpool = prepareVirtualPool(param);
        List<URI> poolURIs = _dbClient.queryByType(StoragePool.class, true);
        List<StoragePool> allPools = _dbClient.queryObject(StoragePool.class, poolURIs);

        List<StoragePool> matchedPools = ImplicitPoolMatcher.getMatchedPoolWithStoragePools(vpool,
                                                                                            allPools,
                                                                                            null,
                                                                                            null,
                                                                                            _dbClient,
                                                                                            _coordinator);
        for (StoragePool pool : matchedPools) {
            poolList.getPools().add(toNamedRelatedResource(pool, pool.getNativeGuid()));
        }
        return poolList;
    }

    /**     
     * Get File Store VirtualPool ACL
     * @param id the URN of a ViPR VirtualPool
     * @brief Show ACL entries for file store VirtualPool
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR})
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnVirtualPool(VirtualPool.Type.file, id);
    }

    /**     
     * Add or remove individual File Store VirtualPool ACL entry(s). Request body must include at least one add or remove operation.
     * @param id the URN of a ViPR VirtualPool
     * @param changes ACL assignment changes
     * @brief Add or remove ACL entries from file store VirtualPool
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SECURITY_ADMIN} , block_proxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        return updateAclsOnVirtualPool(VirtualPool.Type.file, id, changes);
    }

    /**     
     * Returns list of computed id's for all storage pools matching with the VirtualPool.
     * This list of pools will be used to do create Fileshares.
     *
     * @param id the URN of a ViPR VirtualPool.
     *
     * @brief List storage pools in file store VirtualPool
     * @return The ids for all storage pools that satisfy the VirtualPool.
     */
    @GET
    @Path("/{id}/storage-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR})
    public StoragePoolList getStoragePools(@PathParam("id") URI id) {
        return getStoragePoolsForVirtualPool(id);
    }

    /**     
     * This method re-computes the matched pools for this VirtualPool and returns this information.
     *
     * Where as getStoragePools {id}/storage-pools returns whatever is already computed, for matched pools.
     *
     * @param id : the URN of a ViPR Block VirtualPool.
     * @brief Refresh list of storage pools in file store VirtualPool
     * @return : List of Pool Ids matching with this VirtualPool.
     */
    @GET
    @Path("/{id}/refresh-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
	public StoragePoolList refreshMatchedStoragePools(@PathParam("id") URI id) {
        return refreshMatchedPools(VirtualPool.Type.file, id);
    }

    /**     
     * Update File VirtualPool only allows if there are no resources associated and
     * list of attributes changed not changed.
     *
     * List of attributes can updated if it satisfies above constraint:
     *     assignedStoragePools & useMatchedStoragePools flag.
     *
     * @param param
     *            VirtualPool parameters
     * @brief Update description of file store VirtualPool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public FileVirtualPoolRestRep updateFileVirtualPool(@PathParam("id") URI id, FileVirtualPoolUpdateParam param) {
        VirtualPool cos = null;
        ArgValidator.checkFieldUriType(id, VirtualPool.class, "id");
        cos = _dbClient.queryObject(VirtualPool.class, id);
        ArgValidator.checkEntity(cos, id, isIdEmbeddedInURL(id));
        if (!cos.getType().equals(VirtualPool.Type.file.name())) {
            throw APIException.badRequests.unexpectedValueForProperty("VPool type", VirtualPool.Type.file.name(), cos.getType());
        }
        VirtualPoolUtil.validateFileVirtualPoolUpdateParams(cos, param, _dbClient);

        URIQueryResultList resultList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getVirtualPoolFileshareConstraint(id), resultList);
        for (Iterator<URI> fileShareItr = resultList.iterator(); fileShareItr.hasNext();) {
            FileShare fileShare = _dbClient.queryObject(FileShare.class, fileShareItr.next());
            if (!fileShare.getInactive() && checkAttributeValuesChanged(param, cos)) {
            	throw APIException.badRequests.vPoolUpdateNotAllowed("FileShares");
            }
        }
        
        // set common update VirtualPool Params here.
        populateCommonVirtualPoolUpdateParams(cos, param);
        
        if (null != param.getSystemType()) {
            if (cos.getArrayInfo().containsKey(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            for (String systemType : cos.getArrayInfo().get(
                    VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                cos.getArrayInfo().remove(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE,
                        systemType);
                }
            }
            
            if(!(VirtualPool.SystemType.NONE.name().equalsIgnoreCase(param.getSystemType())
                        || VirtualPool.SystemType.isFileTypeSystem(param.getSystemType())))
               throw APIException.badRequests.invalidSystemType("File");                    
            cos.getArrayInfo()
                .put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
        }

        // Update snapshots
        if (null != param.getProtection()) {
            if ((param.getProtection().getSnapshots() != null)
                && (param.getProtection().getSnapshots().getMaxSnapshots() != null)) {
                cos.setMaxNativeSnapshots(param.getProtection().getSnapshots().getMaxSnapshots());
            }
        }

        if (null != param.getLongTermRetention()) {
            cos.setLongTermRetention(param.getLongTermRetention());
        }
        
        // invokes implicit pool matching algorithm.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator);
        // adding supported coses to unmanaged volumes
        
        if (null != cos.getMatchedStoragePools() || null != cos.getInvalidMatchedPools()) {
            ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedFileSystems(cos,
                    _dbClient);
        }
        
        _dbClient.updateAndReindexObject(cos);

        recordOperation(OperationTypeEnum.UPDATE_VPOOL, VPOOL_UPDATED_DESCRIPTION, cos);
        return toFileVirtualPool(cos);
    }

    /**     
     * Update File VirtualPool only allows user to assign matching storage pools.
     *
     * @param param
     *            VirtualPool parameters
     * @brief Update storage pools in file store VirtualPool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{id}/assign-matched-pools")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public FileVirtualPoolRestRep updateFileVirtualPoolWithAssignedPools(@PathParam("id") URI id,
            VirtualPoolPoolUpdateParam param) {
        return toFileVirtualPool(updateVirtualPoolWithAssignedStoragePools(id, param));
    }

    /**     
     * Gets storage capacity information for specified VirtualPool and Neighborhood instances.
     *
     * The method returns set of metrics for capacity available for file storage provisioning:
     * - usable_gb : total storage capacity
     * - free_gb : free storage capacity
     * - used_gb : used storage capacity
     * - percent_used  : percent of usable capacity which is used
     *
     * @param id   the URN of a ViPR VirtualPool.
     * @param varrayId   The id of varray.
     * @brief Show storage capacity for a VirtualPool and varray
     * @return Capacity metrics in GB and percent indicator for used capacity.
     */
    @GET
    @Path("/{id}/varrays/{varrayId}/capacity")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR}, acls = {ACL.USE})
    public CapacityResponse getCapacity(@PathParam("id") URI id,
            @PathParam("varrayId") URI varrayId) {
        return getCapacityForVirtualPoolAndVirtualArray(getVirtualPool(Type.file, id), varrayId);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected Type getVirtualPoolType() {
        return Type.file;
    }
    
    /**
     * @brief List all instances of File VirtualPools
     * 
     */

    @POST
    @Path("/bulk")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Override
    public FileVirtualPoolBulkRep getBulkResources(BulkIdParam param) {
        return (FileVirtualPoolBulkRep) super.getBulkResources(param);
    }

    /**     
     *
     * @param id   the URN of a ViPR VirtualPool.
     * @brief Show quota and available capacity before quota is exhausted
     * @return QuotaInfo Quota metrics.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = {Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo getQuota(@PathParam("id") URI id) throws DatabaseException {
        return  getQuota(getVirtualPool(Type.file, id));
    }

    /**     
     *
     * @param id   the URN of a ViPR VirtualPool.
     * @param param   new values for the quota
     * @brief Updates quota and available capacity before quota is exhausted
     * @return QuotaInfo Quota metrics.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    @Path("/{id}/quota")
    public QuotaInfo updateQuota(@PathParam("id") URI id,
                                 QuotaUpdateParam param)  throws DatabaseException {
        return updateQuota(getVirtualPool(Type.file, id),param);
    }

    private class mapFileVirtualPoolWithResources implements Function<VirtualPool,FileVirtualPoolRestRep>{
        @Override
        public FileVirtualPoolRestRep apply(VirtualPool vpool) {
            FileVirtualPoolRestRep to = VirtualPoolMapper.toFileVirtualPool(vpool);
            to.setNumResources(getNumResources(vpool, _dbClient));
            return to;
        }
    }

    
    @Override
    public FileVirtualPoolBulkRep queryBulkResourceReps(List<URI> ids) {
        
        if (!ids.iterator().hasNext()) {
            return new FileVirtualPoolBulkRep();
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
                //TODO: revisit this exception
                _log.error("error retrieving bulk virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote virtual pool", ex);
            }                
        }
        return new FileVirtualPoolBulkRep(BulkList.wrapping(dbIterator, new mapFileVirtualPoolWithResources(), new BulkList.VirtualPoolFilter(Type.file)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected FileVirtualPoolBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        if (isSystemAdmin()) {
            return queryBulkResourceReps(ids);
        }

        if (!ids.iterator().hasNext()) {
            return new FileVirtualPoolBulkRep();
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
                //TODO: revisit this exception
                _log.error("error retrieving bulk virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote virtual pool", ex);
            }                
        }
        BulkList.ResourceFilter filter = new BulkList.VirtualPoolFilter(Type.file, getUserFromContext(), _permissionsHelper);
        return new FileVirtualPoolBulkRep(BulkList.wrapping(dbIterator, MapFileVirtualPool.getInstance(), filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType(){
        return ResourceTypeEnum.FILE_VPOOL;
    }

    static Integer getNumResources(VirtualPool vpool, DbClient dbClient){
        return dbClient.countObjects(FileShare.class, "virtualPool", vpool.getId());
    }

    // this method must not persist anything to the DB.
    private VirtualPool prepareVirtualPool(FileVirtualPoolParam param) {
        VirtualPool vPool = new VirtualPool();
        vPool.setType(VirtualPool.Type.file.name());
        // set common VirtualPool parameters.
        populateCommonVirtualPoolCreateParams(vPool, param);
        StringSetMap arrayInfo = new StringSetMap();
        if (null != param.getSystemType()) {
            if (!VirtualPool.SystemType.NONE.toString().equals(param.getSystemType())
                    && !VirtualPool.SystemType.isFileTypeSystem(param.getSystemType())) {
                throw APIException.badRequests.invalidParameter("system_type", param.getSystemType());
            }
            arrayInfo.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
            vPool.addArrayInfoDetails(arrayInfo);
        }
        vPool.setMaxNativeSnapshots(VirtualPool.MAX_DISABLED);

        if (null != param.getProtection()) {
            if ((param.getProtection().getSnapshots() != null)
                    && (param.getProtection().getSnapshots().getMaxSnapshots() != null)) {
                vPool.setMaxNativeSnapshots(param.getProtection().getSnapshots().getMaxSnapshots());
            }
        }

        if(null != param.getLongTermRetention()){
            vPool.setLongTermRetention(param.getLongTermRetention());
        }
        return vPool;
    }
    
    /**
    *
    * Check if any VirtualPool attribute values (including long term retention) have changed.
    *
    * @param param
    * @param vpool : VirtualPool in DB.
    * @return : flag to check whether to update VirtualPool or not.
    */
   private boolean checkAttributeValuesChanged(FileVirtualPoolUpdateParam param, VirtualPool vpool) {
       return super.checkAttributeValuesChanged(param, vpool)
    		   || checkLongTermRetentionChanged(param.getLongTermRetention(), vpool.getLongTermRetention());
   }
   
   
   /**
    * check for any change in LongTermRetention
    * 
    * @param paramValue
    * @param vpoolValue
    */
   private boolean checkLongTermRetentionChanged(Boolean paramValue, Boolean vpoolValue) {
	   boolean isModified = false;
       if (null != vpoolValue) {
           if (paramValue == null) {
               isModified = false;
           } else if (paramValue != vpoolValue) {
               isModified = true;
           }
       } else {
           if (null != paramValue) {
               isModified = true;
           }
       }
       return isModified;
	   
   }

}
