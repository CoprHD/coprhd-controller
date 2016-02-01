/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.validator;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.model.auth.AuthnProviderParamsToValidate;
import com.emc.storageos.model.auth.PrincipalsToValidate;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.AuthSvcInternalApiClientIterator;
import com.emc.storageos.security.authentication.ServiceLocatorInfo;
import com.emc.storageos.security.authentication.StorageOSUserRepository;
import com.emc.storageos.security.exceptions.FatalSecurityException;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.resource.UserInfoPage.UserTenantList;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.sun.jersey.api.client.ClientResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * validates if a user/group is valid for the given domain of AD/LDAP
 */
public class Validator {
    private static final int _MAX_VALIDATION_RETRIES = 5;
    private static final Logger _log = LoggerFactory
            .getLogger(Validator.class);
    private static final URI _URI_VALIDATE = URI.create("/internal/principalValidate");
    private static final URI _URI_VALIDATE_PRINCIPALS = URI
            .create("/internal/principalsValidate");
    private static final URI _URI_REFRESH = URI.create("/internal/refreshUser");
    private static final URI _URI_USERTENANT = URI.create("/internal/userTenant");
    private static final URI _URI_VALIDATE_AUTHNPROVIDER = URI.create("/internal/authnProviderValidate");
    private static AuthSvcEndPointLocator _authSvcEndPointLocator;
    private static CoordinatorClient _coordinator;
    private static StorageOSUserRepository _repository;

    /**
     * Validates the principal within the tenant
     * 
     * @param principal
     * @param tenantId : tenant id
     * @return true if the principal is valid within the tenant
     */
    public static boolean isValidPrincipal(StorageOSPrincipal principal, URI tenantId) {
        StringBuilder error = new StringBuilder();
        return Validator.isValidPrincipal(principal, tenantId, error);
    }

    /**
     * Validates the principal within the tenant
     * 
     * @param principal
     * @param tenantId
     *            : tenant id
     * @param error
     *            : a string representing that error that happened.
     * @return true if the principal is valid within the tenant
     */
    public static boolean isValidPrincipal(StorageOSPrincipal principal, URI tenantId,
            StringBuilder error) {

        String queryParams = null;

        switch (principal.getType()) {
            case User:
                String encodedPrincipal;
                String encodedTenant;
                try {
                    encodedPrincipal = URLEncoder.encode(principal.getName(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw APIException.badRequests.unableToEncodeString(principal.getName(),
                            e);
                }
                try {
                    encodedTenant = URLEncoder.encode(tenantId.toString(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw APIException.badRequests.unableToEncodeString(tenantId.toString(), e);
                }
                queryParams = "?subject_id=" + encodedPrincipal + "&tenant_id="
                        + encodedTenant;
                break;
            case Group:
                try {
                    queryParams = "?group=" + URLEncoder.encode(principal.getName(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw APIException.badRequests.unableToEncodeString(principal.getName(),
                            e);
                }
                break;
        }

        String endpoint = null;

        int attempts = 0;
        while (attempts < _MAX_VALIDATION_RETRIES) {
            _log.debug("Validation attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator authSvcClientItr = new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator, _coordinator);
            try {
                if (authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();
                    _log.info("isValidPrincipal(): {}", endpoint);

                    final ClientResponse response = authSvcClientItr.get(URI.create(_URI_VALIDATE + queryParams));
                    final int status = response.getStatus();

                    _log.debug("Status: {}", status);

                    if (status == ClientResponse.Status.OK.getStatusCode()) {
                        return true;
                    } else if (status == ClientResponse.Status.BAD_REQUEST.getStatusCode()
                            || status == ClientResponse.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                        ServiceErrorRestRep errorXml = response
                                .getEntity(ServiceErrorRestRep.class);
                        error.append(errorXml.getDetailedMessage());
                        return false;
                    } else {
                        _log.info("Unexpected response code {}.", status);
                    }
                }
            } catch (Exception e) {
                _log.info("Exception connecting to {}. ", endpoint, e);
            }
        }

        return false;
    }

    /**
     * Validates the principals within the tenant
     * 
     * @param principalsToValidate
     * @param error
     *            :a string representing that error that happened.
     * @return true if all the principal are valid within the tenant
     */
    public static boolean validatePrincipals(PrincipalsToValidate principalsToValidate,
            StringBuilder error) {

        String endpoint = null;
        principalsToValidate.setUsers(deDuplicate(principalsToValidate.getUsers()));
        principalsToValidate.setGroups(deDuplicate(principalsToValidate.getGroups()));
        principalsToValidate.setAltTenantUsers(deDuplicate(principalsToValidate.getAltTenantUsers()));

        int attempts = 0;
        while (attempts < _MAX_VALIDATION_RETRIES) {
            _log.debug("Validation attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator authSvcClientItr =
                    new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator,
                            _coordinator);
            try {
                if (authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();
                    _log.info("validatePrincipals(): {}", endpoint);

                    final ClientResponse response =
                            authSvcClientItr.post(_URI_VALIDATE_PRINCIPALS,
                                    principalsToValidate);
                    final int status = response.getStatus();

                    _log.debug("Status: {}", status);

                    if (status == ClientResponse.Status.OK.getStatusCode()) {
                        return true;
                    } else if (status == ClientResponse.Status.BAD_REQUEST
                            .getStatusCode()
                            || status == ClientResponse.Status.INTERNAL_SERVER_ERROR
                                    .getStatusCode()) {
                        ServiceErrorRestRep errorXml =
                                response.getEntity(ServiceErrorRestRep.class);
                        error.append(errorXml.getDetailedMessage());
                        return false;
                    } else {
                        _log.info("Unexpected response code {}.", status);
                    }
                }
            } catch (Exception e) {
                _log.info("Exception connecting to {}. ", endpoint, e);
                if (e.getMessage().contains("Read timed out")) {
                    throw InternalServerErrorException.internalServerErrors.authTimeout();
                }
            }
        }

        return false;
    }

    /**
     * Sends an internal api call to authsvc to validate authentication provider
     * basic connectivity parameters
     * 
     * @param param has the basic connectivity parameters
     * @param errorString will be set to an error message if the validation fails
     * @return true if validation succeeded. False otherwise.
     */
    public static boolean isUsableAuthenticationProvider(AuthnProviderParamsToValidate param,
            StringBuilder errorString) {
        String endpoint = null;
        int attempts = 0;
        while (attempts < _MAX_VALIDATION_RETRIES) {
            _log.debug("Validation attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator authSvcClientItr = new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator, _coordinator);
            try {
                if (authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();
                    _log.info("isAuthenticationProvider(): {}", endpoint);

                    final ClientResponse response = authSvcClientItr.post(URI.create(_URI_VALIDATE_AUTHNPROVIDER.toString()), param);
                    final int status = response.getStatus();

                    String errorRaw = response.getEntity(String.class);
                    _log.debug("Status: {}", status);
                    _log.debug("Response entity: {}", errorRaw);

                    if (status == ClientResponse.Status.OK.getStatusCode()) {
                        return true;
                    } else if (status == ClientResponse.Status.BAD_REQUEST.getStatusCode()) {
                        errorString.append(errorRaw);
                        return false;
                    } else {
                        _log.info("Unexpected response code {}.", status);
                    }
                }
            } catch (Exception e) {
                _log.info("Exception connecting to {}. ", endpoint, e);
            }
        }
        return false;
    }

    public synchronized static void setAuthSvcEndPointLocator(AuthSvcEndPointLocator authSvcEndPointLocator) {
        _authSvcEndPointLocator = authSvcEndPointLocator;
    }

    public synchronized static void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public synchronized static void setStorageOSUserRepository(StorageOSUserRepository repo) {
        _repository = repo;
    }

    /**
     * determines if a username exists in the local storageos user repository
     * 
     * @param name
     * @return true if yes, false if no
     */
    public static boolean isUserLocal(String name) {
        return _repository.isUserLocal(name);
    }

    /**
     * Make an internal REST API call to the authsvc in order to get the user's
     * tenant mapping
     * 
     * @param username
     * @return List of tenancies the user maps to with the applied mapping
     */

    public static UserTenantList getUserTenants(String username) {
        return getUserTenants(username, null);
    }

    public static UserTenantList getUserTenants(String username, TenantOrg tenant) {
        String endpoint = null;
        int attempts = 0;
        while (attempts < _MAX_VALIDATION_RETRIES) {
            _log.debug("Get user tenants attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator authSvcClientItr = new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator, _coordinator);
            try {
                if (authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();

                    //
                    String queryParameters = "?username=" + username;
                    if (tenant != null) {
                        queryParameters += "&tenantURI=" + tenant.getId();
                        if (tenant.getUserMappings() != null) {
                            String userMappingStr = MarshallUtil.convertTenantUserMappingToString(tenant);
                            String encodedUserMapping = URLEncoder.encode(userMappingStr);
                            queryParameters += "&usermappings=" + encodedUserMapping;
                        }
                    }

                    final ClientResponse response = authSvcClientItr.get(URI.create(_URI_USERTENANT + queryParameters));
                    final int status = response.getStatus();

                    _log.debug("Status: {}", status);

                    if (status == ClientResponse.Status.OK.getStatusCode()) {
                        return response.getEntity(UserTenantList.class);
                    } else if (status == ClientResponse.Status.BAD_REQUEST.getStatusCode()) {
                        throw APIException.badRequests.theParametersAreNotValid(response.hasEntity() ? response.getEntity(String.class)
                                : "Bad request");
                    } else {
                        _log.info("Unexpected response code {}.", status);
                    }
                }
            } catch (APIException e) {
                throw e;
            } catch (Exception e) {
                _log.info("Exception connecting to {}. ", endpoint, e);
            }
        }
        throw SecurityException.retryables
                .requiredServiceUnvailable(ServiceLocatorInfo.AUTH_SVC.getServiceName());
    }

    /**
     * Make an internal REST API call to the authsvc in order to reload the user in the
     * DB.
     * 
     * @param username
     */
    public static void refreshUser(String username) {
        String endpoint = null;
        int attempts = 0;
        while (attempts < _MAX_VALIDATION_RETRIES) {
            _log.debug("Refresh user, attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator authSvcClientItr =
                    new AuthSvcInternalApiClientIterator(
                            _authSvcEndPointLocator, _coordinator);
            try {
                if (authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();

                    final ClientResponse response =
                            authSvcClientItr
                                    .put(URI.create(_URI_REFRESH + "?username="
                                            + URLEncoder.encode(username, "UTF-8")), null);
                    final int status = response.getStatus();

                    _log.debug("Status: {}", status);

                    if (status == ClientResponse.Status.OK.getStatusCode()) {
                        return;
                    } else if (status == ClientResponse.Status.BAD_REQUEST
                            .getStatusCode()) {
                        throw APIException.badRequests.principalSearchFailed(username);
                    } else if (status == ClientResponse.Status.INTERNAL_SERVER_ERROR
                            .getStatusCode()) {
                        ServiceErrorRestRep error =
                                response.getEntity(ServiceErrorRestRep.class);
                        // if we got here, it means that we refresh user has failed
                        throw SecurityException.fatals.failedToRefreshUser(error
                                .getDetailedMessage());
                    } else {
                        _log.error("Unexpected response code {}.", status);
                    }
                }
            } catch (APIException e) {
                throw e;
            } catch (FatalSecurityException e) {
                throw e;
            } catch (Exception e) {
                _log.info("Exception connecting to {}. ", endpoint, e);
            }
        }
        throw SecurityException.retryables
                .requiredServiceUnvailable(ServiceLocatorInfo.AUTH_SVC.getServiceName());
    }

    /**
     * remove duplicated string from a list
     * @param input
     * @return
     */

    private static List<String> deDuplicate(List<String> input) {
	if (input == null) {
            return input;
        }

        HashSet hs = new HashSet();
        hs.addAll(input);

        List<String> result = new ArrayList<String>();
        result.addAll(hs);
        return result;
    }
}
