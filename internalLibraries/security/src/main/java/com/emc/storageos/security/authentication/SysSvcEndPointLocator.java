/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.authentication;

/**
 * Class for looking up registered syssvc endpoints in coordinator
 */
public class SysSvcEndPointLocator extends EndPointLocator {

    public SysSvcEndPointLocator() {
        setServiceLocatorInfo(ServiceLocatorInfo.SYS_SVC);
    }
}
