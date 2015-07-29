/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PasswordChangeIntervalRuleHandler implements UpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(PasswordChangeIntervalRuleHandler.class);

    private String _propertyName = Constants.PASSWORD_CHANGE_INTERVAL;

    public String getPropertyName() {
        return _propertyName;
    }

    /**
     * check if new password_change_interval value is between range [0, 1440), if not fail the property update.
     * 
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        String newValue = newProps.getProperty(getPropertyName());
        if (newValue == null) {
            return;
        }

        int intNewValue = Integer.parseInt(newValue);
        if (intNewValue < 0 || intNewValue > Constants.MAX_PASSWORD_CHANGE_INTERVAL_IN_MINUTES) {
            throw BadRequestException.badRequests.passwordIntervalNotInRange(
                    Constants.MIN_PASSWORD_CHANGE_INTERVAL_IN_MINUTES,
                    Constants.MAX_PASSWORD_CHANGE_INTERVAL_IN_MINUTES);
        }

    }

    /**
     * do nothing
     * 
     * @param oldProps
     * @param newProps
     */
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {

    }

}
