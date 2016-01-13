/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualPoolMapper.toBlockVirtualPool;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

import com.emc.storageos.volumecontroller.AttributeMatcher;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings.CopyModes;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolBulkRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolProtectionUpdateParam;
import com.emc.storageos.model.vpool.BlockVirtualPoolRestRep;
import com.emc.storageos.model.vpool.BlockVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.model.vpool.ProtectionSourcePolicy;
import com.emc.storageos.model.vpool.VirtualPoolChangeList;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam;
import com.emc.storageos.model.vpool.VirtualPoolHighAvailabilityParam.VirtualArrayVirtualPoolMapEntry;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionMirrorParam;
import com.emc.storageos.model.vpool.VirtualPoolProtectionVirtualArraySettingsParam;
import com.emc.storageos.model.vpool.VirtualPoolRemoteProtectionVirtualArraySettingsParam;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitUnManagedObjectsMatcher;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.base.Function;

@Path("/block/vpools")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class BlockVirtualPoolService extends VirtualPoolService {

    private static final Logger _log = LoggerFactory.getLogger(BlockVirtualPoolService.class);
    private static final String NONE = "none";

    /**
     * Returns all potential virtual pools, which supported the given virtual pool change operation
     * for a virtual pool change of the volumes specified in the request
     *
     * @prereq none
     *
     * @param param
     *
     * @brief Show potential virtual pools
     * @return A VirtualPoolChangeList that identifies each potential virtual
     *         pool, whether or not a change is allowed for the virtual pool,
     *         and if not, the reason why.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/vpool-change/vpool")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.OWN, ACL.ALL })
    public VirtualPoolChangeList getVirtualPoolForVirtualPoolChange(@PathParam("id") URI id, BulkIdParam param) {

        VirtualPool vpool = getVirtualPool(VirtualPool.Type.block, id);
        ArgValidator.checkFieldNotEmpty(param.getIds(), "volume_id");

        List<Volume> volumes = _dbClient.queryObject(Volume.class, param.getIds());

        VirtualPoolChangeList virtualPoolChangeList = null;

        if (volumes != null && !volumes.isEmpty()) {
            _log.info("Found {} volumes", volumes.size());

            for (Volume volume : volumes) {
                // throw exception if one of the volume is not in the source virtual pool
                if (!volume.getVirtualPool().equals(id)) {
                    throw APIException.badRequests.volumeNotInVirtualPool(volume.getLabel(), vpool.getLabel());
                }

                // get potential virtual pool change list of each volume
                // Get the block service implementation for this volume.
                BlockServiceApi blockServiceApi = BlockService.getBlockServiceImpl(volume, _dbClient);
                _log.info("Got BlockServiceApi for volume");

                // Return the list of potential VirtualPool for a VirtualPool change for this volume.
                VirtualPoolChangeList volumeVirturalPoolChangeList = blockServiceApi.getVirtualPoolForVirtualPoolChange(volume);

                if (virtualPoolChangeList == null) {
                    // initialized intersected list of the very first volume to use it as based.
                    virtualPoolChangeList = new VirtualPoolChangeList();
                    virtualPoolChangeList.getVirtualPools().addAll(volumeVirturalPoolChangeList.getVirtualPools());
                } else {
                    virtualPoolChangeList.getVirtualPools().retainAll(volumeVirturalPoolChangeList.getVirtualPools());
                }
            }
        }

        return virtualPoolChangeList;
    }

    /**
     * Creates a block store virtual pool
     *
     * @prereq none
     * @param param VirtualPool parameters
     * @brief Create block store virtual pool
     * @return VirtualPool details
     * @throws Exception
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockVirtualPoolRestRep createBlockVirtualPool(BlockVirtualPoolParam param)
            throws DatabaseException {
        ArgValidator.checkFieldNotEmpty(param.getName(), VPOOL_NAME);
        checkForDuplicateName(param.getName(), VirtualPool.class);
        ArgValidator.checkFieldNotEmpty(param.getDescription(), VPOOL_DESCRIPTION);
        VirtualPoolUtil.validateBlockVirtualPoolCreateParams(param, _dbClient);

        Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap = new HashMap<URI, VpoolRemoteCopyProtectionSettings>();
        List<VpoolProtectionVarraySettings> protectionSettings = new ArrayList<VpoolProtectionVarraySettings>();
        Map<URI, VpoolProtectionVarraySettings> protectionSettingsMap = new HashMap<URI, VpoolProtectionVarraySettings>();
        VirtualPool vpool = prepareVirtualPool(param, remoteSettingsMap, protectionSettingsMap, protectionSettings);

        // Set the underlying protection setting objects
        if (!protectionSettings.isEmpty()) {
            _dbClient.createObject(protectionSettings);
        }
        if (!remoteSettingsMap.isEmpty()) {
            _dbClient.createObject(new ArrayList(remoteSettingsMap.values()));
        }

        // update the implicit pools matching with this VirtualPool.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(vpool, _dbClient, _coordinator);
        Set<URI> allSrdfTargetVPools = SRDFUtils.fetchSRDFTargetVirtualPools(_dbClient);
        Set<URI> allRpTargetVPools = RPHelper.fetchRPTargetVirtualPools(_dbClient);
        if (null != vpool.getMatchedStoragePools() || null != vpool.getInvalidMatchedPools()) {
            ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedVolumes(vpool, allSrdfTargetVPools, allRpTargetVPools, _dbClient);
        }

        _dbClient.createObject(vpool);

        recordOperation(OperationTypeEnum.CREATE_VPOOL, VPOOL_CREATED_DESCRIPTION, vpool);
        return toBlockVirtualPool(_dbClient, vpool, VirtualPool.getProtectionSettings(vpool, _dbClient),
                VirtualPool.getRemoteProtectionSettings(vpool, _dbClient));
    }

    /**
     * Return the matching pools for a given set of VirtualPool attributes.
     * This API is useful for user to find the matching pools before creating a VirtualPool.
     *
     * @prereq none
     * @param param : VirtualPoolAttributeParam
     * @brief List matching pools for virtual pool properties
     * @return matching pools.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/matching-pools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolList getMatchingPoolsForVirtualPoolAttributes(BlockVirtualPoolParam param) {
        StoragePoolList poolList = new StoragePoolList();

        Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap = new HashMap<URI, VpoolRemoteCopyProtectionSettings>();
        List<VpoolProtectionVarraySettings> protectionSettings = new ArrayList<VpoolProtectionVarraySettings>();
        Map<URI, VpoolProtectionVarraySettings> protectionSettingsMap = new HashMap<URI, VpoolProtectionVarraySettings>();
        VirtualPool vpool = prepareVirtualPool(param, remoteSettingsMap, protectionSettingsMap, protectionSettings);
        List<URI> storagePoolURIs = _dbClient.queryByType(StoragePool.class, true);
        List<StoragePool> allPools = _dbClient.queryObject(StoragePool.class, storagePoolURIs);

        List<StoragePool> matchedPools = ImplicitPoolMatcher.getMatchedPoolWithStoragePools(vpool, allPools,
                protectionSettingsMap,
                remoteSettingsMap,
                null,
                _dbClient, _coordinator, AttributeMatcher.VPOOL_MATCHERS);
        for (StoragePool pool : matchedPools) {
            poolList.getPools().add(toNamedRelatedResource(pool, pool.getNativeGuid()));
        }
        return poolList;
    }

    /**
     * List the virtual pools for Block Store
     *
     * @prereq none
     * @brief List virtual pools for block store
     * @return Returns the VirtualPool user is authorized to see
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualPoolList getBlockVirtualPool(@DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        return getVirtualPoolList(VirtualPool.Type.block, shortVdcId);
    }

    /**
     * Get info for block store virtual pool
     *
     * @prereq none
     * @param id the URN of a ViPR VirtualPool
     * @brief Show block store virtual pool
     * @return VirtualPool details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public BlockVirtualPoolRestRep getVirtualPool(@PathParam("id") URI id) {
        VirtualPool vpool = getVirtualPool(VirtualPool.Type.block, id);
        BlockVirtualPoolRestRep restRep = getBlockVirtualPoolWithProtection(vpool);
        restRep.setNumResources(getNumResources(vpool, _dbClient));
        return restRep;
    }

    protected BlockVirtualPoolRestRep getBlockVirtualPoolWithProtection(VirtualPool vpool) {
        return toBlockVirtualPool(_dbClient, vpool, VirtualPool.getProtectionSettings(vpool, _dbClient),
                VirtualPool.getRemoteProtectionSettings(vpool, _dbClient));
    }

    /**
     * Deactivate block store virtual pool, this will move the virtual pool to a "marked-for-deletion" state,
     * and no more resource may be created using it.
     * The virtual pool will be deleted when all references to this virtual pool of type Volume are deleted
     *
     * @prereq Dependent resources such as volumes and snapshots must be deleted
     * @param id the URN of a ViPR VirtualPool
     * @brief Delete block store virtual pool
     * @return No data returned in response body
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteBlockVirtualPool(@PathParam("id") URI id) {
        return deleteVirtualPool(VirtualPool.Type.block, id);
    }

    /**
     * Get block store virtual pool ACL
     *
     * @prereq none
     * @param id the URN of a ViPR VirtualPool
     * @brief Show ACL assignment for block store virtual pool
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnVirtualPool(VirtualPool.Type.block, id);
    }

    /**
     * Add or remove individual block store virtual pool ACL entry(s). Request body must include at least one add or remove operation.
     *
     * @prereq none
     * @param id the URN of a ViPR VirtualPool
     * @param changes ACL assignment changes
     * @brief Add or remove block store virtual pool ACL entries
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        return updateAclsOnVirtualPool(VirtualPool.Type.block, id, changes);
    }

    /**
     * Returns list of computed id's for all storage pools matching with the virtual pool.
     * This list of pools will be used when creating volumes.
     *
     * @prereq none
     * @param id the URN of a ViPR VirtualPool.
     *
     * @brief List storage pool ids matching the virtual pool
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
     * This method re-computes the matched pools for this virtual pool and returns this information.
     *
     * Where as getStoragePools {id}/storage-pools returns whatever is already computed, for matched pools.
     *
     * @prereq none
     * @param id : the URN of a ViPR Block VirtualPool
     * @brief Refresh list of storage pools matching the virtual pool
     * @return : List of Pool Ids matching with this VirtualPool.
     */
    @GET
    @Path("/{id}/refresh-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePoolList refreshMatchedStoragePools(@PathParam("id") URI id) {
        return refreshMatchedPools(VirtualPool.Type.block, id);
    }

    /**
     * The block virtual pool can be modified only if there are no associated resources.
     *
     * @prereq No associated resources such as volumes or snapshots should exist
     * @param param VirtualPool parameters
     * @brief Update block store virtual pool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockVirtualPoolRestRep updateBlockVirtualPool(@PathParam("id") URI id, BlockVirtualPoolUpdateParam param) {
        VirtualPool vpool = null;

        ArgValidator.checkFieldUriType(id, VirtualPool.class, "id");
        vpool = _dbClient.queryObject(VirtualPool.class, id);
        ArgValidator.checkEntity(vpool, id, isIdEmbeddedInURL(id));

        if (!vpool.getType().equals(VirtualPool.Type.block.name())) {
            throw APIException.badRequests.providedVirtualPoolNotCorrectType();
        }

        URIQueryResultList resultList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getVirtualPoolVolumeConstraint(id), resultList);
        boolean isActiveVolumePartOfPool = false;
        for (URI uri : resultList) {
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            if (!volume.getInactive()) {
                isActiveVolumePartOfPool = true;
                break;
            }
        }

        if (isActiveVolumePartOfPool && checkAttributeValuesChanged(param, vpool)) {
            throw APIException.badRequests.updateVirtualPoolOnlyAllowedToChange();
        }

        // set common VirtualPool update parameters here.
        populateCommonVirtualPoolUpdateParams(vpool, param);
        if (null != param.getSystemType()) {
            if (vpool.getArrayInfo().containsKey(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                for (String systemType : vpool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                    vpool.getArrayInfo().remove(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, systemType);
                }
            }

            if (!(VirtualPool.SystemType.NONE.name().equalsIgnoreCase(param.getSystemType())
            || VirtualPool.SystemType.isBlockTypeSystem(param.getSystemType()))) {
                throw APIException.badRequests.invalidSystemType("Block");
            }

            if (VirtualPool.SystemType.vnxblock.name().equalsIgnoreCase(param.getSystemType())) {
                Integer thinVolumePreAllocation = vpool.getThinVolumePreAllocationPercentage();
                Integer thinVolumePreAllocationParam = param.getThinVolumePreAllocationPercentage();
                if (thinVolumePreAllocation != null && thinVolumePreAllocation > 0
                        && (thinVolumePreAllocationParam == null || thinVolumePreAllocationParam > 0)) {
                    throw APIException.badRequests.thinVolumePreallocationPercentageOnlyApplicableToVMAX();
                }
            }

            vpool.getArrayInfo().put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
        }

        if (null != param.getRaidLevelChanges()) {
            if (null != param.getRaidLevelChanges().getAdd()) {
                for (String raidLevel : param.getRaidLevelChanges().getAdd().getRaidLevels()) {
                    vpool.getArrayInfo()
                            .put(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL, raidLevel);
                }
            }
            if (null != param.getRaidLevelChanges().getRemove()) {
                for (String raidLevel : param.getRaidLevelChanges().getRemove().getRaidLevels()) {
                    vpool.getArrayInfo().remove(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL,
                            raidLevel);
                }
            }
        }

        if (null != param.getAutoTieringPolicyName()) {
            if (param.getAutoTieringPolicyName().isEmpty()) { // To unset a policy name.
                vpool.setAutoTierPolicyName(NONE);
            } else {
                vpool.setAutoTierPolicyName(param.getAutoTieringPolicyName());
            }
        }

        vpool.setHostIOLimitBandwidth(param.getHostIOLimitBandwidth());

        vpool.setHostIOLimitIOPs(param.getHostIOLimitIOPs());

        if (null != param.getDriveType()) {
            vpool.setDriveType(param.getDriveType());
        }

        validateAndSetPathParams(vpool, param.getMaxPaths(), param.getMinPaths(), param.getPathsPerInitiator());

        if (param.getThinVolumePreAllocationPercentage() != null) {
            vpool.setThinVolumePreAllocationPercentage(param.getThinVolumePreAllocationPercentage());
        }

        if (param.getMultiVolumeConsistency() != null) {
            vpool.setMultivolumeConsistency(param.getMultiVolumeConsistency());
        }

        if (null != param.getExpandable()) {
            vpool.setExpandable(param.getExpandable());
        }

        if (param.getFastExpansion() != null) {
            vpool.setFastExpansion(param.getFastExpansion());
        }

        if (null != param.getUniquePolicyNames()) {
            vpool.setUniquePolicyNames(param.getUniquePolicyNames());
        }

        // Update HA settings.
        if (null != param.getHighAvailability()) {
            updateHAParametersForVirtualPool(vpool, param.getHighAvailability());
        }

        // Update Protection settings.
        if (null != param.getProtection()) {
            updateProtectionParamsForVirtualPool(vpool, param.getProtection(), param.getHighAvailability());
        }

        // Validate Block VirtualPool update params.
        VirtualPoolUtil.validateBlockVirtualPoolUpdateParams(vpool, param, _dbClient);

        // invokes implicit pool matching algorithm.
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(vpool, _dbClient, _coordinator);

        if (null != vpool.getMatchedStoragePools() || null != vpool.getInvalidMatchedPools()) {
            Set<URI> allSrdfTargetVPools = SRDFUtils.fetchSRDFTargetVirtualPools(_dbClient);
            Set<URI> allRpTargetVPools = RPHelper.fetchRPTargetVirtualPools(_dbClient);
            ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedVolumes(vpool, allSrdfTargetVPools, allRpTargetVPools, _dbClient);
        }

        // Validate Mirror Vpool
        if (vpool.getMirrorVirtualPool() != null && !NullColumnValueGetter.isNullURI(URI.create(vpool.getMirrorVirtualPool()))) {
            VirtualPool protectionMirrorVPool = _permissionsHelper.getObjectById(URI.create(vpool.getMirrorVirtualPool()),
                    VirtualPool.class);
            validateMirrorVpool(vpool.getHighAvailability(), protectionMirrorVPool);
        }

        // Validate Max Native Continuous Copies
        if (vpool.getMaxNativeContinuousCopies() != null) {
            validateMaxNativeContinuousCopies(vpool.getMaxNativeContinuousCopies(), vpool.getHighAvailability());
        }

        _dbClient.updateAndReindexObject(vpool);

        recordOperation(OperationTypeEnum.UPDATE_VPOOL, VPOOL_UPDATED_DESCRIPTION, vpool);
        return toBlockVirtualPool(_dbClient, vpool, VirtualPool.getProtectionSettings(vpool, _dbClient),
                VirtualPool.getRemoteProtectionSettings(vpool, _dbClient));
    }

    /**
     * Updates the virtual pool high availability parameters based
     * values of the update request.
     *
     * @param vPool A reference to the virtual pool to update.
     * @param haParam The HA update parameters.
     */
    private void updateHAParametersForVirtualPool(VirtualPool vPool,
            VirtualPoolHighAvailabilityParam haParam) {
        // Get the HA varray/vpool map.
        StringMap haVarrayVpoolMap = vPool.getHaVarrayVpoolMap();

        // Set the MetroPoint value
        if (haParam != null) {
            vPool.setMetroPoint(haParam.getMetroPoint());
        }
        // Set the autoCrossConnectExport if set in the haParam
        if (haParam.getAutoCrossConnectExport() != null) {
            vPool.setAutoCrossConnectExport(haParam.getAutoCrossConnectExport());
        }

        // Check if high availability is changing, if yes check if this virtual pool
        // is used as continuous copies vpool, if yes then throw exception.
        if (vPool.getHighAvailability() != null && haParam.getType() != null && !vPool.getHighAvailability().equals(haParam.getType())) {
            checkIfVpoolIsSetAsContinuousCopiesVpool(vPool);
        } else if (vPool.getHighAvailability() == null && haParam.getType() != null
                && (haParam.getType().equals(VirtualPool.HighAvailabilityType.vplex_local.name())
                || haParam.getType().equals(VirtualPool.HighAvailabilityType.vplex_distributed.name()))) {
            checkIfVpoolIsSetAsContinuousCopiesVpool(vPool);
        } else if (vPool.getHighAvailability() != null &&
                (vPool.getHighAvailability().equals(VirtualPool.HighAvailabilityType.vplex_local.name())
                || vPool.getHighAvailability().equals(VirtualPool.HighAvailabilityType.vplex_distributed.name()))
                && (haParam.getType() == null || String.valueOf(haParam.getType()).isEmpty())) {
            checkIfVpoolIsSetAsContinuousCopiesVpool(vPool);
        }

        // Check for removal of HA from the virtual pool.
        if ((haParam.getType() == null || String.valueOf(haParam.getType()).isEmpty())
                && (haParam.getHaVirtualArrayVirtualPool() == null
                || String.valueOf(haParam.getHaVirtualArrayVirtualPool()).isEmpty())) {
            _log.info("Removing HA from vPool");
            // An empty HA parameter indicates HA should be removed
            // for the virtual pool.
            if (vPool.getHighAvailability() != null) {
                vPool.setHighAvailability(NullColumnValueGetter.getNullStr());
            }
            if ((haVarrayVpoolMap != null) && (!haVarrayVpoolMap.isEmpty())) {
                haVarrayVpoolMap.remove(haVarrayVpoolMap.keySet().iterator().next());
                vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
            }
        }

        // If the type is specified, set it. If the type is local
        // Make sure the HA virtual array/pool map is empty.
        if (haParam.getType() != null && !String.valueOf(haParam.getType()).isEmpty()) {
            // Update the type.
            vPool.setHighAvailability(haParam.getType());
            if ((haParam.getType().equals(VirtualPool.HighAvailabilityType.vplex_local.name()))
                    && (haVarrayVpoolMap != null) && (!haVarrayVpoolMap.isEmpty())) {
                haVarrayVpoolMap.remove(haVarrayVpoolMap.keySet().iterator().next());
                vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
            }
        }

        // Update the HA virtual array and pool settings.
        if (haParam.getHaVirtualArrayVirtualPool() != null) {
            // Check for removal.
            if (haParam.getHaVirtualArrayVirtualPool().getVirtualArray() == null
                    && haParam.getHaVirtualArrayVirtualPool().getVirtualPool() == null) {
                // If empty, indicates any HA virtual array and pool
                // settings should be removed.
                if ((haVarrayVpoolMap != null) && (!haVarrayVpoolMap.isEmpty())) {
                    haVarrayVpoolMap.remove(haVarrayVpoolMap.keySet().iterator().next());
                    vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());

                    // If the type is vplex_distributed, remove the type as well
                    if (vPool.getHighAvailability() != null && vPool.getHighAvailability().equals(
                            VirtualPool.HighAvailabilityType.vplex_distributed.name())) {
                        vPool.setHighAvailability(NullColumnValueGetter.getNullStr());
                    }
                }
            } else {
                // Determine the HA virtual pool.
                String haVpoolId = NullColumnValueGetter.getNullURI().toString();
                if (haParam.getHaVirtualArrayVirtualPool().getVirtualPool() != null) {
                    // Pool is specified in the update.
                    if (!haParam.getHaVirtualArrayVirtualPool().getVirtualPool().toString().isEmpty()) {
                        // Note that an empty pool will mean remove the pool.
                        haVpoolId = haParam.getHaVirtualArrayVirtualPool().getVirtualPool().toString();

                        // If an HA vpool is specified and it is changing then verify the
                        // value specified.
                        if (NullColumnValueGetter.isNotNullValue(haVpoolId)) {
                            if ((haVarrayVpoolMap == null) || (haVarrayVpoolMap.isEmpty())) {
                                // No HA vpool is currently specified, so just verify the new HA vpool.
                                verifyNewHAVpoolForHAVpoolUpdate(vPool, haVpoolId);
                            } else {
                                String currentHAVpoolId = haVarrayVpoolMap.get(haVarrayVpoolMap.keySet().iterator().next());
                                if (!NullColumnValueGetter.isNotNullValue(currentHAVpoolId)) {
                                    // The current specified is null, so just verify the new HA vpool.
                                    verifyNewHAVpoolForHAVpoolUpdate(vPool, haVpoolId);
                                } else if (!haVpoolId.equals(currentHAVpoolId)) {
                                    // Otherwise, verify the HA vpool only if it is changing.
                                    verifyNewHAVpoolForHAVpoolUpdate(vPool, haVpoolId);
                                }
                            }
                        }
                    }
                }

                // Update settings for the HA virtual array.
                if (haParam.getHaVirtualArrayVirtualPool().getVirtualArray() != null) {
                    // HA virtual array specified in update. When specified in
                    // update must supply a valid value.
                    if ((haVarrayVpoolMap != null) && (!haVarrayVpoolMap.isEmpty())) {
                        // Replace entry with new HA virtual array.
                        String haVarray = haVarrayVpoolMap.keySet().iterator().next();
                        if (!haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString().equals(haVarray)) {
                            // only remove the entry if the varray has changed
                            haVarrayVpoolMap.remove(haVarray);
                            vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
                        }
                        haVarrayVpoolMap.put(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString(),
                                haVpoolId);
                        // Use HA Varray as RP Source if boolean is set to true
                        if (haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite() != null
                                && haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite()) {
                            vPool.setHaVarrayConnectedToRp(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString());
                        }
                        else {
                            vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
                        }
                    } else if (haVarrayVpoolMap == null) {
                        // Create new map and entry.
                        haVarrayVpoolMap = new StringMap();
                        haVarrayVpoolMap.put(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString(),
                                haVpoolId);
                        vPool.setHaVarrayVpoolMap(haVarrayVpoolMap);
                        // Use HA Varray as RP Source if boolean is set to true
                        if (haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite() != null
                                && haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite()) {
                            vPool.setHaVarrayConnectedToRp(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString());
                        }
                        else {
                            vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
                        }
                    } else {
                        // Map exists but is empty, just add.
                        haVarrayVpoolMap.put(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString(),
                                haVpoolId);
                        // Use HA Varray as RP Source if boolean is set to true
                        if (haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite() != null
                                && haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite()) {
                            vPool.setHaVarrayConnectedToRp(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString());
                        }
                        else {
                            vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
                        }
                    }
                } else if ((haParam.getHaVirtualArrayVirtualPool().getVirtualPool() != null)
                        && (haVarrayVpoolMap != null) && (!haVarrayVpoolMap.isEmpty())) {
                    // Only HA virtual pool update, update setting for current
                    // HA virtual array.
                    String haVarray = haVarrayVpoolMap.keySet().iterator().next();
                    haVarrayVpoolMap.put(haVarray, haVpoolId);
                    // Use HA Varray as RP Source if boolean is set to true
                    if (haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite() != null
                            && haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite()) {
                        vPool.setHaVarrayConnectedToRp(haParam.getHaVirtualArrayVirtualPool().getVirtualArray().toString());
                    }
                    else {
                        vPool.setHaVarrayConnectedToRp(NullColumnValueGetter.getNullStr());
                    }
                }
            }
        }
    }

    /**
     * When updating a virtual pool, this function verifies that if there is
     * and update to the HA virtual pool, that the specified pool is valid
     * for the virtual pool being updated.
     *
     * @param vPoolBeingUpdated The vpool being updated.
     * @param newHAVpoolId The non-null id of the new HA vpool.
     */
    private void verifyNewHAVpoolForHAVpoolUpdate(VirtualPool vPoolBeingUpdated, String newHAVpoolId) {
        URI newHAVpoolURI = URI.create(newHAVpoolId);
        VirtualPool newHAVpool = _dbClient.queryObject(VirtualPool.class, newHAVpoolURI);
        if (newHAVpool == null) {
            throw APIException.badRequests.haVpoolForVpoolUpdateDoesNotExist(newHAVpoolId);
        }

        if (newHAVpool.getInactive()) {
            throw APIException.badRequests.haVpoolForVpoolUpdateIsInactive(newHAVpool.getLabel());
        }

        StringMap newHAVpoolHAMap = newHAVpool.getHaVarrayVpoolMap();
        if ((newHAVpoolHAMap == null) || (newHAVpoolHAMap.isEmpty())) {
            // New HA vpool does not specify VPLEX HA.
            return;
        }

        String newHAVpoolHAVpoolId = newHAVpoolHAMap.get(newHAVpoolHAMap.keySet().iterator().next());
        if (!NullColumnValueGetter.isNotNullValue(newHAVpoolHAVpoolId)) {
            // No HA Vpool specified.
            return;
        }

        VirtualPool newHAVpoolHAVpool = _dbClient.queryObject(VirtualPool.class, URI.create(newHAVpoolHAVpoolId));
        if (newHAVpoolHAVpool == null) {
            // Invalid HA vpool for the new HA vpool does not exist.
            throw APIException.badRequests.haVpoolForNewHAVpoolForVpoolUpdateDoesNotExist(newHAVpoolHAVpoolId, newHAVpool.getLabel());
        }

        // Make sure that if the HA vpool itself specifies HA, that the
        // HA vpool is not this vpool being updated. See Jira 6797.
        if (newHAVpoolHAVpool.getId().equals(vPoolBeingUpdated.getId())) {
            throw APIException.badRequests.haVpoolForVpoolUpdateHasInvalidHAVpool(newHAVpool.getLabel());
        }
    }

    /**
     * Performs the protection updates on <code>VirtualPool</code>.
     *
     * @param virtualPool Reference to the virtual pool to update.
     * @param param The updates that need to be applied to the virtual pool.
     */
    private void updateProtectionParamsForVirtualPool(VirtualPool virtualPool,
            BlockVirtualPoolProtectionUpdateParam param, VirtualPoolHighAvailabilityParam haParam) {

        // If the update specifies protection, we need to process the update.
        if (param != null) {
            // If the update specifies protection mirroring, process the mirroring update.
            if (param.getContinuousCopies() != null) {
                if ((param.getContinuousCopies().getVpool() == null || String.valueOf(param.getContinuousCopies().getVpool()).isEmpty()) &&
                        (param.getContinuousCopies().getMaxMirrors() == null
                                || param.getContinuousCopies().getMaxMirrors() == VirtualPoolProtectionMirrorParam.MAX_DISABLED
                                || String.valueOf(param.getContinuousCopies().getMaxMirrors()).isEmpty())) {
                    // An empty protection mirror request was sent indicating removal of mirrored protection
                    // so remove protection mirror vpool and set max native continuous copies to disabled value.
                    if (virtualPool.getMirrorVirtualPool() != null) {
                        virtualPool.setMirrorVirtualPool(String.valueOf(NullColumnValueGetter.getNullURI()));
                    }
                    virtualPool.setMaxNativeContinuousCopies(VirtualPool.MAX_DISABLED);
                } else if ((param.getContinuousCopies().getVpool() == null || String.valueOf(param.getContinuousCopies().getVpool())
                        .isEmpty())) {
                    // Setting Mirror Virtual Pool is optional so user can choose to remove mirror virtual pool
                    if (virtualPool.getMirrorVirtualPool() != null) {
                        virtualPool.setMirrorVirtualPool(String.valueOf(NullColumnValueGetter.getNullURI()));
                    }
                    if (param.getContinuousCopies().getMaxMirrors() != null) {
                        // Updating max mirrors
                        virtualPool.setMaxNativeContinuousCopies(param.getContinuousCopies().getMaxMirrors());
                    }
                } else if ((param.getContinuousCopies().getVpool() != null && !String.valueOf(param.getContinuousCopies().getVpool())
                        .isEmpty())
                        && (param.getContinuousCopies().getMaxMirrors() == null)) {
                    // Update protection mirror vpool
                    updateProtectionMirrorVPool(param.getContinuousCopies().getVpool(), virtualPool);
                } else {
                    // continuous protection has been specified with both
                    // the protection vpool and max mirrors
                    if (param.getContinuousCopies().getVpool() != null && !String.valueOf(param.getContinuousCopies().getVpool()).isEmpty()) {
                        updateProtectionMirrorVPool(param.getContinuousCopies().getVpool(), virtualPool);
                    }

                    if (param.getContinuousCopies().getMaxMirrors() != null) {
                        virtualPool.setMaxNativeContinuousCopies(param.getContinuousCopies().getMaxMirrors());
                    }
                }
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

            // Handle SRDF update
            updateRemoteCopyVPool(virtualPool, param);

            // Handle the RP protection updates
            if (param.getRecoverPoint() != null) {
                if (param.getRecoverPoint().getAdd() == null && param.getRecoverPoint().getRemove() == null
                        && param.getRecoverPoint().getSourcePolicy() == null) {
                    // Empty RP protection specified. This indicates removal of
                    // RP protection so remove it.
                    deleteVPoolProtectionVArraySettings(virtualPool);
                } else {
                    // If the source policy is omitted, do nothing.
                    ProtectionSourcePolicy sourcePolicy = param.getRecoverPoint().getSourcePolicy();
                    if (sourcePolicy != null) {
                        String nullValue = NullColumnValueGetter.getNullStr();
                        virtualPool.setJournalSize(StringUtils.defaultString(sourcePolicy.getJournalSize(), nullValue));
                        virtualPool.setJournalVarray(!NullColumnValueGetter.isNullURI(sourcePolicy.getJournalVarray()) ? sourcePolicy
                                .getJournalVarray().toString()
                                : nullValue);
                        if (NullColumnValueGetter.isNullValue(virtualPool.getJournalVarray())) {
                            // If the journal varray is null, the journal vpool has to be null too.
                            virtualPool.setJournalVpool(nullValue);
                        } else {
                            // Set the journal virtual pool. If none is specified, we must determine the default, which
                            // will be the parent vpool or the ha vpool.
                            String defaultVpoolId = nullValue;
                            if (haParam == null || Boolean.TRUE.equals(haParam.getMetroPoint())) {
                                // Default the virtual pool to the parent virtual pool in cases where no high availability
                                // is specified or when HA is specified but not MetroPoint.
                                defaultVpoolId = virtualPool.getId().toString();
                            } else if (Boolean.FALSE.equals(haParam.getMetroPoint()) && haParam.getHaVirtualArrayVirtualPool() != null
                                    && Boolean.TRUE.equals(haParam.getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite())) {
                                // If active protection at HA site is specified, our default vpool should be the HA
                                // virtual pool.
                                if (haParam.getHaVirtualArrayVirtualPool().getVirtualPool() != null) {
                                    defaultVpoolId = haParam.getHaVirtualArrayVirtualPool().getVirtualPool().toString();
                                }
                            }

                            virtualPool.setJournalVpool(!NullColumnValueGetter.isNullURI(sourcePolicy.getJournalVpool()) ? sourcePolicy
                                    .getJournalVpool().toString()
                                    : defaultVpoolId);
                        }

                        if (NullColumnValueGetter.isNotNullValue(virtualPool.getHighAvailability())) {
                            virtualPool.setStandbyJournalVarray(
                                    !NullColumnValueGetter.isNullURI(sourcePolicy.getStandbyJournalVarray()) ? sourcePolicy
                                            .getStandbyJournalVarray().toString()
                                            : nullValue);
                            if (NullColumnValueGetter.isNullValue(virtualPool.getStandbyJournalVarray())) {
                                // If the ha journal varray is null, the ha journal vpool has to be null too.
                                virtualPool.setStandbyJournalVpool(nullValue);
                            } else {

                                String defaultHaVpool = nullValue;
                                // Obtain the default HA virtual pool
                                Map<String, String> haVarrayVpoolMap = virtualPool.getHaVarrayVpoolMap();
                                if (haVarrayVpoolMap != null && !haVarrayVpoolMap.isEmpty()) {
                                    if (NullColumnValueGetter.isNotNullValue(haVarrayVpoolMap.get(virtualPool.getStandbyJournalVarray()))) {
                                        defaultHaVpool = haVarrayVpoolMap.get(virtualPool.getStandbyJournalVarray());
                                    }
                                }

                                // By default, if no standby vpool is set, set the HA journal vpool to the HA vpool.
                                virtualPool.setStandbyJournalVpool(
                                        !NullColumnValueGetter.isNullURI(sourcePolicy.getStandbyJournalVpool()) ? sourcePolicy
                                                .getStandbyJournalVpool().toString()
                                                : defaultHaVpool);
                            }
                        }
                        virtualPool.setRpCopyMode(StringUtils.defaultString(sourcePolicy.getRemoteCopyMode(), nullValue));
                        // If the RPO value is null, set the value to 0 to unset this field.
                        long rpoValue = (sourcePolicy.getRpoValue() == null) ? 0L : sourcePolicy.getRpoValue();
                        virtualPool.setRpRpoValue(rpoValue);
                        virtualPool.setRpRpoType(StringUtils.defaultString(sourcePolicy.getRpoType(), nullValue));
                    }

                    if (param.getRecoverPoint().getRemove() != null && !param.getRecoverPoint().getRemove().isEmpty()) {
                        // Only remove protection copies if there are copies to remove
                        if (virtualPool.getProtectionVarraySettings() != null &&
                                !virtualPool.getProtectionVarraySettings().isEmpty()) {

                            StringMap settingsMap = virtualPool.getProtectionVarraySettings();

                            for (VirtualPoolProtectionVirtualArraySettingsParam settingsParam : param.getRecoverPoint().getRemove()) {
                                if (settingsParam.getVarray() != null && !settingsParam.getVarray().toString().isEmpty()) {
                                    String vpoolProtectionVarraySettingsUri =
                                            virtualPool.getProtectionVarraySettings().get(settingsParam.getVarray().toString());

                                    if (vpoolProtectionVarraySettingsUri == null) {
                                        throw APIException.badRequests.protectionNoCopyCorrespondingToVirtualArray(settingsParam
                                                .getVarray());
                                    }

                                    deleteVPoolProtectionVArraySettings(virtualPool.getProtectionVarraySettings().get(
                                            settingsParam.getVarray().toString()));
                                    settingsMap.remove(settingsParam.getVarray().toString());
                                }
                            }

                            virtualPool.setProtectionVarraySettings(settingsMap);
                        }
                    }

                    if (param.getRecoverPoint().getAdd() != null && !param.getRecoverPoint().getAdd().isEmpty()) {
                        Set<VpoolProtectionVarraySettings> protectionSettingsToAdd = null;
                        StringMap settingsMap = virtualPool.getProtectionVarraySettings();

                        if (settingsMap == null) {
                            settingsMap = new StringMap();
                        }

                        for (VirtualPoolProtectionVirtualArraySettingsParam settingsParam : param.getRecoverPoint().getAdd()) {
                            VirtualArray virtualArray = _permissionsHelper.getObjectById(settingsParam.getVarray(), VirtualArray.class);
                            ArgValidator.checkEntity(virtualArray, settingsParam.getVarray(), false);
                            VpoolProtectionVarraySettings setting = new VpoolProtectionVarraySettings();
                            setting.setId(URIUtil.createId(VpoolProtectionVarraySettings.class));
                            setting.setParent(new NamedURI(virtualPool.getId(), virtualPool.getLabel()));

                            if (settingsParam.getVpool() != null && !String.valueOf(settingsParam.getVpool()).isEmpty()) {
                                setting.setVirtualPool(settingsParam.getVpool());
                            }

                            setting.setJournalSize(settingsParam.getCopyPolicy() != null ? settingsParam.getCopyPolicy().getJournalSize()
                                    : null);
                            setting.setJournalVarray(settingsParam.getCopyPolicy() != null ? settingsParam.getCopyPolicy()
                                    .getJournalVarray() : settingsParam.getVarray());
                            setting.setJournalVpool(settingsParam.getCopyPolicy() != null ? settingsParam.getCopyPolicy().getJournalVpool()
                                    : settingsParam.getVpool());
                            settingsMap.put(settingsParam.getVarray().toString(), setting.getId().toString());
                            if (protectionSettingsToAdd == null) {
                                protectionSettingsToAdd = new HashSet<VpoolProtectionVarraySettings>();
                            }
                            protectionSettingsToAdd.add(setting);
                        }

                        // Set the underlying protection setting objects
                        if (protectionSettingsToAdd != null) {
                            for (VpoolProtectionVarraySettings setting : protectionSettingsToAdd) {
                                _dbClient.createObject(setting);
                            }
                        }

                        virtualPool.setProtectionVarraySettings(settingsMap);
                    }

                    StringMap settingsMap = virtualPool.getProtectionVarraySettings();

                    if (settingsMap != null && settingsMap.size() == 0) {
                        // If there are no copies after the add/remove, remove the source policy
                        virtualPool.setJournalSize(NullColumnValueGetter.getNullStr());
                        virtualPool.setJournalVarray(NullColumnValueGetter.getNullStr());
                        virtualPool.setJournalVpool(NullColumnValueGetter.getNullStr());
                    }
                }
            }
        }
    }

    private void updateRemoteCopyVPool(VirtualPool virtualPool,
            BlockVirtualPoolProtectionUpdateParam param) {
        if (param.getRemoteCopies() != null) {
            StringMap remoteCopySettingsMap = virtualPool.getProtectionRemoteCopySettings();
            if (remoteCopySettingsMap == null) {
                remoteCopySettingsMap = new StringMap();
                virtualPool.setProtectionRemoteCopySettings(remoteCopySettingsMap);
            }

            // Determine if we are updating an existing remote copy setting on the vpool. This case
            // occurs if we are removing and adding a remote copy settings for the same varray. In
            // this case we want to update the existing remote copy settings vpool instead of performing a remove
            // and an add, which is buggy in the StringMap.
            if (param.getRemoteCopies().getRemove() != null && !param.getRemoteCopies().getRemove().isEmpty()
                    && param.getRemoteCopies().getAdd() != null && !param.getRemoteCopies().getAdd().isEmpty()) {
                Iterator<VirtualPoolRemoteProtectionVirtualArraySettingsParam> removeRemoteCopies =
                        param.getRemoteCopies().getRemove().iterator();
                Iterator<VirtualPoolRemoteProtectionVirtualArraySettingsParam> addRemoteCopies =
                        param.getRemoteCopies().getAdd().iterator();

                while (removeRemoteCopies.hasNext()) {
                    VirtualPoolRemoteProtectionVirtualArraySettingsParam removeRemoteCopySettingsParam = removeRemoteCopies.next();

                    while (addRemoteCopies.hasNext()) {
                        VirtualPoolRemoteProtectionVirtualArraySettingsParam addRemoteCopySettingsParam = addRemoteCopies.next();

                        // If we are adding and removing a remote copy setting for the same virtual array, we simply need
                        // to update the remote copy setting vpool in the database. After which we can delete the
                        // remote copy setting from the add/remove lists so they do not get processed individually.
                        if (removeRemoteCopySettingsParam.getVarray().toString().equals(
                                addRemoteCopySettingsParam.getVarray().toString())) {
                            String remoteCopySettingsUri = remoteCopySettingsMap.get(addRemoteCopySettingsParam.getVarray().toString());
                            VpoolRemoteCopyProtectionSettings remoteSettingsObj = _dbClient.queryObject(
                                    VpoolRemoteCopyProtectionSettings.class, URI.create(remoteCopySettingsUri));

                            if (remoteSettingsObj != null) {
                                remoteSettingsObj.setVirtualPool(addRemoteCopySettingsParam.getVpool());
                                if (null != addRemoteCopySettingsParam.getRemoteCopyMode()) {
                                    if (!CopyModes.lookup(addRemoteCopySettingsParam.getRemoteCopyMode())) {
                                        throw APIException.badRequests.invalidCopyMode(addRemoteCopySettingsParam.getRemoteCopyMode());
                                    }
                                    remoteSettingsObj.setCopyMode(addRemoteCopySettingsParam.getRemoteCopyMode());
                                }
                                _dbClient.persistObject(remoteSettingsObj);

                                // Only remove from the add list if the remote copy setting actually
                                // exists. We will still want to add it if it does not exist.
                                param.getRemoteCopies().getAdd().remove(addRemoteCopySettingsParam);
                            }

                            // Always remove the remote copy setting from the remove list
                            param.getRemoteCopies().getRemove().remove(removeRemoteCopySettingsParam);
                        }
                    }
                }
            }

            if (param.getRemoteCopies().getRemove() != null && !param.getRemoteCopies().getRemove().isEmpty()) {
                for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : param.getRemoteCopies().getRemove()) {

                    if (remoteSettings.getVarray() != null && remoteCopySettingsMap.containsKey(remoteSettings.getVarray().toString())) {
                        String remoteCopySettingsUri = remoteCopySettingsMap.get(remoteSettings.getVarray().toString());
                        remoteCopySettingsMap.remove(remoteSettings.getVarray().toString());
                        VpoolRemoteCopyProtectionSettings remoteSettingsObj = _dbClient.queryObject(
                                VpoolRemoteCopyProtectionSettings.class, URI.create(remoteCopySettingsUri));
                        remoteSettingsObj.setInactive(true);
                        _dbClient.persistObject(remoteSettingsObj);
                    }
                }
            }

            if (param.getRemoteCopies().getAdd() != null && !param.getRemoteCopies().getAdd().isEmpty()) {
                List<VpoolRemoteCopyProtectionSettings> remoteSettingsList = new ArrayList<VpoolRemoteCopyProtectionSettings>();
                // already existing remote VArrays
                List<String> existingRemoteUris = new ArrayList<String>(remoteCopySettingsMap.keySet());
                for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : param.getRemoteCopies().getAdd()) {
                    // CTRL-275 fix
                    VirtualArray remoteVArray = _dbClient.queryObject(VirtualArray.class, remoteSettings.getVarray());
                    if (null == remoteVArray || remoteVArray.getInactive()) {
                        throw APIException.badRequests.inactiveRemoteVArrayDetected(remoteSettings.getVarray());
                    }
                    VpoolRemoteCopyProtectionSettings remoteCopySettingsParam = new VpoolRemoteCopyProtectionSettings();
                    remoteSettingsList.add(remoteCopySettingsParam);
                    remoteCopySettingsParam.setId(URIUtil.createId(VpoolRemoteCopyProtectionSettings.class));
                    remoteCopySettingsParam.setVirtualArray(remoteSettings.getVarray());
                    if (existingRemoteUris.contains(remoteSettings.getVarray().toString()) ||
                            remoteCopySettingsMap.containsKey(remoteSettings.getVarray().toString())) {
                        throw APIException.badRequests.duplicateRemoteSettingsDetected(remoteSettings.getVarray());
                    }
                    remoteCopySettingsMap.put(remoteSettings.getVarray().toString(), remoteCopySettingsParam.getId().toString());
                    remoteCopySettingsParam.setVirtualPool(remoteSettings.getVpool());

                    if (null != remoteSettings.getRemoteCopyMode()) {
                        if (!CopyModes.lookup(remoteSettings.getRemoteCopyMode())) {
                            throw APIException.badRequests.invalidCopyMode(remoteSettings.getRemoteCopyMode());
                        }
                        remoteCopySettingsParam.setCopyMode(remoteSettings.getRemoteCopyMode());
                    }
                }
                _dbClient.createObject(remoteSettingsList);
            }
        }
    }

    private void updateProtectionMirrorVPool(URI vPoolUri, VirtualPool virtualPool) {
        ArgValidator.checkUri(vPoolUri);
        VirtualPool protectionMirrorVPool = _permissionsHelper.getObjectById(vPoolUri, VirtualPool.class);
        ArgValidator.checkEntity(protectionMirrorVPool, vPoolUri, false);
        virtualPool.setMirrorVirtualPool(vPoolUri.toString());
    }

    /**
     *
     * Check if any VirtualPool attribute values have changed.
     *
     * @TODO Whenever a new attribute is added to VirtualPool, we should change this
     *       logic. Constraint to check: can we change new attribute, if there
     *       are resources associated with VirtualPool?
     *
     * @param param
     * @param vpool : VirtualPool in DB.
     * @return : flag to check whether to update VirtualPool or not.
     */
    public boolean checkAttributeValuesChanged(BlockVirtualPoolUpdateParam param, VirtualPool vpool) {
        return super.checkAttributeValuesChanged(param, vpool)
                || checkPathParameterModified(vpool.getNumPaths(), param.getMaxPaths())
                || checkPathParameterModified(vpool.getMinPaths(), param.getMinPaths())
                || checkPathParameterModified(vpool.getPathsPerInitiator(), param.getPathsPerInitiator())
                || checkPathParameterModified(vpool.getHostIOLimitBandwidth(), param.getHostIOLimitBandwidth())
                || checkPathParameterModified(vpool.getHostIOLimitIOPs(), param.getHostIOLimitIOPs())
                || VirtualPoolUtil.checkRaidLevelsChanged(vpool.getArrayInfo(), param.getRaidLevelChanges())
                || VirtualPoolUtil.checkForVirtualPoolAttributeModification(vpool.getDriveType(), param.getDriveType())
                || VirtualPoolUtil.checkThinVolumePreAllocationChanged(vpool.getThinVolumePreAllocationPercentage(),
                        param.getThinVolumePreAllocationPercentage())
                || VirtualPoolUtil.checkProtectionChanged(vpool, param.getProtection())
                || VirtualPoolUtil.checkHighAvailabilityChanged(vpool, param.getHighAvailability());
    }

    /**
     * check the if there is any change in VirtualPool maxPaths, minPaths, pathsPerInitiator attributes.
     *
     * @param vpoolValue
     * @param paramValue
     */
    private boolean checkPathParameterModified(Integer vpoolValue, Integer paramValue) {
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
     * This method allows a user to update assigned matched pools.
     *
     * @prereq none
     * @param param
     *            : VirtualPool parameter
     * @brief Update pools in block store virtual pool
     * @return VirtualPool details
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/assign-matched-pools")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public BlockVirtualPoolRestRep updateBlockVirtualPoolWithAssignedPools(@PathParam("id") URI id,
            VirtualPoolPoolUpdateParam param) {
        return toBlockVirtualPool(_dbClient, updateVirtualPoolWithAssignedStoragePools(id, param));
    }

    /**
     * Gets storage capacity information for specified virtual pool and neighborhood instances.
     *
     * The method returns set of metrics for capacity available for block storage provisioning:
     * - free_gb : free storage capacity
     * - used_gb : used storage capacity
     * - provisioned_gb : subscribed storage capacity (may be larger than usable capacity)
     * - percent_used : percent of usable capacity which is used
     * - percent_subscribed : percent of usable capacity which is subscribed (may be more than 100)
     *
     * @prereq none
     * @param id the URN of a ViPR VirtualPool.
     * @param varrayId The id of VirtualArray.
     * @brief Show storage capacity for the virtual pool and virtual array
     * @return Capacity metrics in GB and percent indicators for used and subscribed capacity.
     */
    @GET
    @Path("/{id}/varrays/{varrayId}/capacity")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public CapacityResponse getCapacity(@PathParam("id") URI id,
            @PathParam("varrayId") URI varrayId) {
        return getCapacityForVirtualPoolAndVirtualArray(getVirtualPool(Type.block, id), varrayId);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected Type getVirtualPoolType() {
        return Type.block;
    }

    /**
     * List all instances of block virtual pools
     *
     * @prereq none
     * @brief List all instances of block virtual pools
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public BlockVirtualPoolBulkRep getBulkResources(BulkIdParam param) {
        return (BlockVirtualPoolBulkRep) super.getBulkResources(param);
    }

    /**
     * Show quota and available capacity before quota is exhausted
     *
     * @prereq none
     * @param id the URN of a ViPR VirtualPool.
     * @brief Show quota and available capacity before quota is exhausted
     * @return QuotaInfo Quota metrics.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN })
    @Path("/{id}/quota")
    public QuotaInfo getQuota(@PathParam("id") URI id) throws DatabaseException {
        return getQuota(getVirtualPool(Type.block, id));
    }

    /**
     * Updates quota and available capacity before quota is exhausted
     *
     * @prereq none
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
        return updateQuota(getVirtualPool(Type.block, id), param);
    }

    @Override
    public BlockVirtualPoolBulkRep queryBulkResourceReps(List<URI> ids) {

        if (!ids.iterator().hasNext()) {
            return new BlockVirtualPoolBulkRep();
        }

        // get vdc id from the first id; assume all id's are from the same vdc
        String shortVdcId = VdcUtil.getVdcId(getResourceClass(), ids.iterator().next()).toString();

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
        return new BlockVirtualPoolBulkRep(BulkList.wrapping(dbIterator, BLOCK_VPOOL_MAPPER, new BulkList.VirtualPoolFilter(Type.block)));
    }

    private final BlockVirtualPoolMapper BLOCK_VPOOL_MAPPER = new BlockVirtualPoolMapper();

    private class BlockVirtualPoolMapper implements Function<VirtualPool, BlockVirtualPoolRestRep> {
        @Override
        public BlockVirtualPoolRestRep apply(final VirtualPool vpool) {
            BlockVirtualPoolRestRep restRep = getBlockVirtualPoolWithProtection(vpool);
            restRep.setNumResources(getNumResources(vpool, _dbClient));
            return restRep;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected BlockVirtualPoolBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        if (isSystemOrRestrictedSystemAdmin()) {
            return queryBulkResourceReps(ids);
        }

        if (!ids.iterator().hasNext()) {
            return new BlockVirtualPoolBulkRep();
        }

        // get vdc id from the first id; assume all id's are from the same vdc
        String shortVdcId = VdcUtil.getVdcId(getResourceClass(), ids.iterator().next()).toString();

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

        BulkList.ResourceFilter filter = new BulkList.VirtualPoolFilter(Type.block, getUserFromContext(), _permissionsHelper);
        return new BlockVirtualPoolBulkRep(BulkList.wrapping(dbIterator, BLOCK_VPOOL_MAPPER, filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.BLOCK_VPOOL;
    }

    static Integer getNumResources(VirtualPool vpool, DbClient dbClient) {
        return dbClient.countObjects(Volume.class, "virtualPool", vpool.getId());
    }

    /**
     * Validate and update the various path parameters:
     * 1. If minPaths or pathsPerInitiator are specified, maxPaths must be specified
     * 2. The resulting minPaths cannot be greater than maxPaths
     * 3. The resulting pathsPerInitiator cannot be greater than maxPaths
     *
     * @param vpool
     * @param maxPaths
     * @param minPaths
     * @param pathsPerInitiator
     */
    private void validateAndSetPathParams(VirtualPool vpool,
            Integer maxPaths, Integer minPaths, Integer pathsPerInitiator) {
        // If minPaths is specified, or pathsPerInitiator is specified, maxPaths must be specified
        if ((minPaths != null || pathsPerInitiator != null) && maxPaths == null) {
            throw APIException.badRequests.maxPathsRequired();
        }
        if (maxPaths != null) {
            ArgValidator.checkFieldMinimum(maxPaths, 1, "max_paths");
        }
        if (minPaths != null) {
            ArgValidator.checkFieldMinimum(minPaths, 1, "min_paths");
        }
        if (pathsPerInitiator != null) {
            ArgValidator.checkFieldMinimum(pathsPerInitiator, 1, "paths_per_initiator");
        }
        Integer min = (minPaths != null) ? minPaths : vpool.getMinPaths();
        Integer max = (maxPaths != null) ? maxPaths : vpool.getNumPaths();
        Integer ppi = (pathsPerInitiator != null) ? pathsPerInitiator : vpool.getPathsPerInitiator();
        // Default the parameters to one if not set either in vPool or parameters
        if (min == null) {
            minPaths = min = 1;
        }
        if (max == null) {
            maxPaths = max = 1;
        }
        if (ppi == null) {
            pathsPerInitiator = ppi = 1;
        }
        // minPaths must be <= than maxPaths.
        if (min > max) {
            throw APIException.badRequests.minPathsGreaterThanMaxPaths();
        }
        // pathsPerInitiator must be <= maxPaths.
        if (ppi > max) {
            throw APIException.badRequests.pathsPerInitiatorGreaterThanMaxPaths();
        }
        // If the parameters were set, save them in the vpool.
        if (minPaths != null) {
            vpool.setMinPaths(minPaths);
        }
        if (maxPaths != null) {
            vpool.setNumPaths(maxPaths);
        }
        if (pathsPerInitiator != null) {
            vpool.setPathsPerInitiator(pathsPerInitiator);
        }
    }

    // Validate to make sure continuous copies greater than 1 is not allowed for Vplex
    private void validateMaxNativeContinuousCopies(Integer maxMirrors, String highAvailability) {
        if (highAvailability != null
                && (VirtualPool.HighAvailabilityType.vplex_local.name().equals(highAvailability) || VirtualPool.HighAvailabilityType.vplex_distributed
                        .name().equals(highAvailability))) {
            if (maxMirrors > 1) {
                throw APIException.badRequests.invalidMaxContinuousCopiesForVplex(maxMirrors);
            }
        }
    }

    // Validate the High Availability for virtual pool and the mirror vpool.
    // If HA for virtual pool is NONE then HA for mirror vpool should be NONE
    // If HA for virtual pool is vplex_local then HA for mirror vpool should be vplex_local
    // If HA for virtual pool is vplex_distributed then HA for mirror vpool should be vplex_local
    // This is required because when only detach operation is performed then BlockMirror/VplexMirror
    // is converted into independent volume/vplex volume and virtual pool association do not change.
    private void validateMirrorVpool(String vpoolHighAvailability, VirtualPool mirrorVpool) {
        if (mirrorVpool.getHighAvailability() != null && vpoolHighAvailability != null
                && vpoolHighAvailability.equals(VirtualPool.HighAvailabilityType.vplex_local.name())
                && !(vpoolHighAvailability.equals(mirrorVpool.getHighAvailability()))) {
            throw APIException.badRequests.invalidHighAvailabilityForMirrorVpool(mirrorVpool.getLabel(), mirrorVpool.getHighAvailability(),
                    vpoolHighAvailability, VirtualPool.HighAvailabilityType.vplex_local.name());
        } else if (mirrorVpool.getHighAvailability() != null && vpoolHighAvailability != null
                && vpoolHighAvailability.equals(VirtualPool.HighAvailabilityType.vplex_distributed.name())
                && !(mirrorVpool.getHighAvailability().equals(VirtualPool.HighAvailabilityType.vplex_local.name()))) {
            throw APIException.badRequests.invalidHighAvailabilityForMirrorVpool(mirrorVpool.getLabel(), mirrorVpool.getHighAvailability(),
                    vpoolHighAvailability, VirtualPool.HighAvailabilityType.vplex_local.name());
        } else if (mirrorVpool.getHighAvailability() == null && vpoolHighAvailability != null) {
            throw APIException.badRequests.invalidHighAvailabilityForMirrorVpool(mirrorVpool.getLabel(), "None", vpoolHighAvailability,
                    VirtualPool.HighAvailabilityType.vplex_local.name());
        } else if (mirrorVpool.getHighAvailability() != null && vpoolHighAvailability == null) {
            throw APIException.badRequests.invalidHighAvailabilityForMirrorVpool(mirrorVpool.getLabel(), mirrorVpool.getHighAvailability(),
                    "None", "None");
        }
    }

    // This method must not persist anything to the DB
    private VirtualPool prepareVirtualPool(BlockVirtualPoolParam param,
            Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap,
            Map<URI, VpoolProtectionVarraySettings> protectionSettingsMap,
            List<VpoolProtectionVarraySettings> protectionSettingsList) {

        if (remoteSettingsMap == null) {
            remoteSettingsMap = new HashMap<URI, VpoolRemoteCopyProtectionSettings>();
        }
        if (protectionSettingsMap == null) {
            protectionSettingsMap = new HashMap<URI, VpoolProtectionVarraySettings>();
        }
        if (protectionSettingsList == null) {
            protectionSettingsList = new ArrayList<VpoolProtectionVarraySettings>();
        }

        VirtualPool vpool = new VirtualPool();

        vpool.setType(VirtualPool.Type.block.name());
        // set common VirtualPool parameters.
        populateCommonVirtualPoolCreateParams(vpool, param);

        // By default, mirrors and snaps are disabled
        vpool.setMaxNativeContinuousCopies(VirtualPool.MAX_DISABLED);
        vpool.setMaxNativeSnapshots(VirtualPool.MAX_DISABLED);

        if (param.getThinVolumePreAllocationPercentage() != null) {
            vpool.setThinVolumePreAllocationPercentage(param.getThinVolumePreAllocationPercentage());
        }

        if (param.getMultiVolumeConsistency() != null) {
            vpool.setMultivolumeConsistency(param.getMultiVolumeConsistency());
        }

        StringSetMap arrayInfo = new StringSetMap();
        if (null != param.getRaidLevels()) {
            for (String raidLevel : param.getRaidLevels()) {
                arrayInfo.put(VirtualPoolCapabilityValuesWrapper.RAID_LEVEL, raidLevel);
            }
        }
        if (null != param.getSystemType()) {
            arrayInfo.put(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE, param.getSystemType());
        }

        if (!arrayInfo.isEmpty()) {
            vpool.addArrayInfoDetails(arrayInfo);
        }

        if (param.getProtection() != null) {
            if (param.getProtection().getContinuousCopies() != null) {
                URI ccVpoolURI = param.getProtection().getContinuousCopies().getVpool();
                if (!NullColumnValueGetter.isNullURI(ccVpoolURI)) {
                    URI vpoolUri = param.getProtection().getContinuousCopies().getVpool();
                    ArgValidator.checkUri(vpoolUri);
                    VirtualPool protectionMirrorVpool = _permissionsHelper.getObjectById(vpoolUri,
                            VirtualPool.class);
                    ArgValidator.checkEntity(protectionMirrorVpool, vpoolUri, false);
                    if (param.getHighAvailability() != null) {
                        validateMirrorVpool(param.getHighAvailability().getType(), protectionMirrorVpool);
                    } else {
                        validateMirrorVpool(null, protectionMirrorVpool);
                    }
                    vpool.setMirrorVirtualPool(vpoolUri.toString());
                }
            }

            if ((param.getProtection().getSnapshots() != null)
                    && (param.getProtection().getSnapshots().getMaxSnapshots() != null)) {
                vpool.setMaxNativeSnapshots(param.getProtection().getSnapshots().getMaxSnapshots());
            }

            if ((param.getProtection().getContinuousCopies() != null)
                    && (param.getProtection().getContinuousCopies().getMaxMirrors() != null)) {
                if (param.getHighAvailability() != null) {
                    validateMaxNativeContinuousCopies(param.getProtection().getContinuousCopies().getMaxMirrors(), param
                            .getHighAvailability().getType());
                    if (param.getProtection().getContinuousCopies().getVpool() == null
                            && VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(param.getHighAvailability().getType())
                            && (param.getProtection().getContinuousCopies().getMaxMirrors() > 0)) {
                        throw APIException.badRequests.invalidMirrorVpoolForVplexDistributedVpool();
                    }
                }
                vpool.setMaxNativeContinuousCopies(param.getProtection().getContinuousCopies().getMaxMirrors());
            }

            // Special protection-based per-copy information. Which VirtualPool
            // to use for that copy,
            // what RP policies to use, what journal sizing strategy to
            // employ...
            if ((param.getProtection().getRecoverPoint() != null) && (param.getProtection().getRecoverPoint().getCopies() != null)) {
                ProtectionSourcePolicy sourcePolicy = param.getProtection().getRecoverPoint().getSourcePolicy();
                if (sourcePolicy != null) {
                    vpool.setJournalSize(sourcePolicy.getJournalSize());
                    vpool.setRpRpoValue(sourcePolicy.getRpoValue());
                    vpool.setRpRpoType(sourcePolicy.getRpoType());
                    vpool.setRpCopyMode(sourcePolicy.getRemoteCopyMode());
                    if (!NullColumnValueGetter.isNullURI(sourcePolicy.getJournalVarray())) {
                        vpool.setJournalVarray(sourcePolicy.getJournalVarray().toString());

                        if (!NullColumnValueGetter.isNullURI(sourcePolicy.getJournalVpool())) {
                            vpool.setJournalVpool(sourcePolicy.getJournalVpool().toString());
                        } else {
                            String journalVpoolId = NullColumnValueGetter.getNullStr();

                            if (param.getHighAvailability() == null || Boolean.TRUE.equals(param.getHighAvailability().getMetroPoint())) {
                                // In cases of MetroPoint or when high availability is not specified, default the journal virtual pool
                                // to the parent virtual pool
                                journalVpoolId = vpool.getId().toString();
                            } else if (Boolean.FALSE.equals(param.getHighAvailability().getMetroPoint())
                                    && param.getHighAvailability().getHaVirtualArrayVirtualPool() != null
                                    && Boolean.TRUE.equals(param.getHighAvailability().getHaVirtualArrayVirtualPool()
                                            .getActiveProtectionAtHASite())) {
                                // If active protection at HA site is specified (not MetroPoint), our default ha journal vpool should be the
                                // HA virtual pool.
                                if (param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool() != null) {
                                    journalVpoolId = param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool()
                                            .toString();
                                }
                            } else {
                                // In cases of MetroPoint or when high availability is not specified, default the journal virtual pool
                                // to the parent virtual pool
                                journalVpoolId = vpool.getId().toString();
                            }

                            vpool.setJournalVpool(journalVpoolId);
                        }
                    }

                    if (param.getHighAvailability() != null && param.getHighAvailability().getMetroPoint() != null
                            && param.getHighAvailability().getMetroPoint()) {
                        if (!NullColumnValueGetter.isNullURI(sourcePolicy.getStandbyJournalVarray())) {
                            vpool.setStandbyJournalVarray(sourcePolicy.getStandbyJournalVarray().toString());

                            if (!NullColumnValueGetter.isNullURI(sourcePolicy.getStandbyJournalVpool())) {
                                vpool.setStandbyJournalVpool(sourcePolicy.getStandbyJournalVpool().toString());
                            }
                        }
                    }
                }

                StringMap settingsMap = new StringMap();
                for (VirtualPoolProtectionVirtualArraySettingsParam settingsParam : param.getProtection().getRecoverPoint().getCopies()) {
                    VpoolProtectionVarraySettings setting = new VpoolProtectionVarraySettings();
                    setting.setId(URIUtil.createId(VpoolProtectionVarraySettings.class));
                    setting.setParent(new NamedURI(vpool.getId(), vpool.getLabel()));

                    if (settingsParam.getVpool() != null && !String.valueOf(settingsParam.getVpool()).isEmpty()) {
                        setting.setVirtualPool(settingsParam.getVpool());
                    }

                    if (settingsParam.getCopyPolicy() != null) {
                        setting.setJournalSize(settingsParam.getCopyPolicy().getJournalSize() != null ? settingsParam.getCopyPolicy()
                                .getJournalSize() : null);
                        setting.setJournalVarray(settingsParam.getCopyPolicy().getJournalVarray() != null ? settingsParam.getCopyPolicy()
                                .getJournalVarray() : settingsParam.getVarray());
                        setting.setJournalVpool(settingsParam.getCopyPolicy().getJournalVpool() != null ? settingsParam.getCopyPolicy()
                                .getJournalVpool() : settingsParam.getVpool());
                    }
                    if (settingsParam.getVarray() != null) {
                        settingsMap.put(settingsParam.getVarray().toString(), setting.getId().toString());
                        protectionSettingsMap.put(settingsParam.getVarray(), setting);
                    }
                    protectionSettingsList.add(setting);
                }
                vpool.setProtectionVarraySettings(settingsMap);
            }

            // SRDF remote protection Settings
            if (null != param.getProtection().getRemoteCopies() && null != param.getProtection().getRemoteCopies().getRemoteCopySettings()) {
                StringMap remoteCopysettingsMap = new StringMap();
                for (VirtualPoolRemoteProtectionVirtualArraySettingsParam remoteSettings : param.getProtection().getRemoteCopies()
                        .getRemoteCopySettings()) {
                    VirtualArray remoteVArray = _dbClient.queryObject(VirtualArray.class, remoteSettings.getVarray());
                    if (null == remoteVArray || remoteVArray.getInactive()) {
                        throw APIException.badRequests.inactiveRemoteVArrayDetected(remoteSettings.getVarray());
                    }
                    VpoolRemoteCopyProtectionSettings remoteCopySettingsParam = new VpoolRemoteCopyProtectionSettings();
                    remoteCopySettingsParam.setId(URIUtil.createId(VpoolRemoteCopyProtectionSettings.class));
                    remoteCopySettingsParam.setVirtualArray(remoteSettings.getVarray());
                    if (remoteCopysettingsMap.containsKey(remoteSettings.getVarray().toString())) {
                        throw APIException.badRequests.duplicateRemoteSettingsDetected(remoteSettings.getVarray());

                    }
                    remoteCopysettingsMap.put(remoteSettings.getVarray().toString(), remoteCopySettingsParam.getId().toString());

                    // The remote virtual pool is an optional field. If it is not set, this value will be null and the source
                    // virtual pool will be used to provision the target storage for that remote copy.
                    remoteCopySettingsParam.setVirtualPool(remoteSettings.getVpool());
                    if (null != remoteSettings.getRemoteCopyMode()) {
                        if (!CopyModes.lookup(remoteSettings.getRemoteCopyMode())) {
                            throw APIException.badRequests.invalidCopyMode(remoteSettings.getRemoteCopyMode());
                        }
                        remoteCopySettingsParam.setCopyMode(remoteSettings.getRemoteCopyMode());
                    }
                    remoteSettingsMap.put(remoteSettings.getVarray(), remoteCopySettingsParam);
                }
                vpool.setProtectionRemoteCopySettings(remoteCopysettingsMap);
            }
        }

        // Validate and set high availability.
        if (param.getHighAvailability() != null) {
            _log.debug("Vpool specifies high availability {}", param.getHighAvailability());
            // High availability type must be specified and valid.
            vpool.setHighAvailability(param.getHighAvailability().getType());

            if (!VirtualPool.vPoolSpecifiesHighAvailability(vpool)) {
                throw APIException.badRequests.requiredParameterMissingOrEmpty("highAvailability.type");
            }

            // Set the MetroPoint value.
            vpool.setMetroPoint(param.getHighAvailability().getMetroPoint());
            // Auto cross-connect export can work for either local or distributed volumes
            // Default if no parameter is supplied is enabled
            if (param.getHighAvailability().getAutoCrossConnectExport() != null) {
                vpool.setAutoCrossConnectExport(param.getHighAvailability().getAutoCrossConnectExport());
            }

            // If the high availability type is distributed, then the user
            // must also specify the high availability varray. The
            // user may also specify the high availability VirtualPool.
            if (VirtualPool.HighAvailabilityType.vplex_distributed.name().equals(
                    param.getHighAvailability().getType())) {

                if (param.getHighAvailability().getHaVirtualArrayVirtualPool() == null
                        || param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray() == null) {
                    throw APIException.badRequests.invalidParameterVirtualPoolHighAvailabilityMismatch(param.getHighAvailability()
                            .getType());
                }

                // High availability varray must be specified and valid.
                _log.debug("HA varray VirtualPool map specifies the HA varray {}",
                        param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray());
                VirtualArray haVarray = _dbClient.queryObject(VirtualArray.class,
                        param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray());
                ArgValidator.checkEntity(haVarray,
                        param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray(), false);
                String haVarrayId = param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualArray()
                        .toString();

                // Check the HA varray VirtualPool, which is not required.
                String haVarrayVpoolId = null;
                if ((param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool() != null) &&
                        (!param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool().toString().isEmpty())) {
                    _log.debug("HA varray VirtualPool map specifies the HA vpool {}",
                            param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool());
                    VirtualPool haVpool = _dbClient.queryObject(VirtualPool.class,
                            param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool());
                    ArgValidator.checkEntity(haVpool,
                            param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool(), false);
                    haVarrayVpoolId = param.getHighAvailability().getHaVirtualArrayVirtualPool().getVirtualPool()
                            .toString();

                    // Further validate that this VirtualPool is valid for the
                    // specified high availability varray.
                    StringSet haVpoolVarrays = haVpool.getVirtualArrays();
                    if ((haVpoolVarrays != null) && (!haVpoolVarrays.isEmpty()) && !haVpoolVarrays.contains(haVarrayId)) {
                        throw APIException.badRequests.invalidParameterVirtualPoolNotValidForArray(haVarrayVpoolId, haVarrayId);
                    }
                } else {
                    _log.debug("HA varray VirtualPool map does not specify HA vpool");
                    haVarrayVpoolId = NullColumnValueGetter.getNullURI().toString();
                }

                // Not required, but this flag can be set to specify that the HA varray
                // should be used as the RP source array for volume create.
                if (param.getHighAvailability().getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite() != null
                        && param.getHighAvailability().getHaVirtualArrayVirtualPool().getActiveProtectionAtHASite()) {
                    vpool.setHaVarrayConnectedToRp(haVarrayId);
                }

                StringMap haVarrayVirtualPoolMap = new StringMap();
                haVarrayVirtualPoolMap.put(haVarrayId, haVarrayVpoolId);
                vpool.setHaVarrayVpoolMap(haVarrayVirtualPoolMap);
            } else {
                final VirtualArrayVirtualPoolMapEntry haVirtualArrayVirtualPool = param.getHighAvailability()
                        .getHaVirtualArrayVirtualPool();
                if ((haVirtualArrayVirtualPool != null)
                        && ((haVirtualArrayVirtualPool.getVirtualArray() != null) || (haVirtualArrayVirtualPool.getVirtualPool() != null))) {
                    throw APIException.badRequests.invalidParameterVirtualPoolAndVirtualArrayNotApplicableForHighAvailabilityType(param
                            .getHighAvailability().getType());
                }
            }
        }

        // Set expandable
        if (param.getExpandable() != null) {
            vpool.setExpandable(param.getExpandable());
        }
        if (param.getFastExpansion() != null) {
            vpool.setFastExpansion(param.getFastExpansion());
        }

        if (null != param.getAutoTieringPolicyName() && !param.getAutoTieringPolicyName().isEmpty()) {
            vpool.setAutoTierPolicyName(param.getAutoTieringPolicyName());
        }
        if (null != param.getDriveType()) {
            vpool.setDriveType(param.getDriveType());
        }

        // Set the min/max paths an paths per initiator
        validateAndSetPathParams(vpool, param.getMaxPaths(), param.getMinPaths(), param.getPathsPerInitiator());

        if (null != param.getUniquePolicyNames()) {
            vpool.setUniquePolicyNames(param.getUniquePolicyNames());
        }

        // set limit for host bandwidth
        if (param.getHostIOLimitBandwidth() != null) {
            vpool.setHostIOLimitBandwidth(param.getHostIOLimitBandwidth());
        }

        // set limit for host i/o
        if (param.getHostIOLimitIOPs() != null) {
            vpool.setHostIOLimitIOPs(param.getHostIOLimitIOPs());
        }

        return vpool;
    }
}
