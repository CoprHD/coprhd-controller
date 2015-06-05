/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

/**
 * 
 */
public enum ServiceLocatorInfo {
    AUTH_SVC("authsvc", "1"), 
    SYS_SVC("syssvc", "1");

    private final String serviceName;
    private final String serviceVersion;

    private ServiceLocatorInfo(String serviceName, String serviceVersion) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
    }

    /**
     * @return the serviceName
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * @return the serviceVersion
     */
    public String getServiceVersion() {
        return serviceVersion;
    }

}
