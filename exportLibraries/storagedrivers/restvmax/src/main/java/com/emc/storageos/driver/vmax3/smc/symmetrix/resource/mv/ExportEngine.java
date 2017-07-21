/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.mv;

import com.emc.storageos.driver.vmax3.smc.basetype.DefaultResEngine;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;

/**
 * @author fengs5
 *
 */
public class ExportEngine extends DefaultResEngine {

    /**
     * @param authenticationInfo
     */
    public ExportEngine(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

}
