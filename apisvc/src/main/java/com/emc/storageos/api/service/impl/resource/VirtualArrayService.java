/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.BlockMapper.addAutoTierPolicy;
import static com.emc.storageos.api.mapper.BlockMapper.toVirtualPoolResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.VirtualArrayMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

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
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapVirtualArray;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.GeoVisibilityHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeFabricUplinkPort;
import com.emc.storageos.db.client.model.ComputeFabricUplinkPortChannel;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.block.tier.AutoTierPolicyList;
import com.emc.storageos.model.compute.ComputeSystemBulkRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.pools.StoragePoolList;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.model.varray.AttributeList;
import com.emc.storageos.model.varray.NetworkCreate;
import com.emc.storageos.model.varray.NetworkList;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.VArrayAttributeList;
import com.emc.storageos.model.varray.VirtualArrayBulkRep;
import com.emc.storageos.model.varray.VirtualArrayConnectivityList;
import com.emc.storageos.model.varray.VirtualArrayConnectivityRestRep;
import com.emc.storageos.model.varray.VirtualArrayCreateParam;
import com.emc.storageos.model.varray.VirtualArrayList;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.storageos.model.varray.VirtualArrayUpdateParam;
import com.emc.storageos.model.vpool.VirtualPoolAvailableAttributesResourceRep;
import com.emc.storageos.model.vpool.VirtualPoolList;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.geo.GeoServiceClient;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.google.common.collect.Lists;

/**
 * VirtualArray service - create/list VirtualArrays, create/list transport zones in
 * a VirtualArray.
 */
@Path("/vdc/varrays")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class VirtualArrayService extends TaggedResource {

    private static Logger _log = LoggerFactory.getLogger(VirtualArrayService.class);
    private static final String EVENT_SERVICE_TYPE = "VirtualArray";
    private static final String EVENT_SERVICE_SOURCE = "VirtualArrayService";

    private static final String SEARCH_INITIATOR_PORT = "initiator_port";
    private static final String SEARCH_HOST = "host";
    private static final String SEARCH_CLUSTER = "cluster";

    @Autowired
    private NetworkService _networkService;

    @Autowired
    private ComputeSystemService computeSystemService;

    @Autowired
    protected GeoVisibilityHelper _geoHelper;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private RecordableEventManager eventManager;

    public void setEventManager(RecordableEventManager eventManager) {
        this.eventManager = eventManager;
    }

    private AttributeMatcherFramework _matcherFramework;

    /**
     * List VirtualArrays in zone the user is authorized to see
     * 
     * @brief List VirtualArrays in zone
     * @return List of VirtualArrays
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualArrayList getVirtualArrayList(@DefaultValue("") @QueryParam(VDC_ID_QUERY_PARAM) String shortVdcId) {
        _geoHelper.verifyVdcId(shortVdcId);
        VirtualArrayList list = new VirtualArrayList();
        List<VirtualArray> nhObjList = Collections.emptyList();
        if (_geoHelper.isLocalVdcId(shortVdcId)) {
            _log.debug("retrieving virtual arrays via dbclient");
            final List<URI> ids = _dbClient.queryByType(VirtualArray.class, true);
            nhObjList = _dbClient.queryObject(VirtualArray.class, ids);
        } else {
            _log.debug("retrieving virtual arrays via geoclient");
            try {
                GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
                final List<URI> ids = Lists.newArrayList(geoClient.queryByType(VirtualArray.class, true));
                nhObjList = Lists.newArrayList(geoClient.queryObjects(VirtualArray.class, ids));
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving virtual arrays", ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving virtual arrays", ex);
            }
        }

        StorageOSUser user = getUserFromContext();
        // full list if role is {Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR}
        if (_permissionsHelper.userHasGivenRole(user,
                null, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR)) {
            for (VirtualArray nh : nhObjList) {
                list.getVirtualArrays().add(toNamedRelatedResource(ResourceTypeEnum.VARRAY,
                        nh.getId(), nh.getLabel()));
            }
        } else {
            // otherwise, filter by only authorized to use
            URI tenant = URI.create(user.getTenantId());
            Map<String, Collection<String>> subTenantRoles = _permissionsHelper
                    .getSubtenantRolesForUser(user);
            List<URI> subtenants = new ArrayList<URI>();
            for (String subtenant : subTenantRoles.keySet()) {
                subtenants.add(URI.create(subtenant));
            }

            for (VirtualArray nh : nhObjList) {
                if (_permissionsHelper.tenantHasUsageACL(tenant, nh) ||
                        _permissionsHelper.tenantHasUsageACL(subtenants, nh)) {
                    list.getVirtualArrays().add(toNamedRelatedResource(ResourceTypeEnum.VARRAY,
                            nh.getId(), nh.getLabel()));
                }
            }
        }
        return list;
    }

    /**
     * Get all Auto Tier policies associated with given VirtualArray which satisfies
     * poolType.
     * If provisionType is thick, then only TierPolicies which belongs to Thick Provisioning
     * will be returned.
     * If provisionType is thin, then only TierPolicies which belongs to
     * Thin Provisioning will be returned.
     * If provisionType is not specified, then all TierPolicies will be returned.
     * In addition to the above constraints, only policies which satisfy the following conditions gets returned
     * 1. AutoTiering should be enabled on top level StorageSystem, which these policies belong to
     * 2. Policy should be in enabled State.
     * 
     * ProvisionType Values :
     * {
     * Thin
     * Thick
     * }
     * 
     * @params QueryParam , which includes provisionType
     *         provisionType- Thin or Thick
     * @brief List VirtualArray auto tier policies for provision type
     * @return A reference to a AutoTierPolicy List specifying the id and self link
     *         for each policy
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/auto-tier-policies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN })
    public AutoTierPolicyList getAutoTierPolicies(@PathParam("id") URI id,
            @QueryParam("provisioning_type") String provisionType,
            @QueryParam("unique_auto_tier_policy_names") Boolean uniquePolicyNames) {

        if (null == uniquePolicyNames) {
            uniquePolicyNames = false;
        }
        URIQueryResultList poolsInVirtualArray = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePoolsConstraint(id.toString()), poolsInVirtualArray);
        Iterator<StoragePool> poolIter = _dbClient.queryIterativeObjectField(StoragePool.class, "storageDevice", poolsInVirtualArray);
        Set<URI> systems = new HashSet<>();
        while (poolIter.hasNext()) {
            systems.add(poolIter.next().getStorageDevice());
        }
        Set<URI> autoTierPolicyURIs = new HashSet<URI>();
        for (URI systemId : systems) {
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceFASTPolicyConstraint(systemId), result);
            while (result.iterator().hasNext()) {
                URI policyURI = result.iterator().next();
                autoTierPolicyURIs.add(policyURI);
            }
        }
        return getAutoTierPolicies(provisionType, autoTierPolicyURIs, uniquePolicyNames);
    }

    /**
     * Get all Auto Tier policies for all VArrays for a given provisioning type.
     * If provisionType is thick, then only TierPolicies which belongs to Thick Provisioning
     * will be returned.
     * If provisionType is thin, then only TierPolicies which belongs to
     * Thin Provisioning will be returned.
     * If provisionType is not specified, then all TierPolicies will be returned.
     * In addition to the above constraints, only policies which satisfy the following conditions gets returned
     * 1. AutoTiering should be enabled on top level StorageSystem, which these policies belong to
     * 2. Policy should be in enabled State.
     * 
     * ProvisionType Values :
     * {
     * Thin
     * Thick
     * }
     * 
     * @params QueryParam , which includes provisionType
     *         provisionType- Thin or Thick
     * @brief List VirtualArray auto tier policies for provision type
     * @return A reference to a AutoTierPolicy List specifying the id and self link
     *         for each policy
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/auto-tier-policies")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN })
    public AutoTierPolicyList getAutoTierPolicies(@QueryParam("provisioning_type") String provisionType,
            @QueryParam("unique_auto_tier_policy_names") Boolean uniquePolicyNames,
            BulkIdParam param) {

        if (null == uniquePolicyNames) {
            uniquePolicyNames = false;
        }
        Set<URI> systems = new HashSet<>();
        for (URI id : param.getIds()) {
            URIQueryResultList poolsInVirtualArray = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePoolsConstraint(id.toString()), poolsInVirtualArray);
            Iterator<StoragePool> poolIter = _dbClient.queryIterativeObjectField(StoragePool.class, "storageDevice", poolsInVirtualArray);

            while (poolIter.hasNext()) {
                systems.add(poolIter.next().getStorageDevice());
            }
        }
        Set<URI> autoTierPolicyURIs = new HashSet<>();
        for (URI systemId : systems) {
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceFASTPolicyConstraint(systemId), result);
            while (result.iterator().hasNext()) {
                URI policyURI = result.iterator().next();
                autoTierPolicyURIs.add(policyURI);
            }
        }
        return getAutoTierPolicies(provisionType, autoTierPolicyURIs, uniquePolicyNames);
    }

    /**
     * get Policies which satisfy the below
     * 1. if policy is enabled
     * 2. if is enabled on associated Storage System.
     * 3. Policy's provisioning Type equals given provisioningType
     */
    private AutoTierPolicyList getAutoTierPolicies(String provisionType,
            Set<URI> autoTierPolicyUris, boolean uniquePolicyNames) {
        AutoTierPolicyList result = new AutoTierPolicyList();
        List<URI> autoTierPolicyUriList = new ArrayList<>(autoTierPolicyUris);
        Iterator<AutoTieringPolicy> autoTierPolicies = _dbClient.queryIterativeObjects(AutoTieringPolicy.class,
                autoTierPolicyUriList, true);
        Map<URI, StorageSystem> systemCache = new HashMap<>();
        while (autoTierPolicies.hasNext()) {
            AutoTieringPolicy policy = autoTierPolicies.next();
            // If policy is disabled, skip it
            if (!policy.getPolicyEnabled()) {
                continue;
            }
            if (!doesGivenProvisionTypeMatchAutoTierPolicy(provisionType, policy)) {
                continue;
            }
            StorageSystem system = systemCache.get(policy.getStorageSystem());
            if (system == null) {
                system = _dbClient.queryObject(StorageSystem.class, policy.getStorageSystem());
                systemCache.put(policy.getStorageSystem(), system);
            }
            // if is disabled then skip it too.
            if (null != system && system.getAutoTieringEnabled()) {
                addAutoTierPolicy(policy, result, uniquePolicyNames);
            }
        }
        return result;
    }

    /**
     * Conditions to find out whether given Provision Type matches
     * discovered Policy's provisionType
     * 
     * @param provisioningType
     * @param policy
     * @return
     */
    private boolean doesGivenProvisionTypeMatchAutoTierPolicy(
            String provisioningType, AutoTieringPolicy policy) {
        if (null == provisioningType || provisioningType.isEmpty()) {
            return true;
        }
        // for vnx case, all Policies will be set to ALL
        if (AutoTieringPolicy.ProvisioningType.All.toString().equalsIgnoreCase(
                policy.getProvisioningType())) {
            return true;
        }
        if (provisioningType.equalsIgnoreCase(VirtualPool.ProvisioningType.Thick.toString())
                && AutoTieringPolicy.ProvisioningType.ThicklyProvisioned.toString()
                        .equalsIgnoreCase(policy.getProvisioningType())) {
            return true;
        }
        if (provisioningType.equalsIgnoreCase(VirtualPool.ProvisioningType.Thin.toString())
                && AutoTieringPolicy.ProvisioningType.ThinlyProvisioned.toString()
                        .equalsIgnoreCase(policy.getProvisioningType())) {
            return true;
        }
        return false;
    }

    /**
     * Create VirtualArray
     * 
     * @param param VirtualArray parameters
     * @brief Create VirtualArray
     * @return VirtualArray details
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public VirtualArrayRestRep createVirtualArray(VirtualArrayCreateParam param) {

        // check for active nh with same name
        checkDuplicateLabel(VirtualArray.class, param.getLabel());

        VirtualArray varray = new VirtualArray();
        varray.setId(URIUtil.createId(VirtualArray.class));
        varray.setLabel(param.getLabel());
        if (param.getAutoSanZoning() != null) {
            varray.setAutoSanZoning(param.getAutoSanZoning());
        } else {
            varray.setAutoSanZoning(true);
        }

        if (param.getObjectSettings().getProtectionType() != null) {
            varray.setProtectionType(param.getObjectSettings().getProtectionType());
        }

        _dbClient.createObject(varray);

        auditOp(OperationTypeEnum.CREATE_VARRAY, true, null,
                param.getLabel(), varray.getAutoSanZoning().toString(), varray.getId().toString());

        return map(varray);
    }

    /**
     * @brief Update VirtualArray
     * 
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}")
    public VirtualArrayRestRep updateVirtualArray(@PathParam("id") URI id,
            VirtualArrayUpdateParam param) {

        VirtualArray varray = queryResource(id);

        if (param.getLabel() != null && !param.getLabel().isEmpty()) {
            if (!varray.getLabel().equalsIgnoreCase(param.getLabel())) {
                // check for active VirtualArray with same name
                checkDuplicateLabel(VirtualArray.class, param.getLabel());
            }
            varray.setLabel(param.getLabel());
        }

        if (param.getAutoSanZoning() != null) {
            varray.setAutoSanZoning(param.getAutoSanZoning());
        }

        if (param.getObjectSettings().getProtectionType() != null) {
            varray.setProtectionType(param.getObjectSettings().getProtectionType());
        }

        _dbClient.persistObject(varray);

        auditOp(OperationTypeEnum.UPDATE_VARRAY, true, null,
                id.toString(), param.getLabel(), varray.getAutoSanZoning().toString());

        return map(varray);
    }

    /**
     * Get info for VirtualArray
     * 
     * @param id the URN of a ViPR VirtualArray
     * @brief Show VirtualArray
     * @return VirtualArray details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public VirtualArrayRestRep getVirtualArray(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");
        VirtualArray varray = null;
        if (_geoHelper.isLocalURI(id)) {
            _log.debug("retrieving varray via dbclient");
            varray = getVirtualArrayById(id, false);
        } else {
            _log.debug("retrieving varray via geoclient");
            String shortVdcId = VdcUtil.getVdcId(VirtualArray.class, id).toString();
            // TODO: do we want to leverage caching like the native lookup
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                varray = geoClient.queryObject(VirtualArray.class, id);
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving virtual array from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote array", ex);
            }
        }
        return map(varray);
    }

    @Override
    protected VirtualArray queryResource(URI id) {
        VirtualArray vArray = getVirtualArrayById(id, false);
        ArgValidator.checkEntityNotNull(vArray, id, isIdEmbeddedInURL(id));
        return vArray;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Deactivate the VirtualArray.
     * When a VirtualArray is deactivated it will move to a "marked for deletion" state.
     * Once in this state, new resources may no longer be created in the VirtualArray.
     * The VirtualArray will be permanently deleted once all references to this VirtualArray
     * of type VirtualPool, BlockSnapshot, FileSystem, Volume, HostingDeviceInfo, StorageSystem,
     * Network are deleted
     * 
     * @param id the URN of a ViPR VirtualArray
     * @brief Delete VirtualArray
     * @return No data returned in response body
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteVirtualArray(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntityNotNull(varray, id, isIdEmbeddedInURL(id));
        ArgValidator.checkReference(VirtualArray.class, id, checkForDelete(varray));
        if (varray.getDeviceRegistered()) {
            // registered varray can not be deleted
            throw APIException.badRequests.resourceCannotBeDeleted("Varray is already registered.");
        }
        _dbClient.markForDeletion(varray);

        auditOp(OperationTypeEnum.DELETE_VARRAY, true, null,
                id.toString(), varray.getLabel());

        return Response.ok().build();
    }

    /**
     * 
     * Returns the storage pools for the VirtualArray with the passed id. When
     * the VirtualArray has been explicitly assigned to one or more storage
     * pools, this API will return the ids of those storage pools. VirtualArrays
     * can be explicitly assigned to storage pools when a storage pool is
     * created or later by modifying the storage pool after it has been created.
     * <p>
     * Whether or not a VirtualArray has been explicitly assigned to any storage pools the VirtualArray may still have implicit associations
     * with one or more storage pools due to the VirtualArray's network connectivity. That is, a network resides in a VirtualArray and may
     * contain storage ports. This implies that these storage ports reside in the VirtualArray, which further implies that the storage
     * system and any storage pools on that storage system also reside in the VirtualArray. If the VirtualArray has no explicit assignments,
     * but does have implicit associations, the API will instead return those storage pools implicitly associated.
     * <p>
     * The API provides the ability to force the return of the list of storage pools implicitly associated with the VirtualArray using the
     * request parameter "network_connectivity". Passing this parameter with a value of "true" will return the ids of the storage pools
     * implicitly associated with the VirtualArray as described.
     * 
     * @param id the URN of a ViPR VirtualArray.
     * @param useNetworkConnectivity true to use the network connectivity to
     *            get the list of storage pools implicitly connected to the
     *            VirtualArray.
     * 
     * @brief List VirtualArray storage pools
     * @return The ids of the storage pools associated with the VirtualArray.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-pools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public StoragePoolList getVirtualArrayStoragePools(@PathParam("id") URI id,
            @QueryParam("network_connectivity") boolean useNetworkConnectivity) {

        // Get and validate the varray with the passed id.
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntity(varray, id, isIdEmbeddedInURL(id));

        // Query the database for the storage pools associated with the
        // VirtualArray. If the request is for storage pools whose
        // association with the VirtualArray is implicit through network
        // connectivity, then return only these storage pools.
        // Otherwise, the result is for storage pools explicitly assigned
        // to the VirtualArray.
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        if (useNetworkConnectivity) {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getImplicitVirtualArrayStoragePoolsConstraint(id.toString()),
                    storagePoolURIs);
        } else {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePoolsConstraint(id.toString()), storagePoolURIs);
        }

        // Create and return the result.
        StoragePoolList storagePools = new StoragePoolList();
        for (URI uri : storagePoolURIs) {
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, uri);
            if ((storagePool != null)
                    && (CompatibilityStatus.COMPATIBLE.toString().equals(storagePool
                            .getCompatibilityStatus()))
                    && (RegistrationStatus.REGISTERED.toString().equals(storagePool
                            .getRegistrationStatus()))
                    && DiscoveryStatus.VISIBLE.toString().equals(storagePool.getDiscoveryStatus())) {
                storagePools.getPools().add(toNamedRelatedResource(storagePool, storagePool.getNativeGuid()));
            }
        }
        return storagePools;
    }

    /**
     * 
     * Returns the storage ports for the VirtualArray with the passed id. When
     * the VirtualArray has been explicitly assigned to one or more storage
     * ports, this API will return the ids of those storage ports. VirtualArrays
     * can be explicitly assigned to storage ports when a storage port is
     * created or later by modifying the storage port after it has been created.
     * <p>
     * Whether or not a VirtualArray has been explicitly assigned to any storage ports the VirtualArray may still have implicit associations
     * with one or more storage port due to the VirtualArray's network connectivity. That is, a network resides in a VirtualArray and may
     * contain storage ports. This implies that these storage ports reside in the VirtualArray. If the VirtualArray has no explicit storage
     * port assignments, but does have implicit associations, the API will instead return those storage ports implicitly associated.
     * <p>
     * The API provides the ability to force the return of the list of storage ports implicitly associated with the VirtualArray using the
     * request parameter "network_connectivity". Passing this parameter with a value of "true" will return the ids of the storage ports
     * implicitly associated with the VirtualArray as described.
     * 
     * @param id the URN of a ViPR VirtualArray.
     * @param useNetworkConnectivity true to use the network connectivity to
     *            get the list of storage ports implicitly connected to the
     *            VirtualArray.
     * 
     * @brief List VirtualArray storage ports
     * @return The ids of the storage ports associated with the VirtualArray.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/storage-ports")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public StoragePortList getVirtualArrayStoragePorts(@PathParam("id") URI id,
            @QueryParam("network_connectivity") boolean useNetworkConnectivity) {

        // Get and validate the varray with the passed id.
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntity(varray, id, isIdEmbeddedInURL(id));

        // Query the database for the storage ports associated with the
        // VirtualArray. If the request is for storage ports whose
        // association with the VirtualArray is implicit through network
        // connectivity, then return only these storage ports. Otherwise,
        // the result is for storage ports explicitly assigned to the
        // VirtualArray.
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        if (useNetworkConnectivity) {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getImplicitVirtualArrayStoragePortsConstraint(id.toString()),
                    storagePortURIs);
        } else {
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePortsConstraint(id.toString()), storagePortURIs);
        }

        // Create and return the result.
        StoragePortList storagePorts = new StoragePortList();
        for (URI uri : storagePortURIs) {
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class, uri);
            if ((storagePort != null)
                    && DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                            .equals(storagePort.getCompatibilityStatus())
                    && (RegistrationStatus.REGISTERED.toString().equals(storagePort
                            .getRegistrationStatus()))
                    && DiscoveryStatus.VISIBLE.toString().equals(storagePort.getDiscoveryStatus())) {
                storagePorts.getPorts().add(toNamedRelatedResource(storagePort, storagePort.getNativeGuid()));
            }
        }
        return storagePorts;
    }

    /**
     * Returns the id and self link for all VirtualPool associated
     * with the VirtualArray.
     * 
     * @param id the URN of a ViPR VirtualArray.
     * 
     * @brief List VirtualArray VirtualPools
     * @return A reference to a VirtualPoolList specifying the id and self link for the
     *         VirtualPool for the VirtualArray.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/vpools")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public VirtualPoolList getVirtualArrayVirtualPool(@PathParam("id") URI id) {
        VirtualPoolList cosList = new VirtualPoolList();
        URIQueryResultList resultList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getVirtualArrayVirtualPoolConstraint(id),
                resultList);

        Iterator<URI> cosIterator = resultList.iterator();
        while (cosIterator.hasNext()) {

            URI cosId = cosIterator.next();
            VirtualPool cos = _dbClient.queryObject(VirtualPool.class, cosId);

            if (cosList.containsVirtualPoolResource(cosId.toString())) { // already added, ignore
                continue;
            }

            /*
             * An user can see the vpool if:
             * 1. be sysadmin or sysmonitor or restricted sysadmin
             * 2. mapped to that tenant.
             * 3. tenant admin but not mapping to the tenant cannot see it
             */
            StorageOSUser user = getUserFromContext();
            if (_permissionsHelper.userHasGivenRole(user, null,
                    Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR, Role.RESTRICTED_SYSTEM_ADMIN) ||
                    userTenantHasPermissionForVirtualPool(cosId.toString())) {
                _log.debug("Adding VirtualPool");
                cosList.getVirtualPool().add(toVirtualPoolResource(cos));
            }
        }
        return cosList;
    }

    /**
     * Determines if the VirtualPool with the passed id is accessible to
     * the user's tenant organization.
     * 
     * @param vpoolId The VirtualPool id.
     * 
     * @return true if the VirtualPool is accessible to the user's tenant, false otherwise.
     */
    private boolean userTenantHasPermissionForVirtualPool(String vpoolId) {

        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, URI.create(vpoolId));
        if (vpool == null) {
            _log.error("VirtualPool {} could not be found in the database", vpoolId);
            return false;
        }

        StorageOSUser user = getUserFromContext();
        URI tenantURI = URI.create(user.getTenantId());
        _log.debug("Tenant is {}", tenantURI.toString());
        boolean hasUsageACL = _permissionsHelper.tenantHasUsageACL(tenantURI, vpool);
        _log.debug("Tenant has usage ACL for VirtualPool {}: {}", vpoolId, hasUsageACL);
        return hasUsageACL;
    }

    /**
     * Create a network and assign it to the virtual array.
     * 
     * @param param Network parameters
     * @param id the URN of a ViPR VirtualArray
     * @see NetworkService#createNetwork(NetworkCreate)
     * @deprecated used {@link NetworkService#createNetwork(NetworkCreate)}
     * @brief Create Network
     * @return Network details
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/networks")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public NetworkRestRep createNetwork(@PathParam("id") URI id,
            NetworkCreate param) {
        _log.debug("createNetwork: started for varray {}", id);
        // check VirtualArray
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntity(varray, id, isIdEmbeddedInURL(id));
        if (param.getVarrays() != null && !param.getVarrays().isEmpty()) {
            throw APIException.badRequests.invalidParameterForVarrayNetwork(id.toString());
        }
        // set the varray in the param
        param.setVarrays(Collections.singletonList(id));
        // delegate to network service the actual work
        return _networkService.createNetwork(param);
    }

    /**
     * List transport zones in VirtualArray
     * 
     * @param id the URN of a ViPR VirtualArray
     * @brief List VirtualArray Networks
     * @return List of Networks
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/networks")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public NetworkList getNetworkList(@PathParam("id") URI id) {
        NetworkList networkList = new NetworkList();

        // Verify the passed virtual array id.
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntity(varray, id, isIdEmbeddedInURL(id));
        // Get the networks assigned to the virtual array.
        List<Network> networks = CustomQueryUtility.queryActiveResourcesByRelation(
                _dbClient, id, Network.class, "connectedVirtualArrays");
        for (Network network : networks) {
            if (network == null || network.getInactive() == true) {
                continue;
            }
            networkList.getNetworks().add(
                    toNamedRelatedResource(ResourceTypeEnum.NETWORK, network.getId(),
                            network.getLabel()));
        }
        return networkList;
    }

    /**
     * Get VirtualArray ACL. There is only one privilege, "use", and it is used to determine who can create resources in the VirtualArray.
     * 
     * @param id the URN of a ViPR VirtualArray
     * @brief Show VirtualArray ACL
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsResponse(id);
    }

    /**
     * Add or remove individual ACL entry(s). Request body must include at least one add or remove operation
     * 
     * @param changes ACL assignment changes
     * @param id the URN of a ViPR VirtualArray
     * @brief Add or remove ACL for VirtualArray
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        VirtualArray varray = getVirtualArrayById(id, true);
        _permissionsHelper.updateACLs(varray, changes, new PermissionsHelper.UsageACLFilter(_permissionsHelper));
        _dbClient.persistObject(varray);

        auditOp(OperationTypeEnum.MODIFY_VARRAY_ACL, true, null,
                id.toString(), varray.getLabel());

        return getAclsResponse(id);
    }

    private ACLAssignments getAclsResponse(URI id) {
        VirtualArray varray = getVirtualArrayById(id, false);
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(varray.getAcls()));
        return response;
    }

    /**
     * Get information about the connectivity of the registered VirtualArray
     * with the passed id.
     * 
     * @param id the URN of a ViPR VirtualArray
     * @brief List VirtualArray connectivity
     * @return NeighbourhoodConnectivityRestRep object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/connectivity")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public VirtualArrayConnectivityList getVirtualArrayConnectivity(@PathParam("id") URI id) {
        return getConnectivity(queryResource(id));
    }

    /**
     * Gets information about VirtualArrays that are connected to the passed in VirtualArray that support some kind of protection
     * 
     * @param varray
     * @return rest response
     */
    private VirtualArrayConnectivityList getConnectivity(VirtualArray varray) {

        Set<VirtualArrayConnectivityRestRep> connections = AbstractBlockServiceApiImpl.getVirtualArrayConnectivity(_dbClient,
                varray.getId());

        VirtualArrayConnectivityList connectivityList = new VirtualArrayConnectivityList();
        connectivityList.setConnections(new ArrayList<VirtualArrayConnectivityRestRep>());
        connectivityList.getConnections().addAll(connections);

        return connectivityList;
    }

    /**
     * Get tenant object from id
     * 
     * @param id the URN of a ViPR tenant object
     * @return
     */
    private VirtualArray getVirtualArrayById(URI id, boolean checkInactive) {
        if (id == null) {
            return null;
        }

        VirtualArray n = _permissionsHelper.getObjectById(id, VirtualArray.class);
        ArgValidator.checkEntity(n, id, isIdEmbeddedInURL(id), checkInactive);

        return n;
    }

    /**
     * Finds the available attributes & its values in a varray. Ex: In a
     * varray, if a system supports raid_levels such as RAID1, RAID2 then
     * this API call provides the supported information.
     * 
     * @param id the URN of a ViPR VirtualArray.
     * @brief List available attributes for VirutalArray
     * @return List available attributes for VirutalArray
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/available-attributes")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public AttributeList getAvailableAttributes(@PathParam("id") URI id) {
        // Get and validate the varray with the passed id.
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");

        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntityNotNull(varray, id, isIdEmbeddedInURL(id));

        _log.info("Finding the available attributes for varray: {}", id);
        AttributeList list = new AttributeList();
        list.setVArrayId(id);
        ObjectLocalCache cache = new ObjectLocalCache(_dbClient);
        List<StoragePool> pools = getVirtualArrayPools(Arrays.asList(id), cache).get(id);
        Map<String, Set<String>> availableAttrs = _matcherFramework.getAvailableAttributes(id, pools, cache,
                AttributeMatcher.VPOOL_MATCHERS);
        cache.clearCache();
        for (Map.Entry<String, Set<String>> entry : availableAttrs.entrySet()) {
            list.getAttributes().add(new VirtualPoolAvailableAttributesResourceRep(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    /**
     * Finds the available attributes & its values in a varray. Ex: In a
     * varray, if a system supports raid_levels such as RAID1, RAID2 then
     * this API call provides the supported information.
     * 
     * @brief List available attributes for all VirutalArrays
     * @return List available attributes for all VirutalArrays
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/available-attributes")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public VArrayAttributeList getAvailableAttributes(BulkIdParam param) {
        _log.info("Finding the available attributes for all varray: {}");
        VArrayAttributeList vArrayAttributes = new VArrayAttributeList();
        ObjectLocalCache cache = new ObjectLocalCache(_dbClient);
        Map<URI, List<StoragePool>> allPools = getVirtualArrayPools(param.getIds(), cache);
        for (Map.Entry<URI, List<StoragePool>> varrEntry : allPools.entrySet()) {
            Map<String, Set<String>> availableAttrs = _matcherFramework.
                    getAvailableAttributes(varrEntry.getKey(), varrEntry.getValue(),
                            cache, AttributeMatcher.VPOOL_MATCHERS);
            AttributeList list = new AttributeList();
            list.setVArrayId(varrEntry.getKey());
            for (Map.Entry<String, Set<String>> entry : availableAttrs.entrySet()) {
                list.getAttributes().add(new VirtualPoolAvailableAttributesResourceRep(entry.getKey(), entry.getValue()));
            }
            if (!list.getAttributes().isEmpty()) {
                vArrayAttributes.getAttributes().add(list);
            }
        }
        cache.clearCache();
        return vArrayAttributes;
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of varray resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public VirtualArrayBulkRep getBulkResources(BulkIdParam param) {
        return (VirtualArrayBulkRep) super.getBulkResources(param);
    }

    private Map<URI, List<StoragePool>> getVirtualArrayPools(List<URI> varrayIds, ObjectLocalCache cache) {

        Map<URI, List<StoragePool>> poolMap = new HashMap<>();
        for (URI varr : varrayIds) {
            List<StoragePool> poolList = poolMap.get(varr);
            if (poolList == null) {
                poolList = new ArrayList<>();
                poolMap.put(varr, poolList);
            }
            URIQueryResultList poolsQueryResult = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVirtualArrayStoragePoolsConstraint(varr.toString()), poolsQueryResult);
            Iterator<URI> poolItr = poolsQueryResult.iterator();
            while (poolItr.hasNext()) {
                StoragePool pool = cache.queryObject(StoragePool.class, poolItr.next());
                poolList.add(pool);
            }
        }
        return poolMap;
    }

    public void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<VirtualArray> getResourceClass() {
        return VirtualArray.class;
    }

    @Override
    public VirtualArrayBulkRep queryBulkResourceReps(List<URI> ids) {

        if (!ids.iterator().hasNext()) {
            return new VirtualArrayBulkRep();
        }

        // get vdc id from the first id; assume all id's are from the same vdc
        String shortVdcId = VdcUtil.getVdcId(getResourceClass(), ids.iterator().next()).toString();

        Iterator<VirtualArray> dbIterator;
        if (shortVdcId.equals(VdcUtil.getLocalShortVdcId())) {
            dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                dbIterator = geoClient.queryObjects(getResourceClass(), ids);
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving bulk virtual arrays from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote array", ex);
            }
        }
        return new VirtualArrayBulkRep(BulkList.wrapping(dbIterator, MapVirtualArray.getInstance()));
    }

    /**
     * Fetches all Compute Systems that are visible in the vArray
     * 
     * First determine physical connectivity to any switches in the vArrray.
     * 1. From the vArray, determine the networks. (Call this Network Set)
     * 2. From the networks, get the physical switches that are attached.
     * 3. For each physical switch, iterate through the networks and get the FC endpoints.
     * 4. Look for any of the FIC ports in any of the FC endpoints on any of the
     * networks on the physical switch. When a FIC port matches, call this FIC
     * Port.
     * 5. If found, then there is physical connectivity.
     * 
     * With physical connectivity Established:
     * 1. Given the FIC Port from step (4), pull the VSAN or VSANs assigned to
     * it on UCS.
     * 2. If the set contains one of the networks from the Network
     * Set in (1), we have connectivity to that vArray.
     * 
     * @param id
     *            the URN of a ViPR VirtualArray.
     * @brief List all Compute Systems that are visible in the vArray
     * @return List of Compute Systems
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/compute-systems")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, acls = { ACL.USE })
    public ComputeSystemBulkRep getComputeSystems(@PathParam("id") URI id) {
        _log.info("get connected CS for vArray: {}", id);

        // Get and validate the varray with the passed id.
        ArgValidator.checkFieldUriType(id, VirtualArray.class, "id");

        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, id);
        ArgValidator.checkEntityNotNull(varray, id, isIdEmbeddedInURL(id));

        BulkIdParam matchingCsIds = new BulkIdParam();

        // get varray networks
        List<Network> networks = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient, id, Network.class,
                "connectedVirtualArrays");

        // collect network vsanIds and switch ids
        Set<String> networkVsanIds = new HashSet<>();
        Set<String> nsIds = new HashSet<>();
        for (Network network : networks) {
            if (StorageProtocol.Transport.FC.name().equalsIgnoreCase(network.getTransportType())
                    && DiscoveredSystemObject.RegistrationStatus.REGISTERED.name().equals(network.getRegistrationStatus())) {
                networkVsanIds.add(network.getNativeId());
                if (network.getNetworkSystems() != null) {
                    nsIds.addAll(network.getNetworkSystems());
                }
            }
        }
        _log.info("vArray has these networks: {}", networkVsanIds);

        // use only registered network systems
        Set<URI> nsUris = new HashSet<>();
        for (String nsUri : nsIds) {
            nsUris.add(URI.create(nsUri));
        }
        List<NetworkSystem> nsList = _dbClient.queryObject(NetworkSystem.class, nsUris);
        for (NetworkSystem ns : nsList) {
            if (!DiscoveredSystemObject.RegistrationStatus.REGISTERED.name().equals(ns.getRegistrationStatus())) {
                nsIds.remove(ns.getId().toString());
            }
        }
        _log.info("the networks run on these network systems: {}", nsIds);

        if (networkVsanIds.isEmpty() || nsIds.isEmpty()) {
            // no networks in the array - exit early
            return new ComputeSystemBulkRep();
        }

        // for every switch get FCEndpoint.remotePortName(s)
        Set<String> connectedEndpoints = new HashSet<String>();
        for (String nsId : nsIds) {
            URIQueryResultList uriList = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getNetworkSystemFCPortConnectionConstraint(URI.create(nsId)),
                    uriList);
            List<URI> epIds = new ArrayList<URI>();
            Iterator<URI> iter = uriList.iterator();
            while (iter.hasNext()) {
                epIds.add(iter.next());
            }
            List<FCEndpoint> eps = _dbClient.queryObjectField(FCEndpoint.class, "remotePortName", epIds);
            for (FCEndpoint ep : eps) {
                connectedEndpoints.add(ep.getRemotePortName());
            }
        }
        _log.debug("all connected endpoints: {}", connectedEndpoints);

        // get all CS
        List<URI> csIds = _dbClient.queryByType(ComputeSystem.class, true);
        List<ComputeSystem> csList = _dbClient.queryObject(ComputeSystem.class, csIds);
        for (ComputeSystem cs : csList) {
            if (!DiscoveredSystemObject.RegistrationStatus.REGISTERED.name().equals(cs.getRegistrationStatus())) {
                // skip not registered CS
                continue;
            }
            boolean connected = false;
            _log.info("evaluating uplinks of cs: {}", cs.getLabel());

            // loop thru UplinkPorts to find matches
            URIQueryResultList uris = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getComputeSystemComputeFabricUplinkPortConstraint(cs.getId()), uris);

            List<ComputeFabricUplinkPort> uplinkPorts = _dbClient
                    .queryObject(ComputeFabricUplinkPort.class, uris, true);
            for (ComputeFabricUplinkPort port : uplinkPorts) {
                if (connectedEndpoints.contains(port.getWwpn())) {
                    _log.info("found matching endpoint: {}", port.getWwpn());
                    if (!Collections.disjoint(port.getVsans(), networkVsanIds)) {
                        _log.info("and networks overlap: {}", port.getVsans());
                        matchingCsIds.getIds().add(cs.getId());
                        connected = true;
                        break;
                    }
                }
            }

            if (connected) {
                continue; // skip uplink port channel matching as we are already connected
            }

            // now loop thru UplinkPortChannels to find matches
            uris = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getComputeSystemComputeUplinkPortChannelConstraint(cs.getId()), uris);

            List<ComputeFabricUplinkPortChannel> uplinkPortChannels = _dbClient
                    .queryObject(ComputeFabricUplinkPortChannel.class, uris, true);
            for (ComputeFabricUplinkPortChannel port : uplinkPortChannels) {
                if (connectedEndpoints.contains(port.getWwpn())) {
                    _log.info("found matching endpoint: {}", port.getWwpn());
                    if (!Collections.disjoint(port.getVsans(), networkVsanIds)) {
                        _log.info("and networks overlap: {}", port.getVsans());
                        matchingCsIds.getIds().add(cs.getId());
                        connected = true;
                        break;
                    }
                }
            }
        }
        _log.info("these CS are connected to the vArray: {}", matchingCsIds.getIds());

        if (matchingCsIds.getIds().isEmpty()) {
            return new ComputeSystemBulkRep();
        }

        ComputeSystemBulkRep computeSystemReps = computeSystemService.getBulkResources(matchingCsIds);

        return mapValidServiceProfileTemplatesToComputeSystem(computeSystemReps, varray.getId());
    }

    private ComputeSystemBulkRep mapValidServiceProfileTemplatesToComputeSystem(ComputeSystemBulkRep bulkRep, URI varrayId) {
        _log.debug("mapping Service Profile Templates valid for varray to the Compute Systems");
        ComputeSystemBulkRep rep = new ComputeSystemBulkRep();
        List<ComputeSystemRestRep> computeSystemList = new ArrayList<ComputeSystemRestRep>();
        for (ComputeSystemRestRep computeSystem : bulkRep.getComputeSystems()) {
            computeSystem.setServiceProfileTemplates(getServiceProfileTemplatesForComputeSystem(computeSystem.getId(), varrayId));
            computeSystemList.add(computeSystem);
        }
        rep.setComputeSystems(computeSystemList);
        return rep;
    }

    private List<NamedRelatedResourceRep> getServiceProfileTemplatesForComputeSystem(URI computeSystemId, URI varrayId) {
        List<NamedRelatedResourceRep> templates = new ArrayList<NamedRelatedResourceRep>();
        ComputeSystem computeSystem = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayId);
        _log.debug("Finding SPTs from Compute System:" + computeSystem.getLabel() + " valid for varray:" + varray.getLabel());
        List<NamedRelatedResourceRep> spts = computeSystemService.getServiceProfileTemplatesForComputeSystem(computeSystem, _dbClient);
        StringSet varrays = new StringSet();
        varrays.add(varrayId.toString());
        // Filter SPTs that are not valid for the varrays for the UCS in this vcp
        for (NamedRelatedResourceRep spt : spts) {
            if (computeSystemService.isServiceProfileTemplateValidForVarrays(varrays, spt.getId())) {
                templates.add(spt);
                _log.debug("SPT " + spt.getName() + " is valid for the varray:" + varray.getLabel());
            } else {
                _log.debug("SPT " + spt.getName() + " is not valid for the varray:" + varray.getLabel());

            }
        }
        return templates;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected VirtualArrayBulkRep queryFilteredBulkResourceReps(
            List<URI> ids) {

        if (isSystemOrRestrictedSystemAdmin()) {
            return queryBulkResourceReps(ids);
        }

        if (!ids.iterator().hasNext()) {
            return new VirtualArrayBulkRep();
        }

        // get vdc id from the first id; assume all id's are from the same vdc
        String shortVdcId = VdcUtil.getVdcId(getResourceClass(), ids.iterator().next()).toString();

        Iterator<VirtualArray> dbIterator;
        if (shortVdcId.equals(VdcUtil.getLocalShortVdcId())) {
            dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        } else {
            GeoServiceClient geoClient = _geoHelper.getClient(shortVdcId);
            try {
                dbIterator = geoClient.queryObjects(getResourceClass(), ids);
            } catch (Exception ex) {
                // TODO: revisit this exception
                _log.error("error retrieving bulk virtual arrays from vdc " + shortVdcId, ex);
                throw APIException.internalServerErrors.genericApisvcError("error retrieving remote array", ex);
            }

        }
        BulkList.ResourceFilter filter = new BulkList.VirtualArrayACLFilter(getUserFromContext(), _permissionsHelper);
        return new VirtualArrayBulkRep(BulkList.wrapping(dbIterator, MapVirtualArray.getInstance(), filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VARRAY;
    }

    public static class VirtualArrayResRepFilter extends
            ResRepFilter<SearchResultResourceRep> {

        boolean _authorized = false;

        public VirtualArrayResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper, boolean authorized) {
            super(user, permissionsHelper);
            _authorized = authorized;
        }

        @Override
        public boolean isAccessible(SearchResultResourceRep resrep) {
            URI id = resrep.getId();

            // check permission on varray
            VirtualArray varray = _permissionsHelper
                    .getObjectById(id, VirtualArray.class);
            if (varray == null || varray.getInactive()) {
                _log.error("Could not find varray {} in the database or"
                        + "the varray is inactive", id);
                return false;
            }

            if (!_authorized) {
                if (!isVirtualArrayAccessible(varray)) {
                    _log.error("varray {} is not accessible.", id);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Finds the virtual arrays for the initiator port with the passed
     * identifier and returns the id, name, and self link for those virtual
     * arrays. This API only supports fiber channel and iSCSI initiator ports,
     * and the passed port identifier must be the WWN or IQN of the port.
     * 
     * Note that in order for an initiator to be associated with any virtual,
     * arrays it must be in an active network. The virtual arrays for the passed
     * initiator are those active virtual arrays associated with the storage
     * ports in the initiator's active network. If the initiator is not in a
     * network, an empty list is returned.
     * 
     * parameter: 'initiator_port' The identifier of the initiator port.
     * 
     * 
     * @param parameters The search parameters.
     * @param authorized Whether or not the caller is authorized.
     * 
     * @return The search results specifying the virtual arrays for the
     *         initiator identified in the passed search parameters.
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters,
            boolean authorized) {

        SearchResults result = new SearchResults();
        String[] searchCriteria = { SEARCH_INITIATOR_PORT, SEARCH_HOST, SEARCH_CLUSTER };
        validateSearchParameters(parameters, searchCriteria);

        Set<String> varrayIds = new HashSet<String>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (entry.getKey().equals(SEARCH_INITIATOR_PORT)) {

                String initiatorId = parameters.get(SEARCH_INITIATOR_PORT).get(0);

                // Validate the user passed a value for the initiator port.
                ArgValidator.checkFieldNotEmpty(initiatorId, SEARCH_INITIATOR_PORT);

                // Validate the format of the passed initiator port.
                if (!EndpointUtility.isValidEndpoint(initiatorId, EndpointType.ANY)) {
                    throw APIException.badRequests.initiatorPortNotValid();
                }

                _log.info("Searching for virtual arrays for initiator {}", initiatorId);
                varrayIds.addAll(ConnectivityUtil.getInitiatorVarrays(initiatorId, _dbClient));
                break;
            } else if (entry.getKey().equals(SEARCH_HOST)) {

                // find and validate host
                String hostId = parameters.get(SEARCH_HOST).get(0);
                URI hostUri = URI.create(hostId);
                ArgValidator.checkFieldNotEmpty(hostId, SEARCH_HOST);
                Host host = queryObject(Host.class, hostUri, false);
                verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());

                _log.info("looking for virtual arrays connected to host " + host.getHostName());
                varrayIds.addAll(getVarraysForHost(hostUri));
                break;

            } else if (entry.getKey().equals(SEARCH_CLUSTER)) {

                // find and validate cluster
                String clusterId = parameters.get(SEARCH_CLUSTER).get(0);
                URI clusterUri = URI.create(clusterId);
                ArgValidator.checkFieldNotEmpty(clusterId, SEARCH_CLUSTER);
                Cluster cluster = queryObject(Cluster.class, clusterUri, false);
                verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());

                _log.info("looking for virtual arrays connected to cluster " + cluster.getLabel());
                List<Set<String>> hostVarraySets = new ArrayList<Set<String>>();
                List<NamedElementQueryResultList.NamedElement> dataObjects =
                        listChildren(clusterUri, Host.class, "label", "cluster");
                for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
                    Set<String> hostVarrays = getVarraysForHost(dataObject.getId());
                    hostVarraySets.add(hostVarrays);
                }

                boolean first = true;
                for (Set<String> varrays : hostVarraySets) {
                    if (first) {
                        varrayIds.addAll(varrays);
                        first = false;
                    } else {
                        varrayIds.retainAll(varrays);
                    }
                }
                break;
            }
        }

        // For each virtual array in the set create a search result
        // and add it to the search results list.
        List<SearchResultResourceRep> searchResultList = new ArrayList<SearchResultResourceRep>();
        if (!varrayIds.isEmpty()) {
            for (String varrayId : varrayIds) {
                URI varrayURI = URI.create(varrayId);
                VirtualArray varray = _dbClient.queryObject(VirtualArray.class, varrayURI);

                // Filter out those that are inactive or not accessible to the user.
                if (varray == null || varray.getInactive()) {
                    _log.info("Could not find virtual array {} in the database, or "
                            + "the virtual array is inactive", varrayURI);
                    continue;
                }

                if (!authorized) {
                    if (!_permissionsHelper.tenantHasUsageACL(
                            URI.create(getUserFromContext().getTenantId()), varray)) {
                        _log.info("Virtual array {} is not accessible.", varrayURI);
                        continue;
                    }
                }

                RestLinkRep selfLink = new RestLinkRep("self", RestLinkFactory.newLink(getResourceType(), varrayURI));
                SearchResultResourceRep searchResult = new SearchResultResourceRep(varrayURI, selfLink, varray.getLabel());
                searchResultList.add(searchResult);
            }
        }

        result.setResource(searchResultList);
        return result;
    }

    /**
     * Return the Virtual Arrays connected to a given Host
     * by checking the connectivity of the Host's Initiators.
     * 
     * @param hostUri URI of a Host
     * @return a set of Virtual Array URI strings
     */
    private Set<String> getVarraysForHost(URI hostUri) {
        _log.info("looking for initiators for host URI: " + hostUri);
        Set<String> varrayIds = new HashSet<String>();
        Set<String> initiatorList = new HashSet<String>();
        List<NamedElementQueryResultList.NamedElement> dataObjects =
                listChildren(hostUri, Initiator.class, "iniport", "host");
        for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
            initiatorList.add(dataObject.getId().toString());
        }

        for (String initUri : initiatorList) {
            Initiator init = _dbClient.queryObject(Initiator.class, URI.create(initUri));
            if (null != init) {
                _log.info("   found initiator " + init.getInitiatorPort());
                varrayIds.addAll(ConnectivityUtil.getInitiatorVarrays(
                        init.getInitiatorPort(), _dbClient));
                _log.info("      connected to varrays: " + varrayIds.toString());
            }
        }

        return varrayIds;
    }

    /**
     * Validate if one param passed is valid.
     * 
     * @param params to evaluate
     * @param criterias that can be searched for
     * @return true of false
     */
    private boolean isValidSearch(Map<String, List<String>> params, String[] criterias) {
        for (String search : criterias) {
            if (params.containsKey(search)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate search params.
     * 
     * @param params to evaluate
     * @param criterias that can be searched for
     */
    private void validateSearchParameters(Map<String, List<String>> params, String[] criterias) {

        if (!isValidSearch(params, criterias)) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(
                    getResourceClass().getName(),
                    Arrays.toString(criterias));
        }

        // Make sure all parameters are our parameters, otherwise throw an
        // exception because we don't support other search criteria than our own.
        // Also, make sure only ONE of the acceptable parameters has been given
        List<String> unacceptableKeys = new ArrayList<String>();
        List<String> acceptableKeys = new ArrayList<String>();
        boolean found = false;
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            found = false;
            for (String search : criterias) {
                if (entry.getKey().equals(search)) {
                    found = true;
                    acceptableKeys.add(entry.getKey());
                }
            }
            if (!found) {
                unacceptableKeys.add(entry.getKey());
            }
        }

        // {1} parameter for {0} search could not be combined with any other parameter but found {2}
        if (acceptableKeys.size() > 1) {
            throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(
                    getResourceClass().getName(),
                    Arrays.toString(criterias),
                    acceptableKeys.toString());
        }
        if (!unacceptableKeys.isEmpty()) {
            throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(
                    getResourceClass().getName(),
                    Arrays.toString(criterias),
                    unacceptableKeys.toString());
        }
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper) {
        return new VirtualArrayResRepFilter(user, permissionsHelper, false);
    }

    /**
     * Validates that each of the passed virtual array ids reference an existing
     * virtual array in the database and throws a bad request exception when
     * an invalid id is found.
     * 
     * @param virtualArrayIds The set of virtual array ids to validate
     * @param dbClient A reference to a DB client.
     */
    public static void checkVirtualArrayURIs(Set<String> virtualArrayIds,
            DbClient dbClient) {
        Set<String> invalidIds = new HashSet<String>();

        if ((virtualArrayIds != null) && (!virtualArrayIds.isEmpty())) {
            Iterator<String> virtualArrayIdsIter = virtualArrayIds.iterator();
            while (virtualArrayIdsIter.hasNext()) {
                URI virtualArrayURI = null;
                try {
                    virtualArrayURI = URI.create(virtualArrayIdsIter.next());
                    VirtualArray virtualArray = dbClient.queryObject(VirtualArray.class,
                            virtualArrayURI);
                    if (virtualArray == null) {
                        invalidIds.add(virtualArrayURI.toString());
                    }
                } catch (DatabaseException e) {
                    if (virtualArrayURI != null) {
                        invalidIds.add(virtualArrayURI.toString());
                    }
                }
            }
        }

        if (!invalidIds.isEmpty()) {
            throw APIException.badRequests.theURIsOfParametersAreNotValid("virtual arrays", invalidIds);
        }
    }
}
