/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.toVirtualPoolResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.utils.CapacityUtils;
import com.emc.storageos.api.service.impl.resource.utils.GeoVisibilityHelper;
import com.emc.storageos.api.service.impl.response.FilterIterator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.ProvisioningType;
import com.emc.storageos.db.client.model.VirtualPool.Type;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.quota.QuotaInfo;
import com.emc.storageos.model.quota.QuotaUpdateParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.model.vpool.ProtocolChanges;
import com.emc.storageos.model.vpool.StoragePoolAssignmentChanges;
import com.emc.storageos.model.vpool.StoragePoolAssignments;
import com.emc.storageos.model.vpool.VirtualPoolCommonParam;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.model.vpool.VirtualPoolPoolUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolUpdateParam;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.smis.srdf.SRDFUtils;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitUnManagedObjectsMatcher;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public abstract class VirtualPoolService extends TaggedResource {

    protected static final String EVENT_SERVICE_TYPE = "VPOOL";
    protected static final String EVENT_SERVICE_SOURCE = "VirtualPoolService";
    protected static final String VPOOL_CREATED_DESCRIPTION = "Virtual Pool Created";
    protected static final String VPOOL_UPDATED_DESCRIPTION = "Virtual Pool Updated";
    protected static final String VPOOL_DELETED_DESCRIPTION = "Virtual Pool Deleted";

    protected static final String VPOOL_PROTOCOL_NFS = "NFS";
    protected static final String VPOOL_PROTOCOL_CIFS = "CIFS";
    protected static final String VPOOL_PROTOCOL_NFSv4 = "NFSv4";
    protected static final String VPOOL_PROTOCOL_FC = "FC";
    protected static final String VPOOL_PROTOCOL_ISCSI = "iSCSI";
    protected static final String VPOOL_PROTOCOL_SCALEIO = "ScaleIO";

    protected static final String VPOOL_PROVISIONING_TYPE = "provisioning_type";
    protected static final String VPOOL_PROTOCOLS = "protocols";
    protected static final String VPOOL_NAME = "name";
    protected static final String VPOOL_DESCRIPTION = "description";

    private static final Logger _log = LoggerFactory.getLogger(VirtualPoolService.class);

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static Set<String> fileProtocols = new HashSet<String>();
    private static Set<String> blockProtocols = new HashSet<String>();
    static {
        // Initialize file type protocols
        fileProtocols.add(VPOOL_PROTOCOL_NFS);
        fileProtocols.add(VPOOL_PROTOCOL_CIFS);
        fileProtocols.add(VPOOL_PROTOCOL_NFSv4);

        // initialize block protocols
        blockProtocols.add(VPOOL_PROTOCOL_FC);
        blockProtocols.add(VPOOL_PROTOCOL_ISCSI);
        blockProtocols.add(VPOOL_PROTOCOL_SCALEIO);
    }

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    protected GeoVisibilityHelper _geoHelper;

    protected AttributeMatcherFramework _matcherFramework;

    /**
     * This method is used to set the common VirtualPool create params in
     * VirtualPool model.
     * 
     * @param vpool : VirtualPool object to populate params.
     * @param param : VirtualPoolCreate params.
     * @throws DatabaseException
     */
    protected void populateCommonVirtualPoolCreateParams(VirtualPool vpool,
            VirtualPoolCommonParam param) throws DatabaseException {
        // Validate the name for not null and non-empty values
        // ArgValidator.checkFieldNotEmpty(param.getName(), VPOOL_NAME);
        if (StringUtils.isNotEmpty(param.getName())) {
            vpool.setLabel(param.getName());
        }
        if (StringUtils.isNotEmpty(param.getDescription())) {
            vpool.setDescription(param.getDescription());
        }

        ArgValidator.checkFieldNotEmpty(param.getProvisionType(), VPOOL_PROVISIONING_TYPE);
        ArgValidator.checkFieldValueFromEnum(param.getProvisionType(), VPOOL_PROVISIONING_TYPE,
                EnumSet.of(ProvisioningType.Thick, ProvisioningType.Thin));

        vpool.setId(URIUtil.createId(VirtualPool.class));
        if (null != param.getProvisionType()) {
            vpool.setSupportedProvisioningType(param.getProvisionType());
        }
        vpool.setMaxNativeSnapshots(0);
        vpool.setProtocols(new StringSet());

        // Validate the protocols for not null and non-empty values
        ArgValidator.checkFieldNotEmpty(param.getProtocols(), VPOOL_PROTOCOLS);
        // Validate the protocols for type of VirtualPool.
        validateVirtualPoolProtocol(vpool.getType(), param.getProtocols());
        vpool.getProtocols().addAll(param.getProtocols());

        // validate and set neighborhoods
        if (param.getVarrays() != null) {
            vpool.setVirtualArrays(new StringSet());
            for (String neighborhood : param.getVarrays()) {
                URI neighborhoodURI = URI.create(neighborhood);
                ArgValidator.checkUri(neighborhoodURI);
                VirtualArray varray = _dbClient.queryObject(VirtualArray.class, neighborhoodURI);
                ArgValidator.checkEntity(varray, neighborhoodURI, isIdEmbeddedInURL(neighborhoodURI));
                vpool.getVirtualArrays().add(neighborhood);
            }
        }
        // Set the useMatchedPools flag.
        vpool.setUseMatchedPools(param.getUseMatchedPools());
    }

    protected void validateVirtualPoolProtocol(String type, Set<String> protocols) {
        if (null != protocols && !protocols.isEmpty()) {
            // Validate the protocols for type of VirtualPool.
            switch (VirtualPool.Type.lookup(type)) {
                case file:
                    if (!fileProtocols.containsAll(protocols)) {
                        throw APIException.badRequests.invalidProtocolsForVirtualPool(type, protocols, VPOOL_PROTOCOL_NFS,
                                VPOOL_PROTOCOL_CIFS, VPOOL_PROTOCOL_NFSv4);
                    }
                    break;
                case block:
                    if (!blockProtocols.containsAll(protocols)) {
                        throw APIException.badRequests.invalidProtocolsForVirtualPool(type, protocols, VPOOL_PROTOCOL_FC,
                                VPOOL_PROTOCOL_ISCSI, VPOOL_PROTOCOL_SCALEIO);
                    }
                default:
                    break;
            }
        }
    }

    /**
     * This method is responsible to populate common VirtualPoolUpdateParams.
     * 
     * @param vpool : id of VirtualPool to update.
     * @param param : VirtualPoolParam to update.
     */
    protected void populateCommonVirtualPoolUpdateParams(VirtualPool vpool,
            VirtualPoolUpdateParam param) {

        if (param.getName() != null && !param.getName().isEmpty()) {
            if (!param.getName().equalsIgnoreCase(vpool.getLabel())) {
                checkForDuplicateName(param.getName(), VirtualPool.class);
            }
            vpool.setLabel(param.getName());
        }

        ArgValidator.checkFieldValueWithExpected(!VirtualPool.ProvisioningType.NONE.name()
                .equalsIgnoreCase(param.getProvisionType()), VPOOL_PROVISIONING_TYPE, param.getProvisionType(),
                VirtualPool.ProvisioningType.Thick, VirtualPool.ProvisioningType.Thin);

        if (null != param.getProtocolChanges()) {
            if (null != param.getProtocolChanges().getAdd()) {
                validateVirtualPoolProtocol(vpool.getType(), param.getProtocolChanges().getAdd().getProtocols());
                vpool.addProtocols(param.getProtocolChanges().getAdd().getProtocols());
            }
            if (null != param.getProtocolChanges().getRemove()) {
                validateVirtualPoolProtocol(vpool.getType(), param.getProtocolChanges().getRemove().getProtocols());
                vpool.removeProtocols(param.getProtocolChanges().getRemove().getProtocols());
            }
            // There should be at least one protocol associated with vPool all the time.
            if (vpool.getProtocols().isEmpty()) {
                throw APIException.badRequests.cannotRemoveAllValues(VPOOL_PROTOCOLS, "vPool");
            }
        }
        if (null != param.getProvisionType()) {
            vpool.setSupportedProvisioningType(param.getProvisionType());
        }
        StringSetMap arrayInfo = vpool.getArrayInfo();
        if (null == arrayInfo) {
            vpool.setArrayInfo(new StringSetMap());
        }

        // Validate that the neighborhoods to be assigned to the storage pool
        // reference existing neighborhoods in the database and add them to
        // the storage pool.
        if (null != param.getVarrayChanges()) {
            VirtualArrayAssignments addedNH = param.getVarrayChanges().getAdd();
            if ((addedNH != null) && (!addedNH.getVarrays().isEmpty())) {
                VirtualArrayService.checkVirtualArrayURIs(addedNH.getVarrays(), _dbClient);
                vpool.addVirtualArrays(addedNH.getVarrays());
            }
            // Validate that the neighborhoods to be unassigned from the storage
            // pool reference existing neighborhoods in the database and remove
            // them from the storage pool.
            VirtualArrayAssignments removedNH = param.getVarrayChanges().getRemove();
            if ((removedNH != null) && (!removedNH.getVarrays().isEmpty())) {
                VirtualArrayService.checkVirtualArrayURIs(removedNH.getVarrays(), _dbClient);
                vpool.removeVirtualArrays(removedNH.getVarrays());
            }
        }

        if (null != param.getDescription()) {
            vpool.setDescription(param.getDescription());
        }

        if (null != param.getUseMatchedPools()) {
            // if changing from matched pools to assigned pools, verify that pools with resources
            // are not removed
            if (!param.getUseMatchedPools() && vpool.getUseMatchedPools()) {
                checkPoolsWithResources(null, vpool, _dbClient);
            }
            vpool.setUseMatchedPools(param.getUseMatchedPools());
        }
    }

    /**
     * Check for the removal of virtual arrays from the vpool.
     * 1) The remaining vpool Virtual arrays' pools should cover the virtual pool's assigned pools
     * OR
     * 2) Virtual array should not have virtual pool resources
     * 
     * @param varrays
     * @param vpool
     */
    protected boolean checkVirtualArraysWithVPoolResources(VirtualArrayAssignmentChanges varrayChanges, VirtualPool vpool) {
        boolean isModified = false;
        if (varrayChanges != null && varrayChanges.getRemove() != null) {
            Set<String> removedVarrays = varrayChanges.getRemove().getVarrays();
            if (vpool.getVirtualArrays() == null || vpool.getVirtualArrays().isEmpty()) {
                return isModified;
            }
            // find out the varrays which will be associated with the vpool after the remove operation
            // and find their storage pools.
            Set<String> vpoolVarrays = (StringSet) vpool.getVirtualArrays().clone();
            vpoolVarrays.removeAll(removedVarrays);
            if (varrayChanges.getAdd() != null && !varrayChanges.getAdd().getVarrays().isEmpty()) {
                vpoolVarrays.addAll(varrayChanges.getAdd().getVarrays());
            }

            Set<String> vpoolPools = getVarrayPools(vpoolVarrays);
            Set<String> removedPools = getVarrayPools(removedVarrays);
            removedPools.removeAll(vpoolPools);

            // If the vpool has assigned pool, then they should be within the remaining varray pools, else error.
            Set<String> vpoolAssignedPools = vpool.getAssignedStoragePools();
            if (!vpool.getUseMatchedPools() && vpoolAssignedPools != null && !vpoolAssignedPools.isEmpty()) {
                for (String poolURI : vpoolAssignedPools) {
                    if (!vpoolPools.contains(poolURI)) {
                        throw APIException.badRequests.cannotRemoveVArrayWithPools(removedVarrays);
                    }
                }
            }
            // Error if the varrays have vpool resources
            Set<String> resourceVArrays = getVArraysWithVPoolResources(vpool, removedVarrays, _dbClient);
            if (!resourceVArrays.isEmpty()) {
                throw APIException.badRequests.cannotRemoveVArrayWithVPoolResources(resourceVArrays);
            }
        }

        return isModified;
    }

    // Get the vpools of the virtual arrays
    private Set<String> getVarrayPools(Set<String> vArrays) {
        Set<String> poolURIs = new StringSet();
        Iterator<String> vArrayItr = vArrays.iterator();
        while (vArrayItr.hasNext()) {
            URIQueryResultList vArrayPoolsQueryResult = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePoolsConstraint(vArrayItr.next()),
                    vArrayPoolsQueryResult);
            Iterator<URI> poolIterator = vArrayPoolsQueryResult.iterator();
            while (poolIterator.hasNext()) {
                poolURIs.add(poolIterator.next().toString());
            }
        }
        return poolURIs;
    }

    /**
     * Check VirtualPoolUpdate has any attributes set.
     * 
     * @param param
     * @return
     */
    protected boolean checkAttributeValuesChanged(VirtualPoolUpdateParam param, VirtualPool vpool) {
        return checkVirtualPoolProtocolsChanged(vpool.getProtocols(), param.getProtocolChanges())
                || VirtualPoolUtil.checkForVirtualPoolAttributeModification(vpool.getSupportedProvisioningType(), param.getProvisionType())
                || VirtualPoolUtil.checkSystemTypeChanged(vpool.getArrayInfo(), param.getSystemType())
                || checkVirtualArraysWithVPoolResources(param.getVarrayChanges(), vpool);
    }

    /**
     * check the if there is any change vpool minPortSpeed attribute.
     * 
     * @param minPortSpeed
     * @param paramMinPortSpeed
     */
    private boolean checkVirtualPoolMinPortSpeed(Integer minPortSpeed, Integer paramMinPortSpeed) {
        boolean isModified = false;
        if (null != minPortSpeed) {
            if (paramMinPortSpeed == null) {
                isModified = false;
            } else if (paramMinPortSpeed != minPortSpeed) {
                isModified = true;
            }
        } else {
            if (null != paramMinPortSpeed) {
                isModified = true;
            }
        }
        return isModified;
    }

    /**
     * Check if there is any change in the vpool protocols attribute.
     * 
     * @param protocols
     * @param protocolChanges
     * @return true if all passed URIs reference an existing storage pool in the
     *         DB, false otherwise.
     */
    protected static boolean checkVirtualPoolProtocolsChanged(StringSet protocols, ProtocolChanges protocolChanges) {
        boolean isModified = false;
        // If protocols in DB are not null
        if (null != protocols) {
            // Check if we are adding any new protocol
            if (null != protocolChanges && null != protocolChanges.getAdd()) {
                if (Sets.union(protocols, protocolChanges.getAdd().getProtocols()).size() != protocols.size()) {
                    isModified = true;
                }
            }
            // Check if we are removing any existing protocol
            if (null != protocolChanges && null != protocolChanges.getRemove()) {
                if (!Sets.intersection(protocols, protocolChanges.getRemove().getProtocols()).isEmpty()) {
                    isModified = true;
                }
            }
        } else {
            // There are no protocols in db but passed in params have some
            // values.
            if (null != protocolChanges) {
                isModified = true;
            }
        }
        return isModified;
    }

    /**
     * This method allows user to assign matching pools to VirtualPool.
     * 
     * @param id : the URN of a ViPR VirtualPool.
     * @param param : Pool Update param
     * @return : update VirtualPool.
     */
    protected VirtualPool updateVirtualPoolWithAssignedStoragePools(URI id, VirtualPoolPoolUpdateParam param) {
        ArgValidator.checkUri(id);
        VirtualPool vpool = queryResource(id);
        ArgValidator.checkEntity(vpool, id, isIdEmbeddedInURL(id));
        if (param.getStoragePoolAssignmentChanges() != null) {
            // Validate whether all the pools with virtual pool resources are part of the assigned pools
            checkPoolsWithResources(param.getStoragePoolAssignmentChanges(), vpool, _dbClient);

            StoragePoolAssignments addedAssignedPools = param.getStoragePoolAssignmentChanges().getAdd();
            if ((addedAssignedPools != null) && (!addedAssignedPools.getStoragePools().isEmpty())) {
                validateAssignedPoolInMatchedPools(addedAssignedPools.getStoragePools(), vpool);
                vpool.updateAssignedStoragePools(addedAssignedPools.getStoragePools());
            }

            // Validate that the storage pools to be unassigned from the storage
            // pool reference existing storage pools in the database and remove
            // them from the storage pool.
            StoragePoolAssignments removedPool = param.getStoragePoolAssignmentChanges().getRemove();
            if ((removedPool != null) && (!removedPool.getStoragePools().isEmpty())) {
                checkUnassignedPoolURIs(removedPool.getStoragePools(), vpool, _dbClient);
                _log.debug("Removing pools {} from the virtual pool {}", removedPool.getStoragePools(), id);
                vpool.removeAssignedStoragePools(removedPool.getStoragePools());
            }

            // adding supported vpools to unmanaged volumes/file systems
            if (vpool.getType().equals(VirtualPool.Type.file.name())) {
                ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedFileSystems(vpool,
                        _dbClient);
            } else if (vpool.getType().equals(VirtualPool.Type.block.name())) {
                Set<URI> allSrdfTargetVPools = SRDFUtils.fetchSRDFTargetVirtualPools(_dbClient);
                Set<URI> allRpTargetVpools = RPHelper.fetchRPTargetVirtualPools(_dbClient);
                ImplicitUnManagedObjectsMatcher.matchVirtualPoolsWithUnManagedVolumes(vpool, allSrdfTargetVPools, allRpTargetVpools, _dbClient);
            }

            _dbClient.updateAndReindexObject(vpool);
        }

        return vpool;
    }

    /**
     * Validates passed in unassigned pool uris are in the assigned pools or
     * not. If it is in the assigned matched pools then we can allow the user to
     * delete else throw an exception.
     * 
     * @param unAssignedPoolURIs The set of storage pools URIs to validate
     * @param vpool
     * @param dbClient A reference to a DB client.
     * @return true if all passed URIs reference an existing storage pool in the
     *         DB, false otherwise.
     */
    public static void checkUnassignedPoolURIs(Set<String> unAssignedPoolURIs, VirtualPool vpool, DbClient dbClient) {
        Set<String> badURIs = new HashSet<String>();
        StringSet assignedPoolsInDB = vpool.getAssignedStoragePools();
        for (Iterator<String> assignedPoolURIsIter = unAssignedPoolURIs.iterator(); assignedPoolURIsIter.hasNext();) {
            String poolURI = assignedPoolURIsIter.next().toString();
            if (null != assignedPoolsInDB && !assignedPoolsInDB.contains(poolURI)) {
                badURIs.add(poolURI);
                break;
            }
        }

        if (!badURIs.isEmpty()) {
            throw APIException.badRequests.theURIsOfParametersAreNotValid("pools", badURIs);
        }
    }

    /**
     * Check whether the pools with vpool resources are part of the assigned pools.
     * We should not allow removal of pools with resources.
     * 
     * @param poolAssignmentChanges
     * @param vpool
     * @param dbClient
     */
    public void checkPoolsWithResources(StoragePoolAssignmentChanges poolAssignmentChanges, VirtualPool vpool, DbClient dbClient) {
        Set<String> poolsToCheck = new StringSet();
        // Find the pools which need to be checked for resources.
        if (vpool.getMatchedStoragePools() == null) {
            throw APIException.badRequests.invalidParameterNoMatchingPoolsExistToAssignPools(vpool.getId());
        }
        Set<String> vPoolMatchedPools = (StringSet) vpool.getMatchedStoragePools().clone();
        if (vpool.getAssignedStoragePools() != null && !vpool.getAssignedStoragePools().isEmpty()) {
            vPoolMatchedPools.removeAll(vpool.getAssignedStoragePools());
        }

        if (poolAssignmentChanges != null && poolAssignmentChanges.getAdd() != null) {
            if (poolAssignmentChanges.getAdd().getStoragePools() != null && !poolAssignmentChanges.getAdd().getStoragePools().isEmpty()) {
                vPoolMatchedPools.removeAll(poolAssignmentChanges.getAdd().getStoragePools());
            }
        }

        poolsToCheck.addAll(vPoolMatchedPools);

        if (poolAssignmentChanges != null && poolAssignmentChanges.getRemove() != null) {
            if (poolAssignmentChanges.getRemove().getStoragePools() != null
                    && !poolAssignmentChanges.getRemove().getStoragePools().isEmpty()) {
                poolsToCheck.addAll(poolAssignmentChanges.getRemove().getStoragePools());
            }
        }
        Set<String> resourcePools = getPoolsWithVPoolResources(vpool, poolsToCheck, dbClient);

        if (!resourcePools.isEmpty()) {
            Set<String> poolNames = new StringSet();
            for (String poolUri : resourcePools) {
                StoragePool pool = dbClient.queryObject(StoragePool.class, URI.create(poolUri));
                poolNames.add(pool.getPoolName());
            }
            throw APIException.badRequests.cannotRemovePoolWithResources(poolNames);
        }
    }

    /**
     * Calculate the storage pools which have the vpool resources.
     * 1) Get list of vpool resources
     * 2) For each of the vpool's matched pool, get the pool resources
     * 3) If there is resource in storage pool which is also in the vpool resource list, add to the pool list
     * 
     * @param vpool
     * @param dbClient
     * @return List of storage pools with vpool resources.
     */
    public static Set<String> getPoolsWithVPoolResources(VirtualPool vpool, Set<String> pools, DbClient dbClient) {
        Set<String> resourcePools = new StringSet();
        _log.debug("Getting the storage pools with resources of virtual pool {}.", vpool.getLabel());

        if (null != pools && !pools.isEmpty()) {
            Iterator<String> poolItr = pools.iterator();
            while (poolItr.hasNext()) {
                String matchedPool = poolItr.next();
                URI poolURI = URI.create(matchedPool);
                URIQueryResultList poolResourcesResultList = new URIQueryResultList();
                URIQueryResultList vpoolResourcesResultList = new URIQueryResultList();
                if (VirtualPool.Type.block.name().equals(vpool.getType())) {
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getStoragePoolVolumeConstraint(poolURI), poolResourcesResultList);
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getVirtualPoolVolumeConstraint(vpool.getId()), vpoolResourcesResultList);
                } else if (VirtualPool.Type.file.name().equals(vpool.getType())) {
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getStoragePoolFileshareConstraint(poolURI), poolResourcesResultList);
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getVirtualPoolFileshareConstraint(vpool.getId()), vpoolResourcesResultList);
                } else if (VirtualPool.Type.object.name().equals(vpool.getType())) {
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getStoragePoolBucketConstraint(poolURI), poolResourcesResultList);
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getVirtualPoolBucketConstraint(vpool.getId()), vpoolResourcesResultList);
                }

                // Create a set of vpoolResourcesResultList
                HashSet<URI> vpoolResourceSet = new HashSet<URI>();
                for (URI vpoolResource : vpoolResourcesResultList) {
                    vpoolResourceSet.add(vpoolResource);
                }

                // Now look up if there are pool resources in the vpool resources set.
                for (URI poolResource : poolResourcesResultList) {
                    if (vpoolResourceSet.contains(poolResource)) {
                        boolean inactive = false;
                        if (VirtualPool.Type.block.name().equals(vpool.getType())) {
                            Volume resource = dbClient.queryObject(Volume.class, poolResource);
                            inactive = resource.getInactive();
                        } else if (VirtualPool.Type.file.name().equals(vpool.getType())) {
                            FileShare resource = dbClient.queryObject(FileShare.class, poolResource);
                            inactive = resource.getInactive();
                        }
                        if (!inactive) {
                            _log.info("Found vpool resource {} in the storage pool {}", poolResource, matchedPool);
                            resourcePools.add(matchedPool);
                            break;
                        }
                    }
                }

            }
        }

        return resourcePools;
    }

    /**
     * Calculate the virtual arrays which have the vpool resources.
     * 1) Get list of vpool resources
     * 2) Get the resources for each of the passed in virtual array
     * 3) If there is resource in virtual array which is also in the vpool resource list, add to the virtual array list
     * 
     * @param vpool
     * @param varrays
     * @param dbClient
     * @return List of virtual arrays with vpool resources.
     */
    public static Set<String> getVArraysWithVPoolResources(VirtualPool vpool, Set<String> varrays, DbClient dbClient) {
        Set<String> resourcePools = new StringSet();
        _log.debug("Getting the virtual arrays with resources of virtual pool {}.", vpool.getLabel());

        if (null != varrays && !varrays.isEmpty()) {
            Iterator<String> varrayItr = varrays.iterator();
            while (varrayItr.hasNext()) {
                String varray = varrayItr.next();
                URI varrayURI = URI.create(varray);
                URIQueryResultList varrayResourcesResultList = new URIQueryResultList();
                URIQueryResultList vpoolResourcesResultList = new URIQueryResultList();
                if (VirtualPool.Type.block.name().equals(vpool.getType())) {
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getVirtualArrayVolumeConstraint(varrayURI), varrayResourcesResultList);
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getVirtualPoolVolumeConstraint(vpool.getId()), vpoolResourcesResultList);
                } else if (VirtualPool.Type.file.name().equals(vpool.getType())) {
                    dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVirtualArrayFileSharesConstraint(varrayURI.toString()),
                            varrayResourcesResultList);
                    dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getVirtualPoolFileshareConstraint(vpool.getId()), vpoolResourcesResultList);
                } else if (VirtualPool.Type.object.name().equals(vpool.getType())) {
                    dbClient.queryByConstraint(ContainmentConstraint.Factory.getVirtualArrayBucketsConstraint(varrayURI),
                            varrayResourcesResultList);
                    dbClient.queryByConstraint(ContainmentConstraint.Factory.getVirtualPoolBucketConstraint(vpool.getId()),
                            vpoolResourcesResultList);
                }

                // Create a set of vpoolResourcesResultList
                HashSet<URI> vpoolResourceSet = new HashSet<URI>();
                for (URI vpoolResource : vpoolResourcesResultList) {
                    vpoolResourceSet.add(vpoolResource);
                }

                // Now look up if there are varray resources in the vpool resources set.
                for (URI varrayResource : varrayResourcesResultList) {
                    if (vpoolResourceSet.contains(varrayResource)) {
                        boolean inactive = false;
                        if (VirtualPool.Type.block.name().equals(vpool.getType())) {
                            Volume resource = dbClient.queryObject(Volume.class, varrayResource);
                            inactive = resource.getInactive();
                        } else if (VirtualPool.Type.file.name().equals(vpool.getType())) {
                            FileShare resource = dbClient.queryObject(FileShare.class, varrayResource);
                            inactive = resource.getInactive();
                        } else if (VirtualPool.Type.object.name().equals(vpool.getType())) {
                            Bucket resource = dbClient.queryObject(Bucket.class, varrayResource);
                            inactive = resource.getInactive();
                        }
                        if (!inactive) {
                            _log.info("Found vpool resource {} in the varray {}", varrayResource, varray);
                            resourcePools.add(varray);
                            break;
                        }
                    }
                }

            }
        }

        return resourcePools;
    }

    /**
     * Verify whether the assigned pool is in matched pools or not. If it is in
     * matched pool list, then add it to VirtualPool Else throw exception.
     * 
     * @param assignedPoolList
     * @param vpool
     */
    public void validateAssignedPoolInMatchedPools(Set<String> assignedPoolList, VirtualPool vpool) {
        for (Iterator<String> assignedPoolItr = assignedPoolList.iterator(); assignedPoolItr.hasNext();) {
            String poolStr = assignedPoolItr.next();
            StoragePool pool = _dbClient.queryObject(StoragePool.class, URI.create(poolStr));
            ArgValidator.checkFieldNotNull(pool, "pool");
            if (vpool.getMatchedStoragePools() == null) {
                throw APIException.badRequests.invalidParameterNoMatchingPoolsExistToAssignPools(vpool.getId());
            }
            if (!vpool.getMatchedStoragePools().contains(poolStr)) {
                throw APIException.badRequests.invalidParameterAssignedPoolNotInMatchedPools(poolStr);
            }
        }
    }

    protected VirtualPoolList getVirtualPoolList(VirtualPool.Type type, String shortVdcId) {

        URIQueryResultList vpoolList = new URIQueryResultList();
        VirtualPoolList list = new VirtualPoolList();
        StorageOSUser user = getUserFromContext();

        List<VirtualPool> vpoolObjects = null;

        if (_geoHelper.isLocalVdcId(shortVdcId)) {
            _log.debug("retrieving virtual pools via the dbclient");
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVpoolTypeVpoolConstraint(type), vpoolList);
            List<URI> allowed = new ArrayList<URI>();
            for (URI vpool : vpoolList) {
                allowed.add(vpool);
            }
            vpoolObjects = _dbClient.queryObject(VirtualPool.class, allowed);

        } else {
            _log.debug("retrieving virtual pools via the geoclient");
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                // TODO: query by constraint isn't working on the geosvc
                // List<URI> resultList = geoClient.queryByConstraint(AlternateIdConstraint.Factory.getVpoolTypeVpoolConstraint(type),
                // URIQueryResultList.class);
                Iterator<URI> uriIter = geoClient.queryByType(VirtualPool.class, true);
                List<URI> resultList = Lists.newArrayList(uriIter);
                Iterator<VirtualPool> iter = geoClient.queryObjects(VirtualPool.class, resultList);
                vpoolObjects = Lists.newArrayList(); // iter);
                while (iter.hasNext()) {
                    VirtualPool p = iter.next();
                    if (type.toString().equals(p.getType())) {
                        vpoolObjects.add(p);
                    }
                }
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote pools", ex);
            }
        }

        URI tenant = URI.create(user.getTenantId());
        for (VirtualPool vpool : vpoolObjects) {
            if (!_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
                // filter by only authorized to us
                if (_permissionsHelper.tenantHasUsageACL(tenant, vpool)) {
                    // this is an allowed VirtualPool, add it to the list
                    list.getVirtualPool().add(toVirtualPoolResource(vpool));
                }
            } else {
                list.getVirtualPool().add(toVirtualPoolResource(vpool));
            }
        }

        return list;
    }

    protected VirtualPool getVirtualPool(VirtualPool.Type type, URI id) {
        ArgValidator.checkUri(id);
        VirtualPool vpool = null;
        if (_geoHelper.isLocalURI(id)) {
            _log.debug("retrieving vpool via dbclient");
            vpool = _permissionsHelper.getObjectById(id, VirtualPool.class);
        } else {
            _log.debug("retrieving vpool via geoclient");
            String shortVdcId = VdcUtil.getVdcId(VirtualPool.class, id).toString();
            // TODO: do we want to leverage caching like the native lookup
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                vpool = geoClient.queryObject(VirtualPool.class, id);
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving virtual pools from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote pool", ex);
            }
        }
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));
        if (!vpool.getType().equals(type.name())) {
            throw APIException.badRequests.invalidParameterNoSuchVirtualPoolOfType(id, type.toString());
        }
        return vpool;
    }

    protected Response deleteVirtualPool(VirtualPool.Type type, URI id) {
        ArgValidator.checkUri(id);
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, id);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));
        if (!vpool.getType().equals(type.name())) {
            throw APIException.badRequests.providedVirtualPoolNotCorrectType();
        }

        // make sure vpool is unused by volumes/fileshares
        ArgValidator.checkReference(VirtualPool.class, id, checkForDelete(vpool));

        // Check if vpool is set as a continuous copies vpool
        checkIfVpoolIsSetAsContinuousCopiesVpool(vpool);

        // Additional check for VirtualPool that may be hidden in another VirtualPool via the
        // protection settings
        URIQueryResultList settingsResultList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getVpoolProtectionVarraySettingsConstraint(id.toString()),
                settingsResultList);
        Iterator<URI> settingsListItr = settingsResultList.iterator();
        while (settingsListItr.hasNext()) {
            final URI settingId = settingsListItr.next();
            VpoolProtectionVarraySettings setting = _dbClient.queryObject(VpoolProtectionVarraySettings.class, settingId);
            ArgValidator.checkEntity(setting, settingId, isIdEmbeddedInURL(settingId));
        }

        if (vpool.getProtectionVarraySettings() != null) {
            // Delete all settings associated with the protection settings
            deleteVPoolProtectionVArraySettings(vpool);
        }

        // We also check to see if this virtual pool is specified as the HA virtual pool
        // for some other virtual pool that specifies VPLEX distributed high availability.
        // If this is the case, we disallow the deletion.
        List<URI> vpoolURIs = _dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> vpoolsIter = _dbClient.queryIterativeObjects(VirtualPool.class, vpoolURIs);
        while (vpoolsIter.hasNext()) {
            VirtualPool activeVPool = vpoolsIter.next();
            if (!activeVPool.getId().equals(id)) {
                StringMap haMap = activeVPool.getHaVarrayVpoolMap();
                if ((haMap != null) && (!haMap.isEmpty()) && (haMap.values().contains(id.toString()))) {
                    // There is an active vpool that specifies the vpool being
                    // deleted as the VPLEX HA vpool, so deleting this vpool
                    // is not allowed.
                    throw APIException.badRequests.cantDeleteVPlexHaVPool(activeVPool.getLabel());
                }
            }
        }

        _dbClient.markForDeletion(vpool);

        recordOperation(OperationTypeEnum.DELETE_VPOOL, VPOOL_DELETED_DESCRIPTION, vpool);
        return Response.ok().build();
    }

    /**
     * Deletes all VpoolProtectionVarraySettings objects associated with a VirtualPool.
     * 
     * @param vpool the VirtualPool from which to delete the
     *            VpoolProtectionVarraySettings.
     */
    protected void deleteVPoolProtectionVArraySettings(VirtualPool vpool) {
        // Delete all settings associated with the protection settings
        if (VirtualPool.vPoolSpecifiesProtection(vpool)) {
            for (String protectionVirtualArray : vpool.getProtectionVarraySettings().keySet()) {
                deleteVPoolProtectionVArraySettings(vpool.getProtectionVarraySettings().get(protectionVirtualArray));
            }

            vpool.getProtectionVarraySettings().clear();
        }
    }

    /**
     * Deletes a <code>VpoolProtectionVarraySettings</code> object from
     * the database.
     * 
     * @param uri the URI representing the <code>VpoolProtectionVarraySettings</code> to delete
     */
    protected void deleteVPoolProtectionVArraySettings(String uri) {
        if (uri != null && !uri.isEmpty()) {
            URI settingURI = URI.create(uri);
            ArgValidator.checkUri(settingURI);

            VpoolProtectionVarraySettings setting = _dbClient.queryObject(VpoolProtectionVarraySettings.class, settingURI);
            if (setting == null) {
                throw APIException.badRequests.unableToFindEntity(settingURI);
            }

            // Mark the VpoolProtectionVarraySettings for deletion
            _dbClient.markForDeletion(setting);
        }
    }

    @Override
    protected VirtualPool queryResource(URI id) {
        VirtualPool vpool = _permissionsHelper.getObjectById(id, VirtualPool.class);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));
        return vpool;
    }

    protected ACLAssignments getAclsOnVirtualPool(VirtualPool.Type type, URI id) {
        VirtualPool vpool = queryResource(id);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));
        if (!vpool.getType().equals(type.name())) {
            throw APIException.badRequests.providedVirtualPoolNotCorrectType();
        }
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(vpool.getAcls()));
        return response;
    }

    protected ACLAssignments updateAclsOnVirtualPool(VirtualPool.Type type, URI id, ACLAssignmentChanges changes) {
        VirtualPool vpool = queryResource(id);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));
        if (!vpool.getType().equals(type.name())) {
            throw APIException.badRequests.providedVirtualPoolNotCorrectType();
        }
        _permissionsHelper.updateACLs(vpool, changes,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper, vpool.getType()));
        _dbClient.updateAndReindexObject(vpool);

        auditOp(OperationTypeEnum.MODIFY_VPOOL_ACL, true, null, vpool.getId().toString(), vpool.getLabel(), vpool.getType());
        return getAclsOnVirtualPool(type, id);
    }

    /**
     * Determines the storage pools the satisfy the VirtualPool with the passed URI.
     * This method returns the pools which are valid means the pools will be
     * used for placement. Pools will be returned based on the
     * useMatchedStoragePools flag. if useMatchedStoragePools = true, returns
     * the matched storage pools. else returns assigned storage pools. after
     * that, we should remove the invalid matched pools from matched/assigned
     * pool if there are any such pools exists. valid Pools =
     * (matchedStoragePools/assignedStoragePools - invalidMatchedPools)
     * 
     * @param id the URN of a ViPR VirtualPool.
     * @return A reference to a VirtualPoolStoragePool, which contains a list of the URIs
     *         of the storage pools that satisfy the VirtualPool.
     */
    protected StoragePoolList getStoragePoolsForVirtualPool(URI id) {
        // Get the vpool with the passed id.
        ArgValidator.checkUri(id);
        StoragePoolList poolList = new StoragePoolList();
        VirtualPool vpool = queryResource(id);
        ArgValidator.checkEntity(vpool, id, isIdEmbeddedInURL(id));
        List<StoragePool> validPools = VirtualPool.getValidStoragePools(vpool, _dbClient, false);

        Iterator<StoragePool> poolIterator = validPools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            poolList.getPools().add(toNamedRelatedResource(pool, pool.getNativeGuid()));
        }
        return poolList;
    }

    /**
     * Refresh the matching pools by running implicit pool matcher algorithm and
     * find if there are any new matched pools exists in the environment.
     * Returns the new matched pools to user.
     * 
     * @param id the URN of a ViPR VirtualPool.
     * @return : list of pools.
     */
    protected StoragePoolList refreshMatchedPools(VirtualPool.Type type, URI id) {
        ArgValidator.checkUri(id);
        StoragePoolList poolList = new StoragePoolList();
        VirtualPool vpool = queryResource(id);
        ArgValidator.checkEntityNotNull(vpool, id, isIdEmbeddedInURL(id));
        if (!vpool.getType().equals(type.name())) {
            throw APIException.badRequests.providedVirtualPoolNotCorrectType();
        }
        ImplicitPoolMatcher.matchVirtualPoolWithAllStoragePools(vpool, _dbClient, _coordinator);
        _dbClient.updateAndReindexObject(vpool);
        StringSet matchedPools = vpool.getMatchedStoragePools();
        if (null != matchedPools && !matchedPools.isEmpty()) {
            Iterator<String> vpoolItr = matchedPools.iterator();
            while (vpoolItr.hasNext()) {
                URI poolURI = URI.create(vpoolItr.next());
                StoragePool pool = _dbClient.queryObject(StoragePool.class, poolURI);
                if (pool == null) {
                    continue;
                }
                poolList.getPools().add(toNamedRelatedResource(pool));
            }
        }
        return poolList;
    }

    public QuotaInfo updateQuota(VirtualPool vpool,
            QuotaUpdateParam param) throws DatabaseException {

        // don't allow quota updates on inactive vpools
        ArgValidator.checkEntity(vpool, vpool.getId(), isIdEmbeddedInURL(vpool.getId()));

        vpool.setQuotaEnabled(param.getEnable());
        if (param.getEnable()) {
            long quota_gb = (param.getQuotaInGb() != null) ? param.getQuotaInGb() : vpool.getQuota();
            ArgValidator.checkFieldMinimum(quota_gb, 0, "quota_gb", "GB");
            vpool.setQuota(quota_gb);
        }
        _dbClient.persistObject(vpool);
        return getQuota(vpool);
    }

    protected QuotaInfo getQuota(VirtualPool vpool) {
        QuotaInfo quotaInfo = new QuotaInfo();
        double capacity = CapacityUtils.getVirtualPoolCapacity(_dbClient, vpool.getId(), VirtualPool.Type.lookup(vpool.getType()));
        quotaInfo.setQuotaInGb(vpool.getQuota());
        quotaInfo.setEnabled(vpool.getQuotaEnabled());
        quotaInfo.setCurrentCapacityInGb((long) Math.ceil(capacity / CapacityUtils.GB));
        quotaInfo.setLimitedResource(DbObjectMapper.toNamedRelatedResource(vpool));
        return quotaInfo;
    }

    /**
     * Returns capacity metrics for a given pair of VirtualPool and Neighborhood. The
     * method returns set of metrics for capacity available for storage
     * provisioning: - usable_gb : total storage capacity - free_gb : free
     * storage capacity - used_gb : used storage capacity - subscribed_gb :
     * subscribed storage capacity (may be larger than usable capacity) -
     * percent_used : percent of usable capacity which is used -
     * percent_subscribed : percent of usable capacity which is subscribed (may
     * be more than 100) Subscribed and percent subscribed is returned only for
     * block vpool.
     * 
     * @param vpool
     * @param vArrayId
     * @return CapacityResponse instance
     */
    protected CapacityResponse getCapacityForVirtualPoolAndVirtualArray(VirtualPool vpool, URI vArrayId) {

        VirtualArray varray = _permissionsHelper.getObjectById(vArrayId, VirtualArray.class);
        ArgValidator.checkEntity(varray, vArrayId, isIdEmbeddedInURL(vArrayId));

        // Check permissions: check that varray is accessible to user's
        // tenant
        final StorageOSUser user = getUserFromContext();
        final URI tenant = URI.create(user.getTenantId());
        if (!(_permissionsHelper.userHasGivenRole(user, null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR) || _permissionsHelper
                .tenantHasUsageACL(tenant, varray))) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        return CapacityUtils.getCapacityForVirtualPoolAndVirtualArray(vpool, vArrayId, _dbClient, _coordinator);
    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param type
     * @param type
     * @param description
     * @param vpool
     */
    private void recordVirtualPoolEvent(String type, String description, URI vpool) {
        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */type,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* VirtualPool */vpool,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */vpool,
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
            _log.error("Failed to record event. Event description: {}. Error: {}.", description, ex);
        }
    }

    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }

    public void recordOperation(OperationTypeEnum opType, String evDesc, Object... extParam) {
        String evType;
        evType = opType.getEvType(true);

        _log.info("opType: {} detail: {}", opType.toString(), evType + ':' + evDesc);

        VirtualPool vpool = (VirtualPool) extParam[0];

        recordVirtualPoolEvent(evType, evDesc, vpool.getId());

        StringBuilder protocols = new StringBuilder();
        if (vpool.getProtocols() != null) {
            for (String proto : vpool.getProtocols()) {
                protocols.append(" ");
                protocols.append(proto);
            }
        }
        StringBuilder neighborhoods = new StringBuilder();
        if (vpool.getVirtualArrays() != null) {
            for (String neighborhood : vpool.getVirtualArrays()) {
                neighborhoods.append(" ");
                neighborhoods.append(neighborhood);
            }
        }

        switch (opType) {
            case CREATE_VPOOL:
                auditOp(opType, true, null, vpool.getId().toString(), vpool.getLabel(), vpool.getType(), protocols.toString(),
                        neighborhoods.toString(), vpool.getSupportedProvisioningType(),
                        vpool.getAutoTierPolicyName(), vpool.getDriveType(), vpool.getHighAvailability());
                break;
            case UPDATE_VPOOL:
                auditOp(opType, true, null, vpool.getId().toString(), vpool.getLabel(), vpool.getType(), protocols.toString(),
                        neighborhoods.toString(), vpool.getSupportedProvisioningType(), vpool.getAutoTierPolicyName(),
                        vpool.getDriveType());
                break;
            case DELETE_VPOOL:
                auditOp(opType, true, null, vpool.getId().toString(), vpool.getLabel(), vpool.getType());
                break;
            default:
                _log.error("unrecognized vpool operation type");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<VirtualPool> getResourceClass() {
        return VirtualPool.class;
    }

    @Override
    protected BulkIdParam queryBulkIds() {

        BulkIdParam ret = new BulkIdParam();
        URIQueryResultList vpoolList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getVpoolTypeVpoolConstraint(getVirtualPoolType()), vpoolList);

        ret.setIds(new ArrayList<URI>());
        for (URI vpool : vpoolList) {
            ret.getIds().add(vpool);
        }
        return ret;
    }

    protected abstract Type getVirtualPoolType();

    /**
     * VirtualPool is not a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return true;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VPOOL;
    }

    public static class VirtualPoolResRepFilter<E extends RelatedResourceRep> extends ResRepFilter<E> {

        public VirtualPoolResRepFilter(StorageOSUser user, PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            URI id = resrep.getId();
            VirtualPool resource = _permissionsHelper.getObjectById(id, VirtualPool.class);
            if (resource == null) {
                return false;
            }
            return isVirtualPoolAccessible(resource);
        }
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new VirtualPoolResRepFilter<SearchResultResourceRep>(user, permissionsHelper);
    }

    /**
     * Gives systemType of the given vPool
     * 
     * @param virtualPool
     * @return {@link String} vpool's systemType
     */
    public static String getSystemType(VirtualPool virtualPool) {
        String systemType = null;

        if (virtualPool != null && virtualPool.getArrayInfo().containsKey(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
            for (String sysType : virtualPool.getArrayInfo().get(VirtualPoolCapabilityValuesWrapper.SYSTEM_TYPE)) {
                systemType = sysType;
                break;
            }
        }

        return systemType;
    }

    @Override
    protected SearchedResRepList getTagSearchResults(String tag, URI tenant) {
        SearchedResRepList list = super.getTagSearchResults(tag, tenant);
        return filterResultsList(getUserFromContext(), list);
    }

    @Override
    protected SearchedResRepList getNamedSearchResults(String name, URI projectId) {
        SearchedResRepList list = super.getNamedSearchResults(name, projectId);
        return filterResultsList(getUserFromContext(), list);
    }

    /**
     * Filters the SearchedResRepList by VirtualPoolType (e.g., block or file)
     * 
     * @param user the current user
     * @param list a list of resource matching the search
     * @return a list filtered by virtual pool type
     */
    protected SearchedResRepList filterResultsList(StorageOSUser user, SearchedResRepList list) {

        SearchedResRepList filteredResRepList = new SearchedResRepList();

        Iterator<SearchResultResourceRep> _queryResultIterator = list.iterator();

        ResRepFilter<SearchResultResourceRep> resrepFilter =
                new VirtualPoolTypeFilter<SearchResultResourceRep>(user,
                        _permissionsHelper, this.getVirtualPoolType());

        filteredResRepList.setResult(
                new FilterIterator<SearchResultResourceRep>(_queryResultIterator, resrepFilter));

        return filteredResRepList;
    }

    /**
     * Filters the SearchedResRepList by VirtualPoolType (e.g., block or file)
     */
    public static class VirtualPoolTypeFilter<E extends RelatedResourceRep> extends ResRepFilter<E> {

        private Type poolType;

        public VirtualPoolTypeFilter(StorageOSUser user, PermissionsHelper permissionsHelper, Type poolType) {
            super(user, permissionsHelper);
            this.poolType = poolType;
        }

        @Override
        public boolean isAccessible(E resrep) {
            URI id = resrep.getId();
            VirtualPool resource = _permissionsHelper.getObjectById(id, VirtualPool.class);

            if (resource.getType().equals(poolType.name())) {
                return true;
            }

            return false;
        }
    }

    /**
     * This method checks if the passed vpool is set as the continuous copies vpool for any of the vpool.
     * If yes it throws an exception with virtual pool names where it is used as continuous copies vpool.
     * 
     * @param vpool The reference to Virtual Pool
     */
    public void checkIfVpoolIsSetAsContinuousCopiesVpool(VirtualPool vpool) {
        String virtualPoolNames = VirtualPool.isContinuousCopiesVpool(vpool, _dbClient);
        if (virtualPoolNames.length() != 0) {
            throw APIException.badRequests.virtualPoolIsSetAsContinuousCopiesVpool(vpool.getLabel(), virtualPoolNames);
        }
    }

}
