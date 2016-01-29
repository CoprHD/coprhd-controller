/*
 * Copyright 2013 EMC Corporation
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

import static com.emc.storageos.api.mapper.AuthMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.map;

import java.net.*;
import java.util.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

import com.emc.storageos.keystone.restapi.errorhandling.KeystoneApiException;
import com.emc.storageos.keystone.restapi.model.response.*;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.mapper.AuthMapper;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AuthnProvider;
import com.emc.storageos.db.client.model.AuthnProvider.ProvidersType;
import com.emc.storageos.db.client.model.AuthnProvider.SearchScope;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.UserGroup;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.keystone.KeystoneConstants;
import com.emc.storageos.keystone.restapi.KeystoneApiClient;
import com.emc.storageos.keystone.restapi.KeystoneRestClientFactory;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderBaseParam;
import com.emc.storageos.model.auth.AuthnProviderList;
import com.emc.storageos.model.auth.AuthnProviderParamsToValidate;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.model.auth.RoleAssignmentEntry;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.AuthSvcInternalApiClientIterator;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.security.validator.Validator;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.sun.jersey.api.client.ClientResponse;

/**
 * API for creating and manipulating authentication providers
 */
@Path("/vdc/admin/authnproviders")
@DefaultPermissions(readRoles = { Role.SECURITY_ADMIN },
        writeRoles = { Role.SECURITY_ADMIN })
public class AuthnConfigurationService extends TaggedResource {
    private static final Logger _log = LoggerFactory.getLogger(AuthnConfigurationService.class);

    private static String FEATURE_NAME_LDAP_GROUP_SUPPORT = "Group support for LDAP Authentication Provider";
    private static String OPENSTACK_DEFAULT_REGION = "RegionOne";
    private static final String OPENSTACK_CINDER_NAME = "cinderv2";
    private static final String COPRHD_PORT = ":8080/v2/%(tenant_id)s";
    private static final String HTTP = "http://";

    @Autowired
    private TenantsService _tenantsService;
    @Autowired
    private BasePermissionsHelper permissionHelper;
    @Autowired
    private AuthSvcEndPointLocator _authSvcEndPointLocator;
    private KeystoneRestClientFactory _keystoneApiFactory;
    private static final URI _URI_RELOAD = URI.create("/internal/reload");

    private static final String EVENT_SERVICE_TYPE = "authconfig";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    public void setPermissionHelper(BasePermissionsHelper permissionHelper) {
        this.permissionHelper = permissionHelper;
    }

    public void setTenantsService(TenantsService _tenantsService) {
        this._tenantsService = _tenantsService;
    }

    public void setKeystoneFactory(KeystoneRestClientFactory factory) {
        this._keystoneApiFactory = factory;
    }

    /**
     * Get detailed information for the authentication provider with the given URN
     * 
     * @param id authentication provider URN
     * @brief Show authentication provider
     * @return Provider details
     * @see AuthnProviderRestRep
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public AuthnProviderRestRep getProvider(@PathParam("id") URI id) {
        // TODO: if you need to copy/paste this code, please modify the AbstractPermissionFilter class instead and
        // related CheckPermission annotation code to support "TENANT_ADMIN_IN_ANY_TENANT" permission.
        StorageOSUser user = getUserFromContext();
        if (!_permissionsHelper.userHasGivenRoleInAnyTenant(user, Role.SECURITY_ADMIN, Role.TENANT_ADMIN)) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        return map(getProviderById(id, false));
    }

    /**
     * List authentication providers in the zone.
     * 
     * @brief List authentication providers
     * @return List of authentication providers
     */
    @GET
    // no id, just "/"
            @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
            public
            AuthnProviderList listProviders() {
        // TODO: if you need to copy/paste this code, please modify the AbstractPermissionFilter class instead and
        // related CheckPermission annotation code to support "TENANT_ADMIN_IN_ANY_TENANT" permission.
        StorageOSUser user = getUserFromContext();
        if (!_permissionsHelper.userHasGivenRoleInAnyTenant(user, Role.SECURITY_ADMIN, Role.TENANT_ADMIN)) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }

        NamedElementQueryResultList providers = new NamedElementQueryResultList();
        List<URI> uris = _dbClient.queryByType(AuthnProvider.class, true);
        List<AuthnProvider> configs = _dbClient.queryObject(AuthnProvider.class, uris);

        List<NamedElementQueryResultList.NamedElement> elements =
                new ArrayList<NamedElementQueryResultList.NamedElement>(configs.size());
        for (AuthnProvider p : configs) {
            elements.add(NamedElementQueryResultList.NamedElement.createElement(p.getId(), p.getLabel()));
        }
        providers.setResult(elements.iterator());

        AuthnProviderList list = new AuthnProviderList();
        list.getProviders().addAll(map(ResourceTypeEnum.AUTHN_PROVIDER, providers));
        return list;
    }

    @Override
    protected DataObject queryResource(URI id) {
        return getProviderById(id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Create an authentication provider.
     * The submitted provider element values will be validated.
     * <p>
     * The minimal set of parameters include: mode, server_urls, manager_dn, manager_password, domains, search_base, search_filter and
     * group_attribute
     * <p>
     * 
     * @param param AuthnCreateParam The provider representation with all necessary elements.
     * @brief Create an authentication provider
     * @return Newly created provider details as AuthnProviderRestRep
     * @see com.emc.storageos.model.auth.AuthnCreateParam
     * @see AuthnProviderRestRep
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public AuthnProviderRestRep createProvider(AuthnCreateParam param) {
        validateAuthnCreateParam(param);
        if (param.getDisable() == null || !param.getDisable()) {
            _log.debug("Validating manager dn credentials before provider creation...");
            AuthnProviderParamsToValidate validateP = AuthMapper.mapToValidateCreate(param, null);
            validateP.setUrls(new ArrayList<String>(param.getServerUrls()));
            StringBuilder errorString = new StringBuilder();
            if (!Validator.isUsableAuthenticationProvider(validateP, errorString)) {
                throw BadRequestException.badRequests.
                        authnProviderCouldNotBeValidated(errorString.toString());
            }
        }

        AuthnProvider provider = map(param);
        provider.setId(URIUtil.createId(AuthnProvider.class));

        String mode = provider.getMode();
        if (null != mode && AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(mode)) {
            populateKeystoneToken(provider, null);
            // TODO: check for checkbox value
            if(true){
                // Register CoprHD as a Block Service Provider for OpenStack.
                registerCoprhdInKeystone(provider);
            }
        } else {
            // Now validate the authn provider to make sure
            // either both group object classes and
            // member attributes present or
            // both of them empty. Throw bad request exception
            // if only one of them presents.
            validateLDAPGroupProperties(provider);
        }

        _log.debug("Saving the provider: {}: {}", provider.getId(), provider.toString());
        persistProfileAndNotifyChange(provider, true);

        auditOp(OperationTypeEnum.CREATE_AUTHPROVIDER, true, null,
                provider.toString(), provider.getId().toString());

        // TODO:
        // recordTenantEvent(RecordableEventManager.EventType.ProfiletCreated,
        // "Authentication Profile created", provider.getId());

        return map(provider);
    }

    /**
     * Register CoprHD in Keystone.
     * Creates an endpoint pointing to CoprHd instead to Cinder.
     *
     * @param provider AuthnProvider
     */
    private void registerCoprhdInKeystone(AuthnProvider provider){
        _log.info("START - register CoprHD in Keystone");

        String managerDN = provider.getManagerDN();
        URI authUri = retrieveUriFromServerUrls(provider.getServerUrls());

        String username = managerDN.split(",")[0].split("=")[1];
        String tenantName = managerDN.split(",")[1].split("=")[1];

        // Get Keystone API Client.
        KeystoneApiClient keystoneApi = (KeystoneApiClient) _keystoneApiFactory.getRESTClient(
                authUri, username, provider.getManagerPassword());
        keystoneApi.setTenantName(tenantName);
        // Get Keystone endpoints from Keystone API.
        EndpointResponse endpoints = keystoneApi.getKeystoneEndpoints();
        // Get Keystone services from Keystone API.
        ServiceResponse services = keystoneApi.getKeystoneServices();
        // Find Id of cinderv2 service.
        String cinderServiceId = retrieveCinderv2ServiceId(services);
        // Find endpoint to delete.
        EndpointV2 endpointToDelete = retrieveEndpointId(endpoints, cinderServiceId);
        // Do not execute delete call when endpoint does not exist.
        if(endpointToDelete != null){
            // Override default region name.
            OPENSTACK_DEFAULT_REGION = endpointToDelete.getRegion();
            // Delete endpoint using Keystone API.
            keystoneApi.deleteKeystoneEndpoint(endpointToDelete.getId());
        }
        // Prepare new endpoint.
        EndpointV2 newEndpoint = prepareNewCinderv2Endpoint(OPENSTACK_DEFAULT_REGION, cinderServiceId);
        // Create a new endpoint pointing to CoprHD using Keystone API.
        keystoneApi.createKeystoneEndpoint(newEndpoint);
        // Retrieve tenant from OpenStack via Keystone API.
        TenantResponse tenantResponse = keystoneApi.getKeystoneTenants();
        TenantV2 tenant = retrieveTenant(tenantResponse, tenantName);
        // TODO: create a tenant and a project

        // Create a tenant.
        _tenantsService.createSubTenant();
        // Create a project.
        _tenantsService.createProject();

        _log.info("END - register CoprHD in Keystone");
    }

    /**
     * Prepare a new endpoint for cinderv2 service.
     *
     * @param region Region assigned to the endpoint.
     * @param serviceId Cinderv2 service ID.
     * @return Endpoint filled with necessary information.
     */
    private EndpointV2 prepareNewCinderv2Endpoint(String region, String serviceId){

        String url = "";
        String localAddress;
        String localIP;
        try {
            localAddress = InetAddress.getLocalHost().toString();
            localIP = localAddress.split("/")[1];
            url = HTTP + localIP + COPRHD_PORT;
        } catch (UnknownHostException | NullPointerException e) {
            _log.error(e.getMessage());
            e.printStackTrace();
        }

        EndpointV2 endpoint = new EndpointV2();
        endpoint.setRegion(region);
        endpoint.setServiceId(serviceId);
        endpoint.setPublicURL(url);
        endpoint.setAdminURL(url);
        endpoint.setInternalURL(url);

        return endpoint;
    }

    /**
     * Retrieves OpenStack endpoint ID related to given service ID.
     *
     * @param serviceId Service ID.
     * @return ID of endpoint.
     */
    private EndpointV2 retrieveEndpointId(EndpointResponse response, String serviceId){

        for(EndpointV2 endpoint : response.getEndpoints()){
            if(endpoint.getServiceId().equals(serviceId)){
                return endpoint;
            }
        }
        // TODO: solve problem with multiple endpoints for a single service with different regions
        // Return null if there is no endpoints for cinderv2 service.
        return null;
    }

    /**
     * Retrieves OpenStack tenant with a given name.
     *
     * @param response Keystone response with tenants inside.
     * @param tenantName Name of a tenant to look for.
     * @return Tenant with given name.
     */
    private TenantV2 retrieveTenant(TenantResponse response, String tenantName){

        for(TenantV2 tenant : response.getTenants()){
            if(tenant.getName().equals(tenantName)){
                return tenant;
            }
        }
        // Return null if there is no tenant with given name.
        return null;
    }

    /**
     * Retrieves OpenStack Cinder v2 service ID.
     *
     * @param response Response containing Keystone services.
     * @return ID of service related to cinderv2.
     */
    private String retrieveCinderv2ServiceId(ServiceResponse response){

        for(ServiceV2 service : response.getServices()){
            if(service.getName().equals(OPENSTACK_CINDER_NAME)){
                return service.getId();
            }
        }
        // Raise an exception if cinderv2 service is missing.
        throw KeystoneApiException.exceptions.missingService(OPENSTACK_CINDER_NAME);
    }

    /**
     * Retrieves URI from server urls.
     *
     * @param serverUrls Set of strings representing server urls.
     * @return First URI from server urls param.
     */
    private URI retrieveUriFromServerUrls(Set<String> serverUrls){
        URI authUri = null;
        for (String uri : serverUrls) {
            authUri = URI.create(uri);
            break; // There will be single URL only
        }
        return authUri;
    }

    /**
     * Populate or Modify the keystone token
     * in authentication provider.
     * 
     * @param provider
     */
    private void populateKeystoneToken(AuthnProvider provider, String oldPassword) {
        String managerDn = provider.getManagerDN();
        Set<String> uris = provider.getServerUrls();

        URI authUri = retrieveUriFromServerUrls(uris);

        String password = "";
        if (null == oldPassword) {
            // create case
            password = provider.getManagerPassword();
        } else {
            // Update case
            String newPassword = provider.getManagerPassword();
            password = (null == newPassword) ? oldPassword :
                    (oldPassword.equals(newPassword) ? oldPassword : newPassword);
        }
        String username = managerDn.split(",")[0].split("=")[1];
        String tenantName = managerDn.split(",")[1].split("=")[1];
        KeystoneApiClient keystoneApi = (KeystoneApiClient) _keystoneApiFactory.getRESTClient(
                authUri, username, password);
        keystoneApi.setTenantName(tenantName);
        keystoneApi.authenticate_keystone();
        StringMap keystoneAuthKeys = new StringMap();
        keystoneAuthKeys.put(KeystoneConstants.AUTH_TOKEN, keystoneApi.getAuthToken());
        provider.setKeys(keystoneAuthKeys);
    }

    /**
     * Update the parameters of the target authentication provider. The ID is the URN of the authentication provider.
     * 
     * @param param required The representation of the provider parameters to be modified.
     * @param id the URN of a ViPR authentication provider
     * @param allow Set this field to true to allow modification of the group-attribute field
     * @brief Update authentication provider
     * @return Provider details with updated values
     * @see AuthnProviderRestRep
     * @see com.emc.storageos.model.auth.AuthnUpdateParam
     */
    @PUT
    @Path("/{id}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public AuthnProviderRestRep updateProvider(@PathParam("id") URI id,
            @DefaultValue("false") @QueryParam("allow_group_attr_change") boolean allow,
            AuthnUpdateParam param) {
        AuthnProvider provider = getProviderById(id, false);
        ArgValidator.checkEntityNotNull(provider, id, isIdEmbeddedInURL(id));
        validateAuthnUpdateParam(param, provider);
        // after the overlay, manager dn or manager password may end up null if they were
        // not part of the param object. If so we need to grab a copy now from the db model
        // object. When we validate, we cannot have null values.
        AuthnProviderParamsToValidate validateP = AuthMapper.mapToValidateUpdate(param, provider);
        boolean wasAlreadyDisabled = provider.getDisable();
        String mode = provider.getMode();
        if (null != mode && AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(mode)) {
            return updateKeystoneProvider(id, param, provider, validateP);
        } else {
            return updateAdOrLDAPProvider(id, allow, param, provider, validateP, wasAlreadyDisabled);
        }
    }

    private AuthnProviderRestRep updateKeystoneProvider(URI id, AuthnUpdateParam param,
            AuthnProvider provider, AuthnProviderParamsToValidate validateP) {
        String oldPassword = provider.getManagerPassword();
        overlayProvider(provider, param);
        if (!provider.getDisable()) {
            _log.debug("Validating provider before modification...");
            validateP.setUrls(new ArrayList<String>(provider.getServerUrls()));
            StringBuilder errorString = new StringBuilder();
            if (!Validator.isUsableAuthenticationProvider(validateP, errorString)) {
                throw BadRequestException.badRequests.
                        authnProviderCouldNotBeValidated(errorString.toString());
            }
        }
        populateKeystoneToken(provider, oldPassword);
        _log.debug("Saving to the DB the updated provider: {}", provider.toString());
        persistProfileAndNotifyChange(provider, false);
        auditOp(OperationTypeEnum.UPDATE_AUTHPROVIDER, true, null,
                provider.getId().toString(), provider.toString());
        return map(getProviderById(id, false));
    }

    private AuthnProviderRestRep updateAdOrLDAPProvider(URI id, boolean allow,
            AuthnUpdateParam param, AuthnProvider provider,
            AuthnProviderParamsToValidate validateP, boolean wasAlreadyDisabled) {
        // copy existing domains in separate set
        StringSet existingDomains = new StringSet(provider.getDomains());

        // up front check to see if we are modifying the group attribute.
        // this may trigger an audit.
        boolean groupAttributeModified = false;
        if (StringUtils.isNotBlank(param.getGroupAttribute()) &&
                StringUtils.isNotBlank(provider.getGroupAttribute()) &&
                !provider.getGroupAttribute().equalsIgnoreCase(param.getGroupAttribute())) {
            if (!allow) {
                throw APIException.badRequests.
                        modificationOfGroupAttributeNotAllowed(param.getGroupAttribute());
            } else {
                _log.warn("GROUP ATTRIBUTE FOR PROVIDER {} {} IS BEING " +
                        "MODIFIED USING THE FORCE FLAG, TENANT MAPPINGS, " +
                        "ROLE ASSIGNMENTS OR PROJECT ACLS MAY BE AFFECTED", provider.getLabel(),
                        provider.getId().toString());
                groupAttributeModified = true;
            }
        }

        overlayProvider(provider, param);
        // after overlay, copy new set of domains
        StringSet newDomains = new StringSet(provider.getDomains());
        // do a diff between the two sets and check any domain
        // that was removed
        existingDomains.replace(newDomains);
        Set<String> removed = existingDomains.getRemovedSet();
        if (removed != null) {
            verifyDomainsIsNotInUse(new StringSet(removed));
        }

        if (!provider.getDisable()) {
            _log.debug("Validating provider before modification...");
            validateP.setUrls(new ArrayList<String>(provider.getServerUrls()));
            StringBuilder errorString = new StringBuilder();
            if (!Validator.isUsableAuthenticationProvider(validateP, errorString)) {
                throw BadRequestException.badRequests.
                        authnProviderCouldNotBeValidated(errorString.toString());
            }
        } else if (!wasAlreadyDisabled) {
            // if we are disabling it and it wasn't already disabled, then
            // check if its domains still be in use.
            verifyDomainsIsNotInUse(provider.getDomains());
        }

        // Now validate the authn provider to make sure
        // either both group object classes and
        // member attributes present or
        // both of them empty. Throw bad request exception
        // if only one of them presents.
        validateLDAPGroupProperties(provider);

        _log.debug("Saving to the DB the updated provider: {}", provider.toString());
        persistProfileAndNotifyChange(provider, false);

        if (groupAttributeModified) {
            auditOp(OperationTypeEnum.UPDATE_AUTHPROVIDER_GROUP_ATTR, true, null,
                    provider.getId().toString(), provider.toString());
        } else {
            auditOp(OperationTypeEnum.UPDATE_AUTHPROVIDER, true, null,
                    provider.getId().toString(), provider.toString());
        }
        return map(getProviderById(id, false));
    }

    /**
     * Overlay an existing AuthnConfiguration object using a new AuthnConfiguration
     * update parameter. Regardless of whether the new AuthnConfiguration update
     * param's attribute is null or not, it will be used to overwrite existingi
     * AuthnConfiguration object's attribute or merge with existing AuthnConfiguration
     * object's attribute, because DbClient#persistObject supports partial writes.
     * 
     * @param authn
     *            The existing AuthnConfiguration object
     * 
     * @param param
     *            AuthnConfiguration update param to overlay existing AuthnConfiguration
     *            object
     */
    private void overlayProvider(AuthnProvider authn, AuthnUpdateParam param) {
        if (param == null) {
            return;
        }

        authn.setGroupAttribute(param.getGroupAttribute());

        if (param.getGroupWhitelistValueChanges() != null) {
            StringSet ssOld = authn.getGroupWhitelistValues();
            if (ssOld == null) {
                ssOld = new StringSet();
            }
            if (param.getGroupWhitelistValueChanges().getAdd() != null) {
                ssOld.addAll(param.getGroupWhitelistValueChanges().getAdd());
            }
            if (param.getGroupWhitelistValueChanges().getRemove() != null) {
                ssOld.removeAll(new HashSet<String>(param.getGroupWhitelistValueChanges().getRemove()));
            }
            authn.setGroupWhitelistValues(ssOld);
        }

        if (param.getDomainChanges() != null) {
            StringSet ssOld = authn.getDomains();
            if (ssOld == null) {
                ssOld = new StringSet();
            }
            Set<String> toAdd = param.getDomainChanges().getAdd();
            if (toAdd != null) {
                for (String s : toAdd) {
                    // When Authn provider is added, the domains are converted to lower case, we need to do the same when update provider.
                    // This change is made to fix bug CTRL-5076
                    ssOld.add(s.toLowerCase());
                }
            }
            Set<String> toRemove = param.getDomainChanges().getRemove();
            ssOld.removeAll(toRemove);
            // We shouldn't convert toRemove to lower case. It will disallow us removing the domain we don't want, moreover it might remove
            // a entry we want to keep.
            authn.setDomains(ssOld);
        }

        authn.setManagerDN(param.getManagerDn());

        authn.setManagerPassword(param.getManagerPassword());

        authn.setSearchBase(param.getSearchBase());

        authn.setSearchFilter(param.getSearchFilter());

        authn.setSearchScope(param.getSearchScope());

        if (param.getServerUrlChanges() != null) {
            StringSet ssOld = authn.getServerUrls();
            if (ssOld == null) {
                ssOld = new StringSet();
            }
            if (param.getServerUrlChanges().getAdd() != null) {
                ssOld.addAll(param.getServerUrlChanges().getAdd());
            }
            if (param.getServerUrlChanges().getRemove() != null) {
                ssOld.removeAll(new HashSet<String>(param.getServerUrlChanges().getRemove()));
            }
            if (ssOld.isEmpty()) {
                ArgValidator.checkFieldNotEmpty(ssOld,
                        "Attempt to remove the last url is not allowed.  At least one url must be in the provider.");
            }
            authn.setServerUrls(ssOld);
        }

        if (param.getMode() != null) {
            authn.setMode(param.getMode());
        }

        authn.setLabel(param.getLabel());

        authn.setDescription(param.getDescription());

        authn.setDisable(param.getDisable() != null ? param.getDisable() : authn.getDisable());

        authn.setMaxPageSize(param.getMaxPageSize());

        if (param.getGroupObjectClassChanges() != null) {
            StringSet ssOld = authn.getGroupObjectClassNames();
            if (ssOld == null) {
                ssOld = new StringSet();
            }

            if (param.getGroupObjectClassChanges().getRemove() != null) {
                ssOld.removeAll(new HashSet<String>(param.getGroupObjectClassChanges().getRemove()));
            }

            if (param.getGroupObjectClassChanges().getAdd() != null) {
                ssOld.addAll(param.getGroupObjectClassChanges().getAdd());
            }

            authn.setGroupObjectClassNames(ssOld);
        }

        if (param.getGroupMemberAttributeChanges() != null) {
            StringSet ssOld = authn.getGroupMemberAttributeTypeNames();
            if (ssOld == null) {
                ssOld = new StringSet();
            }

            if (param.getGroupMemberAttributeChanges().getRemove() != null) {
                ssOld.removeAll(new HashSet<String>(param.getGroupMemberAttributeChanges().getRemove()));
            }

            if (param.getGroupMemberAttributeChanges().getAdd() != null) {
                ssOld.addAll(param.getGroupMemberAttributeChanges().getAdd());
            }

            authn.setGroupMemberAttributeTypeNames(ssOld);
        }
    }

    /**
     * check if given domains are in use or not,
     * 
     * if any of them is in use, throw exception.
     * 
     * @param domains
     */
    private void verifyDomainsIsNotInUse(StringSet domains) {
        // check that there are no active tenants with a mapping for given domain(s).
        checkForActiveTenantsUsingDomains(domains);

        // check that there are no users/groups from given domain(s) has vdc role(s).
        checkForVdcRolesUsingDomains(domains);

        // check tenants that no users/groups from given domain(s) has tenant role(s).
        checkForTenantRolesUsingDomains(domains);

        // check if any user attributes group using the domains.
        checkForUserGroupsUsingDomains(domains);
    }

    /**
     * Queries all tenants in the system for which the passed in domain set
     * has a match in their user mapping. If so, will throw an exception.
     * Else will just return normally.
     * 
     * @param domains the domains to check
     */
    private void checkForActiveTenantsUsingDomains(StringSet domains) {
        // check that there are no active tenants with a mapping for this
        // provider's domain(s).
        List<URI> matchingTenants = new ArrayList<URI>();
        for (String domainToCheck : domains) {
            Map<URI, List<UserMapping>> mappings = _permissionsHelper.getAllUserMappingsForDomain(domainToCheck);
            Set<URI> tenantIDset;
            if (mappings == null) {
                _log.debug("No matching tenant found for domain {}", domainToCheck);
                continue;
            }
            tenantIDset = mappings.keySet();
            _log.debug("{} matching tenants found for domain {}", tenantIDset.size(), domainToCheck);
            List<TenantOrg> tenants = _dbClient.queryObject(TenantOrg.class,
                    new ArrayList<URI>(tenantIDset));
            if (tenants != null) {
                for (TenantOrg tenant : tenants) {
                    if (!tenant.getInactive()) {
                        matchingTenants.add(tenant.getId());
                    }
                }
            }
        }
        if (!matchingTenants.isEmpty()) {
            throw APIException.badRequests.cannotDeleteAuthProviderWithTenants(matchingTenants.size(),
                    matchingTenants);
        }
    }

    /**
     * check local vdc to see if any vdc role assignments belongs to the passed in domain set.
     * if so, throw an exception;
     * else, return silently.
     * 
     * @param domains the domains to check
     */
    private void checkForVdcRolesUsingDomains(StringSet domains) {
        // get local vdc's role assignments
        VirtualDataCenter localVdc = VdcUtil.getLocalVdc();
        List<RoleAssignmentEntry> vdcRoles =
                _permissionsHelper.convertToRoleAssignments(localVdc.getRoleAssignments(), true);

        List<String> matchingUsers = checkRolesUsingDomains(vdcRoles, domains);

        if (!matchingUsers.isEmpty()) {
            throw APIException.badRequests.cannotDeleteAuthProviderWithVdcRoles(matchingUsers.size(), matchingUsers);
        }
    }

    /**
     * check tenants to see if any tenant role assignments belongs to the passed in domain set.
     * if so, throw an exception;
     * else, return silently.
     * 
     * @param domains the domains to check
     */
    private void checkForTenantRolesUsingDomains(StringSet domains) {
        List<URI> tenantURIList = _dbClient.queryByType(TenantOrg.class, true);
        for (URI tenantURI : tenantURIList) {
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, tenantURI);
            _log.debug("checking " + tenant.getLabel());
            List<RoleAssignmentEntry> tenantRoles =
                    _permissionsHelper.convertToRoleAssignments(tenant.getRoleAssignments(), false);

            List<String> matchingUsers = checkRolesUsingDomains(tenantRoles, domains);
            if (!matchingUsers.isEmpty()) {
                throw APIException.badRequests.cannotDeleteAuthProviderWithTenantRoles(tenant.getLabel(), matchingUsers.size(),
                        matchingUsers);
            }
        }

    }

    /**
     * compare role assignments against domain(s), return matching users.
     * 
     * @param roleAssignments
     * @param domains
     */
    private List<String> checkRolesUsingDomains(List<RoleAssignmentEntry> roleAssignments, StringSet domains) {

        List<String> matchingUsers = new ArrayList<String>();
        for (RoleAssignmentEntry roleAssignment : roleAssignments) {
            String idOrGroup = !StringUtils.isEmpty(roleAssignment.getSubjectId()) ?
                    roleAssignment.getSubjectId() : roleAssignment.getGroup();

            _log.debug("checking " + idOrGroup);
            String domain = "";
            if (idOrGroup.lastIndexOf("@") != -1) {
                domain = idOrGroup.substring(idOrGroup.lastIndexOf("@") + 1);
            } else {
                continue;
            }

            for (String domainToCheck : domains) {
                if (domainToCheck.equalsIgnoreCase(domain)) {
                    matchingUsers.add(idOrGroup);
                }
            }
        }

        return matchingUsers;
    }

    /**
     * Delete the provider with the provided id.
     * Authentication services will no longer use this provider for authentication.
     * 
     * @param id the URN of a ViPR provider authentication to be deleted
     * @brief Delete authentication provider
     * @return No data returned in response body
     */
    @DELETE
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN })
    public Response deleteProvider(@PathParam("id") URI id) {
        AuthnProvider provider = getProviderById(id, false);
        ArgValidator.checkEntityNotNull(provider, id, isIdEmbeddedInURL(id));
        checkForActiveTenantsUsingDomains(provider.getDomains());
        if (!AuthnProvider.ProvidersType.keystone.toString()
                .equalsIgnoreCase(provider.getMode())) {
            checkForVdcRolesUsingDomains(provider.getDomains());
            checkForUserGroupsUsingDomains(provider.getDomains());
            verifyDomainsIsNotInUse(provider.getDomains());
        }
        _dbClient.removeObject(provider);
        notifyChange();

        // TODO - record event

        auditOp(OperationTypeEnum.DELETE_AUTHPROVIDER, true, null,
                provider.getId().toString());
        return Response.ok().build();
    }

    /**
     * @param id the URN of a ViPR authentication provider to get details from
     * @param checkInactive If true, make sure that provider is not inactive
     * @return AuthnProvider object for the given id
     */
    private AuthnProvider getProviderById(URI id, boolean checkInactive) {
        if (id == null) {
            _log.debug("Provider ID is NULL");
            return null;
        }
        _log.debug("Provider ID is {}", id.toString());
        AuthnProvider provider = _permissionsHelper.getObjectById(id, AuthnProvider.class);
        ArgValidator.checkEntity(provider, id, isIdEmbeddedInURL(id), checkInactive);
        return provider;
    }

    /**
     * Checks if an authn provider exists for the given domain
     * 
     * @param domain
     * @return true if a provider exists, false otherwise
     */
    private boolean doesProviderExistForDomain(String domain) {
        URIQueryResultList providers = new URIQueryResultList();
        try {
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getAuthnProviderDomainConstraint(domain),
                    providers);
        } catch (DatabaseException ex) {
            _log.error("Could not query for authn providers to check for existing domain {}", domain, ex.getStackTrace());
            throw ex;
        }
        return providers.iterator().hasNext();
    }

    private void validateAuthnProviderParam(AuthnProviderBaseParam param, AuthnProvider provider,
            Set<String> server_urls, Set<String> domains, Set<String> group_whitelist_values) {
        if (param.getLabel() != null && !param.getLabel().isEmpty()) {
            if (provider == null || !provider.getLabel().equalsIgnoreCase(param.getLabel())) {
                checkForDuplicateName(param.getLabel(), AuthnProvider.class);
            }
        }
        if (param.getMode() != null) {
            ArgValidator.checkFieldNotEmpty(param.getMode(), "mode");
            try {
                ProvidersType.valueOf(param.getMode());
            } catch (IllegalArgumentException ex) {
                throw APIException.badRequests.invalidParameter("mode", param.getMode(), ex);
            }
        }
        if (param.getManagerDn() != null) {
            ArgValidator.checkFieldNotEmpty(param.getManagerDn(), "manager_dn");
        }
        if (param.getManagerPassword() != null) {
            ArgValidator.checkFieldNotEmpty(param.getManagerPassword(), "manager_password");
        }
        // validate syntax of the provided parameters for POST and PUT operations
        if (domains != null) {
            ArgValidator.checkFieldNotEmpty(domains, "domains");
            for (String domain : domains) {
                ArgValidator.checkFieldNotEmpty(domain, "domain");
                if (provider == null || !provider.getDomains().contains(domain.toLowerCase())) {
                    if (doesProviderExistForDomain(domain)) {
                        throw APIException.badRequests.domainAlreadyExists(domain);
                    }
                }
            }
        }
        if (param.getGroupAttribute() != null) {
            ArgValidator.checkFieldNotEmpty(param.getGroupAttribute(), "group_attribute");
        }
        // Just validate that the mode is one of the enums
        if (group_whitelist_values != null) {
            for (String groupName : group_whitelist_values) {
                ArgValidator.checkFieldNotEmpty(groupName, "group_whitelist_values");
            }
        }
        if (!AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(param.getMode())) {
            if (server_urls != null) {
                boolean isNonSecure = false;
                boolean isSecure = false;
                for (String url : server_urls) {
                    ArgValidator.checkFieldNotEmpty(url, "server_urls");
                    String lowerCaseUrl = url.toLowerCase();
                    if (lowerCaseUrl.startsWith("ldap://")) {
                        isNonSecure = true;
                    } else if (lowerCaseUrl.startsWith("ldaps://")) {
                        isSecure = true;
                    } else {
                        throw APIException.badRequests.invalidParameter("server_url", url);
                    }
                    if (isNonSecure && isSecure) {
                        throw APIException.badRequests.cannotMixLdapAndLdapsUrls();
                    }
                }
            }
            String searchFilter = param.getSearchFilter();
            if (searchFilter != null) {
                if (!searchFilter.contains("=")) {
                    throw APIException.badRequests.searchFilterMustContainEqualTo();
                }

                String afterEqual = searchFilter.substring(searchFilter.indexOf("="));
                if (!afterEqual.contains("%u") && !afterEqual.contains("%U")) {
                    throw APIException.badRequests.searchFilterMustContainPercentU();
                }
            }
            String searchScope = param.getSearchScope();
            if (searchScope != null) {
                try {
                    // Just validate that the scope is one of the enums
                    SearchScope.valueOf(param.getSearchScope());
                } catch (IllegalArgumentException ex) {
                    throw APIException.badRequests.invalidParameter("search_scope", param.getSearchScope(), ex);
                }
            }
            if (param.getSearchBase() != null) {
                ArgValidator.checkFieldNotEmpty(param.getSearchBase(), "search_base");
            }
            if (param.getMaxPageSize() != null) {
                if (param.getMaxPageSize() <= 0) {
                    throw APIException.badRequests.parameterMustBeGreaterThan("max_page_size", 0);
                }
            }
        } else {
            String managerDn = param.getManagerDn();
            if (managerDn != null) {
                if (!managerDn.contains("=")) {
                    throw APIException.badRequests.managerDNMustcontainEqualTo();
                }

                if (!(managerDn.contains("username") || managerDn.contains("userName"))
                        || !(managerDn.contains("tenantName") || managerDn.contains("tenantname"))) {
                    throw APIException.badRequests.managerDNMustcontainUserNameAndTenantName();
                }
            }
        }
    }

    private void validateAuthnCreateParam(AuthnCreateParam param) {
        if (param == null) {
            throw APIException.badRequests.resourceEmptyConfiguration("authn provider");
        }

        _log.debug("Zone authentication create param: {}", param);

        ArgValidator.checkFieldNotNull(param.getMode(), "mode");
        ArgValidator.checkFieldNotNull(param.getManagerDn(), "manager_dn");
        ArgValidator.checkFieldNotNull(param.getManagerPassword(), "manager_password");
        // The syntax for search_filter will be checked in the following section of this function
        // Check that the LDAP server URL is present.
        // The syntax will be checked in the following section of this function
        ArgValidator.checkFieldNotEmpty(param.getServerUrls(), "server_urls");

        // The domains tag must be present in any new profile
        ArgValidator.checkFieldNotNull(param.getDomains(), "domains");
        if (!AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(param.getMode())) {
            ArgValidator.checkFieldNotNull(param.getSearchFilter(), "search_filter");
            ArgValidator.checkFieldNotNull(param.getSearchBase(), "search_base");
        } else {
            ensureSingleKeystoneProvider(param);
        }

        checkIfCreateLDAPGroupPropertiesSupported(param);

        validateAuthnProviderParam(param, null, param.getServerUrls(), param.getDomains(),
                param.getGroupWhitelistValues());
    }

    /**
     * Ensures that there exists a single keystone provider always.
     * 
     * Requests for keystone authentication provider will be coming from OpenStack
     * environment, since it is not possible to figure out which keystone provider to
     * use for which request, hence only single keystone authentication provider is
     * supported.
     * 
     * @param param
     */
    private void ensureSingleKeystoneProvider(AuthnCreateParam param) {
        List<URI> allProviders = _dbClient.queryByType(AuthnProvider.class, true);
        for (URI providerURI : allProviders) {
            AuthnProvider provider = getProviderById(providerURI, true);
            if (AuthnProvider.ProvidersType.keystone.toString().equalsIgnoreCase(provider.getMode().toString())) {
                throw APIException.badRequests.keystoneProviderAlreadyPresent();
            }
        }

    }

    private void validateAuthnUpdateParam(AuthnUpdateParam param, AuthnProvider provider) {
        if (param == null) {
            throw APIException.badRequests.resourceEmptyConfiguration("authn provider");
        }
        _log.debug("Vdc authentication update param: {}", param);

        Set<String> server_urls = null;
        Set<String> domains = null;
        Set<String> group_whitelist_values = null;
        if (param.getServerUrlChanges() != null && !param.getServerUrlChanges().getAdd().isEmpty()) {
            server_urls = param.getServerUrlChanges().getAdd();
        }
        // validate syntax of the provided parameters for POST and PUT operations
        if (param.getDomainChanges() != null && !param.getDomainChanges().getAdd().isEmpty()) {
            domains = param.getDomainChanges().getAdd();
        }
        if (param.getGroupWhitelistValueChanges() != null &&
                !param.getGroupWhitelistValueChanges().getAdd().isEmpty()) {
            group_whitelist_values = param.getGroupWhitelistValueChanges().getAdd();
        }

        checkIfUpdateLDAPGroupPropertiesSupported(param);

        validateAuthnProviderParam(param, provider, server_urls, domains, group_whitelist_values);
    }

    /**
     * Call the internode URI on all authSvc endpoints to reload the auth handlers
     */
    private void notifyChange() {
        try {
            AuthSvcInternalApiClientIterator authSvcItr = new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator, _coordinator);
            while (authSvcItr.hasNext()) {
                String endpoint = authSvcItr.peek().toString();
                try {
                    ClientResponse response = authSvcItr.post(_URI_RELOAD, null);
                    if (response.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
                        _log.error("Failed to reload authN providers on endpoint {} response {}", endpoint, response.toString());
                    }
                } catch (Exception e) {
                    _log.error("Caught exception trying to reload an authsvc on {} continuing", endpoint, e);
                }
            }
        } catch (CoordinatorException e) {
            _log.error("Caught coordinator exception trying to find an authsvc endpoint", e);
        }
    }

    /**
     * update the timestamp and notify
     */
    private synchronized void persistProfileAndNotifyChange(AuthnProvider modifiedProvider,
            boolean newObject) {
        modifiedProvider.setLastModified(System.currentTimeMillis());
        if (newObject) {
            _dbClient.createObject(modifiedProvider);
        } else {
            _dbClient.persistObject(modifiedProvider);
        }
        notifyChange();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<AuthnProvider> getResourceClass() {
        return AuthnProvider.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.AUTHN_PROVIDER;
    }

    /***
     * Make sure all the VDCs in the federation are in the minimum expected version
     * for using the ldap group support.
     * 
     * @param createParam set of group object classes.
     */
    private void checkIfCreateLDAPGroupPropertiesSupported(AuthnCreateParam createParam) {
        boolean checkCompatibleVersion = false;
        if (createParam != null) {
            if (StringUtils.isNotEmpty(createParam.getGroupAttribute())) {
                checkCompatibleVersion = true;
            } else if (!CollectionUtils.isEmpty(createParam.getGroupWhitelistValues())) {
                checkCompatibleVersion = true;
            } else if (!CollectionUtils.isEmpty(createParam.getGroupObjectClasses())) {
                checkCompatibleVersion = true;
            } else if (!CollectionUtils.isEmpty(createParam.getGroupMemberAttributes())) {
                checkCompatibleVersion = true;
            }
        }

        if (checkCompatibleVersion) {
            checkCompatibleVersionForLDAPGroupSupport();
        }
    }

    /***
     * Make sure all the VDCs in the federation are in the minimum expected version
     * for using the ldap group support.
     * 
     * @param updateParam set of group object classes.
     */
    private void checkIfUpdateLDAPGroupPropertiesSupported(AuthnUpdateParam updateParam) {
        boolean checkCompatibleVersion = false;
        if (updateParam != null) {
            if (StringUtils.isNotBlank(updateParam.getGroupAttribute())) {
                checkCompatibleVersion = true;
            } else if (updateParam.getGroupWhitelistValueChanges() != null &&
                    (!CollectionUtils.isEmpty(updateParam.getGroupWhitelistValueChanges().getAdd()) ||
                    !CollectionUtils.isEmpty(updateParam.getGroupWhitelistValueChanges().getRemove()))) {
                checkCompatibleVersion = true;
            } else if (updateParam.getGroupObjectClassChanges() != null &&
                    (!CollectionUtils.isEmpty(updateParam.getGroupObjectClassChanges().getAdd()) ||
                    !CollectionUtils.isEmpty(updateParam.getGroupObjectClassChanges().getRemove()))) {
                checkCompatibleVersion = true;
            } else if (updateParam.getGroupMemberAttributeChanges() != null &&
                    (!CollectionUtils.isEmpty(updateParam.getGroupMemberAttributeChanges().getAdd()) ||
                    !CollectionUtils.isEmpty(updateParam.getGroupMemberAttributeChanges().getRemove()))) {
                checkCompatibleVersion = true;
            }
        }

        if (checkCompatibleVersion) {
            checkCompatibleVersionForLDAPGroupSupport();
        }
    }

    /**
     * Check if all the VDCs in the federation are in the same expected
     * or minimum supported version for this api.
     * 
     */
    private void checkCompatibleVersionForLDAPGroupSupport() {
        if (!_dbClient.checkGeoCompatible(AuthnProvider.getExpectedGeoVDCVersionForLDAPGroupSupport())) {
            throw APIException.badRequests.incompatibleGeoVersions(AuthnProvider.getExpectedGeoVDCVersionForLDAPGroupSupport(),
                    FEATURE_NAME_LDAP_GROUP_SUPPORT);
        }
    }

    /***
     * Validate the ldap group properties (object classes and member attributes)
     * and throws bad request exception if either one of object classes
     * or member attributes present in the authn provider.
     * 
     * @param authnProvider to be validated for ldap group properties.
     * @throws APIException
     */
    private void validateLDAPGroupProperties(AuthnProvider authnProvider) {
        if (authnProvider == null) {
            _log.error("Invalid authentication provider");
            return;
        }

        boolean isGroupObjectClassesEmpty = CollectionUtils.isEmpty(authnProvider.getGroupObjectClassNames());
        boolean isGroupMemberAttributesEmpty = CollectionUtils.isEmpty(authnProvider.getGroupMemberAttributeTypeNames());

        // Return error if either only one of object classes or member attributes
        // is entered and other one is empty.
        if (isGroupObjectClassesEmpty ^ isGroupMemberAttributesEmpty) {
            String param = "Group object classes";
            if (!isGroupObjectClassesEmpty) {
                param = "Group member attributes";
            }
            throw APIException.badRequests.authnProviderGroupObjectClassesAndMemberAttributesRequired(param);
        }
    }

    /**
     * Check if any of the user group using the domains in the authnprovider or not.
     * if so, throw an exception;
     * else, return silently.
     * 
     * @param domains the domains to check
     */
    private void checkForUserGroupsUsingDomains(StringSet domains) {
        if (CollectionUtils.isEmpty(domains)) {
            _log.error("Invalid domains");
            return;
        }

        Set<URI> matchingUserGroupsURI = new HashSet<URI>();
        for (String domain : domains) {
            if (StringUtils.isBlank(domain)) {
                _log.warn("Invalid domain");
                continue;
            }

            List<UserGroup> matchingUserGroup = _permissionsHelper.getAllUserGroupForDomain(domain);
            if (CollectionUtils.isEmpty(matchingUserGroup)) {
                _log.debug("No user group found for the domain {}", domain);
                continue;
            }

            for (UserGroup userGroup : matchingUserGroup) {
                matchingUserGroupsURI.add(userGroup.getId());
            }
        }

        if (!CollectionUtils.isEmpty(matchingUserGroupsURI)) {
            throw APIException.badRequests.cannotDeleteAuthnProviderWithUserGroup(matchingUserGroupsURI.size(),
                    matchingUserGroupsURI);
        }
    }
}
