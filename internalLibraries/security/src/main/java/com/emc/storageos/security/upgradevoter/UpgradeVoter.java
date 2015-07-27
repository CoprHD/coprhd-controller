/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.upgradevoter;

import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

/**
 * Interface exposed by syssvc upgrade service for other high level
 * components(e.g geosvc) to disallow upgrade in some situations.
 *  
 */
public interface UpgradeVoter {

    /**
     * Do upgrade check. A BadRequestException should be thrown for disallowed
     * upgrade.
     * 
     * @param currentVersion current ViPR version before upgrade
     * @param targetVersion target ViPR version for upgrade
     */
    public void isOKForUpgrade(String currentVersion, String targetVersion) throws BadRequestException;
}
