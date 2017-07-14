/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.vmax3.smc.basetype.AbstractManager;
import com.emc.storageos.driver.vmax3.smc.basetype.AuthenticationInfo;

public class VolumeManager extends AbstractManager {
    private static final Logger LOG = LoggerFactory.getLogger(VolumeManager.class);

    static class EndPointHolder {
        public final static String LIST_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume";
        public final static String GET_VOLUME_URL = "/sloprovisioning/symmetrix/%s/volume/%s";
    }

    /**
     * @param authenticationInfo
     */
    public VolumeManager(AuthenticationInfo authenticationInfo) {
        super(authenticationInfo);
    }

}
