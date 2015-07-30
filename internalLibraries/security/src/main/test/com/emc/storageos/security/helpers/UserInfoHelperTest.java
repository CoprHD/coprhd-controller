/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.helpers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.security.resource.UserInfoPage.UserDetails;

/**
 * A test class to test the UserHelper class. This can only be run one a devkit. Note that
 * prior to running this test you will need to add the relevant authentication provider.
 */
public class UserInfoHelperTest {

    private final String USER_WRONG_DOMAIN_CONFIG = "USER_WRONG_DOMAIN";
    private final String USER_DOESNT_EXIST_CONFIG = "USER_DOESNT_EXIST";
    private final String EXISTING_USER_CONFIG = "EXISTING_USER";
    private final String EXISTING_USER_NUM_OF_GROUPS_CONFIG =
            "EXISTING_USER_NUM_OF_GROUPS";

    private final String _server = "localhost";
    private final String _coordinatorServer = "coordinator://" + _server + ":2181";
    private final CoordinatorClientImpl _coordinatorClient = new CoordinatorClientImpl();

    private String user_in_wrong_domain = "invaliduser@invalidDomain.com";
    private String user_doesnt_exist =
            "iShouldntExistAnywhereInTheWholeWideWorld@sanity.local";
    private String existing_user = "userGroupsTestUser@sanity.local";
    private int num_of_groups = 3;
    private UserInfoHelper userInfoHelper;

    @Before
    public void setup() throws Exception {
        List<URI> uri = new ArrayList<URI>();
        uri.add(URI.create(_coordinatorServer));
        ZkConnection connection = new ZkConnection();
        connection.setServer(uri);
        connection.build();

        _coordinatorClient.setZkConnection(connection);
        _coordinatorClient.start();

        String envVar = System.getenv(USER_WRONG_DOMAIN_CONFIG);
        if (StringUtils.isNotBlank(envVar)) {
            user_in_wrong_domain = envVar;
        }
        envVar = System.getenv(USER_DOESNT_EXIST_CONFIG);
        if (StringUtils.isNotBlank(envVar)) {
            user_doesnt_exist = envVar;
        }
        envVar = System.getenv(EXISTING_USER_CONFIG);
        if (StringUtils.isNotBlank(envVar)) {
            existing_user = envVar;
        }
        envVar = System.getenv(EXISTING_USER_NUM_OF_GROUPS_CONFIG);
        if (StringUtils.isNotBlank(envVar)) {
            try {
                num_of_groups = Integer.parseInt(envVar);
            } catch (NumberFormatException e) {
                num_of_groups = 3;
            }
        }
        userInfoHelper = new UserInfoHelper(_coordinatorClient);
    }

    @Test
    public void testGetUserDetails() throws Exception {
        // look for a user with an unsupported domain
        String principalSearchFailedFormat =
                "Search for %s failed for this tenant, or could not be found for this tenant.";
        String user = user_in_wrong_domain;
        StringBuilder error = new StringBuilder();
        UserDetails userDetails = userInfoHelper.getUserDetails(user, error);

        String actualError = error.toString();
        String expectedError = String.format(principalSearchFailedFormat, user);

        Assert.assertNull(userDetails);
        Assert.assertEquals("Got an unexpected error. Error: " + actualError,
                expectedError, actualError);

        // look for a user that doesn't exist
        user = user_doesnt_exist;
        error = new StringBuilder();
        userDetails = userInfoHelper.getUserDetails(user, error);
        Assert.assertNull(userDetails);
        Assert.assertEquals("Got an unexpected error. Error: " + actualError,
                expectedError, actualError);

        // look for a user that does exist
        user = existing_user;
        error = new StringBuilder();
        userDetails = userInfoHelper.getUserDetails(user, error);
        Assert.assertNotNull(userDetails);
        Assert.assertEquals(
                "The groups " + user + " is a member of are: "
                        + StringUtils.join(userDetails.getUserGroupList(), ", "),
                num_of_groups, userDetails.getUserGroupList().size());
        Assert.assertNotNull(userDetails.getTenant());

    }

}
