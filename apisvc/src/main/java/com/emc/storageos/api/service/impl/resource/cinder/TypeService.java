/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 */

package com.emc.storageos.api.service.impl.resource.cinder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.api.service.impl.resource.utils.CinderApiUtils;
import com.emc.storageos.api.service.impl.response.ProjOwnedResRepFilter;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.cinder.model.VolumeType;
import com.emc.storageos.cinder.model.VolumeTypeEncryption;
import com.emc.storageos.cinder.model.VolumeTypeEncryptionResponse;
import com.emc.storageos.cinder.model.VolumeTypesRestResp;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;

@Path("/v2/{tenant_id}/types")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        readAcls = { ACL.USE }, writeRoles = {})
@SuppressWarnings({ "unchecked", "rawtypes" })
public class TypeService extends TaskResourceService {
    private static final Logger _log = LoggerFactory.getLogger(TypeService.class);

    @Override
    public Class<VirtualPool> getResourceClass() {
        return VirtualPool.class;
    }

    /**
     * Get volume types
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * 
     * @brief List volume types
     * @return Volume types list
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public Response getVolumeTypes(@PathParam("tenant_id") URI openstack_tenant_id, @Context HttpHeaders header) {
        // Here we ignore the openstack tenant id
        _log.info("START get list of volume types");
        VolumeTypesRestResp types = new VolumeTypesRestResp();
        StorageOSUser user = getUserFromContext();
        URI tenantId = URI.create(user.getTenantId());
        List<URI> vpools = _dbClient.queryByType(VirtualPool.class, true);
        for (URI vpool : vpools) {
            VirtualPool pool = _dbClient.queryObject(VirtualPool.class, vpool);
            _log.debug("Looking up vpool {}", pool.getLabel());
            if (pool != null && pool.getType().equalsIgnoreCase(VirtualPool.Type.block.name())) {
                if (_permissionsHelper.tenantHasUsageACL(tenantId, pool)) {
                    _log.debug("Adding vpool {}", pool.getLabel());
                    VolumeType type = new VolumeType();
                    type.id = pool.getId().toString();
                    type.name = pool.getLabel();
                    type.extra_specs = new HashMap<String, String>();
                    types.getVolume_types().add(type);
                }
            }
        }
        _log.info("END get list of volume types");
        return CinderApiUtils.getCinderResponse(types, header, false);
    }

    /**
     * Get information about a specified volume type
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param volume_type_id the URN of the volume type
     * 
     * @brief Show volume type
     * @return Volume type details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_type_id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getVolumeType(@PathParam("tenant_id") URI openstacktenant_id,
            @PathParam("volume_type_id") URI volume_type_id, @Context HttpHeaders header) {
        _log.debug("START get volume types {}", volume_type_id);
        // Here we ignore the openstack tenant id
        VolumeType volType = new VolumeType();
        VirtualPool pool = _dbClient.queryObject(VirtualPool.class, volume_type_id);
        if (pool != null) {
            if (pool.getType().equalsIgnoreCase(VirtualPool.Type.block.name())) {
                _log.debug("Found matching vpool {}", pool.getLabel());
                StorageOSUser user = getUserFromContext();
                URI tenantId = URI.create(user.getTenantId());
                if (_permissionsHelper.tenantHasUsageACL(tenantId, pool)) {
                    _log.debug("Has permissions for vpool {}", pool.getLabel());
                    volType.id = pool.getId().toString();
                    volType.name = pool.getLabel();
                    volType.extra_specs = new HashMap<String, String>();
                }
            }
        }
        _log.debug("END get volume types {}", volume_type_id);
        return CinderApiUtils.getCinderResponse(volType, header, true);
    }

    /**
     * Gets encryption information about a specified volume type
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param volume_type_id the URN of the volume type
     * 
     * @brief Show volume type
     * @return Volume type details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_type_id}/encryption")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response getVolumeTypeEncryption(@PathParam("tenant_id") URI openstacktenant_id,
            @PathParam("volume_type_id") URI volume_type_id, @Context HttpHeaders header) {
        _log.debug("START get volume type encryption {}", volume_type_id);

        // TODO - Actual implementation needs to be added
        VolumeTypeEncryptionResponse encryptionRes = new VolumeTypeEncryptionResponse();
        VolumeTypeEncryption volType = new VolumeTypeEncryption();
        encryptionRes.setEncryption(volType);

        _log.debug("END get volume type encryption {}", volume_type_id);
        return CinderApiUtils.getCinderResponse(encryptionRes, header, true);
    }

    /**
     * Get information about a specified volume type
     * 
     * 
     * @prereq none
     * 
     * @param tenant_id the URN of the tenant
     * @param volume_type_id the URN of the volume type
     * 
     * @brief Show volume type
     * @return Volume type details
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{volume_type_id}/extra_specs")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public Response setVolumeTypeKey(@PathParam("tenant_id") URI openstacktenant_id,
            @PathParam("volume_type_id") URI volume_type_id, @Context HttpHeaders header) {
        _log.debug("START set volume types extra specs {}", volume_type_id);

        throw new UnsupportedOperationException();
    }

    /**
     * Type is a zone level resource
     */
    @Override
    protected boolean isZoneLevelResource() {
        return true;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VPOOL;
    }

    /**
     * Get object specific permissions filter
     * 
     */
    @Override
    protected ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new ProjOwnedResRepFilter(user, permissionsHelper, VirtualPool.class);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return _dbClient.queryObject(VirtualPool.class, id);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        throw new UnsupportedOperationException();
    }

}
