/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualPoolMapper.toFileVirtualPool;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationRPOType;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings.CopyModes;
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
import com.emc.storageos.model.vpool.FileReplicationPolicy;
import com.emc.storageos.model.vpool.FileVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.FileVirtualPoolParam;
import com.emc.storageos.model.vpool.FileVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.FileVirtualPoolRestRep;
import com.emc.storageos.model.vpool.FileVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
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
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class FileVirtualPoolService extends VirtualPoolService {

    private static final Logger _log = LoggerFactory.getLogger(FileVirtualPoolService.class);

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
        Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap =
                new HashMap<URI, VpoolRemoteCopyProtectionSettings>();

        VirtualPool cos = prepareVirtualPool(param, remoteSettingsMap);
        if (null != param.getLongTermRetention()) {
            cos.setLongTermRetention(param.getLongTermRetention());
        }

        if (!remoteSettingsMap.isEmpty()) {
            _log.info("Adding file remote replicaition copies to DB ");
            _dbClient.createObject(new ArrayList(remoteSettingsMap.values()));
        }

        // update the implicit pools matching with this VirtualPool.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(cos, _dbClient, _coordinator);

        if (null != cos.getMatchedStoragePools() || null != cos.getInvalidMatchedPools()) {
            ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedFileSystems(cos,
                    _dbClient);
        }
        _dbClient.createObject(cos);

        recordOperation(OperationTypeEnum.CREATE_VPOOL, VPOOL_CREATED_DESCRIPTION, cos);

        return toFileVirtualPool(cos, VirtualPool.getFileRemoteProtectionSettings(cos, _dbClient));
    }

    /**
     * List VirtualPool for File Store
     * 
     * @brief List classes of service for a file store
     * @return Returns the VirtualPool user is authorized to see
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualPoolList listFileVirtualPool(@DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        return getVirtualPoolList(VirtualPool.Type.file, shortVdcId);
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
        FileVirtualPoolRestRep restRep = toFileVirtualPool(vpool,
                VirtualPool.getFileRemoteProtectionSettings(vpool, _dbClient));
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
        Map<URI, VpoolRemoteCopyProtectionSettings> fileReplRemoteSettingsMap =
                new HashMap<URI, VpoolRemoteCopyProtectionSettings>();
        VirtualPool vpool = prepareVirtualPool(param, fileReplRemoteSettingsMap);
        List<URI> poolURIs = _dbClient.queryByType(StoragePool.class, true);
        List<StoragePool> allPools = _dbClient.queryObject(StoragePool.class, poolURIs);

        List<StoragePool> matchedPools = ImplicitPoolMatcher.getMatchedPoolWithStoragePools(vpool,
                allPools,
                null,
                null,
                fileReplRemoteSettingsMap,
                _dbClient,
                _coordinator, AttributeMatcher.VPOOL_MATCHERS);
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

            if (!(VirtualPool.SystemType.NONE.name().equalsIgnoreCase(param.getSystemType())
            || VirtualPool.SystemType.isFileTypeSystem(param.getSystemType()))) {
                throw APIException.badRequests.invalidSystemType("File");
            }
            cos.getArrayInfo()
                    .put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
        }

        // Update file protection parameters!!!
        if (null != param.getProtection()) {
            updateFileProtectionParamsForVirtualPool(cos, param.getProtection());
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
        return toFileVirtualPool(cos, VirtualPool.getFileRemoteProtectionSettings(cos, _dbClient));
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
        return toFileVirtualPool(vPool, VirtualPool.getFileRemoteProtectionSettings(vPool, _dbClient));
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
        return updateQuota(getVirtualPool(Type.file, id), param);
    }

    private class mapFileVirtualPoolWithResources implements Function<VirtualPool, FileVirtualPoolRestRep> {
        @Override
        public FileVirtualPoolRestRep apply(VirtualPool vpool) {
            FileVirtualPoolRestRep to = VirtualPoolMapper.toFileVirtualPool(vpool,
                    VirtualPool.getFileRemoteProtectionSettings(vpool, _dbClient));
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
    private VirtualPool prepareVirtualPool(FileVirtualPoolParam param,
            Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap) {

        if (remoteSettingsMap == null) {
            remoteSettingsMap = new HashMap<URI, VpoolRemoteCopyProtectionSettings>();
        }

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

                if (param.getProtection().getScheduleSnapshots() != null) {
                    vPool.setScheduleSnapshots(param.getProtection().getScheduleSnapshots());
                } else {
                    vPool.setScheduleSnapshots(false);
                }
            }
            if (param.getProtection().getReplicationParam() != null) {
                String copyMode = CopyModes.ASYNCHRONOUS.name();
                Set<VirtualPoolRemoteProtectionVirtualArraySettingsParam> copies =
                        param.getProtection().getReplicationParam().getCopies();

                FileReplicationPolicy replPolicy =
                        param.getProtection().getReplicationParam().getSourcePolicy();
                if (replPolicy != null) {
                    if (null != replPolicy.getReplicationType()) {
                        if (!FileReplicationType.lookup(replPolicy.getReplicationType())) {
                            throw APIException.badRequests.invalidReplicationType(replPolicy.getReplicationType());
                        }
                        vPool.setFileReplicationType(replPolicy.getReplicationType().toUpperCase());
                        // Verify the remote copies given for remote replication!!!
                        if (FileReplicationType.REMOTE.name().equalsIgnoreCase(replPolicy.getReplicationType())) {
                            if (copies == null || copies.isEmpty()) {
                                throw APIException.badRequests.noReplicationRemoteCopies(replPolicy.getReplicationType());
                            }
                        }
                    } else {
                        throw APIException.badRequests.noReplicationTypesSpecified();
                    }
                    if (null != replPolicy.getCopyMode()) {
                        if (!CopyModes.lookup(replPolicy.getCopyMode())) {
                            throw APIException.badRequests.invalidCopyMode(replPolicy.getCopyMode());
                        }
                        copyMode = replPolicy.getCopyMode();
                        vPool.setFileReplicationCopyMode(copyMode.toUpperCase());
                    }
                    if (null != replPolicy.getRpoValue() && replPolicy.getRpoValue() > 0L) {
                        vPool.setFrRpoValue(replPolicy.getRpoValue());
                    } else {
                        throw APIException.badRequests.invalidReplicationRPOValue();
                    }

                    if (validateReplicationRpoParams(replPolicy)) {
                        vPool.setFrRpoType(replPolicy.getRpoType());
                        vPool.setFrRpoValue(replPolicy.getRpoValue());
                    }
                }

                if (FileReplicationType.REMOTE.name().equalsIgnoreCase(vPool.getFileReplicationType()) &&
                        copies != null && !copies.isEmpty()) {
                    if (copies.size() > 1) {
                        _log.error("Single remote copy supported, you have given {} copies ", copies.size());
                        throw APIException.badRequests.moreThanOneRemoteCopiesSpecified();
                    }
                    // Create a Map with remote copies
                    StringMap remoteCopiesMap = new StringMap();
                    for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteCopy : copies) {

                        VirtualArray remoteVArray = _dbClient.queryObject(VirtualArray.class, remoteCopy.getVarray());
                        if (null == remoteVArray || remoteVArray.getInactive()) {
                            throw APIException.badRequests.inactiveRemoteVArrayDetected(remoteCopy.getVarray());
                        }
                        VpoolRemoteCopyProtectionSettings remoteCopySettings = new VpoolRemoteCopyProtectionSettings();
                        remoteCopySettings.setId(URIUtil.createId(VpoolRemoteCopyProtectionSettings.class));
                        remoteCopySettings.setVirtualArray(remoteCopy.getVarray());
                        if (remoteCopiesMap.containsKey(remoteCopy.getVarray().toString())) {
                            throw APIException.badRequests.duplicateRemoteSettingsDetected(remoteCopy.getVarray());
                        }

                        remoteCopySettings.setCopyMode(copyMode);
                        VirtualPool remoteVPool = _dbClient.queryObject(VirtualPool.class, remoteCopy.getVpool());
                        if (null == remoteVPool || remoteVPool.getInactive()) {
                            throw APIException.badRequests.inactiveRemoteVPoolDetected(remoteCopy.getVpool());
                        }
                        if (remoteVPool.getVirtualArrays() != null
                                && !remoteVPool.getVirtualArrays().contains(remoteVArray.getId().toString())) {
                            throw APIException.badRequests.invalidVirtualPoolFromVirtualArray(
                                    remoteVPool.getId(), remoteVArray.getId());
                        }
                        remoteCopySettings.setVirtualPool(remoteCopy.getVpool());

                        remoteCopiesMap.put(remoteCopySettings.getVirtualArray().toString(),
                                remoteCopySettings.getId().toString());
                        remoteSettingsMap.put(remoteCopySettings.getVirtualArray(), remoteCopySettings);
                    }

                    vPool.setFileRemoteCopySettings(remoteCopiesMap);
                    vPool.setFileReplicationType(FileReplicationType.REMOTE.name());
                    _log.info("File Replication type {} and number of remote copies {}",
                            vPool.getFileReplicationType(), remoteCopiesMap.size());
                }

                // Verify remote copies are there for REMOTE replication!!!
                if (FileReplicationType.REMOTE.name().equalsIgnoreCase(vPool.getFileReplicationType()) &&
                        (vPool.getFileRemoteCopySettings() == null ||
                        vPool.getFileRemoteCopySettings().isEmpty())) {
                    _log.info("No remote copies in virtual pool for REMOTE replication ");
                    throw APIException.badRequests.noReplicationRemoteCopies(vPool.getFileReplicationType());
                }
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

        // Check for file replication protection
        if (to.getReplicationParam() != null) {
            if ((null != to.getReplicationParam().getAddRemoteCopies()
                    && !to.getReplicationParam().getAddRemoteCopies().isEmpty())
                    || (null != to.getReplicationParam().getRemoveRemoteCopies()
                    && !to.getReplicationParam().getRemoveRemoteCopies().isEmpty())) {
                // File replication protection Copies are being modified on a vpool.
                // irrespective of whether source vpool has replication protection,
                // any changes to remote copies is not permitted on
                // virtual pools with provisioned file systems.
                _log.info("Replication copies cannot be modified to a vpool with provisioned filessystems ",
                        from.getId());
                return true;
            }
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
            } else {

                virtualPool.setScheduleSnapshots(false);
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
            // Handle the replication params updates!!!
            if (param.getReplicationParam() != null) {
                if (param.getReplicationParam().getAddRemoteCopies() == null
                        && param.getReplicationParam().getRemoveRemoteCopies() == null
                        && param.getReplicationParam().getSourcePolicy() == null) {
                    // Empty replication settings indicates removal of
                    // replication protection, so remove it.
                    deleteReplicationParams(virtualPool, param);
                } else {
                    // Update the replication attributes!!
                    updateReplicationParams(virtualPool, param);
                }

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

    private boolean validateReplicationRpoParams(FileReplicationPolicy sourcePolicy) {
        if (sourcePolicy.getRpoType() == null
                || FileReplicationRPOType.lookup(sourcePolicy.getRpoType()) == null) {
            throw APIException.badRequests.invalidReplicationRPOType(sourcePolicy.getRpoType());
        }

        if (sourcePolicy.getRpoValue() == null || sourcePolicy.getRpoValue() <= 0) {
            throw APIException.badRequests.invalidReplicationRPOValue();
        }

        switch (sourcePolicy.getRpoType().toUpperCase()) {
            case "MINUTES":
                if (sourcePolicy.getRpoValue() > 60) {
                    throw APIException.badRequests.invalidReplicationRPOValueForType(
                            sourcePolicy.getRpoValue().toString(), sourcePolicy.getRpoType());
                }
                break;
            case "HOURS":
                if (sourcePolicy.getRpoValue() > 24) {
                    throw APIException.badRequests.invalidReplicationRPOValueForType(
                            sourcePolicy.getRpoValue().toString(), sourcePolicy.getRpoType());
                }
                break;
        }
        return true;
    }

    private void updateReplicationParams(VirtualPool virtualPool,
            FileVirtualPoolProtectionUpdateParam param) {
        if (param.getReplicationParam() != null) {
            // If the source policy is omitted, do nothing.
            FileReplicationPolicy sourcePolicy = param.getReplicationParam().getSourcePolicy();
            if (sourcePolicy != null) {
                if (sourcePolicy.getReplicationType() != null &&
                        sourcePolicy.getReplicationType().equalsIgnoreCase(FileReplicationType.NONE.name())) {
                    // Delete the existing replication attributes!!
                    deleteReplicationParams(virtualPool, param);

                } else {
                    // Update the policy settings from param!!!
                    if (sourcePolicy.getCopyMode() != null) {
                        if (!CopyModes.lookup(sourcePolicy.getCopyMode())) {
                            throw APIException.badRequests.invalidCopyMode(sourcePolicy.getCopyMode());
                        }
                        virtualPool.setFileReplicationCopyMode(sourcePolicy.getCopyMode().toUpperCase());
                    }
                    if (sourcePolicy.getReplicationType() != null) {
                        if (!FileReplicationType.lookup(sourcePolicy.getReplicationType())) {
                            throw APIException.badRequests.invalidReplicationType(sourcePolicy.getCopyMode());
                        }
                        virtualPool.setFileReplicationType(sourcePolicy.getReplicationType().toUpperCase());
                        if (FileReplicationType.LOCAL.name().equalsIgnoreCase(virtualPool.getFileReplicationType())) {
                            // Clear the remote copies!!
                            deleteRemoteCopies(virtualPool, param);
                        }
                    }
                    if (validateReplicationRpoParams(sourcePolicy)) {
                        virtualPool.setFrRpoType(sourcePolicy.getRpoType());
                        virtualPool.setFrRpoValue(sourcePolicy.getRpoValue());
                    }
                }
            }
            List<VpoolRemoteCopyProtectionSettings> addRemoteSettingsList = new ArrayList<VpoolRemoteCopyProtectionSettings>();
            List<VpoolRemoteCopyProtectionSettings> removeRemoteSettingsList = new ArrayList<VpoolRemoteCopyProtectionSettings>();

            // Update the remote copies!!!
            if (FileReplicationType.REMOTE.name().equalsIgnoreCase(virtualPool.getFileReplicationType()) &&
                    (param.getReplicationParam().getRemoveRemoteCopies() != null ||
                    param.getReplicationParam().getAddRemoteCopies() != null)) {

                StringMap remoteCopySettingsMap = virtualPool.getFileRemoteCopySettings();
                if (remoteCopySettingsMap == null) {
                    remoteCopySettingsMap = new StringMap();
                    virtualPool.setFileRemoteCopySettings(remoteCopySettingsMap);
                }

                // Remove remote settings!!!
                if (param.getReplicationParam().getRemoveRemoteCopies() != null
                        && !param.getReplicationParam().getRemoveRemoteCopies().isEmpty()) {
                    for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : param.getReplicationParam()
                            .getRemoveRemoteCopies()) {
                        URI remoteVarray = remoteSettings.getVarray();
                        if (URIUtil.isValid(remoteVarray)) {
                            if (remoteCopySettingsMap.containsKey(remoteVarray.toString())) {
                                String remoteCopySettingsUri = remoteCopySettingsMap.get(remoteSettings.getVarray().toString());
                                remoteCopySettingsMap.remove(remoteSettings.getVarray().toString());
                                VpoolRemoteCopyProtectionSettings remoteSettingsObj = _dbClient.queryObject(
                                        VpoolRemoteCopyProtectionSettings.class, URI.create(remoteCopySettingsUri));
                                remoteSettingsObj.setInactive(true);
                                removeRemoteSettingsList.add(remoteSettingsObj);
                            } else {
                                _log.error("Remote copy {} trying to remove does not exists in vpool {} ", remoteVarray,
                                        virtualPool.getId());
                                throw APIException.badRequests.remoteCopyDoesNotExists(remoteVarray, remoteSettings.getVpool());
                            }
                        }
                    }
                }
                // Add remote copy settings!!!
                if (param.getReplicationParam().getAddRemoteCopies() != null && !param.getReplicationParam().getAddRemoteCopies().isEmpty()) {
                    // already existing remote VArrays
                    List<String> existingRemoteUris = new ArrayList<String>(remoteCopySettingsMap.keySet());
                    for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : param.getReplicationParam()
                            .getAddRemoteCopies()) {
                        VirtualArray remoteVArray = _dbClient.queryObject(VirtualArray.class, remoteSettings.getVarray());
                        if (null == remoteVArray || remoteVArray.getInactive()) {
                            throw APIException.badRequests.inactiveRemoteVArrayDetected(remoteSettings.getVarray());
                        }
                        VirtualPool remoteVPool = _dbClient.queryObject(VirtualPool.class, remoteSettings.getVpool());
                        if (null == remoteVPool || remoteVPool.getInactive()) {
                            throw APIException.badRequests.inactiveRemoteVPoolDetected(remoteSettings.getVpool());
                        }
                        if (remoteVPool.getVirtualArrays() != null
                                && !remoteVPool.getVirtualArrays().contains(remoteVArray.getId().toString())) {
                            throw APIException.badRequests.invalidVirtualPoolFromVirtualArray(
                                    remoteVPool.getId(), remoteVArray.getId());
                        }
                        VpoolRemoteCopyProtectionSettings remoteCopySettingsParam = new VpoolRemoteCopyProtectionSettings();
                        addRemoteSettingsList.add(remoteCopySettingsParam);
                        remoteCopySettingsParam.setId(URIUtil.createId(VpoolRemoteCopyProtectionSettings.class));
                        remoteCopySettingsParam.setVirtualArray(remoteSettings.getVarray());
                        remoteCopySettingsParam.setVirtualPool(remoteSettings.getVpool());
                        if (existingRemoteUris.contains(remoteSettings.getVarray().toString()) ||
                                remoteCopySettingsMap.containsKey(remoteSettings.getVarray().toString())) {
                            throw APIException.badRequests.duplicateRemoteSettingsDetected(remoteSettings.getVarray());
                        }
                        if (null != remoteSettings.getRemoteCopyMode()) {
                            if (!CopyModes.lookup(remoteSettings.getRemoteCopyMode())) {
                                throw APIException.badRequests.invalidCopyMode(remoteSettings.getRemoteCopyMode());
                            }
                            remoteCopySettingsParam.setCopyMode(remoteSettings.getRemoteCopyMode());
                        }
                        remoteCopySettingsMap.put(remoteSettings.getVarray().toString(), remoteCopySettingsParam.getId().toString());
                    }
                }
            }
            // Verify remote copies are there for REMOTE replication!!!
            if (FileReplicationType.REMOTE.name().equalsIgnoreCase(virtualPool.getFileReplicationType())) {
                if (virtualPool.getFileRemoteCopySettings() == null ||
                        virtualPool.getFileRemoteCopySettings().isEmpty()) {
                    _log.info("No remote copies in virtual pool for REMOTE replication ");
                    throw APIException.badRequests.noReplicationRemoteCopies(virtualPool.getFileReplicationType());
                } else if (virtualPool.getFileRemoteCopySettings().size() > 1) {
                    _log.error("Single remote copy supported, you have given {} copies ",
                            virtualPool.getFileRemoteCopySettings().size());
                    throw APIException.badRequests.moreThanOneRemoteCopiesSpecified();
                }
            }

            // Update the Remote setting objects to DB!!!
            if (addRemoteSettingsList != null && !addRemoteSettingsList.isEmpty()) {
                _dbClient.createObject(addRemoteSettingsList);
            }
            if (removeRemoteSettingsList != null && !removeRemoteSettingsList.isEmpty()) {
                _dbClient.updateObject(removeRemoteSettingsList);
            }
        }
        _log.info("File replication settings are updated to virtual pool {} ", virtualPool.getLabel());
    }
}
