/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualPoolMapper.toFileVirtualPool;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.VirtualPoolMapper;
import com.emc.storageos.api.mapper.functions.MapFileVirtualPool;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationRPOType;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
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
import com.emc.storageos.model.vpool.FileVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionParam;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitUnManagedObjectsMatcher;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Function;

@Path("/file/vpools")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, readAcls = { ACL.USE }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class FileVirtualPoolService extends VirtualPoolService {

    private static final Logger _log = LoggerFactory.getLogger(FileVirtualPoolService.class);
    private static final Long MINUTES_PER_HOUR = 60L;
    private static final Long HOURS_PER_DAY = 24L;

    /**
     * Create File Store VirtualPool
     * 
     * @param param VirtualPool parameters
     * @brief Create VirtualPool for a file store
     * @return VirtualPool details
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public FileVirtualPoolRestRep createFileVirtualPool(FileVirtualPoolParam param) {

        ArgValidator.checkFieldNotEmpty(param.getName(), VPOOL_NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), VPOOL_DESCRIPTION);
        VirtualPoolUtil.validateFileVirtualPoolCreateParams(param, _dbClient);

        VirtualPool cos = prepareVirtualPool(param, true);
        if (null != param.getLongTermRetention()) {
            cos.setLongTermRetention(param.getLongTermRetention());
        }

        StringBuffer errorMessage = new StringBuffer();
        // update the implicit pools matching with this VirtualPool.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator, errorMessage);

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
     * 
     * @brief List classes of service for a file store
     * @return Returns the VirtualPool user is authorized to see
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualPoolList listFileVirtualPool(
            @DefaultValue("") @QueryParam(TENANT_ID_QUERY_PARAM) String tenantId,
            @DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        return getVirtualPoolList(VirtualPool.Type.file, shortVdcId, tenantId);
    }

    /**
     * Get info for File Store VirtualPool
     * 
     * @param id the URN of a ViPR VirtualPool
     * @brief Show file store VirtualPool
     * @return VirtualPool details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
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
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteFileVirtualPool(@PathParam("id") URI id) {
        return deleteVirtualPool(VirtualPool.Type.file, id);
    }

    /**
     * Return the matching pools for a given set of VirtualPool attributes.
     * This API is useful for user to find the matching pools before creating a VirtualPool.
     * 
     * @param param : VirtualPoolAttributeParam
     * @brief List pools matching specified properties in file store VirtualPool
     * @return : matching pools.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/matching-pools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolList getMatchingPoolsForVirtualPoolAttributes(FileVirtualPoolParam param) {
        StoragePoolList poolList = new StoragePoolList();
        VirtualPool vpool = prepareVirtualPool(param, false);
        List<URI> poolURIs = _dbClient.queryByType(StoragePool.class, true);
        List<StoragePool> allPools = _dbClient.queryObject(StoragePool.class, poolURIs);
        StringBuffer errorMessage = new StringBuffer();
        List<StoragePool> matchedPools = ImplicitPoolMatcher.getMatchedPoolWithStoragePools(vpool,
                allPools,
                null,
                null,
                null,
                _dbClient,
                _coordinator, AttributeMatcher.VPOOL_MATCHERS, errorMessage);
        for (StoragePool pool : matchedPools) {
            poolList.getPools().add(toNamedRelatedResource(pool, pool.getNativeGuid()));
        }
        return poolList;
    }

    /**
     * Get File Store VirtualPool ACL
     * 
     * @param id the URN of a ViPR VirtualPool
     * @brief Show ACL entries for file store VirtualPool
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnVirtualPool(VirtualPool.Type.file, id);
    }

    /**
     * Add or remove individual File Store VirtualPool ACL entry(s). Request body must include at least one add or remove operation.
     * 
     * @param id the URN of a ViPR VirtualPool
     * @param changes ACL assignment changes
     * @brief Add or remove ACL entries from file store VirtualPool
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
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
     * @brief Refresh list of storage pools in file store VirtualPool
     * @return : List of Pool Ids matching with this VirtualPool.
     */
    @GET
    @Path("/{id}/refresh-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolList refreshMatchedStoragePools(@PathParam("id") URI id) {
        return refreshMatchedPools(VirtualPool.Type.file, id);
    }

    /**
     * Update File VirtualPool only allows if there are no resources associated and
     * list of attributes changed not changed.
     * 
     * List of attributes can updated if it satisfies above constraint:
     * assignedStoragePools & useMatchedStoragePools flag.
     * 
     * @param param
     *            VirtualPool parameters
     * @brief Update description of file store VirtualPool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
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

        if (param.getProtection() != null && checkProtectionChanged(cos, param.getProtection())) {
            // need to iterate over all the policy and see if policy is associated with vpool .
            // if policy is associated with vpool we can not modify the protection attribute
            List<URI> filePolicyList = _dbClient.queryByType(FilePolicy.class, true);
            for (URI filePolicy : filePolicyList) {
                FilePolicy policyObj = _dbClient.queryObject(FilePolicy.class, filePolicy);
                if (policyObj.getAssignedResources() != null) {
                    if(policyObj.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.project.name()) && (policyObj.getFilePolicyVpool() != null)){
                        if (policyObj.getFilePolicyVpool().toString().equalsIgnoreCase(id.toString())) {
                            checkProtectAttributeAginstPolicy(param.getProtection(), policyObj);
                        }

                    } else if (policyObj.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.vpool.name())) {
                        StringSet assignedResources = policyObj.getAssignedResources();
                        if (assignedResources.contains(id.toString())) {
                        checkProtectAttributeAginstPolicy(param.getProtection(), policyObj);
                        }
                        
                    }
                   
                }
            }
        }
     
        // set common update VirtualPool Params here.
        populateCommonVirtualPoolUpdateParams(cos, param);

        if (null != param.getSystemType()) {
            if (cos.getArrayInfo() != null && cos.getArrayInfo().containsKey(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                for (String systemType : cos.getArrayInfo().get(
                        VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                    cos.getArrayInfo().remove(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE,
                            systemType);
                }
            }

            if (!(VirtualPool.SystemType.NONE.name().equalsIgnoreCase(param.getSystemType())
                    || VirtualPool.SystemType.isFileTypeSystem(param.getSystemType()))) {
                throw APIException.badRequests.invalidSystemType("File");
            }
            if (cos.getArrayInfo() == null) {
                cos.setArrayInfo(new StringSetMap());
            }
            cos.getArrayInfo().put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
        }

        // Update file protection parameters!!!
        if (null != param.getProtection()) {
            updateFileProtectionParamsForVirtualPool(cos, param.getProtection());
        }

        if (null != param.getLongTermRetention()) {
            cos.setLongTermRetention(param.getLongTermRetention());
        }
        StringBuffer errorMessage = new StringBuffer();
        // invokes implicit pool matching algorithm.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator, errorMessage);
        // adding supported coses to unmanaged volumes

        if (null != cos.getMatchedStoragePools() || null != cos.getInvalidMatchedPools()) {
            ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedFileSystems(cos,
                    _dbClient);
        }

        _dbClient.updateObject(cos);

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
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/assign-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public FileVirtualPoolRestRep updateFileVirtualPoolWithAssignedPools(@PathParam("id") URI id,
            VirtualPoolPoolUpdateParam param) {
        VirtualPool vPool = updateVirtualPoolWithAssignedStoragePools(id, param);
        return toFileVirtualPool(vPool);
    }

    /**
     * Gets storage capacity information for specified VirtualPool and Neighborhood instances.
     * 
     * The method returns set of metrics for capacity available for file storage provisioning:
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
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public FileVirtualPoolBulkRep getBulkResources(BulkIdParam param) {
        return (FileVirtualPoolBulkRep) super.getBulkResources(param);
    }

    /**
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
        return getQuota(getVirtualPool(Type.file, id));
    }

    /**
     * 
     * @param id the URN of a ViPR VirtualPool.
     * @param param new values for the quota
     * @brief Update quota and available capacity before quota is exhausted
     * @return QuotaInfo Quota metrics.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo updateQuota(@PathParam("id") URI id,
            QuotaUpdateParam param) throws DatabaseException {
        return updateQuota(getVirtualPool(Type.file, id), param);
    }

    private class mapFileVirtualPoolWithResources implements Function<VirtualPool, FileVirtualPoolRestRep> {
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
                // TODO: revisit this exception
                _log.error("error retrieving bulk virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote virtual pool", ex);
            }
        }
        return new FileVirtualPoolBulkRep(BulkList.wrapping(dbIterator, new mapFileVirtualPoolWithResources(),
                new BulkList.VirtualPoolFilter(Type.file)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected FileVirtualPoolBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        if (isSystemOrRestrictedSystemAdmin()) {
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
                // TODO: revisit this exception
                _log.error("error retrieving bulk virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote virtual pool", ex);
            }
        }
        BulkList.ResourceFilter filter = new BulkList.VirtualPoolFilter(Type.file, getUserFromContext(), _permissionsHelper);
        return new FileVirtualPoolBulkRep(BulkList.wrapping(dbIterator, MapFileVirtualPool.getInstance(), filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.FILE_VPOOL;
    }

    static Integer getNumResources(VirtualPool vpool, DbClient dbClient) {
        return dbClient.countObjects(FileShare.class, "virtualPool", vpool.getId());
    }

    // this method must not persist anything to the DB.
    private VirtualPool prepareVirtualPool(FileVirtualPoolParam param, boolean validateReplArgs) {

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
            FileVirtualPoolProtectionParam protectionParam = param.getProtection();
            if ((protectionParam.getSnapshots() != null)
                    && (protectionParam.getSnapshots().getMaxSnapshots() != null)) {
                vPool.setMaxNativeSnapshots(protectionParam.getSnapshots().getMaxSnapshots());

            }
            vPool.setScheduleSnapshots(false);
            if (protectionParam.getScheduleSnapshots() != null) {
                vPool.setScheduleSnapshots(protectionParam.getScheduleSnapshots());
            }

            vPool.setFileReplicationSupported(false);
            if (protectionParam.getReplicationSupported() != null) {
                vPool.setFileReplicationSupported(protectionParam.getReplicationSupported());
            }

            vPool.setAllowFilePolicyAtProjectLevel(false);
            if (protectionParam.getAllowFilePolicyAtProjectLevel() != null) {
                vPool.setAllowFilePolicyAtProjectLevel(protectionParam.getAllowFilePolicyAtProjectLevel());
            }

            vPool.setAllowFilePolicyAtFSLevel(false);
            if (protectionParam.getAllowFilePolicyAtFSLevel() != null) {
                vPool.setAllowFilePolicyAtFSLevel(protectionParam.getAllowFilePolicyAtFSLevel());
            }
        }

        if (null != param.getLongTermRetention()) {
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
                || checkLongTermRetentionChanged(param.getLongTermRetention(), vpool.getLongTermRetention()
                        || checkProtectionChanged(vpool, param.getProtection()));

    }

    /**
     * If vpool is associated with policy then the respective vpool attribute can not be modified.
     * 
     * @param protectParam
     * @param policyObj
     */
    private void checkProtectAttributeAginstPolicy(FileVirtualPoolProtectionUpdateParam protectParam, FilePolicy policyObj) {
        // now check the policy attribute against modified vpool attribute.
        if (!protectParam.getReplicationSupported()
                && policyObj.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())) {

            throw APIException.badRequests.vPoolUpdateNotAllowed("FileReplication");

        } else if (!protectParam.getScheduleSnapshots()
                && policyObj.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_snapshot.name())) {

            throw APIException.badRequests.vPoolUpdateNotAllowed("ScheduleSnapshot");
        } else if (!protectParam.getAllowFilePolicyAtProjectLevel()
                && policyObj.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.project.name())) {
            throw APIException.badRequests.vPoolUpdateNotAllowed("FilePolicyAtProjectLevel");

        } else if (!protectParam.getAllowFilePolicyAtFSLevel()
                && policyObj.getApplyAt().equalsIgnoreCase(FilePolicyApplyLevel.file_system.name())) {
            throw APIException.badRequests.vPoolUpdateNotAllowed("FilePolicyAtFSLevel");
        }

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

    /**
     * Check whether the file replication attributes have changed.
     * 
     * @param from the source virtual pool without updates
     * @param to the updated virtual pool
     * @return true if the virtual pool has changed, false otherwise
     */
    public static boolean checkProtectionChanged(VirtualPool from, FileVirtualPoolProtectionUpdateParam to) {

        // If the update object is null there are no updates
        if (to == null) {
            _log.info("No virtual pool replication settings changes have been made");
            return false;
        }

        // Check the protection parameters changed!!!
        if (to.getScheduleSnapshots() != from.getScheduleSnapshots()
                || to.getReplicationSupported() != from.getFileReplicationSupported()
                || to.getAllowFilePolicyAtProjectLevel() != from.getAllowFilePolicyAtProjectLevel()
                || to.getAllowFilePolicyAtFSLevel() != from.getAllowFilePolicyAtFSLevel()) {

            _log.info("Protection parameters cannot be modified to a vpool with provisioned filessystems ",
                    from.getId());
            return true;
        }

        // Check the RPO/Type changed!!!
        if (to.getMinRpoType() != from.getFrRpoType() || to.getMinRpoValue() != from.getFrRpoValue()) {
            _log.info("RPO parameters cannot be modified to a vpool with provisioned filessystems ",
                    from.getId());
            return true;
        }

        _log.info("No protection changes");
        return false;
    }

    /**
     * Performs the protection updates on VirtualPool.
     * 
     * @param virtualPool Reference to the virtual pool to update.
     * @param param The updates that need to be applied to the virtual pool.
     */
    private void updateFileProtectionParamsForVirtualPool(VirtualPool virtualPool,
            FileVirtualPoolProtectionUpdateParam param) {

        // If the update specifies replication protection, we need to process the update.
        if (param != null) {
            if (param.getScheduleSnapshots() != null) {
                virtualPool.setScheduleSnapshots(param.getScheduleSnapshots());
            }
            // Handle the protection snapshot updates
            if (param.getSnapshots() != null) {
                // By default the maxSnapshots value should be 0 so this should never be null
                // but good to have just in case...
                if (param.getSnapshots().getMaxSnapshots() != null) {
                    // Keep in mind that if an empty or 0 value is specified snapshots
                    // will be removed from the virtual pool.
                    virtualPool.setMaxNativeSnapshots(param.getSnapshots().getMaxSnapshots());
                } else {
                    // Remove snapshots by setting the disabled value
                    virtualPool.setMaxNativeSnapshots(VirtualPool.MAX_DISABLED);
                }
            }

            if (param.getReplicationSupported() != null) {
                virtualPool.setFileReplicationSupported(param.getReplicationSupported());
            }

            if (param.getAllowFilePolicyAtProjectLevel() != null) {
                virtualPool.setAllowFilePolicyAtProjectLevel(param.getAllowFilePolicyAtProjectLevel());
            }

            if (param.getAllowFilePolicyAtFSLevel() != null) {
                virtualPool.setAllowFilePolicyAtFSLevel(param.getAllowFilePolicyAtFSLevel());
            }
        }
    }

    private void deleteRemoteCopies(VirtualPool virtualPool,
            FileVirtualPoolProtectionUpdateParam param) {
        // Remove all remote copy setttings, if any!!!
        StringMap remoteCopySettingsMap = virtualPool.getFileRemoteCopySettings();
        if (remoteCopySettingsMap != null && !remoteCopySettingsMap.isEmpty()) {
            for (String varray : remoteCopySettingsMap.keySet()) {
                String remoteCopySettingsUri = remoteCopySettingsMap.get(varray);
                remoteCopySettingsMap.remove(varray);
                VpoolRemoteCopyProtectionSettings remoteSettingsObj = _dbClient.queryObject(
                        VpoolRemoteCopyProtectionSettings.class, URI.create(remoteCopySettingsUri));
                remoteSettingsObj.setInactive(true);
                _dbClient.updateObject(remoteSettingsObj);
            }
        }
    }

    private void deleteReplicationParams(VirtualPool virtualPool,
            FileVirtualPoolProtectionUpdateParam param) {
        // Remove replication settings from virtual pool
        // 1. Reset the policy settings
        // 2. Reset the remote copies!!!
        virtualPool.setFrRpoType(null);
        virtualPool.setFrRpoValue(null);
        virtualPool.setFileReplicationType(FileReplicationType.NONE.name());
        // Clear the remote copies!!
        deleteRemoteCopies(virtualPool, param);

        _log.info("File Replication setting removed from virtual pool {} ", virtualPool.getLabel());
    }

    private boolean validateReplicationRpoParams(Long rpoValue, String rpoType) {

        if (rpoValue != null || rpoType != null) {
            if (rpoType == null
                    || FileReplicationRPOType.lookup(rpoType) == null) {
                throw APIException.badRequests.invalidReplicationRPOType(rpoType);
            }

            if (rpoValue == null || rpoValue <= 0) {
                throw APIException.badRequests.invalidReplicationRPOValue();
            }

            switch (rpoType.toUpperCase()) {
                case "MINUTES":
                    if (rpoValue > MINUTES_PER_HOUR) {
                        throw APIException.badRequests.invalidReplicationRPOValueForType(
                                rpoValue.toString(), rpoType);
                    }
                    break;
                case "HOURS":
                    if (rpoValue > HOURS_PER_DAY) {
                        throw APIException.badRequests.invalidReplicationRPOValueForType(
                                rpoValue.toString(), rpoType);
                    }
                    break;
                case "DAYS":
                    // No validation required for Days.
                    break;
                default:
                    throw APIException.badRequests.invalidReplicationRPOType(rpoType);
            }
            return true;
        }
        return false;
    }
}
