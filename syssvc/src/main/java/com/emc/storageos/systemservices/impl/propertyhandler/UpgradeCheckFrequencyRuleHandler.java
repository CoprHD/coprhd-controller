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

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

/**
 * This class serves as an extra validator for the system_update_check_frequency_hours property
 */
public class UpgradeCheckFrequencyRuleHandler implements UpdateHandler {

    /**
     * check if new system_update_check_frequency_hours value is greater than 0, if not fail the property update.
     *
     * @param oldProps
     * @param newProps
     */
    @Override
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        String newValue = newProps.getProperty(Constants.SYSTEM_UPDATE_CHECK_FREQUENCY_HOURS);
        if (newValue == null) {
            return;
        }

        int intNewValue = Integer.parseInt(newValue);
        if (intNewValue <= 0) {
            throw BadRequestException.badRequests.upgradeCheckFrequencyNotPositive();
        }
    }

    /**
     * do nothing
     *
     * @param oldProps
     * @param newProps
     */
    @Override
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
    }
}
