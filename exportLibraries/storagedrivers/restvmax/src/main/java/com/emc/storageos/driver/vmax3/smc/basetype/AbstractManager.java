/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.basetype;

import com.emc.storageos.driver.vmax3.restengine.RestEngine;
import com.emc.storageos.driver.vmax3.utils.UrlGenerator;

public class AbstractManager {

    protected AuthenticationInfo authenticationInfo;
    protected UrlGenerator urlGenerator;
    protected RestEngine engine;

    /**
     * @param host
     * @param port
     * @param userName
     * @param password
     */
    public AbstractManager(String host, Integer port, String userName, String password) {
        this(new AuthenticationInfo(host, port, userName, password));
    }

    /**
     * @param authenticationInfo
     */
    public AbstractManager(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.urlGenerator = new UrlGenerator(authenticationInfo);
        engine = new RestEngine(authenticationInfo);
    }

    /**
     * @return the authenticationInfo
     */
    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

}
