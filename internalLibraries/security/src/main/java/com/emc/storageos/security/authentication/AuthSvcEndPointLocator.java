/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.authentication;


/**
 * Class for looking up registered authsvc endpoints in coordinator
 */
public class AuthSvcEndPointLocator extends EndPointLocator {

    public AuthSvcEndPointLocator() {
        setServiceLocatorInfo(ServiceLocatorInfo.AUTH_SVC);
    }
}
