/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.smc.symmetrix.resource.mv;

import com.emc.storageos.driver.univmax.smc.basetype.AuthenticationInfo;
import com.emc.storageos.driver.univmax.smc.basetype.DefaultResEngine;

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
