/*
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
