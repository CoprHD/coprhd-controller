/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.helpers;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.errorhandling.ServiceErrorRestRep;
import com.emc.storageos.security.authentication.AuthSvcEndPointLocator;
import com.emc.storageos.security.authentication.AuthSvcInternalApiClientIterator;
import com.emc.storageos.security.authentication.ServiceLocatorInfo;
import com.emc.storageos.security.exceptions.SecurityException;
import com.emc.storageos.security.resource.UserInfoPage.UserDetails;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Client class for a user's internal APIs, such as getting a user's groups.
 */
public class UserInfoHelper {

    private static final int _MAX_VALIDATION_RETRIES = 5;
    private static final Logger _log = LoggerFactory.getLogger(UserInfoHelper.class);
    private static final URI _URI_GET_USER_GROUPS = URI.create("/internal/userDetails");
    private final AuthSvcEndPointLocator _authSvcEndPointLocator;
    private final CoordinatorClient _coordinator;

    public UserInfoHelper(CoordinatorClient coordinator) {
        _coordinator = coordinator;
        _authSvcEndPointLocator = new AuthSvcEndPointLocator();
        _authSvcEndPointLocator.setCoordinator(_coordinator);
    }

    /**
     * Gets the groups a user is a member of.
     * 
     * @param username the name of the user
     * @return UserGroupList
     */
    public UserDetails getUserDetails(String username, StringBuilder error) {

        String endpoint = null;

        String param;
        try {
            param = "?username=" + URLEncoder.encode(username, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw APIException.badRequests.unableToEncodeString(username, e);
        }

        int attempts = 0;
        while (attempts < _MAX_VALIDATION_RETRIES) {
            _log.debug("Get user details attempt {}", ++attempts);
            AuthSvcInternalApiClientIterator authSvcClientItr =
                    new AuthSvcInternalApiClientIterator(_authSvcEndPointLocator, _coordinator);
            try {
                if (authSvcClientItr.hasNext()) {
                    endpoint = authSvcClientItr.peek().toString();
                    _log.info("getUserDetails(): {}", endpoint);

                    final ClientResponse response =
                            authSvcClientItr
                                    .get(URI.create(_URI_GET_USER_GROUPS + param));
                    final int status = response.getStatus();

                    _log.debug("Status: {}", status);

                    if (status == ClientResponse.Status.OK.getStatusCode()) {
                        return response.getEntity(UserDetails.class);
                    } else if (status == ClientResponse.Status.BAD_REQUEST
                            .getStatusCode()
                            || status == ClientResponse.Status.INTERNAL_SERVER_ERROR
                                    .getStatusCode()) {
                        ServiceErrorRestRep errorXml =
                                response.getEntity(ServiceErrorRestRep.class);
                        error.append(errorXml.getDetailedMessage());
                        return null;
                    } else {
                        _log.warn("Unexpected response code {}.", status);
                    }
                }
            } catch (Exception e) {
                _log.error(
                        "Exception while getting user groups. Details: "
                                + e.getLocalizedMessage(), e);
            }
        }

        throw SecurityException.retryables
                .requiredServiceUnvailable(ServiceLocatorInfo.AUTH_SVC.getServiceName());
    }

}
