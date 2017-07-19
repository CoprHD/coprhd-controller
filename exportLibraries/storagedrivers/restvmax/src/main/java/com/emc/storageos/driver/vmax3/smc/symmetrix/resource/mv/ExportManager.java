/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.mv;

import com.emc.storageos.driver.vmax3.smc.basetype.DefaultManager;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;

/**
 * @author fengs5
 *
 */
public class ExportManager extends DefaultManager {

    /**
     * @param authenticationInfo
     */
    public ExportManager(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

}
