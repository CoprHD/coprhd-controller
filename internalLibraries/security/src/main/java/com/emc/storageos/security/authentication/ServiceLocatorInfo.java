/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
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
