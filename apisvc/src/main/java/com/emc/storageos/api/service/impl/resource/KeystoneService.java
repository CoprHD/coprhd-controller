/*
 * Copyright 2016 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.api.service.impl.resource;

import com.emc.storageos.api.mapper.DbObjectMapper;
import com.emc.storageos.api.service.impl.resource.utils.OpenStackSynchronizationTask;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.model.response.TenantListRestResp;
import com.emc.storageos.keystone.restapi.model.response.TenantV2;
import com.emc.storageos.keystone.restapi.utils.KeystoneUtils;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.keystone.CoprhdOsTenant;
import com.emc.storageos.model.keystone.CoprhdOsTenantListRestRep;
import com.emc.storageos.model.keystone.OpenStackTenantListParam;
import com.emc.storageos.model.keystone.OpenStackTenantParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * API for manipulating OpenStack Keystone service.
 */
@Path("/v2/keystone")
@DefaultPermissions(readRoles = { Role.SECURITY_ADMIN }, writeRoles = { Role.SECURITY_ADMIN })
public class KeystoneService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(KeystoneService.class);

    private KeystoneUtils _keystoneUtils;
    private OpenStackSynchronizationTask _openStackSynchronizationTask;
    private AuthnConfigurationService _authService;

    public void setAuthService(AuthnConfigurationService authService) {
        this._authService = authService;
    }

    public void setOpenStackSynchronizationTask(OpenStackSynchronizationTask openStackSynchronizationTask) {
        this._openStackSynchronizationTask = openStackSynchronizationTask;
    }

    public void setKeystoneUtils(KeystoneUtils keystoneUtils) {
        this._keystoneUtils = keystoneUtils;
    }

    /**
     * Get a list of OpenStack Tenants.
     * Uses data from Keystone Authentication Provider to connect Keystone and retrieve Tenants information.
     *
     * @brief Show OpenStack Tenants.
     * @return OpenStack Tenants details.
     * @see TenantListRestResp
     */
    @GET
    @Path("/tenants")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public TenantListRestResp listOpenstackTenants() {

        _log.debug("Keystone Service - listOpenstackTenants");

        StorageOSUser user = getUserFromContext();
        if (!_permissionsHelper.userHasGivenRoleInAnyTenant(user, Role.SECURITY_ADMIN, Role.TENANT_ADMIN)) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        AuthnProvider keystoneProvider = _keystoneUtils.getKeystoneProvider();

        // Get OpenStack Tenants only when Keystone Provider exists.
        if (keystoneProvider != null) {

            KeystoneApiClient keystoneApiClient = _keystoneUtils.getKeystoneApi(keystoneProvider.getManagerDN(),
                    keystoneProvider.getServerUrls(), keystoneProvider.getManagerPassword());

            List<TenantV2> OSTenantList = new ArrayList<>(Arrays.asList(keystoneApiClient.getKeystoneTenants().getTenants()));

            TenantListRestResp response = new TenantListRestResp();
            response.setOpenstackTenants(OSTenantList);

            return response;
        }

        throw APIException.internalServerErrors.targetIsNullOrEmpty("Keystone Authentication Provider");
    }

    /**
     * Get OpenStack Tenant with given ID.
     * Uses data from Keystone Authentication Provider to connect Keystone and retrieve Tenant information.
     *
     * @param id OpenStack Tenant ID.
     * @brief Show OpenStack Tenant.
     * @return OpenStack Tenant details.
     * @see TenantListRestResp
     */
    @GET
    @Path("/tenants/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public OpenStackTenantParam getOpenstackTenant(@PathParam("id") URI id) {

        if (id == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Tenant ID");
        }

        _log.debug("Keystone Service - getOpenstackTenant with id: {}", id.toString());

        List<TenantV2> tenants = listOpenstackTenants().getOpenstackTenants();

        for (TenantV2 tenant : tenants) {
            if (tenant.getId().equals(id.toString())) {
                return mapToOpenstackParam(tenant);
            }
        }

        throw APIException.notFound.unableToFindEntityInURL(id);
    }

    /**
     * Creates representation of OpenStack Tenants in CoprHD.
     *
     * @param param OpenStackTenantListParam OpenStack Tenants representation with all necessary elements.
     * @brief Creates representation of OpenStack Tenants in CoprHD.
     * @return Newly created Tenants.
     * @see
     */
    @POST
    @Path("/tenants")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public CoprhdOsTenantListRestRep saveOpenstackTenants(OpenStackTenantListParam param) {

        _log.debug("Keystone Service - saveOpenstackTenants");

        if (param.getOpenstackTenants() == null || param.getOpenstackTenants().isEmpty()) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Tenant list param");
        }

        List<OSTenant> openstackTenants = new ArrayList<>();

        for (OpenStackTenantParam openStackTenantParam : param.getOpenstackTenants()) {
            openstackTenants.add(prepareOpenstackTenant(openStackTenantParam));
        }

        if (!openstackTenants.isEmpty()) {
            _dbClient.createObject(openstackTenants);
        }

        AuthnProvider keystoneProvider = _keystoneUtils.getKeystoneProvider();

        if (keystoneProvider == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Keystone Authentication Provider");
        }

        if (keystoneProvider.getAutoRegCoprHDNImportOSProjects()) {

            if (_openStackSynchronizationTask.getSynchronizationTask() == null) {
                // Do not create Tenants and Projects once synchronization task is running.
                _authService.createTenantsAndProjectsForAutomaticKeystoneRegistration();
                _openStackSynchronizationTask.startSynchronizationTask(_openStackSynchronizationTask.getTaskInterval());
            }
        }

        return map(openstackTenants);
    }

    /**
     * Updates representation of OpenStack Tenants in CoprHD.
     * Creates Tenants and Projects for new Tenants and deletes them for excluded Tenants.
     *
     * @param param OpenStackTenantListParam OpenStack Tenants representation with all necessary elements for update.
     * @brief Updates representation of OpenStack Tenants in CoprHD.
     * @return Updated Tenants.
     * @see
     */
    @PUT
    @Path("/ostenants")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public CoprhdOsTenantListRestRep updateOpenstackTenants(CoprhdOsTenantListRestRep param) {

        _log.debug("Keystone Service - updateOpenstackTenants");

        if (param.getCoprhdOsTenants() == null || param.getCoprhdOsTenants().isEmpty()) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Tenant list param");
        }

        CoprhdOsTenantListRestRep resp = new CoprhdOsTenantListRestRep();
        List<OSTenant> tenantsToUpdate = new ArrayList<>();
        List<OSTenant> tenantsToDelete = new ArrayList<>();
        OSTenant osTenant;
        for (CoprhdOsTenant tenant : param.getCoprhdOsTenants()) {
            osTenant = _dbClient.queryObject(OSTenant.class, tenant.getId());

            if (!osTenant.getExcluded().equals(tenant.getExcluded())) {
                // Tenant changed from included to excluded. Mark for deletion related Tenant and Project.
                if (!osTenant.getExcluded()) {
                    tenantsToDelete.add(osTenant);
                } else {
                    tenantsToUpdate.add(osTenant);
                }
                osTenant.setExcluded(tenant.getExcluded());
                resp.getCoprhdOsTenants().add(mapToCoprhdOsTenant(osTenant));
            }
        }

        // List of CoprHD Tenants mapped with OpenStack ID.
        List<TenantOrg> tenantOrgs = _keystoneUtils.getCoprhdTenantsWithOpenStackId();

        if (!tenantsToUpdate.isEmpty()) {
            // Create Tenant and Project for included Tenants.
            for (OSTenant tenant : tenantsToUpdate) {
                if (_keystoneUtils.getCoprhdTenantWithOpenstackId(tenant.getOsId()) == null) {
                    _authService.createTenantAndProjectForOpenstackTenant(tenant);
                }
            }
        }

        tenantsToUpdate.addAll(tenantsToDelete);

        if (!tenantsToUpdate.isEmpty()) {
            _dbClient.updateObject(tenantsToUpdate);
        }

        if (!tenantsToDelete.isEmpty()) {

            for (OSTenant tenant : tenantsToDelete) {
                TenantOrg tenantOrg = _keystoneUtils.getCoprhdTenantWithOpenstackId(tenant.getOsId());
                if (tenantOrg != null && !TenantOrg.isRootTenant(tenantOrg)) {
                    URIQueryResultList uris = new URIQueryResultList();
                    _dbClient.queryByConstraint(
                            PrefixConstraint.Factory.getTagsPrefixConstraint(
                                    Project.class, tenant.getOsId(), tenantOrg.getId()),
                            uris);

                    for (URI projectUri : uris) {
                        Project project = _dbClient.queryObject(Project.class, projectUri);
                        ArgValidator.checkReference(Project.class, project.getId(), checkForDelete(project));
                        _dbClient.markForDeletion(project);
                    }

                    ArgValidator.checkReference(TenantOrg.class, tenantOrg.getId(), checkForDelete(tenantOrg));
                    _dbClient.markForDeletion(tenantOrg);
                }
            }
        }

        return resp;
    }

    /**
     * Gets a list of CoprHd representation of OpenStack Tenants (OSTenant).
     *
     * @brief Show CoprHD OpenStack Tenants.
     * @return CoprHD OpenStack Tenants details.
     * @see CoprhdOsTenantListRestRep
     */
    @GET
    @Path("/ostenants")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public CoprhdOsTenantListRestRep listCoprhdOsTenants() {

        _log.debug("Keystone Service - listCoprhdOsTenants");

        List<OSTenant> tenants = getOsTenantsFromCoprhdDb();

        return map(tenants);
    }

    private List<OSTenant> getOsTenantsFromCoprhdDb() {

        List<URI> osTenantURIs = _dbClient.queryByType(OSTenant.class, true);

        return _dbClient.queryObject(OSTenant.class, osTenantURIs);
    }

    /**
     * Gets a CoprHd representation of OpenStack Tenant (OSTenant) with given ID.
     *
     * @param id CoprHD Tenant ID.
     * @brief Show CoprHD Tenant.
     * @return CoprHD Tenant details.
     * @see CoprhdOsTenant
     */
    @GET
    @Path("/ostenants/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public CoprhdOsTenant getCoprhdOsTenant(@PathParam("id") URI id) {

        if (id == null) {
            throw APIException.internalServerErrors.targetIsNullOrEmpty("Tenant ID");
        }

        _log.debug("Keystone Service - getCoprhdOsTenant with id: {}", id.toString());

        OSTenant osTenant = _dbClient.queryObject(OSTenant.class, id);

        if (osTenant != null) {
            return mapToCoprhdOsTenant(osTenant);
        }

        throw APIException.notFound.unableToFindEntityInURL(id);
    }

    /**
     * Synchronize CoprHD and OpenStack Tenants in CoprHD database.
     *
     * @brief Updates CoprHD OSTenant objects.
     * @return Response code 200
     * @see OSTenant
     */
    @PUT
    @Path("/ostenants/sync")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public Response synchronizeOpenstackTenants() {

        List<TenantV2> openstackTenantsList = listOpenstackTenants().getOpenstackTenants();
        List<OSTenant> coprhdOsTenantsList = getOsTenantsFromCoprhdDb();

        for (Iterator<TenantV2> i = openstackTenantsList.iterator(); i.hasNext();) {

            TenantV2 tenant = i.next();
            for (Iterator<OSTenant> j = coprhdOsTenantsList.iterator(); j.hasNext();) {

                OSTenant osTenant = j.next();
                // Remove Tenant from both list once match is found.
                if (osTenant.getOsId().equals(tenant.getId())) {
                    j.remove();
                    i.remove();
                }
            }
        }

        // Remove OSTenant from CoprHD when related Tenant was removed in OpenStack.
        for (OSTenant osTenant : coprhdOsTenantsList) {
            _dbClient.removeObject(osTenant);
        }

        // Create OOSTenant in CoprHD for every new Tenant in OpenStack.
        for (TenantV2 tenantV2 : openstackTenantsList) {
            OSTenant tenant = _keystoneUtils.mapToOsTenant(tenantV2);
            tenant.setId(URIUtil.createId(OSTenant.class));
            _dbClient.createObject(tenant);
        }

        return Response.ok().build();
    }

    private OSTenant prepareOpenstackTenant(OpenStackTenantParam param) {

        OSTenant openstackTenant = new OSTenant();
        openstackTenant.setId(URIUtil.createId(OSTenant.class));
        openstackTenant.setName(param.getName());
        openstackTenant.setDescription(_keystoneUtils.getProperTenantDescription(param.getDescription()));
        openstackTenant.setEnabled(param.getEnabled());
        openstackTenant.setExcluded(param.getExcluded());
        openstackTenant.setOsId(param.getOsId());
        return openstackTenant;
    }

    private CoprhdOsTenantListRestRep map(List<OSTenant> tenants) {

        CoprhdOsTenantListRestRep response = new CoprhdOsTenantListRestRep();
        List<CoprhdOsTenant> coprhdOsTenants = new ArrayList<>();
        for (OSTenant osTenant : tenants) {
            coprhdOsTenants.add(mapToCoprhdOsTenant(osTenant));
        }
        response.setCoprhdOsTenants(coprhdOsTenants);

        return response;
    }

    private OpenStackTenantParam mapToOpenstackParam(TenantV2 from) {
        OpenStackTenantParam to = new OpenStackTenantParam();

        to.setOsId(from.getId());
        to.setDescription(from.getDescription());
        to.setEnabled(Boolean.parseBoolean(from.getEnabled()));
        to.setExcluded(false);
        to.setName(from.getName());

        return to;
    }

    private CoprhdOsTenant mapToCoprhdOsTenant(OSTenant from) {

        CoprhdOsTenant to = new CoprhdOsTenant();
        DbObjectMapper.mapDataObjectFields(from, to);
        to.setExcluded(from.getExcluded());
        to.setDescription(from.getDescription());
        to.setOsId(from.getOsId());
        to.setEnabled(from.getEnabled());
        to.setName(from.getName());

        return to;
    }

    @Override
    protected DataObject queryResource(URI id) {
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return null;
    }
}
