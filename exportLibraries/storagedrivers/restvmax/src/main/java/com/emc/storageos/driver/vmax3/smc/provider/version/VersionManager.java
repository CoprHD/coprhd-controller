/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.provider.version;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractManager;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;

public class VersionManager extends AbstractManager {

    /**
     * @param authenticationInfo
     */
    public VersionManager(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

}
